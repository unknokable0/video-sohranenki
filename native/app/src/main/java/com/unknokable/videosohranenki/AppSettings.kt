package com.unknokable.videosohranenki

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("video_sohranenki_settings", Context.MODE_PRIVATE)

    var autoplay: Boolean
        get() = prefs.getBoolean("autoplay", false)
        set(value) = prefs.edit().putBoolean("autoplay", value).apply()

    var previewMode: String
        get() {
            val fallback = if (prefs.getBoolean("previews", true)) "wifi" else "off"
            return when (val value = prefs.getString("preview_mode", fallback) ?: fallback) {
                "always", "off" -> value
                else -> "wifi"
            }
        }
        set(value) {
            val normalized = when (value) {
                "always", "off" -> value
                else -> "wifi"
            }
            prefs.edit()
                .putString("preview_mode", normalized)
                .putBoolean("previews", normalized != "off")
                .apply()
        }

    var previews: Boolean
        get() = previewMode != "off"
        set(value) {
            previewMode = if (value) "wifi" else "off"
        }

    var animations: Boolean
        get() = prefs.getBoolean("animations", true)
        set(value) = prefs.edit().putBoolean("animations", value).apply()

    var playbackSpeed: Float
        get() = prefs.getFloat("playback_speed", 1f).coerceIn(0.25f, 2f)
        set(value) = prefs.edit().putFloat("playback_speed", value.coerceIn(0.25f, 2f)).apply()

    var authPhone: String?
        get() = prefs.getString("auth_phone", null)
        set(value) = prefs.edit().apply {
            if (value == null) remove("auth_phone") else putString("auth_phone", value)
        }.apply()

    var authGeneration: Int
        get() = prefs.getInt("auth_generation", 1)
        set(value) = prefs.edit().putInt("auth_generation", value).apply()

    var languageCode: String
        get() = prefs.getString("language_code", "ru") ?: "ru"
        set(value) = prefs.edit().putString("language_code", value).apply()

    var lightTheme: Boolean
        get() = prefs.getBoolean("light_theme", false)
        set(value) = prefs.edit().putBoolean("light_theme", value).apply()

    var themeAccent: String
        get() = AppThemes.preset(prefs.getString("theme_accent", "violet") ?: "violet").key
        set(value) = prefs.edit().putString("theme_accent", AppThemes.preset(value).key).apply()

    var postLoginTourSeen: Boolean
        get() = prefs.getBoolean("post_login_tour_seen", false)
        set(value) = prefs.edit().putBoolean("post_login_tour_seen", value).apply()

    var guestMode: Boolean
        get() = prefs.getBoolean("guest_mode", false)
        set(value) = prefs.edit().putBoolean("guest_mode", value).apply()

    var videoSource: String
        get() = prefs.getString("video_source", "telegram") ?: "telegram"
        set(value) = prefs.edit().putString("video_source", if (value == "twitch") "twitch" else "telegram").apply()

    var twitchAccessToken: String?
        get() = prefs.getString("twitch_access_token", null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove("twitch_access_token") else putString("twitch_access_token", value)
        }.apply()

    var twitchOauthState: String?
        get() = prefs.getString("twitch_oauth_state", null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove("twitch_oauth_state") else putString("twitch_oauth_state", value)
        }.apply()

    var twitchRefreshToken: String?
        get() = prefs.getString("twitch_refresh_token", null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove("twitch_refresh_token") else putString("twitch_refresh_token", value)
        }.apply()

    var twitchLogin: String?
        get() = prefs.getString("twitch_login", null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove("twitch_login") else putString("twitch_login", value)
        }.apply()

    var collectionSort: CollectionSort
        get() = runCatching {
            CollectionSort.valueOf(prefs.getString("collection_sort", CollectionSort.NEWEST.name)!!)
        }.getOrDefault(CollectionSort.NEWEST)
        set(value) = prefs.edit().putString("collection_sort", value.name).apply()

    var videoSort: VideoSort
        get() = runCatching {
            VideoSort.valueOf(prefs.getString("video_sort", VideoSort.NEWEST.name)!!)
        }.getOrDefault(VideoSort.NEWEST)
        set(value) = prefs.edit().putString("video_sort", value.name).apply()

    fun playbackPosition(messageId: Long): Long =
        prefs.getLong("playback_position_" + messageId, 0L)

    fun savePlaybackPosition(messageId: Long, positionMs: Long, durationMs: Long) {
        val shouldClear = positionMs < 1_000L ||
            (durationMs > 0L && durationMs - positionMs <= 10_000L)
        prefs.edit().apply {
            if (shouldClear) remove("playback_position_" + messageId)
            else putLong("playback_position_" + messageId, positionMs)
        }.apply()
    }

    fun clearPlaybackPosition(messageId: Long) {
        prefs.edit().remove("playback_position_" + messageId).apply()
    }

    fun markPlayed(messageId: Long, atMs: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("last_played_" + messageId, atMs).apply()
    }

    fun lastPlayedAt(messageId: Long): Long =
        prefs.getLong("last_played_" + messageId, 0L)

    fun palette(): ThemePalette = AppThemes.palette(lightTheme, themeAccent)
}
