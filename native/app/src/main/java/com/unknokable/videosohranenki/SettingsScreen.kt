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
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SettingsScreen(
    private val activity: Activity,
    private val settings: AppSettings,
    private val onBack: (Boolean) -> Unit,
    private val onAppearanceChanged: (Boolean, String, View) -> Unit,
    private val onLanguageChanged: () -> Unit,
    private val onCheckUpdates: () -> Unit
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

        root.addView(statisticsCard())
        root.addView(sourceSelector())

        root.addView(languageSelector())
        root.addView(appearanceSelector())

        root.addView(previewModeSelector())

        root.addView(settingRow(
            iconRes = R.drawable.ic_setting_autoplay,
            iconColor = "#B89AFF",
            title = "Автовоспроизведение",
            description = t("autoplay_desc"),
            checked = settings.autoplay
        ) { settings.autoplay = it })

        root.addView(settingRow(
            iconRes = R.drawable.ic_setting_animations,
            iconColor = "#76A9FF",
            title = t("animations"),
            description = t("animations_desc"),
            checked = settings.animations
        ) { settings.animations = it })

        root.addView(actionRow(
            iconRes = R.drawable.ic_setting_update,
            iconColor = "#8B5CF6",
            title = "Проверить обновления",
            description = "Автопроверка активна • SOHR " + BuildConfig.VERSION_NAME
        ) {
            onCheckUpdates()
        })

        val scroll = ScrollView(activity).apply {
            tag = "sohr_settings_scroll"
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

    private fun statisticsCard(): View {
        val prefs = activity.getSharedPreferences("sohr_stats", android.content.Context.MODE_PRIVATE)
        val videos = prefs.getInt("videos", 0)
        val watched = prefs.getInt("watched", 0)
        val seconds = prefs.getLong("watched_seconds", 0L)
        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L
        val tracker = StreakTracker(activity)
        val bestStreak = tracker.longestStreak()
        val completion = if (videos > 0) {
            ((watched.toFloat() / videos.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } else 0

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(palette.surface, 18)
        }

        card.addView(TextView(activity).apply {
            text = "Статистика"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })
        card.addView(TextView(activity).apply {
            text = "Коротко о ваших просмотрах в SOHR"
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(0, dp(3), 0, dp(12))
        })

        fun statCell(iconRes: Int, value: String, label: String): View =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = rounded(palette.surfaceAlt, 15)

                addView(ImageView(activity).apply {
                    setImageResource(iconRes)
                    imageTintList = ColorStateList.valueOf(palette.accent)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    background = rounded(palette.accentSoft, 12)
                }, LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                    marginEnd = dp(9)
                })

                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(activity).apply {
                        text = value
                        textSize = 16f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(palette.text)
                        includeFontPadding = false
                    })
                    addView(TextView(activity).apply {
                        text = label
                        textSize = 10.5f
                        setTextColor(palette.muted)
                        includeFontPadding = false
                        setPadding(0, dp(3), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }

        fun addRow(left: View, right: View) {
            card.addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(left, LinearLayout.LayoutParams(0, dp(62), 1f).apply {
                        marginEnd = dp(4)
                    })
                    addView(right, LinearLayout.LayoutParams(0, dp(62), 1f).apply {
                        marginStart = dp(4)
                    })
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(62)
                ).apply { bottomMargin = dp(8) }
            )
        }

        addRow(
            statCell(R.drawable.ic_nav_video, videos.toString(), "Всего видео"),
            statCell(R.drawable.ic_check, watched.toString(), "Просмотрено")
        )
        addRow(
            statCell(
                R.drawable.ic_stat_clock,
                if (hours > 0) "${hours}ч ${minutes}м" else "${minutes}м",
                "В просмотренном"
            ),
            statCell(R.drawable.ic_stat_trophy, bestStreak.toString(), "Лучший стрик")
        )

        val progressHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(4), dp(2), dp(7))
        }
        progressHeader.addView(TextView(activity).apply {
            text = "Прогресс медиатеки"
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        progressHeader.addView(TextView(activity).apply {
            text = completion.toString() + "%"
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.accent)
        })
        card.addView(progressHeader)

        val progressTrack = FrameLayout(activity).apply {
            background = rounded(palette.surfaceAlt, 5)
            clipToOutline = true
        }
        val progressFill = View(activity).apply {
            background = rounded(palette.accent, 5)
        }
        progressTrack.addView(
            progressFill,
            FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        card.addView(
            progressTrack,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8))
        )
        progressTrack.post {
            val params = progressFill.layoutParams as FrameLayout.LayoutParams
            params.width = (progressTrack.width * (completion / 100f)).toInt()
            progressFill.layoutParams = params
        }


        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(card)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
    }

    private fun sourceSelector(): View {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(palette.surface, 18)
        }

        box.addView(TextView(activity).apply {
            text = "Источник видео"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })
        box.addView(TextView(activity).apply {
            text = "Какие сборники показывать на главной"
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(0, dp(3), 0, dp(12))
        })

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

        fun option(label: String) = TextView(activity).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            isClickable = true
            isFocusable = true
        }

        val telegram = option("Telegram")
        val twitch = option("Twitch")
        buttons.addView(telegram, LinearLayout.LayoutParams(0, dp(44), 1f))
        buttons.addView(twitch, LinearLayout.LayoutParams(0, dp(44), 1f))
        selector.addView(indicator, FrameLayout.LayoutParams(0, dp(44), Gravity.START or Gravity.CENTER_VERTICAL))
        selector.addView(buttons, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), Gravity.CENTER))

        val sourceHint = TextView(activity).apply {
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(dp(2), dp(10), dp(2), 0)
        }

        fun updateHint(animated: Boolean) {
            val next = if (settings.videoSource == "twitch") {
                "Сборники Twitch • @t2x2"
            } else {
                "Сборники Telegram • @t2x2_video"
            }
            if (!animated || !settings.animations) {
                sourceHint.text = next
                sourceHint.alpha = 1f
                sourceHint.translationY = 0f
                return
            }
            sourceHint.animate().cancel()
            sourceHint.animate()
                .alpha(0f)
                .translationY(dp(3).toFloat())
                .setDuration(75L)
                .withEndAction {
                    sourceHint.text = next
                    sourceHint.translationY = -dp(3).toFloat()
                    sourceHint.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(SohrMotion.FAST)
                        .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                        .start()
                }
                .start()
        }

        fun moveIndicator(toTwitch: Boolean, animate: Boolean) {
            val slot = ((selector.width - selector.paddingLeft - selector.paddingRight) / 2f).coerceAtLeast(0f)
            if (slot <= 0f) return
            val params = indicator.layoutParams as FrameLayout.LayoutParams
            params.width = slot.toInt()
            params.height = dp(44)
            indicator.layoutParams = params
            val target = if (toTwitch) slot else 0f

            indicator.animate().cancel()
            if (animate && settings.animations) {
                indicator.animate()
                    .translationX(target)
                    .setDuration(230L)
                    .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                    .start()
            } else {
                indicator.translationX = target
            }

            telegram.setTextColor(if (!toTwitch) Color.WHITE else palette.muted)
            twitch.setTextColor(if (toTwitch) Color.WHITE else palette.muted)

            if (animate && settings.animations) {
                val active = if (toTwitch) twitch else telegram
                active.animate().cancel()
                active.scaleX = 0.96f
                active.scaleY = 0.96f
                active.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180L)
                    .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                    .start()
            }
        }

        telegram.setOnClickListener {
            if (settings.videoSource == "telegram") return@setOnClickListener
            animateTap(telegram)
            settings.videoSource = "telegram"
            needsReload = true
            moveIndicator(false, true)
            updateHint(true)
        }
        twitch.setOnClickListener {
            if (settings.videoSource == "twitch") return@setOnClickListener
            animateTap(twitch)
            settings.videoSource = "twitch"
            needsReload = true
            moveIndicator(true, true)
            updateHint(true)
        }

        selector.post { moveIndicator(settings.videoSource == "twitch", false) }
        updateHint(false)

        box.addView(selector, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        box.addView(sourceHint)

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(box)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
    }

    private fun previewModeSelector(): View {
        fun modeLabel(mode: String): String = when (mode) {
            "always" -> "Всегда"
            "off" -> "Выкл"
            else -> "Wi‑Fi"
        }

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(12), dp(13))
            background = rounded(palette.surface, 18)
            isClickable = true
            isFocusable = true
        }

        val icon = ImageView(activity).apply {
            setImageResource(R.drawable.ic_setting_previews)
            imageTintList = ColorStateList.valueOf(Color.parseColor("#58DFA0"))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = rounded(palette.surfaceAlt, 14)
        }

        val labels = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }

        val title = TextView(activity).apply {
            text = t("previews")
            textSize = 15f
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        }

        val subtitle = TextView(activity).apply {
            text = "Тихое превью • " + modeLabel(settings.previewMode)
            textSize = 12f
            includeFontPadding = false
            setTextColor(palette.muted)
            setPadding(0, dp(3), 0, 0)
        }

        val current = TextView(activity).apply {
            text = modeLabel(settings.previewMode)
            textSize = 11.5f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.accent)
            setPadding(dp(10), 0, dp(10), 0)
            background = rounded(palette.accentSoft, 13)
        }

        fun openPicker() {
            animateTap(row)
            val options = listOf("Wi‑Fi", "Всегда", "Выкл")
            val keys = listOf("wifi", "always", "off")
            val selected = keys.indexOf(settings.previewMode).coerceAtLeast(0)
            ModernDialogs.showChoices(
                context = activity,
                palette = palette,
                title = "Превью видео",
                options = options,
                selected = selected
            ) { which ->
                val next = keys.getOrNull(which) ?: return@showChoices
                if (settings.previewMode == next) return@showChoices
                settings.previewMode = next
                needsReload = true
                SohrHaptics.select(row)
                val label = modeLabel(next)
                current.text = label
                subtitle.text = "Тихое превью • " + label
            }
        }

        labels.addView(title)
        labels.addView(subtitle)

        row.addView(icon, LinearLayout.LayoutParams(dp(46), dp(46)))
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(current, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)))

        row.setOnClickListener { openPicker() }
        current.setOnClickListener { openPicker() }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
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

    private fun appearanceSelector(): View {
        val preset = AppThemes.preset(settings.themeAccent)
        val presetIndex = AppThemes.Presets.indexOfFirst { it.key == preset.key }.coerceAtLeast(0)

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(12), dp(13))
            background = rounded(palette.surface, 18)
            isClickable = true
            isFocusable = true
        }

        val icon = FrameLayout(activity).apply {
            background = rounded(palette.surfaceAlt, 14)
        }
        icon.addView(
            ImageView(activity).apply {
                setImageResource(R.drawable.ic_setting_appearance)
                imageTintList = ColorStateList.valueOf(palette.accent)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(11), dp(11), dp(11), dp(11))
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val labels = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        labels.addView(TextView(activity).apply {
            text = "Оформление"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })
        labels.addView(TextView(activity).apply {
            text = (if (settings.lightTheme) "Светлая" else "Тёмная") + " • " + preset.label
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(0, dp(3), 0, 0)
        })

        val preview = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf(
            AppThemes.Presets[presetIndex % AppThemes.Presets.size].previewColor,
            AppThemes.Presets[(presetIndex + 1) % AppThemes.Presets.size].previewColor,
            AppThemes.Presets[(presetIndex + 2) % AppThemes.Presets.size].previewColor
        ).forEach { color ->
            preview.addView(
                View(activity).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                    }
                },
                LinearLayout.LayoutParams(dp(12), dp(12)).apply {
                    marginStart = dp(3)
                }
            )
        }

        val arrow = ImageView(activity).apply {
            setImageResource(R.drawable.ic_chevron_right)
            imageTintList = ColorStateList.valueOf(palette.muted)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        card.addView(icon, LinearLayout.LayoutParams(dp(46), dp(46)))
        card.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)))
        card.addView(arrow, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginStart = dp(5) })

        card.setOnClickListener {
            animateTap(card)
            ModernDialogs.showAppearancePicker(
                context = activity,
                palette = palette,
                lightTheme = settings.lightTheme,
                accentKey = settings.themeAccent,
                presets = AppThemes.Presets
            ) { light, accent ->
                val normalized = AppThemes.preset(accent).key
                if (light != settings.lightTheme || normalized != settings.themeAccent) {
                    onAppearanceChanged(light, normalized, card)
                }
            }
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(card)
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
        SohrMotion.press(view, settings.animations)
    }

    private fun animateIconPulse(icon: View) {
        if (!settings.animations) return
        val ease = SohrMotion.smooth()
        icon.animate().cancel()
        icon.animate()
            .scaleX(0.88f)
            .scaleY(0.88f)
            .rotation(-4f)
            .setDuration(SohrMotion.FAST / 2)
            .setInterpolator(ease)
            .withEndAction {
                icon.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .rotation(0f)
                    .setDuration(150L)
                    .setInterpolator(ease)
                    .start()
            }
            .start()
    }

    private fun actionRow(
        iconRes: Int,
        iconColor: String,
        title: String,
        description: String,
        onClick: () -> Unit
    ): View {
        val iconView = ImageView(activity).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageTintList = ColorStateList.valueOf(Color.parseColor(iconColor))
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = rounded(palette.surfaceAlt, 14)
        }

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(12), dp(13))
            background = rounded(palette.surface, 18)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                animateTap(this)
                animateIconPulse(iconView)
                onClick()
            }
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

        val arrow = ImageView(activity).apply {
            setImageResource(R.drawable.ic_chevron_right)
            imageTintList = ColorStateList.valueOf(palette.muted)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(8), dp(8), dp(8), dp(8))
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
        iconRes: Int,
        iconColor: String,
        title: String,
        description: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ): View {
        val iconView = ImageView(activity).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageTintList = ColorStateList.valueOf(Color.parseColor(iconColor))
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = rounded(palette.surfaceAlt, 14)
        }

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(12), dp(13))
            background = rounded(palette.surface, 18)
            isClickable = true
            isFocusable = true
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
            FrameLayout.LayoutParams(
                dp(22),
                dp(22),
                Gravity.CENTER_VERTICAL or Gravity.START
            ).apply {
                leftMargin = dp(3)
            }
        )
        thumb.translationX = if (checked) dp(20).toFloat() else 0f

        var value = checked

        fun render(next: Boolean, animate: Boolean) {
            val previous = value
            value = next
            val target = if (next) dp(20).toFloat() else 0f
            val startTrack = if (previous) palette.accent else palette.surfaceAlt
            val endTrack = if (next) palette.accent else palette.surfaceAlt
            val startThumb = if (previous) Color.WHITE else palette.muted
            val endThumb = if (next) Color.WHITE else palette.muted

            thumb.animate().cancel()
            if (animate && settings.animations) {
                val ease = android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)

                android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 205L
                    interpolator = ease
                    val evaluator = android.animation.ArgbEvaluator()
                    addUpdateListener { animator ->
                        val fraction = animator.animatedFraction
                        val trackColor = evaluator.evaluate(fraction, startTrack, endTrack) as Int
                        val thumbColor = evaluator.evaluate(fraction, startThumb, endThumb) as Int
                        track.background = rounded(trackColor, 15)
                        thumb.background = rounded(thumbColor, 11)
                    }
                    start()
                }

                thumb.animate()
                    .translationX(target)
                    .setDuration(210L)
                    .setInterpolator(ease)
                    .start()

                toggle.animate().cancel()
                toggle.animate()
                    .scaleX(0.965f)
                    .scaleY(0.965f)
                    .setDuration(65L)
                    .setInterpolator(ease)
                    .withEndAction {
                        toggle.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(120L)
                            .setInterpolator(ease)
                            .start()
                    }
                    .start()
            } else {
                track.background = rounded(endTrack, 15)
                thumb.background = rounded(endThumb, 11)
                thumb.translationX = target
            }
        }

        fun toggleValue() {
            val next = !value
            render(next, true)
            animateIconPulse(iconView)
            onChange(next)
        }

        toggle.setOnClickListener { toggleValue() }
        row.setOnClickListener {
            animateTap(row)
            toggleValue()
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
