package com.unknokable.videosohranenki

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

enum class SohrTab {
    VIDEOS,
    SETTINGS,
    ACCOUNT
}

class SohrBottomNavView(
    context: Context,
    private val palette: ThemePalette,
    selected: SohrTab,
    private val onSelect: (SohrTab) -> Unit
) : LinearLayout(context) {

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(6), dp(6), dp(6), dp(6))
        clipChildren = false
        clipToPadding = false
        background = GradientDrawable().apply {
            setColor(withAlpha(palette.surface, 218))
            cornerRadius = dp(26).toFloat()
            setStroke(dp(1), withAlpha(palette.stroke, 190))
        }
        elevation = dp(5).toFloat()

        addTab(SohrTab.VIDEOS, R.drawable.ic_nav_video, "Видео", selected == SohrTab.VIDEOS)
        addTab(SohrTab.SETTINGS, R.drawable.ic_nav_settings, "Настройки", selected == SohrTab.SETTINGS)
        addTab(SohrTab.ACCOUNT, R.drawable.ic_nav_account, "Аккаунт", selected == SohrTab.ACCOUNT)
    }

    private fun addTab(tab: SohrTab, iconRes: Int, label: String, active: Boolean) {
        val item = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = GradientDrawable().apply {
                setColor(
                    if (active) withAlpha(palette.accentSoft, 210)
                    else Color.TRANSPARENT
                )
                cornerRadius = dp(20).toFloat()
            }
        }

        val iconView = ImageView(context).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(
                if (active) palette.accent else withAlpha(palette.muted, 220)
            )
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

        val labelView = TextView(context).apply {
            text = label
            textSize = 10.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(if (active) palette.text else palette.muted)
            setPadding(0, dp(2), 0, 0)
            maxLines = 1
        }

        item.addView(iconView, LayoutParams(dp(23), dp(23)))
        item.addView(
            labelView,
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        item.setOnClickListener {
            if (active) return@setOnClickListener
            item.animate()
                .scaleX(0.94f)
                .scaleY(0.94f)
                .setDuration(70)
                .withEndAction {
                    item.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(110)
                        .withEndAction { onSelect(tab) }
                        .start()
                }
                .start()
        }

        addView(
            item,
            LayoutParams(0, dp(56), 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
