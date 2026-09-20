from __future__ import annotations

import json
import os
import queue
import socket
import threading
import traceback
import uuid
from dataclasses import asdict, dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from tkinter import LEFT, RIGHT, X, Button, Frame, Label, Tk

from engine import AnalysisCancelled, Chapter, VideoOnlyAnalyzer

APP_VERSION = "0.1.0"
HTTP_PORT = 8765
DISCOVERY_PORT = 8764
DISCOVERY_MAGIC = b"SOHR_DISCOVER_V1"


def app_dir() -> Path:
    base = os.environ.get("LOCALAPPDATA") or str(Path.home())
    path = Path(base) / "SOHR Analyzer"
    path.mkdir(parents=True, exist_ok=True)
    return path


def local_ipv4() -> str:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("1.1.1.1", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


@dataclass
class JobState:
    id: str
    video_id: str
    duration_seconds: int
    status: str = "queued"
    progress: int = 0
    message: str = "В очереди"
    chapters: list[dict] | None = None
    error: str | None = None


class State:
    def __init__(self):
        self.dir = app_dir()
        self.results = self.dir / "results"
        self.results.mkdir(exist_ok=True)
        self.token_path = self.dir / "device-token.txt"
        if self.token_path.exists():
            self.token = self.token_path.read_text("utf-8").strip()
        else:
            self.token = uuid.uuid4().hex
            self.token_path.write_text(self.token, "utf-8")
        self.jobs: dict[str, JobState] = {}
        self.jobs_lock = threading.Lock()
        self.queue: queue.Queue[JobState] = queue.Queue()
        self.stop = threading.Event()
        self.analyzer = VideoOnlyAnalyzer(self.dir)
        self.listeners = []

    def emit(self):
        for fn in tuple(self.listeners):
            try:
                fn()
            except Exception:
                pass

    def result_path(self, vod_id: str) -> Path:
        return self.results / f"{vod_id}.json"

    def cached(self, vod_id: str):
        p = self.result_path(vod_id)
        if not p.exists():
            return None
        try:
            return json.loads(p.read_text("utf-8"))
        except Exception:
            return None

    def save(self, vod_id: str, chapters: list[Chapter]):
        payload = {
            "version": 1,
            "analyzerVersion": APP_VERSION,
            "videoId": vod_id,
            "chapters": [asdict(x) for x in chapters],
        }
        self.result_path(vod_id).write_text(
            json.dumps(payload, ensure_ascii=False, indent=2),
            "utf-8",
        )
        return payload


STATE = State()


class Handler(BaseHTTPRequestHandler):
    server_version = "SOHRAnalyzer/0.1"

    def _json(self, code: int, obj):
        data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def _auth(self) -> bool:
        if self.path == "/health":
            return True
        return self.headers.get("X-SOHR-Token", "") == STATE.token

    def do_GET(self):
        if not self._auth():
            return self._json(401, {"error": "unauthorized"})

        if self.path == "/health":
            return self._json(
                200,
                {
                    "ok": True,
                    "name": "SOHR Analyzer",
                    "version": APP_VERSION,
                    "mode": "video-only",
                },
            )

        if self.path.startswith("/v1/jobs/"):
            job_id = self.path.rsplit("/", 1)[-1]
            with STATE.jobs_lock:
                job = STATE.jobs.get(job_id)
            return self._json(
                200 if job else 404,
                asdict(job) if job else {"error": "not_found"},
            )

        if self.path.startswith("/v1/results/"):
            vod = self.path.rsplit("/", 1)[-1]
            cached = STATE.cached(vod)
            return self._json(
                200 if cached else 404,
                cached or {"error": "not_found"},
            )

        return self._json(404, {"error": "not_found"})

    def do_POST(self):
        if not self._auth():
            return self._json(401, {"error": "unauthorized"})

        if self.path != "/v1/analyze":
            return self._json(404, {"error": "not_found"})

        try:
            n = int(self.headers.get("Content-Length") or "0")
            body = json.loads(self.rfile.read(n).decode("utf-8"))
            vod = str(body.get("video_id") or "").strip()
            duration = int(body.get("duration_seconds") or 0)
            force = bool(body.get("force") or False)

            if not vod.isdigit() or duration <= 0:
                return self._json(400, {"error": "bad_request"})

            if not force:
                cached = STATE.cached(vod)
                if cached:
                    return self._json(
                        200,
                        {"cached": True, "result": cached},
                    )

            job = JobState(
                id=uuid.uuid4().hex,
                video_id=vod,
                duration_seconds=duration,
            )
            with STATE.jobs_lock:
                STATE.jobs[job.id] = job

            STATE.queue.put(job)
            STATE.emit()

            return self._json(
                202,
                {"cached": False, "job_id": job.id},
            )
        except Exception as e:
            return self._json(400, {"error": str(e)})

    def log_message(self, *_):
        pass


def worker():
    while not STATE.stop.is_set():
        try:
            job = STATE.queue.get(timeout=0.3)
        except queue.Empty:
            continue

        job.status = "running"
        job.progress = 1
        job.message = "Запускаем анализ"
        STATE.emit()

        def progress(p: int, msg: str):
            job.progress = max(0, min(99, int(p)))
            job.message = msg
            STATE.emit()

        try:
            chapters = STATE.analyzer.analyze(
                job.video_id,
                job.duration_seconds,
                progress,
                STATE.stop,
            )
            result = STATE.save(job.video_id, chapters)
            job.chapters = result["chapters"]
            job.progress = 100
            job.status = "done"
            job.message = f"Готово • найдено видео: {len(chapters)}"
        except AnalysisCancelled:
            job.status = "cancelled"
            job.message = "Остановлено"
        except Exception as e:
            job.status = "error"
            job.error = f"{type(e).__name__}: {e}"
            job.message = "Ошибка анализа"
            (STATE.dir / "last-error.txt").write_text(
                traceback.format_exc(),
                "utf-8",
            )
        finally:
            STATE.emit()
            STATE.queue.task_done()


def discovery_server():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.bind(("", DISCOVERY_PORT))
    sock.settimeout(0.5)

    while not STATE.stop.is_set():
        try:
            data, addr = sock.recvfrom(2048)
        except socket.timeout:
            continue
        except OSError:
            break

        if data.strip() != DISCOVERY_MAGIC:
            continue

        payload = json.dumps(
            {
                "type": "SOHR_ANALYZER_V1",
                "name": "SOHR Analyzer",
                "version": APP_VERSION,
                "ip": local_ipv4(),
                "port": HTTP_PORT,
                "token": STATE.token,
            }
        ).encode("utf-8")

        try:
            sock.sendto(payload, addr)
        except OSError:
            pass

    sock.close()


def http_server():
    server = ThreadingHTTPServer(("0.0.0.0", HTTP_PORT), Handler)
    server.timeout = 0.5

    while not STATE.stop.is_set():
        server.handle_request()

    server.server_close()


class App:
    def __init__(self):
        self.root = Tk()
        self.root.title("SOHR Analyzer")
        self.root.geometry("560x420")
        self.root.minsize(500, 360)
        self.root.protocol("WM_DELETE_WINDOW", self.close)

        Label(
            self.root,
            text="SOHR Analyzer",
            font=("Segoe UI", 22, "bold"),
        ).pack(pady=(22, 4))

        Label(
            self.root,
            text="VIDEO-ONLY • анализ Twitch VOD на твоём ПК",
            font=("Segoe UI", 10),
        ).pack(pady=(0, 16))

        card = Frame(self.root, bd=1, relief="groove")
        card.pack(fill=X, padx=22, pady=8)

        self.status = Label(
            card,
            text="Analyzer запущен",
            font=("Segoe UI", 12, "bold"),
            anchor="w",
        )
        self.status.pack(fill=X, padx=14, pady=(12, 3))

        self.address = Label(
            card,
            text=f"Телефон найдёт ПК автоматически • {local_ipv4()}:{HTTP_PORT}",
            font=("Segoe UI", 10),
            anchor="w",
        )
        self.address.pack(fill=X, padx=14, pady=(0, 12))

        self.job = Label(
            self.root,
            text="Ожидаю SOHR на телефоне…",
            font=("Segoe UI", 11),
            justify=LEFT,
            anchor="w",
        )
        self.job.pack(fill=X, padx=24, pady=(18, 6))

        self.progress = Label(
            self.root,
            text="",
            font=("Segoe UI", 10),
            justify=LEFT,
            anchor="w",
        )
        self.progress.pack(fill=X, padx=24, pady=(0, 14))

        help_text = (
            "1. ПК и телефон должны быть в одной Wi‑Fi/локальной сети.\n"
            "2. Оставь это окно открытым.\n"
            "3. В SOHR нажми «Анализировать на ПК».\n"
            "4. После получения глав ПК можно выключить."
        )

        Label(
            self.root,
            text=help_text,
            font=("Segoe UI", 10),
            justify=LEFT,
            anchor="w",
        ).pack(fill=X, padx=24, pady=(10, 16))

        buttons = Frame(self.root)
        buttons.pack(fill=X, padx=22, pady=8)

        Button(
            buttons,
            text="Открыть папку результатов",
            command=self.open_results,
        ).pack(side=LEFT)

        Button(
            buttons,
            text="Закрыть Analyzer",
            command=self.close,
        ).pack(side=RIGHT)

        STATE.listeners.append(self.refresh)
        self.root.after(400, self.refresh)

    def refresh(self):
        running = None
        queued = 0

        with STATE.jobs_lock:
            for job in STATE.jobs.values():
                if job.status == "running":
                    running = job
                elif job.status == "queued":
                    queued += 1

        if running:
            self.job.config(text=f"Twitch VOD {running.video_id}")
            self.progress.config(
                text=f"{running.progress}% • {running.message}"
            )
        else:
            self.job.config(
                text="Ожидаю SOHR на телефоне…"
                if not queued
                else f"В очереди: {queued}"
            )
            self.progress.config(text="")

        if self.root.winfo_exists():
            self.root.after(500, self.refresh)

    def open_results(self):
        os.startfile(str(STATE.results))

    def close(self):
        STATE.stop.set()
        try:
            self.root.destroy()
        except Exception:
            pass

    def run(self):
        self.root.mainloop()


def main():
    threading.Thread(target=http_server, daemon=True).start()
    threading.Thread(target=discovery_server, daemon=True).start()
    threading.Thread(target=worker, daemon=True).start()
    App().run()


if __name__ == "__main__":
    main()
