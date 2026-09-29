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

data class ThemePreset(
    val key: String,
    val label: String,
    val previewColor: Int,
    val darkBackground: Int,
    val darkSurface: Int,
    val darkSurfaceAlt: Int,
    val darkAccent: Int,
    val darkAccentSoft: Int,
    val darkStroke: Int,
    val lightBackground: Int,
    val lightSurface: Int,
    val lightSurfaceAlt: Int,
    val lightAccent: Int,
    val lightAccentSoft: Int,
    val lightStroke: Int
)

object AppThemes {
    val Presets = listOf(
        ThemePreset("violet","Фиолет",Color.parseColor("#9B6DFF"),Color.parseColor("#08070D"),Color.parseColor("#11101A"),Color.parseColor("#191323"),Color.parseColor("#9B6DFF"),Color.parseColor("#2D1D49"),Color.parseColor("#292132"),Color.parseColor("#F7F5FB"),Color.WHITE,Color.parseColor("#EFEBF6"),Color.parseColor("#7C4DFF"),Color.parseColor("#E9E0FF"),Color.parseColor("#DDD7E8")),
        ThemePreset("magenta","Маджента",Color.parseColor("#FF4DE3"),Color.parseColor("#0A070C"),Color.parseColor("#151018"),Color.parseColor("#221426"),Color.parseColor("#FF4DE3"),Color.parseColor("#3D1738"),Color.parseColor("#342333"),Color.parseColor("#FFF6FD"),Color.WHITE,Color.parseColor("#F7E9F5"),Color.parseColor("#D936C1"),Color.parseColor("#F9DDF4"),Color.parseColor("#EAD7E6")),
        ThemePreset("ocean","Океан",Color.parseColor("#4D9DFF"),Color.parseColor("#060A10"),Color.parseColor("#0E151E"),Color.parseColor("#132033"),Color.parseColor("#4D9DFF"),Color.parseColor("#132B49"),Color.parseColor("#203248"),Color.parseColor("#F4F8FF"),Color.WHITE,Color.parseColor("#E9F1FC"),Color.parseColor("#397DEB"),Color.parseColor("#DCEAFF"),Color.parseColor("#D5E0EE")),
        ThemePreset("ice","Лёд",Color.parseColor("#55D8FF"),Color.parseColor("#050B0E"),Color.parseColor("#0C171C"),Color.parseColor("#12252D"),Color.parseColor("#55D8FF"),Color.parseColor("#10343F"),Color.parseColor("#1E3942"),Color.parseColor("#F2FBFD"),Color.WHITE,Color.parseColor("#E5F5F8"),Color.parseColor("#25AFCF"),Color.parseColor("#D8F3F9"),Color.parseColor("#CFE6EA")),
        ThemePreset("mint","Мята",Color.parseColor("#4ED9A0"),Color.parseColor("#060C0A"),Color.parseColor("#0E1815"),Color.parseColor("#14251F"),Color.parseColor("#4ED9A0"),Color.parseColor("#14372A"),Color.parseColor("#203B31"),Color.parseColor("#F3FBF7"),Color.WHITE,Color.parseColor("#E8F5EE"),Color.parseColor("#2EBE84"),Color.parseColor("#D9F4E7"),Color.parseColor("#D2E7DB")),
        ThemePreset("sunset","Закат",Color.parseColor("#FF7A63"),Color.parseColor("#0D0807"),Color.parseColor("#1A110F"),Color.parseColor("#291815"),Color.parseColor("#FF7A63"),Color.parseColor("#432019"),Color.parseColor("#3A2824"),Color.parseColor("#FFF8F5"),Color.WHITE,Color.parseColor("#F8ECE8"),Color.parseColor("#E85F49"),Color.parseColor("#FBE2DC"),Color.parseColor("#EADBD7"))
    )

    fun preset(key: String): ThemePreset =
        Presets.firstOrNull { it.key == key } ?: Presets.first()

    fun palette(light: Boolean, key: String): ThemePalette {
        val p = preset(key)
        return if (light) ThemePalette(
            p.lightBackground, p.lightSurface, p.lightSurfaceAlt,
            Color.parseColor("#17131F"), Color.parseColor("#716A7E"),
            p.lightAccent, p.lightAccentSoft, p.lightStroke, Color.BLACK
        ) else ThemePalette(
            p.darkBackground, p.darkSurface, p.darkSurfaceAlt,
            Color.parseColor("#F8F6FF"), Color.parseColor("#9F98AD"),
            p.darkAccent, p.darkAccentSoft, p.darkStroke, Color.BLACK
        )
    }

    val Dark: ThemePalette get() = palette(false, "violet")
    val Light: ThemePalette get() = palette(true, "violet")
}
