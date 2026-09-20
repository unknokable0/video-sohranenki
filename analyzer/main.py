from __future__ import annotations

import ctypes
import json
import os
import queue
import socket
import sys
import threading
import traceback
import uuid
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import tkinter as tk
from tkinter import ttk

from engine import AnalysisCancelled, Chapter, VideoOnlyAnalyzer

APP_VERSION = "0.2.0"
HTTP_PORT = 8765
DISCOVERY_PORT = 8764
DISCOVERY_MAGIC = b"SOHR_DISCOVER_V1"
MAX_REQUEST_BYTES = 64 * 1024
RESULT_CACHE_LIMIT = 200
RESULT_CACHE_MAX_AGE_DAYS = 90

BG = "#100d16"
PANEL = "#181320"
PANEL_2 = "#20182b"
TEXT = "#f7f3ff"
MUTED = "#aa9fb8"
PURPLE = "#8b5cf6"
PURPLE_HOVER = "#9f7aea"
BORDER = "#332640"
DANGER = "#d8b4fe"


def app_dir() -> Path:
    base = os.environ.get("LOCALAPPDATA") or str(Path.home())
    path = Path(base) / "SOHR Analyzer"
    path.mkdir(parents=True, exist_ok=True)
    return path


def set_low_impact_priority() -> None:
    """Keep the analyzer responsive without stealing resources from games/apps."""
    if os.name != "nt":
        return
    try:
        BELOW_NORMAL_PRIORITY_CLASS = 0x00004000
        PROCESS_MODE_BACKGROUND_BEGIN = 0x00100000
        kernel32 = ctypes.windll.kernel32
        handle = kernel32.GetCurrentProcess()
        kernel32.SetPriorityClass(handle, BELOW_NORMAL_PRIORITY_CLASS)
        # Background mode is best-effort and may be rejected on some systems.
        kernel32.SetPriorityClass(handle, PROCESS_MODE_BACKGROUND_BEGIN)
    except Exception:
        pass


def local_ipv4() -> str:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        # UDP connect does not send traffic; it only asks Windows which interface
        # would be used for the route.
        sock.connect(("1.1.1.1", 80))
        return sock.getsockname()[0]
    except Exception:
        try:
            return socket.gethostbyname(socket.gethostname())
        except Exception:
            return "127.0.0.1"
    finally:
        sock.close()


def fmt_time(ts: float | None = None) -> str:
    dt = datetime.fromtimestamp(ts, tz=timezone.utc).astimezone() if ts else datetime.now().astimezone()
    return dt.strftime("%H:%M:%S")


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
    created_at: str = ""


class State:
    def __init__(self):
        self.dir = app_dir()
        self.results = self.dir / "results"
        self.results.mkdir(exist_ok=True)
        self.logs = self.dir / "logs"
        self.logs.mkdir(exist_ok=True)
        self.token_path = self.dir / "device-token.txt"
        if self.token_path.exists():
            self.token = self.token_path.read_text("utf-8").strip()
        else:
            self.token = uuid.uuid4().hex
            self.token_path.write_text(self.token, "utf-8")

        self.jobs: dict[str, JobState] = {}
        self.jobs_lock = threading.RLock()
        self.queue: queue.Queue[JobState] = queue.Queue()
        self.stop = threading.Event()
        self.current_cancel = threading.Event()
        self.current_job_id: str | None = None
        self.analyzer = VideoOnlyAnalyzer(self.dir)
        self.last_event = "Analyzer запущен"
        self.last_event_time = fmt_time()
        self.cleanup_results()

    def note(self, message: str) -> None:
        with self.jobs_lock:
            self.last_event = message
            self.last_event_time = fmt_time()

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
        self.cleanup_results()
        return payload

    def cleanup_results(self) -> None:
        try:
            files = [p for p in self.results.glob("*.json") if p.is_file()]
            files.sort(key=lambda p: p.stat().st_mtime, reverse=True)
            now = datetime.now(tz=timezone.utc).timestamp()
            max_age = RESULT_CACHE_MAX_AGE_DAYS * 86400
            for index, path in enumerate(files):
                too_old = (now - path.stat().st_mtime) > max_age
                too_many = index >= RESULT_CACHE_LIMIT
                if too_old or too_many:
                    try:
                        path.unlink()
                    except OSError:
                        pass
        except Exception:
            pass

    def create_job(self, vod: str, duration: int) -> JobState:
        job = JobState(
            id=uuid.uuid4().hex,
            video_id=vod,
            duration_seconds=duration,
            created_at=datetime.now(tz=timezone.utc).isoformat(),
        )
        with self.jobs_lock:
            self.jobs[job.id] = job
            self.last_event = f"VOD {vod} добавлен в очередь"
            self.last_event_time = fmt_time()
        self.queue.put(job)
        return job

    def update_job(self, job: JobState, **changes) -> None:
        with self.jobs_lock:
            for key, value in changes.items():
                setattr(job, key, value)
            if "message" in changes:
                self.last_event = str(changes["message"])
                self.last_event_time = fmt_time()

    def get_job(self, job_id: str) -> JobState | None:
        with self.jobs_lock:
            return self.jobs.get(job_id)

    def snapshot(self):
        with self.jobs_lock:
            running = next((j for j in self.jobs.values() if j.status == "running"), None)
            queued = sum(1 for j in self.jobs.values() if j.status == "queued")
            done = sum(1 for j in self.jobs.values() if j.status == "done")
            errors = sum(1 for j in self.jobs.values() if j.status == "error")
            return running, queued, done, errors, self.last_event, self.last_event_time

    def cancel_current_job(self) -> bool:
        with self.jobs_lock:
            if not self.current_job_id:
                return False
            self.current_cancel.set()
            job = self.jobs.get(self.current_job_id)
            if job:
                job.message = "Останавливаем анализ…"
            self.last_event = "Останавливаем текущий анализ…"
            self.last_event_time = fmt_time()
            return True

    def write_error(self, text: str) -> None:
        try:
            (self.dir / "last-error.txt").write_text(text, "utf-8")
            stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
            (self.logs / f"error-{stamp}.txt").write_text(text, "utf-8")
            old = sorted(self.logs.glob("error-*.txt"), key=lambda p: p.stat().st_mtime, reverse=True)
            for path in old[10:]:
                try:
                    path.unlink()
                except OSError:
                    pass
        except Exception:
            pass


