package com.unknokable.videosohranenki

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SettingsScreen(
    private val activity: Activity,
    private val settings: AppSettings,
    private val onBack: (Boolean) -> Unit,
    private val onThemeChanged: (Boolean, View) -> Unit,
    private val onLanguageChanged: () -> Unit,
    private val onCheckUpdates: () -> Unit,
    private val onLogout: () -> Unit
) {
    private var needsReload = false
    private lateinit var frameRoot: FrameLayout
    private val palette get() = settings.palette()
    private fun t(key: String): String = AppLanguages.t(settings.languageCode, key)

    fun build(): View {
        frameRoot = FrameLayout(activity).apply {
            setBackgroundColor(palette.background)
        }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(20))
            setBackgroundColor(Color.TRANSPARENT)
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_back)
            background = rounded(palette.surfaceAlt, 24)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { animateTap(this); onBack(needsReload) }
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
            icon = "↻",
            iconColor = "#FF83BE",
            title = t("rotate"),
            description = t("rotate_desc"),
            checked = settings.autoRotateFullscreen
        ) { settings.autoRotateFullscreen = it })

        root.addView(actionRow(
            icon = "↓",
            iconColor = "#8B5CF6",
            title = "Проверить обновления",
            description = "SOHR " + BuildConfig.VERSION_NAME
        ) {
            onCheckUpdates()
        })

        val logout = TextView(activity).apply {
            text = t("logout")
            textSize = 15f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(Color.parseColor("#D9435F"), 16)
            setOnClickListener { animateTap(this); onLogout() }
        }
        root.addView(
            logout,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply { topMargin = dp(4); bottomMargin = dp(16) }
        )

        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(Color.TRANSPARENT)
            addView(root, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        frameRoot.addView(
            scroll,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        return frameRoot
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
                animateTap(this)
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

        val selector = FrameLayout(activity).apply {
            background = rounded(palette.surfaceAlt, 16)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            clipChildren = true
            clipToPadding = true
        }

        val indicator = View(activity).apply {
            background = rounded(palette.accent, 13)
        }

        val buttons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val dark = themeOption(R.drawable.ic_theme_moon, !settings.lightTheme, "Тёмная")
        val light = themeOption(R.drawable.ic_theme_sun, settings.lightTheme, "Светлая")

        buttons.addView(dark, LinearLayout.LayoutParams(0, dp(46), 1f))
        buttons.addView(light, LinearLayout.LayoutParams(0, dp(46), 1f))

        selector.addView(
            indicator,
            FrameLayout.LayoutParams(0, dp(46), Gravity.START or Gravity.CENTER_VERTICAL)
        )
        selector.addView(
            buttons,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46),
                Gravity.CENTER
            )
        )

        fun updateThemeIcons(lightSelected: Boolean) {
            dark.imageTintList = ColorStateList.valueOf(if (!lightSelected) Color.WHITE else palette.muted)
            light.imageTintList = ColorStateList.valueOf(if (lightSelected) Color.WHITE else palette.muted)
        }

        fun moveIndicator(toLight: Boolean, source: View, notify: Boolean) {
            val slot = ((selector.width - selector.paddingLeft - selector.paddingRight) / 2f).coerceAtLeast(0f)
            if (slot <= 0f) return

            val params = indicator.layoutParams as FrameLayout.LayoutParams
            params.width = slot.toInt()
            params.height = dp(46)
            indicator.layoutParams = params

            val target = if (toLight) slot else 0f
            updateThemeIcons(toLight)

            indicator.animate().cancel()
            indicator.animate()
                .translationX(target)
                .setDuration(if (settings.animations) 220L else 0L)
                .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                .withEndAction {
                    if (notify && settings.lightTheme != toLight) {
                        onThemeChanged(toLight, source)
                    }
                }
                .start()
        }

        dark.setOnClickListener {
            if (!settings.lightTheme) return@setOnClickListener
            animateTap(dark)
            moveIndicator(false, dark, true)
        }

        light.setOnClickListener {
            if (settings.lightTheme) return@setOnClickListener
            animateTap(light)
            moveIndicator(true, light, true)
        }

        selector.post {
            val slot = ((selector.width - selector.paddingLeft - selector.paddingRight) / 2f).coerceAtLeast(0f)
            val params = indicator.layoutParams as FrameLayout.LayoutParams
            params.width = slot.toInt()
            params.height = dp(46)
            indicator.layoutParams = params
            indicator.translationX = if (settings.lightTheme) slot else 0f
            updateThemeIcons(settings.lightTheme)
        }

        box.addView(title)
        box.addView(subtitle)
        box.addView(
            selector,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
            )
        )

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(box)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
    }

    private fun themeOption(
        iconRes: Int,
        selected: Boolean,
        description: String
    ): ImageButton =
        ImageButton(activity).apply {
            setImageResource(iconRes)
            contentDescription = description
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageTintList = ColorStateList.valueOf(
                if (selected) Color.WHITE else palette.muted
            )
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = null
        }


    private fun animateTap(view: View) {
        if (!settings.animations) return
        val ease = android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
        view.animate().cancel()
        view.animate()
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(60L)
            .setInterpolator(ease)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(130L)
                    .setInterpolator(ease)
                    .start()
            }
            .start()
    }

    private fun actionRow(
        icon: String,
        iconColor: String,
        title: String,
        description: String,
        onClick: () -> Unit
    ): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(12), dp(13))
            background = rounded(palette.surface, 18)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                animateTap(this)
                onClick()
            }
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

        val arrow = TextView(activity).apply {
            text = "›"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(palette.muted)
        }

        labels.addView(titleView)
        labels.addView(descriptionView)

        row.addView(iconView, LinearLayout.LayoutParams(dp(46), dp(46)))
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(arrow, LinearLayout.LayoutParams(dp(34), dp(46)))

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
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

        val toggle = FrameLayout(activity).apply {
            isClickable = true
            isFocusable = true
            contentDescription = title
        }

        val track = View(activity).apply {
            background = rounded(
                if (checked) palette.accent else palette.surfaceAlt,
                15
            )
        }

        val thumb = View(activity).apply {
            background = rounded(
                if (checked) Color.WHITE else palette.muted,
                11
            )
            elevation = dp(2).toFloat()
        }

        toggle.addView(
            track,
            FrameLayout.LayoutParams(dp(48), dp(28), Gravity.CENTER)
        )
        toggle.addView(
            thumb,
            FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER_VERTICAL or Gravity.START).apply {
                leftMargin = if (checked) dp(23) else dp(3)
            }
        )

        var value = checked

        fun render(next: Boolean, animate: Boolean) {
            value = next
            val target = if (next) dp(23).toFloat() else dp(3).toFloat()

            track.background = rounded(
                if (next) palette.accent else palette.surfaceAlt,
                15
            )
            thumb.background = rounded(
                if (next) Color.WHITE else palette.muted,
                11
            )

            thumb.animate().cancel()
            if (animate && settings.animations) {
                thumb.animate()
                    .translationX(target - (thumb.layoutParams as FrameLayout.LayoutParams).leftMargin)
                    .setDuration(190L)
                    .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                    .withEndAction {
                        val lp = thumb.layoutParams as FrameLayout.LayoutParams
                        lp.leftMargin = target.toInt()
                        thumb.translationX = 0f
                        thumb.layoutParams = lp
                    }
                    .start()

                toggle.animate().cancel()
                toggle.scaleX = 0.97f
                toggle.scaleY = 0.97f
                toggle.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(170L)
                    .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                    .start()
            } else {
                val lp = thumb.layoutParams as FrameLayout.LayoutParams
                lp.leftMargin = target.toInt()
                thumb.layoutParams = lp
            }
        }

        toggle.setOnClickListener {
            val next = !value
            render(next, true)
            onChange(next)
        }

        row.addView(iconView, LinearLayout.LayoutParams(dp(46), dp(46)))
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(toggle, LinearLayout.LayoutParams(dp(54), dp(36)))

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
