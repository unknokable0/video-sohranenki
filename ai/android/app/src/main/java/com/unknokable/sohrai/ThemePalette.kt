package com.unknokable.sohrai

import android.graphics.Color

data class ThemePalette(
    val background: Int,
    val surface: Int,
    val surfaceAlt: Int,
    val text: Int,
    val muted: Int,
    val accent: Int,
    val accentSoft: Int,
    val stroke: Int
)

object AppThemes {
    val Dark = ThemePalette(
        background = Color.parseColor("#212121"),
        surface = Color.parseColor("#2F2F2F"),
        surfaceAlt = Color.parseColor("#303030"),
        text = Color.parseColor("#ECECEC"),
        muted = Color.parseColor("#A8A8A8"),
        accent = Color.parseColor("#FFFFFF"),
        accentSoft = Color.parseColor("#3A3A3A"),
        stroke = Color.parseColor("#454545")
    )

    val Light = ThemePalette(
        background = Color.parseColor("#FFFFFF"),
        surface = Color.parseColor("#F4F4F4"),
        surfaceAlt = Color.parseColor("#ECECEC"),
        text = Color.parseColor("#0D0D0D"),
        muted = Color.parseColor("#6E6E80"),
        accent = Color.parseColor("#0D0D0D"),
        accentSoft = Color.parseColor("#E8E8E8"),
        stroke = Color.parseColor("#DDDDDD")
    )
}
