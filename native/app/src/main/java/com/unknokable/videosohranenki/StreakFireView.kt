package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class StreakFireView(
    context: Context,
    private var flameColor: Int
) : View(context) {

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outerPath = Path()
    private val innerPath = Path()
    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2600L
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
        val sway = sin(t) * w * 0.022f
        val tipSway = sin(t * 1.35f + 0.7f) * w * 0.035f
        val breatheX = 1f + sin(t * 1.7f) * 0.018f
        val breatheY = 1f + sin(t * 1.25f + 0.4f) * 0.025f

        val topColor = blend(flameColor, Color.WHITE, 0.34f)
        outerPaint.shader = LinearGradient(
            0f,
            h * 0.10f,
            0f,
            h * 0.90f,
            topColor,
            flameColor,
            Shader.TileMode.CLAMP
        )

        canvas.save()
        canvas.translate(sway, 0f)
        canvas.scale(breatheX, breatheY, w * 0.5f, h * 0.82f)

        val cx = w * 0.5f
        val bottom = h * 0.90f

        outerPath.reset()
        outerPath.moveTo(cx, bottom)
        outerPath.cubicTo(
            w * 0.22f, h * 0.82f,
            w * 0.16f, h * 0.60f,
            w * 0.31f, h * 0.44f
        )
        outerPath.cubicTo(
            w * 0.39f, h * 0.35f,
            w * 0.35f, h * 0.23f,
            cx + tipSway, h * 0.08f
        )
        outerPath.cubicTo(
            w * 0.56f + tipSway * 0.45f, h * 0.24f,
            w * 0.73f, h * 0.30f,
            w * 0.70f, h * 0.49f
        )
        outerPath.cubicTo(
            w * 0.86f, h * 0.62f,
            w * 0.77f, h * 0.84f,
            cx, bottom
        )
        outerPath.close()
        canvas.drawPath(outerPath, outerPaint)

        innerPaint.shader = null
        innerPaint.color = blend(flameColor, Color.WHITE, 0.62f)
        innerPaint.alpha = 190

        val innerShift = sin(t * 1.55f + 1.2f) * w * 0.018f
        innerPath.reset()
        innerPath.moveTo(cx + innerShift, h * 0.80f)
        innerPath.cubicTo(
            w * 0.39f, h * 0.73f,
            w * 0.40f, h * 0.60f,
            w * 0.47f, h * 0.51f
        )
        innerPath.cubicTo(
            w * 0.52f, h * 0.45f,
            w * 0.50f, h * 0.38f,
            w * 0.55f + innerShift, h * 0.31f
        )
        innerPath.cubicTo(
            w * 0.63f, h * 0.49f,
            w * 0.69f, h * 0.63f,
            w * 0.63f, h * 0.72f
        )
        innerPath.cubicTo(
            w * 0.60f, h * 0.77f,
            w * 0.55f, h * 0.80f,
            cx + innerShift, h * 0.80f
        )
        innerPath.close()
        canvas.drawPath(innerPath, innerPaint)

        canvas.restore()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        fun colorForStreak(streak: Int): Int = when {
            streak <= 0 -> Color.parseColor("#777782")
            streak < 10 -> Color.parseColor("#E7E7EC")
            streak < 20 -> Color.parseColor("#9A68FF")
            streak < 50 -> Color.parseColor("#4D98FF")
            streak < 100 -> Color.parseColor("#FF4A5E")
            else -> Color.parseColor("#B7FF28")
        }

        private fun blend(from: Int, to: Int, amount: Float): Int {
            val a = amount.coerceIn(0f, 1f)
            return Color.rgb(
                (Color.red(from) + (Color.red(to) - Color.red(from)) * a).toInt(),
                (Color.green(from) + (Color.green(to) - Color.green(from)) * a).toInt(),
                (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * a).toInt()
            )
        }
    }
}