STATE = State()


class Handler(BaseHTTPRequestHandler):
    server_version = "SOHRAnalyzer/0.2"

    def _json(self, code: int, obj):
        data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
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
            job = STATE.get_job(job_id)
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

        if self.path.startswith("/v1/jobs/") and self.path.endswith("/cancel"):
            job_id = self.path.split("/")[-2]
            job = STATE.get_job(job_id)
            if not job:
                return self._json(404, {"error": "not_found"})
            if STATE.current_job_id == job_id:
                STATE.cancel_current_job()
                return self._json(202, {"ok": True})
            return self._json(409, {"error": "not_running"})

        if self.path != "/v1/analyze":
            return self._json(404, {"error": "not_found"})

        try:
            n = int(self.headers.get("Content-Length") or "0")
            if n <= 0 or n > MAX_REQUEST_BYTES:
                return self._json(413, {"error": "request_too_large"})
            body = json.loads(self.rfile.read(n).decode("utf-8"))
            vod = str(body.get("video_id") or "").strip()
            duration = int(body.get("duration_seconds") or 0)
            force = bool(body.get("force") or False)

            if not vod.isdigit() or duration <= 0:
                return self._json(400, {"error": "bad_request"})

            if not force:
                cached = STATE.cached(vod)
                if cached:
                    STATE.note(f"VOD {vod}: отдали сохранённый результат")
                    return self._json(200, {"cached": True, "result": cached})

            job = STATE.create_job(vod, duration)
            return self._json(202, {"cached": False, "job_id": job.id})
        except Exception as exc:
            return self._json(400, {"error": str(exc)})

    def log_message(self, *_):
        pass


def worker():
    while not STATE.stop.is_set():
        try:
            job = STATE.queue.get(timeout=0.3)
        except queue.Empty:
            continue

        STATE.current_cancel = threading.Event()
        with STATE.jobs_lock:
            STATE.current_job_id = job.id
        STATE.update_job(job, status="running", progress=1, message="Запускаем анализ")

        def progress(p: int, msg: str):
            STATE.update_job(job, progress=max(0, min(99, int(p))), message=msg)

        try:
            chapters = STATE.analyzer.analyze(
                job.video_id,
                job.duration_seconds,
                progress,
                STATE.current_cancel,
            )
            result = STATE.save(job.video_id, chapters)
            STATE.update_job(
                job,
                chapters=result["chapters"],
                progress=100,
                status="done",
                message=f"Готово • найдено видео: {len(chapters)}",
            )
        except AnalysisCancelled:
            STATE.update_job(job, status="cancelled", message="Анализ остановлен")
        except Exception as exc:
            STATE.update_job(
                job,
                status="error",
                error=f"{type(exc).__name__}: {exc}",
                message="Ошибка анализа",
            )
            STATE.write_error(traceback.format_exc())
        finally:
            with STATE.jobs_lock:
                STATE.current_job_id = None
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
    server.daemon_threads = True
    try:
        while not STATE.stop.is_set():
            server.handle_request()
    finally:
        server.server_close()


