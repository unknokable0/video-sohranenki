package com.unknokable.videosohranenki

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsScreen(
    private val activity: Activity,
    private val settings: AppSettings,
    private val onBack: (Boolean) -> Unit,
    private val onThemeChanged: () -> Unit,
    private val onLanguageChanged: () -> Unit,
    private val onLogout: () -> Unit
) {
    private var needsReload = false
    private val palette get() = settings.palette()
    private fun t(key: String): String = AppLanguages.t(settings.languageCode, key)

    fun build(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
            setPadding(dp(16), dp(10), dp(16), dp(20))
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_back)
            background = rounded(palette.surfaceAlt, 24)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { onBack(needsReload) }
        }

        val title = TextView(activity).apply {
            text = t("settings")
            textSize = 24f
            setTextColor(palette.text)
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
        }

        val spacer = View(activity)
        header.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(spacer, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)

        val subtitle = TextView(activity).apply {
            text = t("personalization")
            textSize = 13f
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(palette.muted)
            setPadding(0, dp(10), 0, dp(18))
        }
        root.addView(subtitle)

        root.addView(themeSelector())
        root.addView(languageSelector())

        root.addView(settingRow(
            icon = "▶",
            iconColor = "#B89AFF",
            title = "Автовоспроизведение",
            description = t("autoplay_desc"),
            checked = settings.autoplay
        ) { settings.autoplay = it })

        root.addView(settingRow(
            icon = "▣",
            iconColor = "#58DFA0",
            title = t("previews"),
            description = t("previews_desc"),
            checked = settings.previews
        ) {
            settings.previews = it
            needsReload = true
        })

        root.addView(settingRow(
            icon = "✦",
            iconColor = "#76A9FF",
            title = t("animations"),
            description = t("animations_desc"),
            checked = settings.animations
        ) { settings.animations = it })

        root.addView(settingRow(
            icon = "AI",
            iconColor = "#B89AFF",
            title = t("ai"),
            description = t("ai_desc"),
            checked = settings.aiAnalysis
        ) { settings.aiAnalysis = it })

        root.addView(settingRow(
            icon = "↻",
            iconColor = "#FF83BE",
            title = t("rotate"),
            description = t("rotate_desc"),
            checked = settings.autoRotateFullscreen
        ) { settings.autoRotateFullscreen = it })

        val logout = TextView(activity).apply {
            text = t("logout")
            textSize = 15f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = rounded(Color.parseColor("#D9435F"), 16)
            setOnClickListener { onLogout() }
        }
        root.addView(
            logout,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4); bottomMargin = dp(12) }
        )

        return root
    }

    private fun languageSelector(): View {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(palette.surface, 18)
        }

        val labels = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = TextView(activity).apply {
            text = t("language")
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        }

        val subtitle = TextView(activity).apply {
            text = t("choose_language")
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(0, dp(3), 0, 0)
        }

        labels.addView(title)
        labels.addView(subtitle)

        val current = AppLanguages.byCode(settings.languageCode)
        val button = TextView(activity).apply {
            text = current.shortLabel
            gravity = Gravity.CENTER
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(palette.accent, 13)
            setOnClickListener {
                ModernDialogs.showChoices(
                    context = activity,
                    palette = palette,
                    title = t("choose_language"),
                    options = AppLanguages.all.map { it.label },
                    selected = AppLanguages.all.indexOfFirst { it.code == settings.languageCode }.coerceAtLeast(0)
                ) { which ->
                    settings.languageCode = AppLanguages.all[which].code
                    onLanguageChanged()
                }
            }
        }

        box.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(button, LinearLayout.LayoutParams(dp(58), dp(42)))

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(box)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
    }

    private fun themeSelector(): View {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(palette.surface, 18)
        }

        val title = TextView(activity).apply {
            text = t("theme")
            textSize = 15f
            setTextColor(palette.text)
            setTypeface(typeface, Typeface.BOLD)
        }

        val subtitle = TextView(activity).apply {
            text = t("theme_desc")
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(0, dp(3), 0, dp(12))
        }

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(palette.surfaceAlt, 16)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val dark = themeOption(t("dark"), !settings.lightTheme) {
            if (settings.lightTheme) animateThemeChange(false)
        }
        val light = themeOption(t("light"), settings.lightTheme) {
            if (!settings.lightTheme) animateThemeChange(true)
        }

        row.addView(dark, LinearLayout.LayoutParams(0, dp(46), 1f))
        row.addView(light, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(4) })

        box.addView(title)
        box.addView(subtitle)
        box.addView(row)

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(box)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
    }

    private fun themeOption(label: String, selected: Boolean, onClick: () -> Unit): TextView =
        TextView(activity).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (selected) Color.WHITE else palette.muted)
            background = rounded(if (selected) palette.accent else Color.TRANSPARENT, 13)
            setOnClickListener { onClick() }
        }

    private fun animateThemeChange(light: Boolean) {
        val decor = activity.window.decorView
        val duration = if (settings.animations) 150L else 0L
        decor.animate()
            .alpha(0.45f)
            .scaleX(0.992f)
            .scaleY(0.992f)
            .setDuration(duration)
            .withEndAction {
                settings.lightTheme = light
                onThemeChanged()
                decor.alpha = 0.45f
                decor.scaleX = 0.992f
                decor.scaleY = 0.992f
                decor.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(duration + 60L)
                    .start()
            }
            .start()
    }

    private fun settingRow(
        icon: String,
        iconColor: String,
        title: String,
        description: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(12), dp(13))
            background = rounded(palette.surface, 18)
        }

        val iconView = TextView(activity).apply {
            text = icon
            gravity = Gravity.CENTER
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(iconColor))
            background = rounded(palette.surfaceAlt, 14)
        }

        val labels = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }

        val titleView = TextView(activity).apply {
            text = title
            textSize = 15f
            setTextColor(palette.text)
            setTypeface(typeface, Typeface.BOLD)
        }

        val descriptionView = TextView(activity).apply {
            text = description
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(0, dp(3), 0, 0)
        }

        labels.addView(titleView)
        labels.addView(descriptionView)

        val toggle = SwitchMaterial(activity).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }

        row.addView(iconView, LinearLayout.LayoutParams(dp(46), dp(46)))
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(toggle)

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
