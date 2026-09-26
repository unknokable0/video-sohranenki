# SOHR development rules

These rules are part of the SOHR release workflow and must be followed for every user-facing build.

1. Every new SOHR build must be installable through the in-app updater.
2. Every release must increment versionCode and versionName.
3. Publish a unique versioned APK asset: SOHR-<versionCode>.apk.
4. update.json must point to that exact unique versioned APK.
5. The SHA-256 in update.json must exactly match the published versioned APK digest.
6. Do not call a build ready until GitHub Actions, release publishing, version metadata, update.json, and SHA-256 have all been verified.
7. SOHR.apk may exist as a convenience asset, but the in-app updater must use the unique versioned APK URL.
8. Twitch LIVE production behavior: if @t2x2 is offline, show offline. Random active Twitch channels are allowed only behind an explicitly temporary test flag and must not become normal fallback behavior.
