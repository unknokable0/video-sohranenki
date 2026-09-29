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
import kotlin.math.max

class LoadingWaveView(
    context: Context,
    private val color: Int
) : View(context) {

    private val sPath = Path()
    private val moving = Path()
    private val measure = PathMeasure()

    private val outerGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val innerRail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val movingGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
        duration = 1320L
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
        sPath.moveTo(width * 0.78f, height * 0.16f)
        sPath.cubicTo(
            width * 0.60f, height * 0.075f,
            width * 0.24f, height * 0.13f,
            width * 0.23f, height * 0.34f
        )
        sPath.cubicTo(
            width * 0.22f, height * 0.50f,
            width * 0.77f, height * 0.47f,
            width * 0.77f, height * 0.64f
        )
        sPath.cubicTo(
            width * 0.77f, height * 0.84f,
            width * 0.43f, height * 0.93f,
            width * 0.21f, height * 0.82f
        )

        measure.setPath(sPath, false)

        rail.shader = LinearGradient(
            0f,
            height * 0.10f,
            width,
            height * 0.90f,
            intArrayOf(
                lighten(color, 0.34f),
                color,
                lighten(color, 0.16f)
            ),
            floatArrayOf(0f, 0.53f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val length = measure.length
        if (length <= 0f || width <= 0 || height <= 0) return

        val unit = max(1f, minOf(width, height).toFloat() / 96f)

        outerGlow.shader = null
        outerGlow.color = withAlpha(color, 30)
        outerGlow.strokeWidth = 13f * unit
        canvas.drawPath(sPath, outerGlow)

        rail.alpha = 225
        rail.strokeWidth = 8.2f * unit
        canvas.drawPath(sPath, rail)

        innerRail.shader = null
        innerRail.color = withAlpha(Color.BLACK, 118)
        innerRail.strokeWidth = 3.5f * unit
        canvas.drawPath(sPath, innerRail)

        val segmentLength = length * 0.21f
        val start = phase * length
        val end = start + segmentLength

        moving.reset()
        if (end <= length) {
            measure.getSegment(start, end, moving, true)
        } else {
            measure.getSegment(start, length, moving, true)
            measure.getSegment(0f, end - length, moving, true)
        }

        movingGlow.shader = null
        movingGlow.color = withAlpha(lighten(color, 0.50f), 100)
        movingGlow.strokeWidth = 7f * unit
        canvas.drawPath(moving, movingGlow)

        movingPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                withAlpha(color, 22),
                lighten(color, 0.72f),
                Color.WHITE,
                lighten(color, 0.44f)
            ),
            floatArrayOf(0f, 0.34f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        movingPaint.strokeWidth = 3f * unit
        canvas.drawPath(moving, movingPaint)
        movingPaint.shader = null

        val headDistance = end % length
        if (measure.getPosTan(headDistance, headPos, null)) {
            headPaint.color = lighten(color, 0.58f)
            headPaint.alpha = 72
            canvas.drawCircle(headPos[0], headPos[1], 4.6f * unit, headPaint)

            headPaint.color = Color.WHITE
            headPaint.alpha = 238
            canvas.drawCircle(headPos[0], headPos[1], 2.05f * unit, headPaint)
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
