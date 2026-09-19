package com.unknokable.videosohranenki

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
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

    private val slotViews = mutableListOf<FrameLayout>()
    private val columns = mutableListOf<LinearLayout>()
    private val rippleViews = mutableListOf<View>()
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
        isFocusable = true
        isClickable = true
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
            row.addView(
                buildSlot(index, tab),
                LinearLayout.LayoutParams(0, dp(56), 1f)
            )
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
            animateSelectedEntrance(selectedIndex)
        }
    }

    private fun buildSlot(index: Int, tab: SohrTab): FrameLayout {
        val slot = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            isClickable = true
            isFocusable = true
            setOnClickListener { selectIndex(index, true) }
            contentDescription = labels[index]
        }

        val ripple = View(context).apply {
            alpha = 0f
            scaleX = 0.74f
            scaleY = 0.74f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(withAlpha(palette.accent, 178))
            }
        }

        slot.addView(
            ripple,
            LayoutParams(dp(38), dp(38), Gravity.CENTER)
        )

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = null
            pivotX = dp(36).toFloat()
            pivotY = dp(28).toFloat()
        }

        val icon = ImageView(context).apply {
            setImageResource(icons[index])
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            pivotX = dp(11.5f)
            pivotY = dp(11.5f)
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
        slot.addView(
            column,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        slotViews += slot
        columns += column
        rippleViews += ripple
        iconViews += icon
        labelViews += label
        return slot
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

        animateJsonTap(target)
        animateOldSelection(old)

        ValueAnimator.ofFloat(from, to).apply {
            duration = 270L
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener {
                indicator.translationX = it.animatedValue as Float
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    animating = false
                }
            })
            start()
        }

        // Let the JSON-like tap animation start, then switch content almost immediately.
        // This removes the old "pressed... wait... screen changes" feeling.
        postDelayed({ onSelect(tabs[target]) }, 55L)
    }

    private fun animateJsonTap(index: Int) {
        val ripple = rippleViews[index]
        val column = columns[index]
        val icon = iconViews[index]

        // JSON reference: circle 74% -> 138%, opacity about 70% -> 0.
        ripple.animate().cancel()
        ripple.alpha = 0.70f
        ripple.scaleX = 0.74f
        ripple.scaleY = 0.74f
        ripple.animate()
            .scaleX(1.38f)
            .scaleY(1.38f)
            .setDuration(333L)
            .setInterpolator(DecelerateInterpolator(1.4f))
            .start()

        ripple.animate()
            .alpha(0f)
            .setStartDelay(67L)
            .setDuration(417L)
            .start()

        // JSON reference overshoot: ~100 -> 111.1 -> 110%.
        column.animate().cancel()
        val scaleUp = ObjectAnimator.ofPropertyValuesHolder(
            column,
            PropertyValuesHolder.ofFloat(View.SCALE_X, column.scaleX, 1.111f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, column.scaleY, 1.111f)
        ).apply {
            duration = 200L
            interpolator = DecelerateInterpolator(1.9f)
        }

        val settle = ObjectAnimator.ofPropertyValuesHolder(
            column,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.111f, 1.10f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.111f, 1.10f)
        ).apply {
            duration = 200L
            interpolator = DecelerateInterpolator(1.5f)
        }

        // JSON reference rotation: 0 -> -11 -> +5 -> -3 -> 0 degrees.
        val rotation = ObjectAnimator.ofFloat(
            icon,
            View.ROTATION,
            0f, -11f, 5f, -3f, 0f
        ).apply {
            duration = 883L
            interpolator = DecelerateInterpolator(1.25f)
        }

        AnimatorSet().apply {
            playSequentially(scaleUp, settle)
            start()
        }
        rotation.start()
    }

    private fun animateOldSelection(index: Int) {
        val column = columns[index]
        column.animate().cancel()

        val compress = ObjectAnimator.ofPropertyValuesHolder(
            column,
            PropertyValuesHolder.ofFloat(View.SCALE_X, column.scaleX, 0.982f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, column.scaleY, 0.982f)
        ).apply {
            duration = 200L
            interpolator = DecelerateInterpolator(1.7f)
        }

        val settle = ObjectAnimator.ofPropertyValuesHolder(
            column,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.982f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.982f, 1f)
        ).apply {
            duration = 260L
            interpolator = DecelerateInterpolator(1.5f)
        }

        AnimatorSet().apply {
            playSequentially(compress, settle)
            start()
        }
    }

    private fun animateSelectedEntrance(index: Int) {
        val column = columns[index]
        column.scaleX = 1.04f
        column.scaleY = 1.04f
        column.animate()
            .scaleX(1.10f)
            .scaleY(1.10f)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator(1.7f))
            .start()
    }

    private fun updateStates(active: Int) {
        columns.forEachIndexed { index, column ->
            val selected = index == active
            iconViews[index].imageTintList = ColorStateList.valueOf(
                if (selected) palette.accent else withAlpha(palette.muted, 220)
            )
            labelViews[index].setTextColor(if (selected) palette.text else palette.muted)
            labelViews[index].setTypeface(
                labelViews[index].typeface,
                if (selected) Typeface.BOLD else Typeface.NORMAL
            )
            column.alpha = if (selected) 1f else 0.86f
            if (!selected && column.scaleX < 0.99f) {
                column.scaleX = 1f
                column.scaleY = 1f
            }
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
            indicator.animate()
                .translationX(selectedIndex * width)
                .setDuration(220L)
                .setInterpolator(DecelerateInterpolator(1.7f))
                .start()
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

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).toInt()
}
