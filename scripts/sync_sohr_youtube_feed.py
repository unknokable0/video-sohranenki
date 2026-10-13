#!/usr/bin/env python3
"""Refresh SOHR public YouTube feed from real channel uploads."""
import json, subprocess, re, sys, time, html, urllib.request
from pathlib import Path

out = Path("data/sohr_youtube_feed.json")
old = {}
if out.exists():
    try: old = json.loads(out.read_text(encoding="utf-8"))
    except (ValueError, OSError): pass
previous = {c["handle"]: c for c in old.get("channels", []) if isinstance(c, dict) and "handle" in c}
sources = [
    ("@T2x2_stream", "T2x2 STREAM", "UC5AvrWA9E3DvFyy4qjuXtNA"),
    ("@beerloga_t2x2", "Берлога T2x2", "UCiOjixt3AcBRPaRQEEj2S9w"),
]
channels = []
successes = 0
for handle, name, cid in sources:
    try:
        result = subprocess.run(
            [sys.executable, "-m", "yt_dlp", "--flat-playlist", "--playlist-end",
             "30", "--dump-single-json", "--no-warnings", "--skip-download",
             "https://www.youtube.com/" + handle + "/videos"],
            capture_output=True, text=True, timeout=95,
        )
        if result.returncode: raise RuntimeError(result.stderr[-350:])
        data = json.loads(result.stdout)
        if data.get("channel_id") != cid: raise RuntimeError("Channel ID mismatch")
        videos = []
        seen = set()
        for entry in data.get("entries") or []:
            if not isinstance(entry, dict): continue
            vid, title = str(entry.get("id") or ""), str(entry.get("title") or "").strip()
            if not re.fullmatch(r"[A-Za-z0-9_-]{11}", vid) or not title or vid in seen: continue
            seen.add(vid)
            t = entry.get("timestamp") or entry.get("release_timestamp") or 0
            try: timestamp = int(t)
            except (TypeError, ValueError): timestamp = 0
            if timestamp < 946684800 or timestamp > time.time() + 86400: timestamp = 0
            videos.append({
                "videoId": vid, "title": title[:300], "description": "",
                "publishedAt": timestamp, "publishedExact": bool(timestamp),
                "publishedLabel": "", "durationSeconds": int(entry.get("duration") or 0),
                "thumbnailUrl": "https://i.ytimg.com/vi/" + vid + "/hqdefault.jpg",
            })
        if not videos: raise RuntimeError("No videos")
        avatar = previous.get(handle, {}).get("avatarUrl", "")
        try:
            page_url = "https://www.youtube.com/" + handle + "/videos"
            req = urllib.request.Request(page_url, headers={
                "User-Agent": "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/141 Safari/537.36",
            })
            with urllib.request.urlopen(req, timeout=7) as response:
                markup = response.read(250000).decode("utf-8", "replace")
            match = re.search(r'<meta property="og:image" content="([^"]+)"', markup)
            if match:
                candidate = html.unescape(match.group(1))
                if candidate.startswith(("https://yt3.googleusercontent.com/", "https://yt3.ggpht.com/")):
                    avatar = candidate
        except Exception:
            pass
        channel = {"channelId": cid, "handle": handle, "title": name,
                   "avatarUrl": avatar, "channelUrl": "https://www.youtube.com/" + handle,
                   "videos": videos}
        successes += 1
        print(handle, len(videos), "public video IDs")
    except Exception as error:
        print(handle, repr(error), file=sys.stderr)
        channel = previous.get(handle)
    if channel: channels.append(channel)
if not successes: raise RuntimeError("All public channel requests failed; keeping old data")
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps({"version": 1, "generatedAtEpoch": int(time.time()),
                           "channels": channels}, ensure_ascii=False, indent=2) + "\n",
               encoding="utf-8")
print("SOHR public videos:", sum(len(c["videos"]) for c in channels))
