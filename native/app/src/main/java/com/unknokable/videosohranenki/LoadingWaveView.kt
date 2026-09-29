package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.view.View
import android.view.animation.LinearInterpolator

class LoadingWaveView(
    context: Context,
    private val color: Int
) : View(context) {

    private val basePath = Path()
    private val segmentPath = Path()
    private val measure = PathMeasure()
    private var phase = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1380L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val width = w.toFloat()
        val height = h.toFloat()
        val left = width * 0.25f
        val right = width * 0.75f
        val top = height * 0.19f
        val bottom = height * 0.81f

        basePath.reset()
        basePath.moveTo(right, top)
        basePath.cubicTo(
            width * 0.50f, height * 0.15f,
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
            width * 0.56f, height * 0.83f,
            left, bottom
        )

        measure.setPath(basePath, false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val length = measure.length
        if (width <= 0 || height <= 0 || length <= 0f) return

        val segmentLength = length * 0.30f
        val start = phase * length
        val end = start + segmentLength

        segmentPath.reset()
        if (end <= length) {
            measure.getSegment(start, end, segmentPath, true)
        } else {
            measure.getSegment(start, length, segmentPath, true)
            measure.getSegment(0f, end - length, segmentPath, true)
        }

        paint.color = color
        paint.alpha = 235
        paint.strokeWidth = dp(4.4f)
        canvas.drawPath(segmentPath, paint)
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

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}
