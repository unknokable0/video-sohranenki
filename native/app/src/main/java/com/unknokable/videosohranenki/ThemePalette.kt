package com.unknokable.videosohranenki

import android.graphics.Color

data class ThemePalette(
    val background: Int,
    val surface: Int,
    val surfaceAlt: Int,
    val text: Int,
    val muted: Int,
    val accent: Int,
    val accentSoft: Int,
    val stroke: Int,
    val playerBackground: Int
)

object AppThemes {
    val Dark = ThemePalette(
        background = Color.parseColor("#081019"),
        surface = Color.parseColor("#D414202B"),
        surfaceAlt = Color.parseColor("#BF1B2A36"),
        text = Color.parseColor("#F4FAFF"),
        muted = Color.parseColor("#91A5B4"),
        accent = Color.parseColor("#69B5E8"),
        accentSoft = Color.parseColor("#5C315F7C"),
        stroke = Color.parseColor("#80516B7A"),
        playerBackground = Color.BLACK
    )

    val Light = ThemePalette(
        background = Color.parseColor("#EDF5FA"),
        surface = Color.parseColor("#E8FFFFFF"),
        surfaceAlt = Color.parseColor("#D7E3EEF4"),
        text = Color.parseColor("#152432"),
        muted = Color.parseColor("#667D8E"),
        accent = Color.parseColor("#3E8DBE"),
        accentSoft = Color.parseColor("#663E8DBE"),
        stroke = Color.parseColor("#806F91A8"),
        playerBackground = Color.BLACK
    )
}
