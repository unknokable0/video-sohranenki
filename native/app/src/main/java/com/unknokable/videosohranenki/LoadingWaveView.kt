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
import kotlin.math.sin

class LoadingWaveView(
    context: Context,
    private val color: Int
) : View(context) {
    private val sPath = Path()
    private val movingPath = Path()
    private val tailPath = Path()
    private val measure = PathMeasure()

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val railPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val movingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

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
        sPath.moveTo(width * .79f, height * .18f)
        sPath.cubicTo(
            width * .68f, height * .08f,
            width * .35f, height * .075f,
            width * .23f, height * .25f
        )
        sPath.cubicTo(
            width * .09f, height * .45f,
            width * .29f, height * .52f,
            width * .54f, height * .55f
        )
        sPath.cubicTo(
            width * .79f, height * .58f,
            width * .88f, height * .72f,
            width * .72f, height * .84f
        )
        sPath.cubicTo(
            width * .56f, height * .96f,
            width * .29f, height * .92f,
            width * .16f, height * .80f
        )
        measure.setPath(sPath, false)

        railPaint.shader = LinearGradient(
            width * .12f, height * .12f,
            width * .88f, height * .88f,
            intArrayOf(
                withAlpha(lighten(color, .22f), 135),
                withAlpha(color, 190),
                withAlpha(lighten(color, .28f), 145)
            ),
            floatArrayOf(0f, .52f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val length = measure.length
        if (length <= 0f || width <= 0 || height <= 0) return

        val unit = max(1f, minOf(width, height).toFloat() / 96f)
        val breathe = ((sin(phase * Math.PI.toFloat() * 2f) + 1f) * .5f)

        shadowPaint.shader = null
        shadowPaint.color = withAlpha(color, (12 + breathe * 9).toInt())
        shadowPaint.strokeWidth = 10.2f * unit
        canvas.drawPath(sPath, shadowPaint)

        railPaint.strokeWidth = 5.8f * unit
        canvas.drawPath(sPath, railPaint)

        val segmentLength = length * .19f
        val start = phase * length
        val end = start + segmentLength

        movingPath.reset()
        tailPath.reset()
        addWrappedSegment(movingPath, start, end, length)

        val tailStart = (start - length * .055f + length) % length
        val tailEnd = (start + length * .055f) % length
        if (tailStart <= tailEnd) {
            measure.getSegment(tailStart, tailEnd, tailPath, true)
        } else {
            measure.getSegment(tailStart, length, tailPath, true)
            measure.getSegment(0f, tailEnd, tailPath, true)
        }

        movingPaint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(
                withAlpha(lighten(color, .25f), 60),
                lighten(color, .52f),
                Color.WHITE,
                lighten(color, .40f)
            ),
            floatArrayOf(0f, .38f, .72f, 1f),
            Shader.TileMode.CLAMP
        )
        movingPaint.strokeWidth = 5.2f * unit
        canvas.drawPath(movingPath, movingPaint)

        corePaint.shader = null
        corePaint.color = withAlpha(Color.WHITE, 160)
        corePaint.strokeWidth = 1.65f * unit
        canvas.drawPath(tailPath, corePaint)
    }

    private fun addWrappedSegment(path: Path, start: Float, end: Float, length: Float) {
        if (end <= length) {
            measure.getSegment(start, end, path, true)
        } else {
            measure.getSegment(start, length, path, true)
            measure.getSegment(0f, end - length, path, true)
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
