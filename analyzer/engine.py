from __future__ import annotations

import json
import math
import re
import subprocess
import threading
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable, Optional

import cv2
import imageio_ffmpeg
import numpy as np

try:
    from rapidocr import RapidOCR
except Exception:
    RapidOCR = None

GQL_URL = "https://gql.twitch.tv/gql"
TWITCH_WEB_CLIENT_ID = "ue6666qo983tsx6so1t0vnawi233wa"
PLAYBACK_QUERY = """
query PlaybackAccessToken_Template(
  $login: String!, $isLive: Boolean!, $vodID: ID!, $isVod: Boolean!, $playerType: String!
) {
  streamPlaybackAccessToken(
    channelName: $login,
    params: {platform: "web", playerBackend: "mediaplayer", playerType: $playerType}
  ) @include(if: $isLive) { value signature __typename }
  videoPlaybackAccessToken(
    id: $vodID,
    params: {platform: "web", playerBackend: "mediaplayer", playerType: $playerType}
  ) @include(if: $isVod) { value signature __typename }
}
"""

VIDEO_TOKENS = (
    "youtube", "youtu.be", "ютуб", "подписаться", "подписчик", "просмотров",
    "смотреть позже", "comments", "share", "autoplay", "автовоспроиз",
    "скорость воспроиз", "playback speed", "pause", "quality", "качество",
)
NOISE_TOKENS = (
    "twitch", "чат", "зрител", "донат", "руб", "₽", "следить", "подписка twitch",
)
TIME_PAIR = re.compile(
    r"(?<!\d)(?:\d{1,2}:)?\d{1,2}:\d{2}\s*[/|]\s*(?:\d{1,2}:)?\d{1,2}:\d{2}(?!\d)"
)
TIME_SINGLE = re.compile(r"(?<!\d)(?:\d{1,2}:)?\d{1,2}:\d{2}(?!\d)")


@dataclass
class Chapter:
    start_seconds: int
    end_seconds: int
    title: str
    confidence: int
    detail: str = "SOHR PC Analyzer"


@dataclass
class ScanPoint:
    t: float
    visual: np.ndarray
    hash64: int
    change: int
    ocr_text: str = ""
    ocr_lines: tuple[str, ...] = ()
    player_score: int = 0
    title: Optional[str] = None
    baseline_distance: float = 0.0


class AnalysisCancelled(Exception):
    pass


class TwitchResolver:
    @staticmethod
    def _post_json(url: str, body: dict, timeout: float = 10.0) -> dict:
        data = json.dumps(body).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=data,
            headers={
                "Client-ID": TWITCH_WEB_CLIENT_ID,
                "Content-Type": "application/json",
                "Accept": "application/json",
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SOHR-Analyzer/0.1",
            },
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return json.loads(r.read().decode("utf-8", "replace"))

    @classmethod
    def resolve_master(cls, video_id: str) -> str:
        if not video_id.isdigit():
            raise ValueError("Некорректный Twitch VOD ID")
        payload = {
            "operationName": "PlaybackAccessToken_Template",
            "query": PLAYBACK_QUERY,
            "variables": {
                "isLive": False,
                "login": "",
                "isVod": True,
                "vodID": video_id,
                "playerType": "site",
            },
        }
        root = cls._post_json(GQL_URL, payload)
        if root.get("errors"):
            raise RuntimeError(root["errors"][0].get("message") or "Twitch не выдал токен")
        token = (root.get("data") or {}).get("videoPlaybackAccessToken") or {}
        sig = token.get("signature") or ""
        value = token.get("value") or ""
        if not sig or not value:
            raise RuntimeError("Twitch не выдал доступ к VOD")
        q = urllib.parse.urlencode(
            {
                "allow_source": "true",
                "allow_audio_only": "false",
                "playlist_include_framerate": "true",
                "platform": "web",
                "player": "twitchweb",
                "supported_codecs": "h264",
                "sig": sig,
                "token": value,
            }
        )
        return f"https://usher.ttvnw.net/vod/{video_id}.m3u8?{q}"

    @staticmethod
    def choose_variant(master_url: str, target_height: int = 480) -> str:
        req = urllib.request.Request(
            master_url,
            headers={"User-Agent": "Mozilla/5.0 SOHR-Analyzer/0.1"},
        )
        with urllib.request.urlopen(req, timeout=12.0) as r:
            text = r.read().decode("utf-8", "replace")

        lines = [x.strip() for x in text.splitlines() if x.strip()]
        variants = []
        for i, line in enumerate(lines):
            if not line.startswith("#EXT-X-STREAM-INF:"):
                continue
            attrs = line.split(":", 1)[1]
            if i + 1 >= len(lines) or lines[i + 1].startswith("#"):
                continue
            url = urllib.parse.urljoin(master_url, lines[i + 1])
            m = re.search(r"RESOLUTION=(\d+)x(\d+)", attrs)
            bw = re.search(r"BANDWIDTH=(\d+)", attrs)
            width, height = (0, 0)
            if m:
                width, height = int(m.group(1)), int(m.group(2))
            bandwidth = int(bw.group(1)) if bw else 0
            if height > 0:
                variants.append((height, bandwidth, width, url))

        if not variants:
            return master_url
        under = [v for v in variants if v[0] <= target_height]
        if under:
            return max(under, key=lambda x: (x[0], x[1]))[3]
        return min(variants, key=lambda x: (x[0], x[1]))[3]


