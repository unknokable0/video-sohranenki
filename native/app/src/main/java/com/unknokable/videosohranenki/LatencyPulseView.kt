package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator

class LatencyPulseView(context: Context) : View(context) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }

    private var statusColor = Color.parseColor("#43D18D")
    private var active = true
    private var animationsEnabled = true
    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1350L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }

    fun setState(color: Int, active: Boolean, animations: Boolean) {
        val changed =
            statusColor != color ||
            this.active != active ||
            animationsEnabled != animations

        statusColor = color
        this.active = active
        animationsEnabled = animations

        if (active && animations && isAttachedToWindow) {
            if (!animator.isStarted) animator.start()
        } else {
            if (animator.isStarted) animator.cancel()
            phase = 0f
        }

        if (changed) invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (active && animationsEnabled && !animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        if (animator.isStarted) animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f

        if (active && animationsEnabled) {
            val radius = dp(4.8f + phase * 3.8f)
            val alpha = ((1f - phase) * 72f).toInt().coerceIn(0, 72)
            ringPaint.color = withAlpha(statusColor, alpha)
            canvas.drawCircle(cx, cy, radius, ringPaint)
        }

        dotPaint.color = if (active) statusColor else withAlpha(statusColor, 190)
        canvas.drawCircle(cx, cy, dp(3.0f), dotPaint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
