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
    private val onBack: (Boolean) -> Unit
) {
    private var needsReload = false

    fun build(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#08070D"))
            setPadding(dp(12), dp(8), dp(12), dp(18))
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_back)
            background = rounded("#181322", 24)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { onBack(needsReload) }
        }

        val title = TextView(activity).apply {
            text = "Настройки"
            textSize = 24f
            setTextColor(Color.parseColor("#F7F5FF"))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(14), 0, 0, 0)
        }

        header.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(title)
        root.addView(header)

        val subtitle = TextView(activity).apply {
            text = "Персонализация и воспроизведение"
            textSize = 13f
            setTextColor(Color.parseColor("#9E96AD"))
            setPadding(dp(4), dp(12), 0, dp(18))
        }
        root.addView(subtitle)

        root.addView(settingRow(
            icon = "▶",
            iconColor = "#B89AFF",
            title = "Автовоспроизведение",
            description = "Начинать видео сразу после открытия",
            checked = settings.autoplay
        ) { settings.autoplay = it })

        root.addView(settingRow(
            icon = "▣",
            iconColor = "#7CFFB2",
            title = "Превью видео",
            description = "Показывать изображения из Telegram",
            checked = settings.previews
        ) {
            settings.previews = it
            needsReload = true
        })

        root.addView(settingRow(
            icon = "✦",
            iconColor = "#76A9FF",
            title = "Плавные анимации",
            description = "Переходы, нажатия и появление карточек",
            checked = settings.animations
        ) { settings.animations = it })

        root.addView(settingRow(
            icon = "↻",
            iconColor = "#FF9BCB",
            title = "Автоповорот fullscreen",
            description = "Поворачивать плеер горизонтально",
            checked = settings.autoRotateFullscreen
        ) { settings.autoRotateFullscreen = it })

        val info = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded("#11101A", 18)
        }
        val infoTitle = TextView(activity).apply {
            text = "ВИДЕО СОХРАНЕНКИ"
            textSize = 15f
            setTextColor(Color.parseColor("#F7F5FF"))
            setTypeface(typeface, Typeface.BOLD)
        }
        val infoText = TextView(activity).apply {
            text = "Нативный Android-плеер • Telegram TDLib"
            textSize = 12f
            setTextColor(Color.parseColor("#81798E"))
            setPadding(0, dp(5), 0, 0)
        }
        info.addView(infoTitle)
        info.addView(infoText)

        root.addView(
            info,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(20) }
        )

        return root
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
            background = rounded("#11101A", 18)
        }

        val iconView = TextView(activity).apply {
            text = icon
            gravity = Gravity.CENTER
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(iconColor))
            background = rounded("#211A30", 14)
        }

        val labels = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }

        val titleView = TextView(activity).apply {
            text = title
            textSize = 15f
            setTextColor(Color.parseColor("#F7F5FF"))
            setTypeface(typeface, Typeface.BOLD)
        }

        val descriptionView = TextView(activity).apply {
            text = description
            textSize = 12f
            setTextColor(Color.parseColor("#91899D"))
            setPadding(0, dp(3), 0, 0)
        }

        labels.addView(titleView)
        labels.addView(descriptionView)

        val toggle = SwitchMaterial(activity).apply {
            isChecked = checked
            buttonTintList = null
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

    private fun rounded(color: String, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