class App:
    def __init__(self):
        self.root = tk.Tk()
        self.root.title(f"SOHR Analyzer {APP_VERSION}")
        self.root.geometry("720x560")
        self.root.minsize(660, 520)
        self.root.configure(bg=BG)
        self.root.protocol("WM_DELETE_WINDOW", self.close)
        self.root.option_add("*Font", "Segoe UI 10")
        self.ip = local_ipv4()

        self._setup_style()
        self._build_ui()
        self.root.after(100, self.refresh)

    def _setup_style(self):
        style = ttk.Style(self.root)
        try:
            style.theme_use("clam")
        except tk.TclError:
            pass
        style.configure(
            "SOHR.Horizontal.TProgressbar",
            troughcolor=PANEL_2,
            background=PURPLE,
            bordercolor=PANEL_2,
            lightcolor=PURPLE,
            darkcolor=PURPLE,
            thickness=10,
        )
        style.configure(
            "Primary.TButton",
            background=PURPLE,
            foreground="white",
            borderwidth=0,
            focusthickness=0,
            padding=(16, 10),
        )
        style.map("Primary.TButton", background=[("active", PURPLE_HOVER)])
        style.configure(
            "Secondary.TButton",
            background=PANEL_2,
            foreground=TEXT,
            bordercolor=BORDER,
            borderwidth=1,
            focusthickness=0,
            padding=(14, 9),
        )
        style.map("Secondary.TButton", background=[("active", "#2a2036")])
        style.configure(
            "Danger.TButton",
            background="#2a1b35",
            foreground=DANGER,
            bordercolor="#4b2b60",
            borderwidth=1,
            focusthickness=0,
            padding=(14, 9),
        )
        style.map("Danger.TButton", background=[("active", "#362045")])

    def _label(self, parent, text, size=10, weight="normal", color=TEXT, **kwargs):
        return tk.Label(
            parent,
            text=text,
            font=("Segoe UI", size, weight),
            fg=color,
            bg=parent.cget("bg"),
            **kwargs,
        )

    def _card(self, parent):
        return tk.Frame(parent, bg=PANEL, highlightthickness=1, highlightbackground=BORDER)

    def _build_ui(self):
        outer = tk.Frame(self.root, bg=BG)
        outer.pack(fill="both", expand=True, padx=26, pady=24)

        header = tk.Frame(outer, bg=BG)
        header.pack(fill="x")
        self._label(header, "SOHR Analyzer", 24, "bold").pack(side="left")
        version = tk.Label(
            header,
            text=f"  {APP_VERSION}  ",
            font=("Segoe UI", 9, "bold"),
            fg="#d8c7ff",
            bg="#2b1f3a",
            padx=5,
            pady=4,
        )
        version.pack(side="left", padx=(10, 0), pady=(5, 0))
        self._label(
            header,
            "Twitch VOD анализ на ПК",
            10,
            color=MUTED,
        ).pack(side="right", pady=(8, 0))

        connection = self._card(outer)
        connection.pack(fill="x", pady=(20, 12))
        row = tk.Frame(connection, bg=PANEL)
        row.pack(fill="x", padx=18, pady=16)
        self.connection_dot = tk.Canvas(row, width=12, height=12, bg=PANEL, highlightthickness=0)
        self.connection_dot.create_oval(2, 2, 10, 10, fill=PURPLE, outline=PURPLE)
        self.connection_dot.pack(side="left", padx=(0, 10))
        col = tk.Frame(row, bg=PANEL)
        col.pack(side="left", fill="x", expand=True)
        self._label(col, "Готов к подключению", 12, "bold").pack(anchor="w")
        self.address = self._label(
            col,
            f"Телефон найдёт ПК автоматически • {self.ip}:{HTTP_PORT}",
            9,
            color=MUTED,
        )
        self.address.pack(anchor="w", pady=(3, 0))
        self._label(row, "LAN", 9, "bold", color="#d8c7ff").pack(side="right")

        status_card = self._card(outer)
        status_card.pack(fill="x", pady=(0, 12))
        status_inner = tk.Frame(status_card, bg=PANEL)
        status_inner.pack(fill="x", padx=18, pady=16)

        self.job_title = self._label(status_inner, "Ожидаю SOHR на телефоне", 13, "bold")
        self.job_title.pack(anchor="w")
        self.job_message = self._label(status_inner, "Можно начинать анализ из приложения", 10, color=MUTED)
        self.job_message.pack(anchor="w", pady=(4, 12))

        self.progress_var = tk.DoubleVar(value=0)
        self.progressbar = ttk.Progressbar(
            status_inner,
            variable=self.progress_var,
            maximum=100,
            style="SOHR.Horizontal.TProgressbar",
        )
        self.progressbar.pack(fill="x")

        progress_row = tk.Frame(status_inner, bg=PANEL)
        progress_row.pack(fill="x", pady=(7, 0))
        self.progress_text = self._label(progress_row, "0%", 9, "bold", color="#d8c7ff")
        self.progress_text.pack(side="left")
        self.queue_text = self._label(progress_row, "Очередь: 0", 9, color=MUTED)
        self.queue_text.pack(side="right")

        stats = tk.Frame(outer, bg=BG)
        stats.pack(fill="x", pady=(0, 12))
        self.done_value = self._stat(stats, "ГОТОВО", "0", 0)
        self.error_value = self._stat(stats, "ОШИБКИ", "0", 1)
        self.mode_value = self._stat(stats, "РЕЖИМ", "Экономный", 2)

        info = self._card(outer)
        info.pack(fill="x", pady=(0, 12))
        info_inner = tk.Frame(info, bg=PANEL)
        info_inner.pack(fill="x", padx=18, pady=14)
        self.last_event = self._label(info_inner, "Analyzer запущен", 10)
        self.last_event.pack(anchor="w")
        self.last_event_time = self._label(info_inner, "", 9, color=MUTED)
        self.last_event_time.pack(anchor="w", pady=(3, 0))

        actions = tk.Frame(outer, bg=BG)
        actions.pack(fill="x", pady=(2, 0))
        ttk.Button(actions, text="Папка результатов", style="Secondary.TButton", command=self.open_results).pack(side="left")
        ttk.Button(actions, text="Логи", style="Secondary.TButton", command=self.open_logs).pack(side="left", padx=(8, 0))
        self.cancel_button = ttk.Button(actions, text="Остановить анализ", style="Danger.TButton", command=self.cancel_job)
        self.cancel_button.pack(side="right", padx=(8, 0))
        ttk.Button(actions, text="Закрыть", style="Primary.TButton", command=self.close).pack(side="right")

        self._label(
            outer,
            "Оставь Analyzer открытым. Телефон и ПК должны быть в одной локальной сети.",
            9,
            color=MUTED,
        ).pack(anchor="w", pady=(16, 0))

    def _stat(self, parent, title: str, value: str, column: int):
        card = self._card(parent)
        card.grid(row=0, column=column, sticky="nsew", padx=(0 if column == 0 else 6, 0))
        parent.grid_columnconfigure(column, weight=1)
        self._label(card, title, 8, "bold", color=MUTED).pack(anchor="w", padx=14, pady=(12, 2))
        label = self._label(card, value, 14, "bold")
        label.pack(anchor="w", padx=14, pady=(0, 12))
        return label

    def refresh(self):
        if not self.root.winfo_exists():
            return

        running, queued, done, errors, event, event_time = STATE.snapshot()
        self.done_value.config(text=str(done))
        self.error_value.config(text=str(errors))
        self.queue_text.config(text=f"Очередь: {queued}")
        self.last_event.config(text=event)
        self.last_event_time.config(text=f"Последнее событие • {event_time}")

        if running:
            self.job_title.config(text=f"Twitch VOD {running.video_id}")
            self.job_message.config(text=running.message)
            self.progress_var.set(running.progress)
            self.progress_text.config(text=f"{running.progress}%")
            self.cancel_button.state(["!disabled"])
        else:
            self.job_title.config(text="Ожидаю SOHR на телефоне")
            self.job_message.config(
                text=f"В очереди: {queued}" if queued else "Можно начинать анализ из приложения"
            )
            self.progress_var.set(0)
            self.progress_text.config(text="0%")
            self.cancel_button.state(["disabled"])

        self.root.after(400, self.refresh)

    def open_results(self):
        try:
            os.startfile(str(STATE.results))
        except Exception:
            pass

    def open_logs(self):
        try:
            os.startfile(str(STATE.logs))
        except Exception:
            pass

    def cancel_job(self):
        STATE.cancel_current_job()

    def close(self):
        STATE.stop.set()
        STATE.current_cancel.set()
        try:
            self.root.destroy()
        except Exception:
            pass

    def run(self):
        self.root.mainloop()


def self_test() -> int:
    try:
        ffmpeg = Path(STATE.analyzer._ffmpeg())
        if not ffmpeg.exists():
            raise RuntimeError("FFmpeg binary is missing")
        # Force OCR initialization so packaging problems are caught in CI.
        STATE.analyzer.ocr._ensure()
        return 0
    except Exception:
        trace = traceback.format_exc()
        STATE.write_error(trace)
        try:
            sys.stderr.write(trace)
            sys.stderr.flush()
        except Exception:
            pass
        return 1


def main():
    if "--self-test" in sys.argv:
        raise SystemExit(self_test())

    set_low_impact_priority()
    threading.Thread(target=http_server, name="sohr-http", daemon=True).start()
    threading.Thread(target=discovery_server, name="sohr-discovery", daemon=True).start()
    threading.Thread(target=worker, name="sohr-worker", daemon=True).start()
    App().run()


if __name__ == "__main__":
    main()
