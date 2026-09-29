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

class LoadingWaveView(
    context: Context,
    private val color: Int
) : View(context) {

    private val basePath = Path()
    private val segmentPath = Path()
    private val measure = PathMeasure()
    private var phase = 0f

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val width = w.toFloat()
        val height = h.toFloat()
        val left = width * 0.24f
        val right = width * 0.76f
        val top = height * 0.18f
        val bottom = height * 0.82f

        basePath.reset()
        basePath.moveTo(right, top)
        basePath.cubicTo(
            width * 0.49f, height * 0.14f,
            left, height * 0.25f,
            left, height * 0.37f
        )
        basePath.cubicTo(
            left, height * 0.49f,
            right, height * 0.48f,
            right, height * 0.59f
        )
        basePath.cubicTo(
            right, height * 0.72f,
            width * 0.56f, height * 0.84f,
            left, bottom
        )

        measure.setPath(basePath, false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val length = measure.length
        if (width <= 0 || height <= 0 || length <= 0f) return

        val breathe = 1f + sin((phase * Math.PI * 2).toFloat()) * 0.006f
        canvas.save()
        canvas.scale(breathe, breathe, width / 2f, height / 2f)

        basePaint.color = withAlpha(color, 46)
        basePaint.strokeWidth = dp(4.2f)
        canvas.drawPath(basePath, basePaint)

        val start = phase * length
        val end = start + length * 0.32f
        segmentPath.reset()

        if (end <= length) {
            measure.getSegment(start, end, segmentPath, true)
        } else {
            measure.getSegment(start, length, segmentPath, true)
            measure.getSegment(0f, end - length, segmentPath, true)
        }

        segmentPaint.color = withAlpha(color, 238)
        segmentPaint.strokeWidth = dp(5.8f)
        canvas.drawPath(segmentPath, segmentPaint)

        canvas.restore()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncAnimator()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        syncAnimator()
    }

    private fun syncAnimator() {
        if (isAttachedToWindow && windowVisibility == VISIBLE) {
            if (!animator.isRunning) animator.start()
        } else if (animator.isRunning) {
            animator.cancel()
        }
    }

    private fun withAlpha(value: Int, alpha: Int): Int =
        (value and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}
