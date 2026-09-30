package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

class LivePulseView(context: Context) : View(context) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var live = false
    private var animations = true
    private var phase = 0f

    // Deliberately muted live red: visible on the dark SOHR surface without
    // looking neon or glowing.
    private var liveColor = Color.parseColor("#E5484D")

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800L
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
            dotPaint.color = Color.parseColor("#747984")
            canvas.drawCircle(cx, cy, dp(2.45f), dotPaint)
            return
        }

        if (animations) {
            drawRipple(canvas, cx, cy, phase)
        } else {
            ringPaint.color = withAlpha(liveColor, 40)
            ringPaint.strokeWidth = dp(0.78f)
            canvas.drawCircle(cx, cy, dp(5.3f), ringPaint)
        }

        // A very small breathing motion gives life to the dot without a glow.
        val breath = if (animations) {
            ((sin(phase * PI * 2.0) + 1.0) * 0.5).toFloat()
        } else {
            0.5f
        }
        val dotRadius = dp(2.55f + breath * 0.18f)

        dotPaint.color = liveColor
        canvas.drawCircle(cx, cy, dotRadius, dotPaint)
    }

    private fun drawRipple(canvas: Canvas, cx: Float, cy: Float, wavePhase: Float) {
        val progress = wavePhase.coerceIn(0f, 1f)
        val eased = 1f - (1f - progress) * (1f - progress)
        val radius = dp(4.1f + eased * 4.6f)
        val fade = 1f - progress
        val alpha = (fade * fade * 46f).toInt().coerceIn(0, 46)

        ringPaint.color = withAlpha(liveColor, alpha)
        ringPaint.strokeWidth = dp(0.82f - 0.18f * eased)
        canvas.drawCircle(cx, cy, radius, ringPaint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
