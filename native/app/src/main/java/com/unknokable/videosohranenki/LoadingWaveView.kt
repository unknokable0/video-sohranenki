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
import android.view.animation.LinearInterpolator

class LoadingWaveView(context: Context, private val color: Int) : View(context) {
    private val sPath = Path()
    private val movingPath = Path()
    private val measure = PathMeasure()

    private val railPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val innerRailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val softGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val movingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var phase = 0f
    private var movingGradient: LinearGradient? = null

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1180L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rebuildS(w.toFloat(), h.toFloat())
    }

    private fun rebuildS(w: Float, h: Float) {
        val left = w * 0.20f
        val right = w * 0.80f

        sPath.reset()
        sPath.moveTo(right, h * 0.17f)
        sPath.cubicTo(
            w * 0.62f, h * 0.095f,
            left, h * 0.15f,
            left, h * 0.34f
        )
        sPath.cubicTo(
            left, h * 0.49f,
            right, h * 0.48f,
            right, h * 0.62f
        )
        sPath.cubicTo(
            right, h * 0.81f,
            w * 0.38f, h * 0.91f,
            left, h * 0.82f
        )

        measure.setPath(sPath, false)

        movingGradient = LinearGradient(
            0f,
            h * 0.20f,
            w,
            h * 0.80f,
            intArrayOf(
                withAlpha(Color.WHITE, 90),
                lighten(color, 0.72f),
                Color.WHITE,
                lighten(color, 0.46f)
            ),
            floatArrayOf(0f, 0.24f, 0.56f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val length = measure.length
        if (width <= 0 || height <= 0 || length <= 0f) return

        // The S itself stays calm and solid.
        railPaint.color = withAlpha(color, 82)
        railPaint.strokeWidth = dp(10.0f)
        canvas.drawPath(sPath, railPaint)

        // Darker inner rail gives the impression that the moving line travels inside the S.
        innerRailPaint.color = withAlpha(Color.BLACK, 92)
        innerRailPaint.strokeWidth = dp(4.6f)
        canvas.drawPath(sPath, innerRailPaint)

        // A compact light segment travels through the entire S continuously.
        val segmentLength = length * 0.25f
        val start = phase * length
        val end = start + segmentLength

        movingPath.reset()
        if (end <= length) {
            measure.getSegment(start, end, movingPath, true)
        } else {
            measure.getSegment(start, length, movingPath, true)
            measure.getSegment(0f, end - length, movingPath, true)
        }

        softGlowPaint.color = withAlpha(color, 82)
        softGlowPaint.strokeWidth = dp(7.0f)
        canvas.drawPath(movingPath, softGlowPaint)

        movingPaint.shader = movingGradient
        movingPaint.strokeWidth = dp(3.4f)
        canvas.drawPath(movingPath, movingPaint)
        movingPaint.shader = null
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
