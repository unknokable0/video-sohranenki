package com.unknokable.videosohranenki

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = GradientDrawable().apply {
            setColor(palette.surface)
            cornerRadius = dp(28).toFloat()
            setStroke(dp(1), palette.stroke)
        }
        elevation = dp(10).toFloat()

        addTab(SohrTab.VIDEOS, "▶", "Видео", selected == SohrTab.VIDEOS)
        addTab(SohrTab.SETTINGS, "⚙", "Настройки", selected == SohrTab.SETTINGS)
        addTab(SohrTab.ACCOUNT, "●", "Аккаунт", selected == SohrTab.ACCOUNT)
    }

    private fun addTab(tab: SohrTab, icon: String, label: String, active: Boolean) {
        val item = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = GradientDrawable().apply {
                setColor(if (active) palette.accentSoft else Color.TRANSPARENT)
                cornerRadius = dp(20).toFloat()
            }
            if (active) {
                translationY = -dp(4).toFloat()
                scaleX = 1.04f
                scaleY = 1.04f
            }
        }

        val iconView = TextView(context).apply {
            text = icon
            textSize = if (tab == SohrTab.ACCOUNT) 15f else 17f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (active) palette.accent else palette.muted)
        }

        val labelView = TextView(context).apply {
            text = label
            textSize = 10.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(if (active) palette.text else palette.muted)
            setPadding(0, dp(2), 0, 0)
        }

        item.addView(iconView)
        item.addView(labelView)

        item.setOnClickListener {
            if (active) return@setOnClickListener
            item.animate()
                .translationY(-dp(7).toFloat())
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setDuration(95)
                .withEndAction {
                    item.animate()
                        .translationY(-dp(4).toFloat())
                        .scaleX(1.04f)
                        .scaleY(1.04f)
                        .setDuration(120)
                        .withEndAction { onSelect(tab) }
                        .start()
                }
                .start()
        }

        addView(
            item,
            LayoutParams(0, dp(58), 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
