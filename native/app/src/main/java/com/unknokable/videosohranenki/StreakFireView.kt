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
import kotlin.math.cos
import kotlin.math.sin

class StreakFireView(context: Context, private var flameColor: Int) : View(context) {
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outer = Path()
    private val inner = Path()
    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2400L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedFraction; invalidate() }
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

        val t = phase * Math.PI.toFloat() * 2f
        val sway = sin(t) * w * .018f
        val tip = sin(t * 1.27f + .4f) * w * .034f
        val cx = w * .5f
        val bottom = h * .89f

        outer.reset()
        outer.moveTo(cx, bottom)
        outer.cubicTo(w * .23f, h * .83f, w * .16f, h * .62f, w * .31f, h * .46f)
        outer.cubicTo(w * .41f, h * .35f, w * .37f, h * .23f, cx + tip, h * .07f)
        outer.cubicTo(w * .58f + tip * .25f, h * .27f, w * .75f, h * .32f, w * .70f, h * .50f)
        outer.cubicTo(w * .84f, h * .63f, w * .76f, h * .83f, cx, bottom)
        outer.close()

        glowPaint.style = Paint.Style.FILL
        glowPaint.color = withAlpha(flameColor, 115)
        glowPaint.maskFilter = BlurMaskFilter(dp(9f), BlurMaskFilter.Blur.NORMAL)

        canvas.save()
        canvas.translate(sway, 0f)
        canvas.drawPath(outer, glowPaint)

        outerPaint.shader = LinearGradient(
            0f, h * .12f, 0f, h * .9f,
            blend(flameColor, Color.WHITE, .35f), flameColor, Shader.TileMode.CLAMP
        )
        outerPaint.maskFilter = null
        canvas.drawPath(outer, outerPaint)

        inner.reset()
        inner.moveTo(cx, h * .79f)
        inner.cubicTo(w * .40f, h * .72f, w * .41f, h * .58f, w * .48f, h * .49f)
        inner.cubicTo(w * .53f, h * .42f, w * .51f, h * .36f, w * .56f, h * .28f)
        inner.cubicTo(w * .65f, h * .49f, w * .68f, h * .64f, w * .62f, h * .73f)
        inner.cubicTo(w * .59f, h * .77f, w * .55f, h * .79f, cx, h * .79f)
        inner.close()
        innerPaint.color = withAlpha(blend(flameColor, Color.WHITE, .72f), 215)
        innerPaint.shader = null
        canvas.drawPath(inner, innerPaint)
        canvas.restore()

        sparkPaint.color = blend(flameColor, Color.WHITE, .45f)
        repeat(3) { i ->
            val local = (phase + i * .29f) % 1f
            val x = cx + cos((i + 1) * 1.7f) * w * .13f + sin(t + i) * w * .02f
            val y = h * .42f - local * h * .26f
            val alpha = (1f - local).coerceIn(0f, 1f)
            sparkPaint.alpha = (170 * alpha).toInt()
            canvas.drawCircle(x, y, dp(1.8f + 1.2f * alpha), sparkPaint)
        }
        sparkPaint.alpha = 255
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

    private fun withAlpha(v: Int, a: Int): Int =
        (v and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    private fun blend(from: Int, to: Int, amount: Float): Int {
        val a = amount.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * a).toInt(),
            (Color.red(from) + (Color.red(to) - Color.red(from)) * a).toInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * a).toInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * a).toInt()
        )
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