class OCR:
    def __init__(self):
        self._engine = None
        self._lock = threading.Lock()

    def _ensure(self):
        if self._engine is not None:
            return
        if RapidOCR is None:
            raise RuntimeError("RapidOCR не установлен")
        self._engine = RapidOCR(
            params={
                "Rec.lang_type": "ru",
                "Global.log_level": "warning",
                "Global.text_score": 0.45,
            }
        )

    @staticmethod
    def _extract_lines(result) -> list[str]:
        if result is None:
            return []
        for attr in ("txts", "texts"):
            value = getattr(result, attr, None)
            if value is not None:
                return [str(x).strip() for x in value if str(x).strip()]
        if isinstance(result, dict):
            for key in ("txts", "texts", "text"):
                value = result.get(key)
                if isinstance(value, (list, tuple)):
                    return [str(x).strip() for x in value if str(x).strip()]
        if isinstance(result, (list, tuple)):
            out = []
            for item in result:
                if isinstance(item, (list, tuple)) and len(item) >= 2 and isinstance(item[1], str):
                    out.append(item[1].strip())
            return [x for x in out if x]
        return []

    def run(self, frame_bgr: np.ndarray) -> tuple[str, tuple[str, ...]]:
        with self._lock:
            self._ensure()
            result = self._engine(frame_bgr)
        lines = self._extract_lines(result)
        return " ".join(lines), tuple(lines)


def difference_hash(frame: np.ndarray) -> int:
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    small = cv2.resize(gray, (9, 8), interpolation=cv2.INTER_AREA)
    value = 0
    bit = 0
    for y in range(8):
        for x in range(8):
            if int(small[y, x]) > int(small[y, x + 1]):
                value |= 1 << bit
            bit += 1
    return value


def hamming(a: int, b: int) -> int:
    return int((a ^ b).bit_count())


def visual_feature(frame: np.ndarray) -> np.ndarray:
    h, w = frame.shape[:2]
    crop = frame[: max(1, int(h * 0.90)), : max(1, int(w * 0.82))]
    tiny = cv2.resize(crop, (24, 14), interpolation=cv2.INTER_AREA)
    hsv = cv2.cvtColor(tiny, cv2.COLOR_BGR2HSV)
    rgb = (tiny.astype(np.float32) / 255.0).reshape(-1)
    hist = cv2.calcHist([hsv], [0, 1], None, [12, 6], [0, 180, 0, 256]).reshape(-1)
    hist = hist / max(float(hist.sum()), 1.0)
    gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
    edge = cv2.Canny(gray, 70, 150)
    grid = cv2.resize(edge, (12, 7), interpolation=cv2.INTER_AREA).astype(np.float32).reshape(-1) / 255.0
    return np.concatenate([rgb, hist.astype(np.float32), grid])


