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
        background = Color.parseColor("#08070D"),
        surface = Color.parseColor("#11101A"),
        surfaceAlt = Color.parseColor("#181322"),
        text = Color.parseColor("#F7F5FF"),
        muted = Color.parseColor("#9E96AD"),
        accent = Color.parseColor("#8B5CF6"),
        accentSoft = Color.parseColor("#2B1D47"),
        stroke = Color.parseColor("#262130"),
        playerBackground = Color.BLACK
    )

    val Light = ThemePalette(
        background = Color.parseColor("#F5F4F8"),
        surface = Color.WHITE,
        surfaceAlt = Color.parseColor("#ECE9F2"),
        text = Color.parseColor("#17131F"),
        muted = Color.parseColor("#716A7E"),
        accent = Color.parseColor("#7C4DFF"),
        accentSoft = Color.parseColor("#E9E0FF"),
        stroke = Color.parseColor("#DDD8E6"),
        playerBackground = Color.BLACK
    )
}
