package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

class LoadingWaveView(context: Context, private val color: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1100L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedFraction; invalidate() }
    }

    init { animator.start() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) * .23f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2.2f)
        paint.color = withAlpha(color, 42)
        canvas.drawCircle(cx, cy, radius, paint)

        paint.style = Paint.Style.FILL
        repeat(3) { index ->
            val local = (phase + index / 3f) % 1f
            val angle = local * Math.PI.toFloat() * 2f - Math.PI.toFloat() / 2f
            val pulse = .72f + .28f * (.5f + .5f * sin((local * Math.PI * 2).toFloat()))
            paint.color = withAlpha(color, (150 + 105 * pulse).toInt())
            canvas.drawCircle(
                cx + cos(angle) * radius,
                cy + sin(angle) * radius,
                dp(3.2f + 1.2f * pulse),
                paint
            )
        }

        paint.color = withAlpha(color, 205)
        canvas.drawCircle(cx, cy, dp(2.7f), paint)
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
