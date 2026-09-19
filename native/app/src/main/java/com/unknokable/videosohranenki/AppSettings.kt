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

    var aiAnalysis: Boolean
        get() = prefs.getBoolean("ai_analysis", true)
        set(value) = prefs.edit().putBoolean("ai_analysis", value).apply()

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

    fun palette(): ThemePalette = if (lightTheme) AppThemes.Light else AppThemes.Dark
}
