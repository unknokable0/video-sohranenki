package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Shader
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
    private val headPosition = FloatArray(2)
    private var phase = 0f

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val segmentGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1650L
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rebuildPath(w.toFloat(), h.toFloat())
    }

    private fun rebuildPath(w: Float, h: Float) {
        val left = w * 0.24f
        val right = w * 0.76f
        val top = h * 0.18f
        val bottom = h * 0.82f

        basePath.reset()
        basePath.moveTo(right, top)
        basePath.cubicTo(
            w * 0.49f, h * 0.14f,
            left, h * 0.25f,
            left, h * 0.37f
        )
        basePath.cubicTo(
            left, h * 0.49f,
            right, h * 0.48f,
            right, h * 0.59f
        )
        basePath.cubicTo(
            right, h * 0.72f,
            w * 0.56f, h * 0.84f,
            left, bottom
        )

        measure.setPath(basePath, false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val length = measure.length
        if (width <= 0 || height <= 0 || length <= 0f) return

        val breathe = 1f + sin((phase * Math.PI * 2).toFloat()) * 0.010f
        canvas.save()
        canvas.scale(breathe, breathe, width / 2f, height / 2f)

        basePaint.shader = null
        basePaint.color = withAlpha(color, 44)
        basePaint.strokeWidth = dp(5.0f)
        canvas.drawPath(basePath, basePaint)

        val start = phase * length
        val end = start + length * 0.34f
        segmentPath.reset()

        if (end <= length) {
            measure.getSegment(start, end, segmentPath, true)
        } else {
            measure.getSegment(start, length, segmentPath, true)
            measure.getSegment(0f, end - length, segmentPath, true)
        }

        segmentGlowPaint.shader = null
        segmentGlowPaint.color = withAlpha(color, 64)
        segmentGlowPaint.strokeWidth = dp(10.0f)
        canvas.drawPath(segmentPath, segmentGlowPaint)

        segmentPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                lighten(color, 0.12f),
                Color.WHITE,
                lighten(color, 0.20f)
            ),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
        segmentPaint.strokeWidth = dp(6.4f)
        canvas.drawPath(segmentPath, segmentPaint)
        segmentPaint.shader = null

        val headDistance = end % length
        if (measure.getPosTan(headDistance, headPosition, null)) {
            headPaint.color = withAlpha(Color.WHITE, 230)
            canvas.drawCircle(headPosition[0], headPosition[1], dp(2.2f), headPaint)
        }

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

    private fun lighten(value: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)

        fun channel(c: Int): Int =
            (c + (255 - c) * t).toInt().coerceIn(0, 255)

        return Color.rgb(
            channel(Color.red(value)),
            channel(Color.green(value)),
            channel(Color.blue(value))
        )
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}
