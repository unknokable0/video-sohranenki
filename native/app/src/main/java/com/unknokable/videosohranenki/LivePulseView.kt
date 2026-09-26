package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator

class LivePulseView(context: Context) : View(context) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.25f)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(dp(4.2f), BlurMaskFilter.Blur.NORMAL)
    }

    private var live = false
    private var animations = true
    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1450L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }

    fun setState(live: Boolean, animations: Boolean) {
        this.live = live
        this.animations = animations
        if (live && animations && isAttachedToWindow) {
            if (!animator.isStarted) animator.start()
        } else {
            if (animator.isStarted) animator.cancel()
            phase = 0f
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (live && animations && !animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        if (animator.isStarted) animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f

        if (!live) {
            val idle = Color.parseColor("#727783")
            glowPaint.color = withAlpha(idle, 22)
            canvas.drawCircle(cx, cy, dp(5.4f), glowPaint)
            dotPaint.color = idle
            canvas.drawCircle(cx, cy, dp(3.1f), dotPaint)
            return
        }

        val roseRed = Color.parseColor("#FF5F7E")

        if (animations) {
            val eased = 1f - (1f - phase) * (1f - phase)
            val radius = dp(5.4f + eased * 5.8f)
            val alpha = ((1f - phase) * 62f).toInt().coerceIn(0, 62)

            ringPaint.color = withAlpha(roseRed, alpha)
            ringPaint.strokeWidth = dp(1.15f)
            canvas.drawCircle(cx, cy, radius, ringPaint)

            glowPaint.color = withAlpha(roseRed, 42)
            canvas.drawCircle(cx, cy, dp(4.7f), glowPaint)

            dotPaint.color = roseRed
            canvas.drawCircle(cx, cy, dp(3.45f), dotPaint)
        } else {
            glowPaint.color = withAlpha(roseRed, 38)
            canvas.drawCircle(cx, cy, dp(4.8f), glowPaint)

            ringPaint.color = withAlpha(roseRed, 48)
            canvas.drawCircle(cx, cy, dp(8.5f), ringPaint)

            dotPaint.color = roseRed
            canvas.drawCircle(cx, cy, dp(3.45f), dotPaint)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
