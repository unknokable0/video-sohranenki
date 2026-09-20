# SOHR Analyzer

Local Windows analyzer for SOHR Smart Chapters.

## What it does

- Runs on the user's PC.
- Receives Twitch VOD analysis jobs from SOHR Android over the local network.
- Resolves a Twitch VOD and scans it sequentially with FFmpeg.
- Uses a low-resolution continuous pass to find likely watched-video intervals.
- Uses local OCR (RapidOCR / ONNX Runtime) for player UI and titles.
- Performs a denser pass around candidate boundaries.
- Stores chapter JSON under %LOCALAPPDATA%/SOHR Analyzer/results.
- Returns only watched-video chapters; games are intentionally excluded.

## Local protocol

- UDP discovery: port 8764, payload SOHR_DISCOVER_V1.
- HTTP API: port 8765.
- Discovery returns a per-install token used in X-SOHR-Token.
- GET /health
- POST /v1/analyze
- GET /v1/jobs/{jobId}
- GET /v1/results/{vodId}
