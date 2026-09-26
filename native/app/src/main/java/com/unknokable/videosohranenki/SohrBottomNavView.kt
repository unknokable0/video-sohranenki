package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

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
    private var downX = 0f
    private var indicatorAnimator: ValueAnimator? = null

    private val smoothInterpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)
    private val streakColor = StreakFireView.colorForStreak(StreakTracker(context).currentStreak())

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

        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    if (abs(dx) > dp(44)) {
                        val target = if (dx < 0) selectedIndex + 1 else selectedIndex - 1
                        selectIndex(target.coerceIn(0, tabs.lastIndex), true, true)
                    }
                    performClick()
                    true
                }
                else -> true
            }
        }

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
            setOnClickListener { selectIndex(index, true, true) }
        }

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            clipChildren = true
            clipToPadding = true
        }

        val icon: View = if (tab == SohrTab.STREAK) {
            StreakFireView(context, streakColor).apply {
                contentDescription = "Стрик"
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

    fun syncSelected(tab: SohrTab, animate: Boolean = false) {
        val target = tabs.indexOf(tab)
        if (target < 0 || target == selectedIndex) {
            updateStates(selectedIndex)
            return
        }
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
                duration = 205L
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
            .setDuration(65L)
            .setInterpolator(smoothInterpolator)
            .withEndAction {
                column.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(125L)
                    .setInterpolator(smoothInterpolator)
                    .start()
            }
            .start()

        if (icon !is StreakFireView) {
            icon.animate()
                .rotation(-2.5f)
                .setDuration(70L)
                .setInterpolator(smoothInterpolator)
                .withEndAction {
                    icon.animate()
                        .rotation(0f)
                        .setDuration(115L)
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
                icon.setFlameColor(streakColor)
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
            indicator.animate()
                .translationX(selectedIndex * slot)
                .setDuration(190L)
                .setInterpolator(smoothInterpolator)
                .start()
        } else {
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
