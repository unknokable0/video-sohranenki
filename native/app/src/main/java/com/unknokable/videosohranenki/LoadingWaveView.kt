package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.PathInterpolator
import kotlin.math.sin

class LoadingWaveView(context: Context, private val color: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1180L
        repeatCount = ValueAnimator.INFINITE
        interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }

    init { animator.start() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val size = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val radius = size * .29f
        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius)

        paint.strokeWidth = dp(2.4f)
        paint.color = withAlpha(color, 36)
        canvas.drawCircle(cx, cy, radius, paint)

        val start = -90f + phase * 360f
        paint.strokeWidth = dp(3.2f)
        paint.color = withAlpha(color, 245)
        canvas.drawArc(bounds, start, 104f, false, paint)

        paint.strokeWidth = dp(2.2f)
        paint.color = withAlpha(color, 105)
        canvas.drawArc(bounds, start + 182f, 54f, false, paint)

        val pulse = .5f + .5f * sin(phase * Math.PI.toFloat() * 2f)
        centerPaint.color = withAlpha(color, (135 + pulse * 90f).toInt())
        canvas.drawCircle(cx, cy, dp(2.4f + pulse * .8f), centerPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isRunning) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private fun withAlpha(value: Int, alpha: Int): Int =
        (value and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
