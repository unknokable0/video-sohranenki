package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator

class LivePulseView(context: Context) : View(context) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var live = false
    private var animations = true
    private var phase = 0f
    private var liveColor = Color.parseColor("#E91916")

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1600L
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
            val idle = Color.parseColor("#777B86")
            haloPaint.color = withAlpha(idle, 14)
            canvas.drawCircle(cx, cy, dp(3.8f), haloPaint)

            dotPaint.color = idle
            canvas.drawCircle(cx, cy, dp(2.55f), dotPaint)
            return
        }

        if (animations) {
            // Twitch-like minimal pulse: one clean primary ring and one much
            // softer trailing ring. Both share the exact same geometry.
            drawWave(canvas, cx, cy, phase, 1f)
            drawWave(canvas, cx, cy, (phase + 0.5f) % 1f, 0.56f)
        } else {
            ringPaint.color = withAlpha(liveColor, 34)
            ringPaint.strokeWidth = dp(0.78f)
            canvas.drawCircle(cx, cy, dp(5.8f), ringPaint)
        }

        // Keep the red contained to the indicator itself. No red labels/cards.
        haloPaint.color = withAlpha(liveColor, 24)
        canvas.drawCircle(cx, cy, dp(4.0f), haloPaint)

        ringPaint.color = withAlpha(liveColor, 58)
        ringPaint.strokeWidth = dp(0.72f)
        canvas.drawCircle(cx, cy, dp(3.7f), ringPaint)

        dotPaint.color = liveColor
        canvas.drawCircle(cx, cy, dp(2.7f), dotPaint)
    }

    private fun drawWave(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        wavePhase: Float,
        strength: Float
    ) {
        val progress = wavePhase.coerceIn(0f, 1f)
        val inverse = 1f - progress
        val eased = 1f - inverse * inverse * inverse
        val radius = dp(4.15f + eased * 5.25f)
        val alpha = (inverse * inverse * 62f * strength)
            .toInt()
            .coerceIn(0, 62)

        ringPaint.color = withAlpha(liveColor, alpha)
        ringPaint.strokeWidth = dp(0.86f - 0.24f * eased)
        canvas.drawCircle(cx, cy, radius, ringPaint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
