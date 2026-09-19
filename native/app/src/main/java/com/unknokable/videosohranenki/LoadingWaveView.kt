package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.sin

class LoadingWaveView(context: Context, private val color: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(5f)
    }
    private val rect = RectF()
    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200L
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }

    init { animator.start() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = dp(8f)
        rect.set(pad, pad, width - pad, height - pad)
        for (i in 0..2) {
            val local = (phase + i * 0.14f) % 1f
            val alpha = (220 - i * 55).coerceAtLeast(70)
            paint.color = (color and 0x00FFFFFF) or (alpha shl 24)
            paint.strokeWidth = dp(4.8f - i * 0.7f)
            val wobble = (sin((phase * Math.PI * 2 + i).toFloat()) * 12f)
            canvas.drawArc(rect, local * 360f + wobble, 78f - i * 12f, false, paint)
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