def player_evidence(text: str, lines: Iterable[str]) -> tuple[int, Optional[str]]:
    clean = re.sub(r"\s+", " ", text).strip()
    low = clean.lower()
    score = 0
    if any(token in low for token in VIDEO_TOKENS):
        score += 5
    if TIME_PAIR.search(clean):
        score += 5
    elif len(TIME_SINGLE.findall(clean)) >= 2:
        score += 3
    if any(x in low for x in ("play", "pause", "автовоспроиз", "скорость", "quality", "качество")):
        score += 3
    if any(token in low for token in NOISE_TOKENS):
        score -= 1

    candidates = []
    for raw in lines:
        line = re.sub(r"\s+", " ", raw).strip(" |•—-:;")
        if not (10 <= len(line) <= 110):
            continue
        if sum(ch.isalpha() for ch in line) < 6:
            continue
        ll = line.lower()
        if any(token in ll for token in VIDEO_TOKENS + NOISE_TOKENS):
            continue
        if "http://" in ll or "https://" in ll or "www." in ll:
            continue
        alpha = sum(ch.isalpha() for ch in line)
        words = len([x for x in line.split() if len(x) > 1])
        candidates.append((alpha + words * 4 + (12 if 14 <= len(line) <= 80 else 0), line))
    title = max(candidates, default=(0, None))[1]
    return score, title


