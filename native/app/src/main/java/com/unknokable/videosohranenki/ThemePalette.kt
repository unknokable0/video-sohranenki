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
        ThemePreset("sunset","Закат",Color.parseColor("#FF7A63"),Color.parseColor("#0D0807"),Color.parseColor("#1A110F"),Color.parseColor("#291815"),Color.parseColor("#FF7A63"),Color.parseColor("#432019"),Color.parseColor("#3A2824"),Color.parseColor("#FFF8F5"),Color.WHITE,Color.parseColor("#F8ECE8"),Color.parseColor("#E85F49"),Color.parseColor("#FBE2DC"),Color.parseColor("#EADBD7")),
        ThemePreset("rose","Роза",Color.parseColor("#FF5C8A"),Color.parseColor("#0D070A"),Color.parseColor("#1A0F14"),Color.parseColor("#29151D"),Color.parseColor("#FF5C8A"),Color.parseColor("#421824"),Color.parseColor("#3B252D"),Color.parseColor("#FFF6F9"),Color.WHITE,Color.parseColor("#F8E9EF"),Color.parseColor("#E93F73"),Color.parseColor("#FADCE6"),Color.parseColor("#EAD6DD")),
        ThemePreset("amber","Янтарь",Color.parseColor("#FFB347"),Color.parseColor("#0D0A05"),Color.parseColor("#1A150D"),Color.parseColor("#292014"),Color.parseColor("#FFB347"),Color.parseColor("#433019"),Color.parseColor("#3B3022"),Color.parseColor("#FFFAF2"),Color.WHITE,Color.parseColor("#F8F0E2"),Color.parseColor("#E69528"),Color.parseColor("#FBE9C9"),Color.parseColor("#EADFCB")),
        ThemePreset("lime","Лайм",Color.parseColor("#A7E635"),Color.parseColor("#090C05"),Color.parseColor("#12190C"),Color.parseColor("#1B2812"),Color.parseColor("#A7E635"),Color.parseColor("#294114"),Color.parseColor("#2D3B20"),Color.parseColor("#F9FDF2"),Color.WHITE,Color.parseColor("#EFF7E2"),Color.parseColor("#7FBD19"),Color.parseColor("#E8F6C9"),Color.parseColor("#DDE8CC")),
        ThemePreset("aqua","Аква",Color.parseColor("#22D3C5"),Color.parseColor("#050C0C"),Color.parseColor("#0C1818"),Color.parseColor("#102626"),Color.parseColor("#22D3C5"),Color.parseColor("#103B38"),Color.parseColor("#1E3B39"),Color.parseColor("#F2FCFB"),Color.WHITE,Color.parseColor("#E4F7F5"),Color.parseColor("#14AD9F"),Color.parseColor("#D5F4F0"),Color.parseColor("#CCE8E5")),
        ThemePreset("indigo","Индиго",Color.parseColor("#6E7CFF"),Color.parseColor("#070810"),Color.parseColor("#10121D"),Color.parseColor("#181B30"),Color.parseColor("#6E7CFF"),Color.parseColor("#1C234A"),Color.parseColor("#282D49"),Color.parseColor("#F5F6FF"),Color.WHITE,Color.parseColor("#EAECFA"),Color.parseColor("#5364EC"),Color.parseColor("#E0E4FF"),Color.parseColor("#D6D9EB")),
        ThemePreset("crimson","Малина",Color.parseColor("#FF456F"),Color.parseColor("#0D0608"),Color.parseColor("#190E12"),Color.parseColor("#28131A"),Color.parseColor("#FF456F"),Color.parseColor("#431526"),Color.parseColor("#3C222B"),Color.parseColor("#FFF5F7"),Color.WHITE,Color.parseColor("#F9E7EC"),Color.parseColor("#E62F5D"),Color.parseColor("#FBD9E2"),Color.parseColor("#EAD4DA"))
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
