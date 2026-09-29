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
        ThemePreset("crimson","Малина",Color.parseColor("#FF456F"),Color.parseColor("#0D0608"),Color.parseColor("#190E12"),Color.parseColor("#28131A"),Color.parseColor("#FF456F"),Color.parseColor("#431526"),Color.parseColor("#3C222B"),Color.parseColor("#FFF5F7"),Color.WHITE,Color.parseColor("#F9E7EC"),Color.parseColor("#E62F5D"),Color.parseColor("#FBD9E2"),Color.parseColor("#EAD4DA")),
        ThemePreset("electric","Электрик",Color.parseColor("#00A8FF"),Color.parseColor("#050A0D"),Color.parseColor("#0C151B"),Color.parseColor("#112531"),Color.parseColor("#00A8FF"),Color.parseColor("#0D3449"),Color.parseColor("#203845"),Color.parseColor("#F2FAFF"),Color.WHITE,Color.parseColor("#E5F3FA"),Color.parseColor("#0089D6"),Color.parseColor("#D6EEFB"),Color.parseColor("#CEE2EC")),
        ThemePreset("emerald","Изумруд",Color.parseColor("#19C37D"),Color.parseColor("#050C08"),Color.parseColor("#0C1913"),Color.parseColor("#11271D"),Color.parseColor("#19C37D"),Color.parseColor("#103D2A"),Color.parseColor("#203C31"),Color.parseColor("#F2FCF7"),Color.WHITE,Color.parseColor("#E4F6ED"),Color.parseColor("#0FA565"),Color.parseColor("#D4F3E3"),Color.parseColor("#CCE7D9")),
        ThemePreset("gold","Золото",Color.parseColor("#F6C453"),Color.parseColor("#0C0A05"),Color.parseColor("#18150D"),Color.parseColor("#282113"),Color.parseColor("#F6C453"),Color.parseColor("#423517"),Color.parseColor("#3A3221"),Color.parseColor("#FFFBF1"),Color.WHITE,Color.parseColor("#F8F1DF"),Color.parseColor("#D7A52D"),Color.parseColor("#FAEBC8"),Color.parseColor("#E9DFC9")),
        ThemePreset("coral","Коралл",Color.parseColor("#FF6B6B"),Color.parseColor("#0D0707"),Color.parseColor("#1A1010"),Color.parseColor("#291616"),Color.parseColor("#FF6B6B"),Color.parseColor("#431D1D"),Color.parseColor("#3B2828"),Color.parseColor("#FFF7F7"),Color.WHITE,Color.parseColor("#F8EAEA"),Color.parseColor("#E94F4F"),Color.parseColor("#FADDDD"),Color.parseColor("#EAD6D6")),
        ThemePreset("lavender","Лаванда",Color.parseColor("#C084FC"),Color.parseColor("#0A0710"),Color.parseColor("#15101D"),Color.parseColor("#22162F"),Color.parseColor("#C084FC"),Color.parseColor("#321C4B"),Color.parseColor("#342743"),Color.parseColor("#FAF6FF"),Color.WHITE,Color.parseColor("#F1E9FA"),Color.parseColor("#A65FE5"),Color.parseColor("#ECDDFB"),Color.parseColor("#E1D6EA")),
        ThemePreset("ruby","Рубин",Color.parseColor("#F43F5E"),Color.parseColor("#0D0608"),Color.parseColor("#190E12"),Color.parseColor("#29141A"),Color.parseColor("#F43F5E"),Color.parseColor("#421523"),Color.parseColor("#3A222A"),Color.parseColor("#FFF5F7"),Color.WHITE,Color.parseColor("#F8E7EC"),Color.parseColor("#D92749"),Color.parseColor("#FAD8E0"),Color.parseColor("#E9D4DA")),
        ThemePreset("ultraviolet","Ультрафиолет",Color.parseColor("#B14CFF"),Color.parseColor("#09060E"),Color.parseColor("#140E1B"),Color.parseColor("#21132E"),Color.parseColor("#B14CFF"),Color.parseColor("#32164D"),Color.parseColor("#352440"),Color.parseColor("#FAF5FF"),Color.WHITE,Color.parseColor("#F1E7FA"),Color.parseColor("#9337E5"),Color.parseColor("#ECD7FF"),Color.parseColor("#E0D2E8")),
        ThemePreset("sky","Небо",Color.parseColor("#38BDF8"),Color.parseColor("#050B0F"),Color.parseColor("#0C171D"),Color.parseColor("#102633"),Color.parseColor("#38BDF8"),Color.parseColor("#10394C"),Color.parseColor("#203B49"),Color.parseColor("#F2FBFF"),Color.WHITE,Color.parseColor("#E4F5FC"),Color.parseColor("#159DD7"),Color.parseColor("#D4F0FC"),Color.parseColor("#CDE5EE")),
        ThemePreset("peach","Персик",Color.parseColor("#FF9B73"),Color.parseColor("#0D0806"),Color.parseColor("#1A110D"),Color.parseColor("#291A14"),Color.parseColor("#FF9B73"),Color.parseColor("#43261B"),Color.parseColor("#3C2B24"),Color.parseColor("#FFF8F4"),Color.WHITE,Color.parseColor("#F8ECE6"),Color.parseColor("#EA7750"),Color.parseColor("#FBE3D8"),Color.parseColor("#EAD9D2")),
        ThemePreset("cobalt","Кобальт",Color.parseColor("#4F67FF"),Color.parseColor("#060711"),Color.parseColor("#0E1120"),Color.parseColor("#151A35"),Color.parseColor("#4F67FF"),Color.parseColor("#18224E"),Color.parseColor("#262D4A"),Color.parseColor("#F4F6FF"),Color.WHITE,Color.parseColor("#E9ECFA"),Color.parseColor("#3850E8"),Color.parseColor("#DCE2FF"),Color.parseColor("#D3D8EB")),
        ThemePreset("forest","Лес",Color.parseColor("#22C55E"),Color.parseColor("#050B07"),Color.parseColor("#0D1811"),Color.parseColor("#12261A"),Color.parseColor("#22C55E"),Color.parseColor("#123B22"),Color.parseColor("#213A2A"),Color.parseColor("#F3FBF5"),Color.WHITE,Color.parseColor("#E6F4EA"),Color.parseColor("#17A34B"),Color.parseColor("#D7F1DF"),Color.parseColor("#CEE4D4")),
        ThemePreset("silver","Серебро",Color.parseColor("#AEB7C4"),Color.parseColor("#08090B"),Color.parseColor("#121419"),Color.parseColor("#1D2028"),Color.parseColor("#AEB7C4"),Color.parseColor("#2A2E39"),Color.parseColor("#333742"),Color.parseColor("#F8F9FB"),Color.WHITE,Color.parseColor("#EEF0F4"),Color.parseColor("#7F8998"),Color.parseColor("#E5E8EE"),Color.parseColor("#DADDE3"))
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
