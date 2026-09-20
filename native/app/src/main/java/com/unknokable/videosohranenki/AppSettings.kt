package com.unknokable.videosohranenki

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("video_sohranenki_settings", Context.MODE_PRIVATE)

    var autoplay: Boolean
        get() = prefs.getBoolean("autoplay", false)
        set(value) = prefs.edit().putBoolean("autoplay", value).apply()

    var previews: Boolean
        get() = prefs.getBoolean("previews", true)
        set(value) = prefs.edit().putBoolean("previews", value).apply()

    var animations: Boolean
        get() = prefs.getBoolean("animations", true)
        set(value) = prefs.edit().putBoolean("animations", value).apply()

    var autoRotateFullscreen: Boolean
        get() = prefs.getBoolean("auto_rotate_fullscreen", true)
        set(value) = prefs.edit().putBoolean("auto_rotate_fullscreen", value).apply()

    var playbackSpeed: Float
        get() = prefs.getFloat("playback_speed", 1f).coerceIn(0.25f, 2f)
        set(value) = prefs.edit().putFloat("playback_speed", value.coerceIn(0.25f, 2f)).apply()

    var stableVolume: Boolean
        get() = prefs.getBoolean("stable_volume", false)
        set(value) = prefs.edit().putBoolean("stable_volume", value).apply()

    var preferredQuality: Int
        get() = prefs.getInt("preferred_quality", 0)
        set(value) = prefs.edit().putInt("preferred_quality", value).apply()

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
        val shouldClear = positionMs < 5_000L ||
            (durationMs > 0L && durationMs - positionMs <= 10_000L)
        prefs.edit().apply {
            if (shouldClear) remove("playback_position_" + messageId)
            else putLong("playback_position_" + messageId, positionMs)
        }.apply()
    }

    fun clearPlaybackPosition(messageId: Long) {
        prefs.edit().remove("playback_position_" + messageId).apply()
    }

    fun palette(): ThemePalette = if (lightTheme) AppThemes.Light else AppThemes.Dark
}
