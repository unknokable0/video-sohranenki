package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.view.View
import android.view.animation.PathInterpolator
import kotlin.math.sin

class LoadingWaveView(
    context: Context,
    private val color: Int
) : View(context) {

    private val cloudPath = Path()
    private val sCutPath = Path()
    private var phase = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1600L
        repeatCount = ValueAnimator.INFINITE
        interpolator = PathInterpolator(0.42f, 0f, 0.58f, 1f)
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val width = w.toFloat()
        val height = h.toFloat()
        val cx = width * 0.50f
        val top = height * 0.17f
        val shoulderY = height * 0.40f
        val sideY = height * 0.56f
        val bottom = height * 0.82f

        cloudPath.reset()
        cloudPath.moveTo(cx, top)
        cloudPath.cubicTo(
            width * 0.36f, height * 0.16f,
            width * 0.32f, height * 0.29f,
            width * 0.31f, shoulderY
        )
        cloudPath.cubicTo(
            width * 0.19f, height * 0.42f,
            width * 0.13f, height * 0.49f,
            width * 0.13f, sideY
        )
        cloudPath.cubicTo(
            width * 0.13f, height * 0.68f,
            width * 0.24f, height * 0.73f,
            width * 0.33f, height * 0.74f
        )
        cloudPath.cubicTo(
            width * 0.39f, height * 0.75f,
            width * 0.42f, bottom,
            cx, bottom
        )
        cloudPath.cubicTo(
            width * 0.58f, bottom,
            width * 0.61f, height * 0.75f,
            width * 0.67f, height * 0.74f
        )
        cloudPath.cubicTo(
            width * 0.76f, height * 0.73f,
            width * 0.87f, height * 0.68f,
            width * 0.87f, sideY
        )
        cloudPath.cubicTo(
            width * 0.87f, height * 0.49f,
            width * 0.81f, height * 0.42f,
            width * 0.69f, shoulderY
        )
        cloudPath.cubicTo(
            width * 0.68f, height * 0.29f,
            width * 0.64f, height * 0.16f,
            cx, top
        )
        cloudPath.close()

        sCutPath.reset()
        sCutPath.moveTo(width * 0.65f, height * 0.34f)
        sCutPath.cubicTo(
            width * 0.57f, height * 0.25f,
            width * 0.38f, height * 0.27f,
            width * 0.36f, height * 0.39f
        )
        sCutPath.cubicTo(
            width * 0.34f, height * 0.49f,
            width * 0.64f, height * 0.49f,
            width * 0.65f, height * 0.61f
        )
        sCutPath.cubicTo(
            width * 0.66f, height * 0.73f,
            width * 0.44f, height * 0.76f,
            width * 0.35f, height * 0.67f
        )

        cutPaint.strokeWidth = minOf(width, height) * 0.105f
        fillPaint.shader = LinearGradient(
            width * 0.18f,
            height * 0.18f,
            width * 0.82f,
            height * 0.82f,
            intArrayOf(lighten(color, 0.26f), color, darken(color, 0.18f)),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val wave = sin((phase * Math.PI * 2).toFloat())
        val scale = 0.985f + 0.015f * ((wave + 1f) * 0.5f)
        val shiftY = dp(1.2f) * wave
        val cx = width / 2f
        val cy = height / 2f

        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.save()
        canvas.translate(0f, shiftY)
        canvas.scale(scale, scale, cx, cy)
        fillPaint.alpha = 245
        canvas.drawPath(cloudPath, fillPaint)
        canvas.drawPath(sCutPath, cutPaint)
        canvas.restore()
        canvas.restoreToCount(layer)
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

    private fun lighten(value: Int, amount: Float): Int =
        blend(value, 0xFFFFFFFF.toInt(), amount)

    private fun darken(value: Int, amount: Float): Int =
        blend(value, 0xFF17112B.toInt(), amount)

    private fun blend(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * t).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or
            (channel(16) shl 16) or
            (channel(8) shl 8) or
            channel(0)
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}
