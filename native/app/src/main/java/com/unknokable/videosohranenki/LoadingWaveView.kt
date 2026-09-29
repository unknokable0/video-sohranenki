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
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

class LoadingWaveView(
    context: Context,
    private val color: Int
) : View(context) {

    private val sPath = Path()
    private val movingPath = Path()
    private val measure = PathMeasure()

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val railPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val movingGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val movingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val headPos = FloatArray(2)
    private var phase = 0f

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
        val width = w.toFloat()
        val height = h.toFloat()

        sPath.reset()
        sPath.moveTo(width * 0.79f, height * 0.17f)
        sPath.cubicTo(
            width * 0.64f, height * 0.075f,
            width * 0.30f, height * 0.10f,
            width * 0.21f, height * 0.29f
        )
        sPath.cubicTo(
            width * 0.12f, height * 0.48f,
            width * 0.36f, height * 0.52f,
            width * 0.57f, height * 0.55f
        )
        sPath.cubicTo(
            width * 0.78f, height * 0.58f,
            width * 0.84f, height * 0.69f,
            width * 0.75f, height * 0.80f
        )
        sPath.cubicTo(
            width * 0.61f, height * 0.96f,
            width * 0.32f, height * 0.93f,
            width * 0.18f, height * 0.82f
        )

        measure.setPath(sPath, false)

        railPaint.shader = LinearGradient(
            width * 0.18f,
            height * 0.10f,
            width * 0.82f,
            height * 0.90f,
            intArrayOf(
                withAlpha(lighten(color, 0.20f), 165),
                withAlpha(color, 205),
                withAlpha(lighten(color, 0.34f), 180)
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val length = measure.length
        if (length <= 0f || width <= 0 || height <= 0) return

        val unit = max(1f, minOf(width, height).toFloat() / 96f)
        val breathe = ((sin(phase * 2f * PI.toFloat()) + 1f) * 0.5f)

        glowPaint.shader = null
        glowPaint.color = withAlpha(color, (18 + breathe * 18f).toInt())
        glowPaint.strokeWidth = 11.5f * unit
        canvas.drawPath(sPath, glowPaint)

        railPaint.strokeWidth = 6.7f * unit
        canvas.drawPath(sPath, railPaint)

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

        movingGlowPaint.shader = null
        movingGlowPaint.color = withAlpha(lighten(color, 0.64f), 88)
        movingGlowPaint.strokeWidth = 6.8f * unit
        canvas.drawPath(movingPath, movingGlowPaint)

        movingPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                withAlpha(lighten(color, 0.18f), 35),
                lighten(color, 0.62f),
                Color.WHITE,
                lighten(color, 0.38f)
            ),
            floatArrayOf(0f, 0.33f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        movingPaint.strokeWidth = 3.1f * unit
        canvas.drawPath(movingPath, movingPaint)
        movingPaint.shader = null

        val headDistance = end % length
        if (measure.getPosTan(headDistance, headPos, null)) {
            headPaint.color = withAlpha(lighten(color, 0.55f), 72)
            canvas.drawCircle(headPos[0], headPos[1], 4.1f * unit, headPaint)

            headPaint.color = Color.WHITE
            canvas.drawCircle(headPos[0], headPos[1], 1.75f * unit, headPaint)
        }
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
}
