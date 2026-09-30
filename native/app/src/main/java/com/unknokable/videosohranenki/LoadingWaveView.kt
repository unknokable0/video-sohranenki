package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.view.View
import android.os.SystemClock
import android.view.animation.LinearInterpolator
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

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val cycleMs = 1850L

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        // The animator is only a frame ticker. Position comes from uptime, so the
        // moving segment never pauses on animator repeat boundaries.
        duration = 60_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            val raw = ((SystemClock.uptimeMillis() % cycleMs).toFloat() / cycleMs.toFloat())
                .coerceIn(0f, 0.999999f)
            // Old cloud path/timing stays the same. The segment only slows down
            // and speeds up; derivative remains positive, so it never stops.
            phase = raw + 0.068f * sin(raw * Math.PI.toFloat() * 2f)
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

        val minDp = minOf(width, height) / resources.displayMetrics.density
        // About 25% thinner than the previous version: still clearly visible,
        // but lighter and more premium without a neon-heavy outline.
        val baseStroke = (minDp * 0.046f).coerceIn(2.4f, 4.05f)
        val activeStroke = (minDp * 0.061f).coerceIn(3.2f, 5.2f)

        basePaint.color = withAlpha(color, 86)
        basePaint.strokeWidth = dp(baseStroke)
        canvas.drawPath(outlinePath, basePaint)

        val segmentLength = length * 0.275f
        val start = phase * length
        val end = start + segmentLength
        segmentPath.reset()

        if (end <= length) {
            measure.getSegment(start, end, segmentPath, true)
        } else {
            measure.getSegment(start, length, segmentPath, true)
            measure.getSegment(0f, end - length, segmentPath, true)
        }

        segmentPaint.color = withAlpha(color, 224)
        segmentPaint.strokeWidth = dp(activeStroke)
        canvas.drawPath(segmentPath, segmentPaint)
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
