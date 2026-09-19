package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class StreakFireView(
    context: Context,
    private var flameColor: Int
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }

    init {
        animator.start()
    }

    fun setFlameColor(color: Int) {
        flameColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val t = (phase * Math.PI * 2).toFloat()
        val sway = sin(t) * w * 0.025f
        val breathe = 1f + sin(t * 1.7f) * 0.025f

        canvas.save()
        canvas.scale(breathe, breathe, w / 2f, h * 0.72f)
        canvas.translate(sway, 0f)

        drawFlame(
            canvas,
            alpha = 50,
            scale = 1.06f,
            yOffset = h * 0.005f,
            wobble = sin(t * 0.7f) * w * 0.018f
        )
        drawFlame(
            canvas,
            alpha = 255,
            scale = 0.92f,
            yOffset = h * 0.055f,
            wobble = -sin(t * 1.1f) * w * 0.02f
        )

        paint.color = Color.argb(
            100,
            Color.red(flameColor),
            Color.green(flameColor),
            Color.blue(flameColor)
        )
        canvas.drawCircle(w * 0.5f, h * 0.73f, w * 0.12f, paint)

        canvas.restore()
    }

    private fun drawFlame(
        canvas: Canvas,
        alpha: Int,
        scale: Float,
        yOffset: Float,
        wobble: Float
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f + wobble
        val bottom = h * 0.86f + yOffset
        val top = h * 0.11f + yOffset
        val left = cx - w * 0.29f * scale
        val right = cx + w * 0.29f * scale

        path.reset()
        path.moveTo(cx, bottom)
        path.cubicTo(
            left - w * 0.03f, h * 0.70f,
            left + w * 0.02f, h * 0.49f,
            cx - w * 0.11f, h * 0.37f
        )
        path.cubicTo(
            cx - w * 0.17f, h * 0.28f,
            cx - w * 0.04f, h * 0.20f,
            cx + w * 0.01f, top
        )
        path.cubicTo(
            cx + w * 0.12f, h * 0.24f,
            right + w * 0.01f, h * 0.34f,
            right - w * 0.03f, h * 0.49f
        )
        path.cubicTo(
            right + w * 0.06f, h * 0.66f,
            right - w * 0.04f, h * 0.82f,
            cx, bottom
        )
        path.close()

        paint.color = Color.argb(
            alpha,
            Color.red(flameColor),
            Color.green(flameColor),
            Color.blue(flameColor)
        )
        canvas.drawPath(path, paint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
