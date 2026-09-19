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
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

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
) : FrameLayout(context) {

    private val tabs = listOf(SohrTab.VIDEOS, SohrTab.SETTINGS, SohrTab.ACCOUNT)
    private val icons = listOf(R.drawable.ic_nav_video, R.drawable.ic_nav_settings, R.drawable.ic_nav_account)
    private val labels = listOf("Видео", "Настройки", "Аккаунт")
    private val itemViews = mutableListOf<LinearLayout>()
    private val iconViews = mutableListOf<ImageView>()
    private val labelViews = mutableListOf<TextView>()
    private var selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)
    private var downX = 0f
    private var animating = false

    private val indicator = View(context).apply {
        background = GradientDrawable().apply {
            setColor(withAlpha(palette.accentSoft, 220))
            cornerRadius = dp(20).toFloat()
        }
    }

    init {
        setPadding(dp(6), dp(6), dp(6), dp(6))
        clipChildren = false
        clipToPadding = false
        background = GradientDrawable().apply {
            setColor(withAlpha(palette.surface, 224))
            cornerRadius = dp(26).toFloat()
            setStroke(dp(1), withAlpha(palette.stroke, 175))
        }
        elevation = dp(4).toFloat()

        addView(indicator, LayoutParams(0, dp(56), Gravity.START or Gravity.CENTER_VERTICAL))

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)
        }

        tabs.forEachIndexed { index, tab ->
            val item = buildItem(index, tab)
            row.addView(item, LinearLayout.LayoutParams(0, dp(56), 1f))
        }

        addView(
            row,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(56),
                Gravity.CENTER
            )
        )

        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    if (abs(dx) > dp(42)) {
                        val target = if (dx < 0) selectedIndex + 1 else selectedIndex - 1
                        selectIndex(target.coerceIn(0, tabs.lastIndex), true)
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

    private fun buildItem(index: Int, tab: SohrTab): LinearLayout {
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = null
            setOnClickListener { selectIndex(index, true) }
        }

        val icon = ImageView(context).apply {
            setImageResource(icons[index])
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

        val label = TextView(context).apply {
            text = labels[index]
            textSize = 10.5f
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(0, dp(2), 0, 0)
        }

        item.addView(icon, LinearLayout.LayoutParams(dp(23), dp(23)))
        item.addView(label)

        itemViews += item
        iconViews += icon
        labelViews += label
        return item
    }

    private fun selectIndex(target: Int, animate: Boolean) {
        if (target == selectedIndex || target !in tabs.indices || animating) return

        val old = selectedIndex
        selectedIndex = target
        updateStates(target)

        val slot = slotWidth()
        val from = indicator.translationX
        val to = target * slot

        if (!animate || slot <= 0f) {
            indicator.translationX = to
            onSelect(tabs[target])
            return
        }

        animating = true

        itemViews[old].animate()
            .scaleX(0.98f)
            .scaleY(0.98f)
            .setDuration(90)
            .start()

        itemViews[target].scaleX = 0.96f
        itemViews[target].scaleY = 0.96f
        itemViews[target].animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220)
            .start()

        ValueAnimator.ofFloat(from, to).apply {
            duration = 270L
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener {
                indicator.translationX = it.animatedValue as Float
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    animating = false
                    itemViews[old].scaleX = 1f
                    itemViews[old].scaleY = 1f
                    onSelect(tabs[target])
                }
            })
            start()
        }
    }

    private fun updateStates(active: Int) {
        itemViews.forEachIndexed { index, item ->
            val selected = index == active
            iconViews[index].imageTintList = ColorStateList.valueOf(
                if (selected) palette.accent else withAlpha(palette.muted, 220)
            )
            labelViews[index].setTextColor(if (selected) palette.text else palette.muted)
            labelViews[index].setTypeface(
                labelViews[index].typeface,
                if (selected) Typeface.BOLD else Typeface.NORMAL
            )
            item.alpha = if (selected) 1f else 0.86f
        }
    }

    private fun positionIndicator(animate: Boolean) {
        val width = slotWidth()
        if (width <= 0f) return
        val params = indicator.layoutParams as LayoutParams
        params.width = width.toInt()
        params.height = dp(56)
        indicator.layoutParams = params
        if (animate) {
            indicator.animate().translationX(selectedIndex * width).setDuration(220).start()
        } else {
            indicator.translationX = selectedIndex * width
        }
    }

    private fun slotWidth(): Float =
        ((width - paddingLeft - paddingRight).coerceAtLeast(0) / 3f)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { positionIndicator(false) }
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
