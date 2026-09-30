package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

enum class SohrTab {
    VIDEOS,
    STREAK,
    SETTINGS,
    ACCOUNT
}

class SohrBottomNavView(
    context: Context,
    private val palette: ThemePalette,
    selected: SohrTab,
    private val onSelect: (SohrTab) -> Unit
) : FrameLayout(context) {

    private val tabs = listOf(SohrTab.VIDEOS, SohrTab.STREAK, SohrTab.SETTINGS, SohrTab.ACCOUNT)
    private val icons = listOf(
        R.drawable.ic_nav_video,
        R.drawable.ic_nav_streak,
        R.drawable.ic_nav_settings,
        R.drawable.ic_nav_account
    )
    private val labels = listOf("Видео", "Стрик", "Настройки", "Аккаунт")

    private val columns = mutableListOf<LinearLayout>()
    private val iconViews = mutableListOf<View>()
    private val labelViews = mutableListOf<TextView>()

    private var selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)
    private var indicatorAnimator: ValueAnimator? = null

    private val smoothInterpolator = SohrMotion.smooth()
    private val streakTracker = StreakTracker(context)

    private val indicator = View(context).apply {
        background = GradientDrawable().apply {
            setColor(palette.accentSoft)
            cornerRadius = dp(20).toFloat()
        }
    }

    init {
        isClickable = true
        isFocusable = true
        setPadding(dp(6), dp(6), dp(6), dp(6))
        clipChildren = true
        clipToPadding = true

        background = GradientDrawable().apply {
            setColor(palette.surface)
            cornerRadius = dp(26).toFloat()
            setStroke(dp(1), palette.stroke)
        }
        elevation = dp(4).toFloat()

        addView(indicator, LayoutParams(0, dp(56), Gravity.START or Gravity.CENTER_VERTICAL))

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = true
            clipToPadding = true
        }

        tabs.forEachIndexed { index, tab ->
            row.addView(buildSlot(index, tab), LinearLayout.LayoutParams(0, dp(56), 1f))
        }

        addView(
            row,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56), Gravity.CENTER)
        )

        // Do not consume touches on the whole navigation container.
        // Each tab owns its full slot, so taps cannot be swallowed by a parent gesture listener.
        isClickable = false
        isFocusable = false

        post {
            positionIndicator(false)
            updateStates(selectedIndex)
        }
    }

    private fun buildSlot(index: Int, tab: SohrTab): FrameLayout {
        val slot = FrameLayout(context).apply {
            clipChildren = true
            clipToPadding = true
            isClickable = true
            isFocusable = true
            contentDescription = labels[index]
            setOnClickListener { requestIndex(index) }
        }

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            clipChildren = true
            clipToPadding = true
        }

        val icon: View = if (tab == SohrTab.STREAK) {
            StreakFireView(
                context,
                StreakFireView.colorForStreak(streakTracker.currentStreak())
            ).apply {
                contentDescription = "Стрик"
                setLit(streakTracker.watchedToday(), animate = false)
            }
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

        val iconSize = if (tab == SohrTab.STREAK) dp(25) else dp(23)
        column.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))
        column.addView(label)

        slot.addView(
            column,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        columns += column
        iconViews += icon
        labelViews += label
        return slot
    }

    private fun requestIndex(target: Int) {
        if (target !in tabs.indices) return

        SohrHaptics.select(this)

        // Always forward the tap. If visual state got ahead of the screen during
        // a fast transition, tapping the same tab retries navigation instead of
        // being silently ignored.
        selectedIndex = target
        updateStates(target)
        positionIndicator(animate = true)
        animatePress(target)

        // Keep navigation immediate. The previous next-frame deferral made
        // the bottom bar feel laggy even though the animation itself was fast.
        onSelect(tabs[target])
    }

    fun syncSelected(tab: SohrTab, animate: Boolean = false) {
        val target = tabs.indexOf(tab)
        if (target < 0) return

        if (target == selectedIndex) {
            updateStates(selectedIndex)
            // A user tap may already be animating the indicator. Do not cancel
            // that transition just because the destination screen rendered.
            if (!animate) positionIndicator(false)
            return
        }

        indicatorAnimator?.cancel()
        indicator.animate().cancel()
        selectIndex(target, animate, false)
    }

    private fun selectIndex(target: Int, animate: Boolean, notify: Boolean) {
        if (target !in tabs.indices || target == selectedIndex) return

        selectedIndex = target
        updateStates(target)

        val slot = slotWidth()
        if (slot <= 0f) {
            if (notify) onSelect(tabs[target])
            return
        }

        indicatorAnimator?.cancel()

        if (animate) {
            val from = indicator.translationX
            val to = target * slot
            indicatorAnimator = ValueAnimator.ofFloat(from, to).apply {
                duration = 105L
                interpolator = smoothInterpolator
                addUpdateListener { indicator.translationX = it.animatedValue as Float }
                start()
            }
            animatePress(target)
        } else {
            indicator.translationX = target * slot
        }

        if (notify) onSelect(tabs[target])
    }

    private fun animatePress(index: Int) {
        val column = columns[index]
        val icon = iconViews[index]

        column.animate().cancel()
        icon.animate().cancel()
        column.scaleX = 1f
        column.scaleY = 1f
        icon.rotation = 0f

        column.animate()
            .scaleX(0.955f)
            .scaleY(0.955f)
            .setDuration(45L)
            .setInterpolator(smoothInterpolator)
            .withEndAction {
                column.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(90L)
                    .setInterpolator(smoothInterpolator)
                    .start()
            }
            .start()

        if (icon !is StreakFireView) {
            icon.animate()
                .rotation(-2.5f)
                .setDuration(SohrMotion.FAST / 2)
                .setInterpolator(smoothInterpolator)
                .withEndAction {
                    icon.animate()
                        .rotation(0f)
                        .setDuration(SohrMotion.FAST)
                        .setInterpolator(smoothInterpolator)
                        .start()
                }
                .start()
        }
    }

    private fun updateStates(active: Int) {
        columns.forEachIndexed { index, column ->
            val selected = index == active
            val icon = iconViews[index]

            if (icon is ImageView) {
                icon.imageTintList = ColorStateList.valueOf(
                    if (selected) palette.accent else withAlpha(palette.muted, 220)
                )
            } else if (icon is StreakFireView) {
                icon.setFlameColor(
                    StreakFireView.colorForStreak(streakTracker.currentStreak())
                )
                icon.setLit(streakTracker.watchedToday(), animate = false)
                icon.alpha = if (selected) 1f else 0.58f
            }

            labelViews[index].setTextColor(if (selected) palette.text else palette.muted)
            labelViews[index].setTypeface(
                labelViews[index].typeface,
                if (selected) Typeface.BOLD else Typeface.NORMAL
            )
            column.alpha = if (selected) 1f else 0.84f
            if (!selected) {
                column.scaleX = 1f
                column.scaleY = 1f
                icon.rotation = 0f
            }
        }
    }

    private fun positionIndicator(animate: Boolean) {
        val slot = slotWidth()
        if (slot <= 0f) return

        val params = indicator.layoutParams as LayoutParams
        params.width = slot.toInt()
        params.height = dp(56)
        indicator.layoutParams = params

        if (animate) {
            indicator.animate().cancel()
            indicatorAnimator?.cancel()
            val from = indicator.translationX
            val to = selectedIndex * slot
            indicator.scaleY = 0.94f
            indicatorAnimator = ValueAnimator.ofFloat(from, to).apply {
                duration = SohrMotion.NORMAL
                interpolator = smoothInterpolator
                addUpdateListener { indicator.translationX = it.animatedValue as Float }
                start()
            }
            indicator.animate()
                .scaleY(1f)
                .setDuration(105L)
                .setInterpolator(smoothInterpolator)
                .start()
        } else {
            indicatorAnimator?.cancel()
            indicator.animate().cancel()
            indicator.scaleY = 1f
            indicator.translationX = selectedIndex * slot
        }
    }

    private fun slotWidth(): Float =
        ((width - paddingLeft - paddingRight).coerceAtLeast(0) / tabs.size.toFloat())

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { positionIndicator(false) }
    }

    override fun onDetachedFromWindow() {
        indicatorAnimator?.cancel()
        columns.forEach { it.animate().cancel() }
        iconViews.forEach { it.animate().cancel() }
        super.onDetachedFromWindow()
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
