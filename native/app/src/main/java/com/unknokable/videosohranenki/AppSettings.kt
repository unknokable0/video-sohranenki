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
}
