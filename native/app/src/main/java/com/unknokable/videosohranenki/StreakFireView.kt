package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class StreakFireView(context: Context, private var flameColor: Int) : View(context) {
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outer = Path()
    private val inner = Path()
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
        setLayerType(LAYER_TYPE_SOFTWARE, null)
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

        val wave = sin(phase * Math.PI.toFloat() * 2f)
        val cx = w * .5f
        val sway = wave * w * .010f
        val tipShift = wave * w * .016f
        val bottom = h * .88f

        outer.reset()
        outer.moveTo(cx, bottom)
        outer.cubicTo(w * .31f, h * .86f, w * .22f, h * .72f, w * .28f, h * .57f)
        outer.cubicTo(w * .33f, h * .45f, w * .45f, h * .39f, w * .43f, h * .25f)
        outer.cubicTo(w * .42f, h * .18f, w * .47f, h * .11f, cx + tipShift, h * .07f)
        outer.cubicTo(w * .55f, h * .20f, w * .70f, h * .31f, w * .68f, h * .49f)
        outer.cubicTo(w * .80f, h * .61f, w * .76f, h * .83f, cx, bottom)
        outer.close()

        glowPaint.style = Paint.Style.FILL
        glowPaint.color = withAlpha(flameColor, 72)
        glowPaint.maskFilter = BlurMaskFilter(dp(7f), BlurMaskFilter.Blur.NORMAL)

        canvas.save()
        canvas.translate(sway, 0f)
        canvas.drawPath(outer, glowPaint)

        outerPaint.maskFilter = null
        outerPaint.shader = LinearGradient(
            0f,
            h * .10f,
            0f,
            bottom,
            intArrayOf(
                blend(flameColor, Color.WHITE, .40f),
                flameColor,
                blend(flameColor, Color.BLACK, .08f)
            ),
            floatArrayOf(0f, .55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(outer, outerPaint)

        inner.reset()
        inner.moveTo(cx, h * .77f)
        inner.cubicTo(w * .43f, h * .72f, w * .42f, h * .62f, w * .47f, h * .53f)
        inner.cubicTo(w * .51f, h * .46f, w * .53f, h * .39f, w * .54f, h * .33f)
        inner.cubicTo(w * .63f, h * .45f, w * .66f, h * .59f, w * .61f, h * .69f)
        inner.cubicTo(w * .58f, h * .75f, w * .54f, h * .77f, cx, h * .77f)
        inner.close()

        innerPaint.shader = LinearGradient(
            0f,
            h * .34f,
            0f,
            h * .78f,
            blend(flameColor, Color.WHITE, .82f),
            blend(flameColor, Color.WHITE, .52f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(inner, innerPaint)
        canvas.restore()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isRunning) animator.start()
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
            streak < 200 -> Color.parseColor("#B7FF28")
            else -> Color.parseColor("#55E6FF")
        }
    }

    private fun withAlpha(value: Int, alpha: Int): Int =
        (value and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun blend(from: Int, to: Int, amount: Float): Int {
        val a = amount.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * a).toInt(),
            (Color.red(from) + (Color.red(to) - Color.red(from)) * a).toInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * a).toInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * a).toInt()
        )
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
