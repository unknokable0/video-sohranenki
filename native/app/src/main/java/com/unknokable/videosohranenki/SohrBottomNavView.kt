package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

enum class SohrTab { VIDEOS, STREAK, SETTINGS, ACCOUNT }

class SohrBottomNavView(
    context: Context,
    private val palette: ThemePalette,
    selected: SohrTab,
    private val onSelect: (SohrTab) -> Unit
) : FrameLayout(context) {

    private val tabs = listOf(SohrTab.VIDEOS, SohrTab.STREAK, SohrTab.SETTINGS, SohrTab.ACCOUNT)
    private val icons = listOf(R.drawable.ic_nav_video, R.drawable.ic_nav_streak, R.drawable.ic_nav_settings, R.drawable.ic_nav_account)
    private val labels = listOf("Смотреть", "Стрик", "Настройки", "Профиль")
    private val slots = mutableListOf<FrameLayout>()
    private val iconsView = mutableListOf<View>()
    private val labelsView = mutableListOf<TextView>()
    private var selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)
    private var animator: ValueAnimator? = null
    private val ease = PathInterpolator(0.22f, 1f, 0.36f, 1f)
    private val streakColor = StreakFireView.colorForStreak(StreakTracker(context).currentStreak())

    private val indicator = View(context).apply {
        background = GradientDrawable().apply {
            setColor(alpha(palette.accentSoft, 205))
            cornerRadius = dp(19).toFloat()
            setStroke(dp(1), alpha(palette.accent, 100))
        }
    }

    init {
        setPadding(dp(6), dp(6), dp(6), dp(6))
        clipChildren = true
        clipToPadding = true
        background = GradientDrawable().apply {
            setColor(alpha(palette.surface, 225))
            cornerRadius = dp(28).toFloat()
            setStroke(dp(1), alpha(palette.stroke, 170))
        }
        elevation = dp(7).toFloat()

        addView(indicator, LayoutParams(0, dp(54), Gravity.START or Gravity.CENTER_VERTICAL))

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        tabs.forEachIndexed { index, tab ->
            row.addView(slot(index, tab), LinearLayout.LayoutParams(0, dp(54), 1f))
        }
        addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54), Gravity.CENTER))
        post { position(false); render() }
    }

    private fun slot(index: Int, tab: SohrTab): FrameLayout {
        val root = FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            contentDescription = labels[index]
            setOnClickListener { select(index, true, true) }
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(5), dp(7), dp(4))
        }
        val icon: View = if (tab == SohrTab.STREAK) {
            StreakFireView(context, streakColor)
        } else {
            ImageView(context).apply {
                setImageResource(icons[index])
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
        }
        val label = TextView(context).apply {
            text = labels[index]
            textSize = 10.5f
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(0, dp(2), 0, 0)
        }
        column.addView(icon, LinearLayout.LayoutParams(dp(23), dp(23)))
        column.addView(label)
        root.addView(column, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        slots += root; iconsView += icon; labelsView += label
        return root
    }

    fun syncSelected(tab: SohrTab, animate: Boolean = false) {
        val i = tabs.indexOf(tab)
        if (i >= 0 && i != selectedIndex) select(i, animate, false) else render()
    }

    private fun select(index: Int, animate: Boolean, notify: Boolean) {
        if (index !in tabs.indices || index == selectedIndex) return
        selectedIndex = index
        render()
        val slot = slotWidth()
        if (slot > 0) {
            animator?.cancel()
            if (animate) {
                animator = ValueAnimator.ofFloat(indicator.translationX, index * slot).apply {
                    duration = 220
                    interpolator = ease
                    addUpdateListener { indicator.translationX = it.animatedValue as Float }
                    start()
                }
                val v = slots[index]
                v.scaleX=.94f; v.scaleY=.94f
                v.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(ease).start()
            } else indicator.translationX = index * slot
        }
        if (notify) onSelect(tabs[index])
    }

    private fun render() {
        tabs.indices.forEach { i ->
            val active = i == selectedIndex
            val icon = iconsView[i]
            if (icon is ImageView) {
                icon.imageTintList = ColorStateList.valueOf(if (active) palette.accent else alpha(palette.muted, 210))
            } else if (icon is StreakFireView) {
                icon.setFlameColor(streakColor)
                icon.alpha = if (active) 1f else .56f
            }
            labelsView[i].setTextColor(if (active) palette.text else palette.muted)
            labelsView[i].setTypeface(labelsView[i].typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
            slots[i].alpha = if (active) 1f else .82f
        }
    }

    private fun position(animate: Boolean) {
        val slot = slotWidth()
        if (slot <= 0f) return
        (indicator.layoutParams as LayoutParams).also {
            it.width = slot.toInt()
            it.height = dp(54)
            indicator.layoutParams = it
        }
        if (animate) indicator.animate().translationX(selectedIndex * slot).setDuration(180).setInterpolator(ease).start()
        else indicator.translationX = selectedIndex * slot
    }

    private fun slotWidth(): Float = (width - paddingLeft - paddingRight).coerceAtLeast(0) / tabs.size.toFloat()
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){ super.onSizeChanged(w,h,oldw,oldh); post{position(false)} }
    override fun onDetachedFromWindow(){ animator?.cancel(); super.onDetachedFromWindow() }

    private fun alpha(color:Int, a:Int)=Color.argb(a.coerceIn(0,255),Color.red(color),Color.green(color),Color.blue(color))
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
