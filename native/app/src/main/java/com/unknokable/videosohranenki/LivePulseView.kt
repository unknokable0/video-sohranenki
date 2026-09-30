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
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(1f)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(dp(3.6f), BlurMaskFilter.Blur.NORMAL)
    }

    private var live = false
    private var animations = true
    private var phase = 0f
    private var liveColor = Color.parseColor("#FF304F")

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1480L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }

    fun setLiveColor(color: Int) {
        liveColor = color
        invalidate()
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
            glowPaint.color = withAlpha(idle, 18)
            canvas.drawCircle(cx, cy, dp(4.8f), glowPaint)
            dotPaint.color = idle
            canvas.drawCircle(cx, cy, dp(3f), dotPaint)
            return
        }

        if (animations) {
            // Two identical waves with an even half-cycle offset keep the pulse
            // visually balanced and prevent one ring from bunching into another.
            drawWave(canvas, cx, cy, phase)
            drawWave(canvas, cx, cy, (phase + 0.5f) % 1f)

            glowPaint.color = withAlpha(liveColor, 30)
            canvas.drawCircle(cx, cy, dp(4.5f), glowPaint)

            ringPaint.color = withAlpha(liveColor, 70)
            ringPaint.strokeWidth = dp(0.85f)
            canvas.drawCircle(cx, cy, dp(4.8f), ringPaint)

            dotPaint.color = liveColor
            canvas.drawCircle(cx, cy, dp(3.1f), dotPaint)
        } else {
            glowPaint.color = withAlpha(liveColor, 26)
            canvas.drawCircle(cx, cy, dp(4.5f), glowPaint)

            ringPaint.color = withAlpha(liveColor, 42)
            ringPaint.strokeWidth = dp(0.9f)
            canvas.drawCircle(cx, cy, dp(7.8f), ringPaint)

            dotPaint.color = liveColor
            canvas.drawCircle(cx, cy, dp(3.1f), dotPaint)
        }
    }

    private fun drawWave(canvas: Canvas, cx: Float, cy: Float, wavePhase: Float) {
        // Smooth outward motion, then a soft fade. Both waves use the exact same
        // curve and geometry so the animation stays centered and even.
        val eased = 1f - (1f - wavePhase) * (1f - wavePhase)
        val radius = dp(4.9f + eased * 7.1f)
        val fade = 1f - wavePhase
        val alpha = (fade * fade * 88f).toInt().coerceIn(0, 88)

        ringPaint.color = withAlpha(liveColor, alpha)
        ringPaint.strokeWidth = dp(1.0f - 0.34f * eased)
        canvas.drawCircle(cx, cy, radius, ringPaint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
