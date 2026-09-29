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
import kotlin.math.sin

class LoadingWaveView(context: Context, private val color: Int) : View(context) {
    private val basePath = Path()
    private val activePath = Path()
    private val measure = PathMeasure()
    private val head = FloatArray(2)
    private var phase = 0f
    private var gradient: LinearGradient? = null

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
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1350L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val left = w * 0.22f
        val right = w * 0.78f
        basePath.reset()
        basePath.moveTo(right, h * 0.18f)
        basePath.cubicTo(w * 0.55f, h * 0.10f, left, h * 0.22f, left, h * 0.36f)
        basePath.cubicTo(left, h * 0.50f, right, h * 0.48f, right, h * 0.61f)
        basePath.cubicTo(right, h * 0.75f, w * 0.52f, h * 0.90f, left, h * 0.81f)
        measure.setPath(basePath, false)
        gradient = LinearGradient(
            0f, h * 0.15f, w.toFloat(), h * 0.85f,
            intArrayOf(lighten(color, 0.42f), color, lighten(color, 0.18f)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val length = measure.length
        if (width <= 0 || height <= 0 || length <= 0f) return

        val pulse = 1f + sin((phase * Math.PI * 2).toFloat()) * 0.018f
        canvas.save()
        canvas.scale(pulse, pulse, width / 2f, height / 2f)

        basePaint.color = withAlpha(color, 42)
        basePaint.strokeWidth = dp(4.8f)
        canvas.drawPath(basePath, basePaint)

        val start = phase * length
        val end = start + length * 0.38f
        activePath.reset()
        if (end <= length) {
            measure.getSegment(start, end, activePath, true)
        } else {
            measure.getSegment(start, length, activePath, true)
            measure.getSegment(0f, end - length, activePath, true)
        }

        glowPaint.color = withAlpha(color, 56)
        glowPaint.strokeWidth = dp(10.5f)
        canvas.drawPath(activePath, glowPaint)

        activePaint.shader = gradient
        activePaint.strokeWidth = dp(6.2f)
        canvas.drawPath(activePath, activePaint)
        activePaint.shader = null

        if (measure.getPosTan(end % length, head, null)) {
            headPaint.color = lighten(color, 0.56f)
            canvas.drawCircle(head[0], head[1], dp(3.1f), headPaint)
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
        fun channel(c: Int): Int = (c + (255 - c) * t).toInt().coerceIn(0, 255)
        return Color.rgb(
            channel(Color.red(value)),
            channel(Color.green(value)),
            channel(Color.blue(value))
        )
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