class VideoOnlyAnalyzer:
    def __init__(self, data_dir: Path):
        self.data_dir = data_dir
        self.data_dir.mkdir(parents=True, exist_ok=True)
        self.ocr = OCR()

    @staticmethod
    def _ffmpeg() -> str:
        return imageio_ffmpeg.get_ffmpeg_exe()

    @staticmethod
    def _cancelled(cancel_event: Optional[threading.Event]):
        if cancel_event is not None and cancel_event.is_set():
            raise AnalysisCancelled()

    def _iter_frames(
        self,
        hls_url: str,
        fps: float,
        width: int,
        height: int,
        start: Optional[float] = None,
        duration: Optional[float] = None,
        cancel_event: Optional[threading.Event] = None,
    ):
        cmd = [self._ffmpeg(), "-hide_banner", "-loglevel", "error"]
        if start is not None:
            cmd += ["-ss", f"{max(0.0, start):.3f}"]
        cmd += ["-hwaccel", "auto", "-i", hls_url]
        if duration is not None:
            cmd += ["-t", f"{max(0.1, duration):.3f}"]
        vf = (
            f"fps={fps},"
            f"scale={width}:{height}:force_original_aspect_ratio=decrease,"
            f"pad={width}:{height}:(ow-iw)/2:(oh-ih)/2"
        )
        cmd += ["-an", "-vf", vf, "-pix_fmt", "bgr24", "-f", "rawvideo", "pipe:1"]
        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            stdin=subprocess.DEVNULL,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
        frame_bytes = width * height * 3
        idx = 0
        try:
            while True:
                self._cancelled(cancel_event)
                buf = proc.stdout.read(frame_bytes) if proc.stdout else b""
                if len(buf) < frame_bytes:
                    break
                arr = np.frombuffer(buf, dtype=np.uint8).reshape((height, width, 3)).copy()
                base = start or 0.0
                yield base + idx / fps, arr
                idx += 1
        finally:
            if proc.poll() is None:
                proc.kill()
            try:
                proc.wait(timeout=2)
            except Exception:
                pass

    def _coarse_scan(
        self,
        hls_url: str,
        duration_seconds: int,
        progress: Callable[[int, str], None],
        cancel_event: Optional[threading.Event],
    ) -> list[ScanPoint]:
        fps = 0.5
        points: list[ScanPoint] = []
        prev_hash = 0
        total = max(int(duration_seconds * fps), 1)

        progress(8, "Читаем весь VOD одним проходом…")
        for idx, (t, frame) in enumerate(
            self._iter_frames(hls_url, fps, 480, 270, cancel_event=cancel_event)
        ):
            h = difference_hash(frame)
            change = hamming(prev_hash, h) if points else 0
            point = ScanPoint(
                t=t,
                visual=visual_feature(frame),
                hash64=h,
                change=change,
            )
            if idx % 2 == 0 or change >= 20:
                try:
                    text, lines = self.ocr.run(frame)
                    score, title = player_evidence(text, lines)
                    point.ocr_text = text
                    point.ocr_lines = lines
                    point.player_score = score
                    point.title = title
                except Exception:
                    pass
            points.append(point)
            prev_hash = h
            if idx % 12 == 0:
                pct = 8 + min(58, int((idx / total) * 58))
                progress(pct, f"Просмотрено {int(t // 60)} мин из {max(1, duration_seconds // 60)}")

        return points

    @staticmethod
    def _build_baseline(points: list[ScanPoint]) -> np.ndarray:
        if not points:
            raise RuntimeError("Twitch не отдал кадры")
        candidates = [
            p.visual for p in points
            if p.t <= 1200 and p.player_score <= 0 and p.change <= 18
        ]
        if len(candidates) < 12:
            candidates = [p.visual for p in points[: min(len(points), 300)] if p.player_score <= 0]
        if not candidates:
            candidates = [p.visual for p in points[: min(len(points), 100)]]
        return np.median(np.stack(candidates, axis=0), axis=0)

    @staticmethod
    def _apply_baseline(points: list[ScanPoint], baseline: np.ndarray) -> tuple[float, float]:
        distances = []
        denom = math.sqrt(float(len(baseline))) or 1.0
        for p in points:
            p.baseline_distance = float(np.linalg.norm(p.visual - baseline) / denom)
            distances.append(p.baseline_distance)
        arr = np.asarray(distances, dtype=np.float32)
        med = float(np.median(arr))
        mad = float(np.median(np.abs(arr - med))) + 1e-6
        return med, mad

    @staticmethod
    def _segments(points: list[ScanPoint], baseline_med: float, baseline_mad: float) -> list[tuple[int, int]]:
        if not points:
            return []
        far_threshold = baseline_med + max(0.045, baseline_mad * 3.8)
        near_threshold = baseline_med + max(0.025, baseline_mad * 2.0)

        out = []
        active = None
        pending = []
        weak = 0
        last_video_like = -1

        for i, p in enumerate(points):
            explicit = p.player_score >= 5
            moderate = p.player_score >= 3
            far = p.baseline_distance >= far_threshold
            dynamic = p.change >= 13
            start_signal = explicit or (moderate and (far or dynamic))

            if active is None:
                if start_signal:
                    pending.append(i)
                    pending = [x for x in pending if p.t - points[x].t <= 10]
                    if explicit or len(pending) >= 2:
                        active = max(0, pending[0] - 2)
                        last_video_like = i
                        weak = 0
                        pending.clear()
                else:
                    if pending and p.t - points[pending[-1]].t > 10:
                        pending.clear()
                continue

            video_like = explicit or moderate or far
            if video_like:
                last_video_like = i
                weak = 0
            else:
                weak += 1

            near_baseline = p.baseline_distance <= near_threshold
            if weak >= 6 and near_baseline:
                end_i = min(i, max(active + 1, last_video_like + 1))
                out.append((active, end_i))
                active = None
                pending.clear()
                weak = 0
                last_video_like = -1

        if active is not None:
            out.append((active, len(points) - 1))

        merged = []
        for start, end in out:
            if not merged:
                merged.append([start, end])
                continue
            prev = merged[-1]
            gap = points[start].t - points[prev[1]].t
            if gap <= 18:
                prev[1] = end
            else:
                merged.append([start, end])
        return [(a, b) for a, b in merged if points[b].t - points[a].t >= 20]

    def _refine_start(
        self,
        hls_url: str,
        coarse: float,
        cancel_event: Optional[threading.Event],
    ) -> tuple[int, Optional[str]]:
        start = max(0.0, coarse - 14.0)
        best_title = None
        frames = list(self._iter_frames(hls_url, 2.0, 720, 405, start=start, duration=28, cancel_event=cancel_event))
        if not frames:
            return int(max(0, coarse)), None

        hashes = [difference_hash(f) for _, f in frames]
        scores = []
        for i, (_, frame) in enumerate(frames):
            score = 0
            title = None
            if i % 2 == 0 or (i > 0 and hamming(hashes[i - 1], hashes[i]) >= 18):
                try:
                    text, lines = self.ocr.run(frame)
                    score, title = player_evidence(text, lines)
                    if title and (best_title is None or score >= 3):
                        best_title = title
                except Exception:
                    pass
            scores.append(score)

        evidence_indices = [i for i, sc in enumerate(scores) if sc >= 3]
        if not evidence_indices:
            return int(max(0, coarse)), best_title

        first = evidence_indices[0]
        boundary = first
        floor = max(1, first - 16)
        for i in range(first, floor - 1, -1):
            if hamming(hashes[i - 1], hashes[i]) >= 18:
                boundary = i
                break
        return int(round(frames[boundary][0])), best_title

    def _refine_end(
        self,
        hls_url: str,
        coarse: float,
        baseline: np.ndarray,
        cancel_event: Optional[threading.Event],
    ) -> int:
        start = max(0.0, coarse - 14.0)
        frames = list(self._iter_frames(hls_url, 2.0, 720, 405, start=start, duration=30, cancel_event=cancel_event))
        if not frames:
            return int(max(0, coarse))

        denom = math.sqrt(float(len(baseline))) or 1.0
        near_run = 0
        first_near = None
        for i, (t, frame) in enumerate(frames):
            feat = visual_feature(frame)
            dist = float(np.linalg.norm(feat - baseline) / denom)
            try:
                text, lines = self.ocr.run(frame) if i % 4 == 0 else ("", ())
                score, _ = player_evidence(text, lines)
            except Exception:
                score = 0
            if score <= 0 and dist < 0.12:
                if first_near is None:
                    first_near = t
                near_run += 1
                if near_run >= 4:
                    return int(round(first_near))
            else:
                near_run = 0
                first_near = None
        return int(max(0, coarse))

    @staticmethod
    def _best_title(points: list[ScanPoint], start_i: int, end_i: int) -> Optional[str]:
        candidates = []
        start_t = points[start_i].t
        for p in points[start_i : min(end_i + 1, start_i + 40)]:
            if not p.title:
                continue
            weight = p.player_score * 10
            if p.t <= start_t + 60:
                weight += 20
            weight += min(len(p.title), 80)
            candidates.append((weight, p.title))
        return max(candidates, default=(0, None))[1]

    def analyze(
        self,
        video_id: str,
        duration_seconds: int,
        progress: Callable[[int, str], None],
        cancel_event: Optional[threading.Event] = None,
    ) -> list[Chapter]:
        self._cancelled(cancel_event)
        progress(2, "Получаем Twitch VOD…")
        master = TwitchResolver.resolve_master(video_id)
        low = TwitchResolver.choose_variant(master, 480)
        high = TwitchResolver.choose_variant(master, 720)

        points = self._coarse_scan(low, duration_seconds, progress, cancel_event)
        if not points:
            raise RuntimeError("Не удалось прочитать кадры Twitch VOD")

        progress(69, "Учимся отличать обычный стрим от просмотра видео…")
        baseline = self._build_baseline(points)
        med, mad = self._apply_baseline(points, baseline)
        ranges = self._segments(points, med, mad)

        chapters = []
        total = max(len(ranges), 1)
        for n, (a, b) in enumerate(ranges):
            self._cancelled(cancel_event)
            progress(72 + int((n / total) * 23), f"Уточняем ролик {n + 1} из {len(ranges)}…")
            coarse_start = points[a].t
            coarse_end = points[b].t
            precise_start, refined_title = self._refine_start(high, coarse_start, cancel_event)
            precise_end = self._refine_end(high, coarse_end, baseline, cancel_event)
            if precise_end <= precise_start + 15:
                precise_end = max(int(coarse_end), precise_start + 16)
            title = refined_title or self._best_title(points, a, b)
            chapters.append(
                Chapter(
                    start_seconds=max(0, precise_start),
                    end_seconds=max(precise_start + 1, min(duration_seconds, precise_end)),
                    title=f"Смотрит: {title}" if title else "Смотрит видео",
                    confidence=92 if title else 82,
                    detail="SOHR PC Analyzer • последовательный VOD-анализ",
                )
            )

        progress(98, f"Найдено видео: {len(chapters)}")
        return chapters
