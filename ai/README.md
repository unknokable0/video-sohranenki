# SOHR AI

A separate single-chat AI app. It does not share the Android package or runtime state with SOHR.

## Structure

- `android/` — Android client.
- `server/` — private backend proxy for AI providers.

## Backend

Never put provider API keys into the APK.

Required server environment variable:

```
OPENAI_API_KEY=...
```

Optional:

```
OPENAI_MODEL=gpt-5.6-sol
OPENAI_REASONING_EFFORT=high
PORT=8787
```

Run locally:

```
cd ai/server
npm install
npm start
```

## Android

The Android build reads `SOHR_AI_API_URL` from the environment. If it is absent, debug builds use `http://10.0.2.2:8787` for the Android emulator.

The app is intentionally one-chat-only in the MVP.
