package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.sin

/**
 * SOHR loader inspired by the supplied Lottie morph timing, but rendered natively.
 * It draws a flowing S ribbon, so there is no extra Lottie runtime or JSON parsing cost.
 */
class LoadingWaveView(context: Context, private val color: Int) : View(context) {

    private val basePath = Path()
    private val drawPath = Path()
    private val measure = PathMeasure()
    private var phase = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1600L
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }

    init {
        animator.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rebuildPath(w.toFloat(), h.toFloat())
    }

    private fun rebuildPath(w: Float, h: Float) {
        val left = w * 0.25f
        val right = w * 0.75f
        val top = h * 0.18f
        val mid = h * 0.50f
        val bottom = h * 0.82f

        basePath.reset()
        basePath.moveTo(right, top)
        basePath.cubicTo(
            w * 0.44f, top - h * 0.02f,
            left, h * 0.28f,
            left, h * 0.38f
        )
        basePath.cubicTo(
            left, h * 0.49f,
            right, h * 0.48f,
            right, mid + h * 0.06f
        )
        basePath.cubicTo(
            right, h * 0.68f,
            w * 0.58f, bottom + h * 0.02f,
            left, bottom
        )
        measure.setPath(basePath, false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0 || measure.length <= 0f) return

        val length = measure.length
        val breathe = 1f + sin((phase * Math.PI * 2).toFloat()) * 0.025f
        canvas.save()
        canvas.scale(breathe, breathe, width / 2f, height / 2f)

        for (trail in 2 downTo 0) {
            val local = (phase + trail * 0.11f) % 1f
            val segment = 0.42f - trail * 0.055f
            val start = local * length
            val end = start + segment * length

            drawPath.reset()
            if (end <= length) {
                measure.getSegment(start, end, drawPath, true)
            } else {
                measure.getSegment(start, length, drawPath, true)
                measure.getSegment(0f, end - length, drawPath, true)
            }

            val alpha = when (trail) {
                0 -> 255
                1 -> 150
                else -> 70
            }
            paint.color = (color and 0x00FFFFFF) or (alpha shl 24)
            paint.strokeWidth = dp(
                when (trail) {
                    0 -> 7.2f
                    1 -> 5.4f
                    else -> 3.8f
                }
            )
            canvas.drawPath(drawPath, paint)
        }

        canvas.restore()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
