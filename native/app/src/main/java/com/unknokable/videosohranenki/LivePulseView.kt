package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class LivePulseView(context: Context) : View(context) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.8f)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var live = false
    private var animations = true
    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1650L
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
            dotPaint.color = Color.parseColor("#6D7480")
            canvas.drawCircle(cx, cy, dp(5f), dotPaint)

            ringPaint.color = withAlpha(Color.parseColor("#6D7480"), 54)
            canvas.drawCircle(cx, cy, dp(10f), ringPaint)
            return
        }

        val red = Color.parseColor("#FF334F")

        if (animations) {
            drawWave(canvas, cx, cy, red, phase)
            drawWave(canvas, cx, cy, red, (phase + .5f) % 1f)

            val pulse = .5f + .5f * sin(phase * Math.PI.toFloat() * 2f)
            dotPaint.color = red
            canvas.drawCircle(cx, cy, dp(5.2f + pulse * .7f), dotPaint)

            dotPaint.color = withAlpha(Color.WHITE, 120)
            canvas.drawCircle(cx - dp(1.4f), cy - dp(1.4f), dp(1.4f), dotPaint)
        } else {
            ringPaint.color = withAlpha(red, 92)
            canvas.drawCircle(cx, cy, dp(11f), ringPaint)
            ringPaint.color = withAlpha(red, 44)
            canvas.drawCircle(cx, cy, dp(17f), ringPaint)
            dotPaint.color = red
            canvas.drawCircle(cx, cy, dp(5.5f), dotPaint)
        }
    }

    private fun drawWave(canvas: Canvas, cx: Float, cy: Float, color: Int, p: Float) {
        val radius = dp(8f + 14f * p)
        val alpha = ((1f - p) * 105f).toInt().coerceIn(0, 105)
        ringPaint.color = withAlpha(color, alpha)
        ringPaint.strokeWidth = dp(1.5f + (1f - p) * .7f)
        canvas.drawCircle(cx, cy, radius, ringPaint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
