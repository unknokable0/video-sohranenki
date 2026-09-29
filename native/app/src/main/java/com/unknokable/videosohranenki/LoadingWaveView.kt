package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.view.View
import android.view.animation.PathInterpolator
import kotlin.math.sin

class LoadingWaveView(
    context: Context,
    private val color: Int
) : View(context) {

    private val outlinePath = Path()
    private val segmentPath = Path()
    private val measure = PathMeasure()
    private var phase = 0f

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
        duration = 1850L
        repeatCount = ValueAnimator.INFINITE
        interpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val width = w.toFloat()
        val height = h.toFloat()

        val cx = width * 0.50f
        val top = height * 0.18f
        val shoulderY = height * 0.40f
        val sideY = height * 0.56f
        val bottom = height * 0.82f

        outlinePath.reset()
        outlinePath.moveTo(cx, top)

        outlinePath.cubicTo(
            width * 0.36f, height * 0.17f,
            width * 0.32f, height * 0.30f,
            width * 0.31f, shoulderY
        )
        outlinePath.cubicTo(
            width * 0.19f, height * 0.42f,
            width * 0.13f, height * 0.49f,
            width * 0.13f, sideY
        )
        outlinePath.cubicTo(
            width * 0.13f, height * 0.68f,
            width * 0.24f, height * 0.73f,
            width * 0.33f, height * 0.74f
        )
        outlinePath.cubicTo(
            width * 0.39f, height * 0.75f,
            width * 0.42f, bottom,
            cx, bottom
        )
        outlinePath.cubicTo(
            width * 0.58f, bottom,
            width * 0.61f, height * 0.75f,
            width * 0.67f, height * 0.74f
        )
        outlinePath.cubicTo(
            width * 0.76f, height * 0.73f,
            width * 0.87f, height * 0.68f,
            width * 0.87f, sideY
        )
        outlinePath.cubicTo(
            width * 0.87f, height * 0.49f,
            width * 0.81f, height * 0.42f,
            width * 0.69f, shoulderY
        )
        outlinePath.cubicTo(
            width * 0.68f, height * 0.30f,
            width * 0.64f, height * 0.17f,
            cx, top
        )
        outlinePath.close()

        measure.setPath(outlinePath, true)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val length = measure.length
        if (width <= 0 || height <= 0 || length <= 0f) return

        val breathe = 1f + sin((phase * Math.PI * 2).toFloat()) * 0.012f
        val centerX = width / 2f
        val centerY = height / 2f

        canvas.save()
        canvas.scale(breathe, breathe, centerX, centerY)

        basePaint.color = withAlpha(color, 52)
        basePaint.strokeWidth = dp(2.2f)
        canvas.drawPath(outlinePath, basePaint)

        glowPaint.color = withAlpha(color, 30)
        glowPaint.strokeWidth = dp(7.5f)
        canvas.drawPath(outlinePath, glowPaint)

        val segmentLength = length * 0.27f
        val start = phase * length
        val end = start + segmentLength
        segmentPath.reset()

        if (end <= length) {
            measure.getSegment(start, end, segmentPath, true)
        } else {
            measure.getSegment(start, length, segmentPath, true)
            measure.getSegment(0f, end - length, segmentPath, true)
        }

        segmentPaint.color = withAlpha(color, 245)
        segmentPaint.strokeWidth = dp(3.6f)
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
