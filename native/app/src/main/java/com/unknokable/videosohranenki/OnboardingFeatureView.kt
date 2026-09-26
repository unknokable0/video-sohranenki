package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class OnboardingFeatureView(context: Context, private val accent: Int) : View(context) {
    enum class Kind { COLLECTIONS, RESUME, STREAK, UPDATE }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var phase = 0f
    private var kind = Kind.COLLECTIONS
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2200L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedFraction; invalidate() }
    }

    init { animator.start() }

    fun setKind(value: Kind) {
        kind = value
        phase = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.shader = RadialGradient(
            width / 2f, height / 2f, minOf(width, height) * .48f,
            intArrayOf(withAlpha(accent, 72), 0x00000000),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(width / 2f, height / 2f, minOf(width, height) * .48f, paint)
        paint.shader = null
        when (kind) {
            Kind.COLLECTIONS -> drawCollections(canvas)
            Kind.RESUME -> drawResume(canvas)
            Kind.STREAK -> drawStreak(canvas)
            Kind.UPDATE -> drawUpdate(canvas)
        }
    }

    private fun drawCollections(c: Canvas) {
        val bounce = sin(phase * Math.PI.toFloat() * 2f) * dp(2f)
        repeat(3) { i ->
            paint.style = Paint.Style.FILL
            paint.color = withAlpha(accent, 70 + i * 42)
            val l = width * .20f + i * dp(8f)
            val t = height * .23f + i * dp(14f) + bounce * (if (i % 2 == 0) 1 else -1)
            c.drawRoundRect(l, t, width * .76f + i * dp(2f), t + height * .21f, dp(11f), dp(11f), paint)
        }
    }

    private fun drawResume(c: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        path.reset()
        path.moveTo(width * .41f, height * .27f)
        path.lineTo(width * .70f, height * .50f)
        path.lineTo(width * .41f, height * .73f)
        path.close()
        c.drawPath(path, paint)

        val left = width * .18f
        val right = width * .82f
        val y = height * .80f
        paint.color = withAlpha(Color.WHITE, 65)
        c.drawRoundRect(left, y, right, y + dp(4f), dp(2f), dp(2f), paint)
        val progress = .15f + .70f * ((phase * 1.1f) % 1f)
        paint.color = Color.rgb(255, 0, 51)
        c.drawRoundRect(left, y, left + (right - left) * progress, y + dp(4f), dp(2f), dp(2f), paint)
        c.drawCircle(left + (right - left) * progress, y + dp(2f), dp(4.5f), paint)
    }

    private fun drawStreak(c: Canvas) {
        val t = phase * Math.PI.toFloat() * 2f
        val sway = sin(t) * width * .025f
        paint.style = Paint.Style.FILL
        paint.color = accent
        path.reset()
        path.moveTo(width * .5f, height * .84f)
        path.cubicTo(width * .24f, height * .77f, width * .21f, height * .56f, width * .36f, height * .42f)
        path.cubicTo(width * .47f, height * .32f, width * .42f, height * .18f, width * .55f + sway, height * .07f)
        path.cubicTo(width * .61f, height * .29f, width * .78f, height * .38f, width * .72f, height * .57f)
        path.cubicTo(width * .83f, height * .70f, width * .72f, height * .84f, width * .5f, height * .84f)
        path.close()
        c.drawPath(path, paint)
        paint.color = withAlpha(Color.WHITE, 190)
        c.drawCircle(width * .53f + sway * .25f, height * .62f, dp(11f + 2f * sin(t * 1.3f)), paint)
    }

    private fun drawUpdate(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) * .27f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(6f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = withAlpha(accent, 65)
        c.drawCircle(cx, cy, r, paint)
        paint.color = accent
        c.drawArc(cx - r, cy - r, cx + r, cy + r, -90f, 75f + 245f * phase, false, paint)

        paint.style = Paint.Style.FILL
        path.reset()
        path.moveTo(cx, cy - dp(23f))
        path.lineTo(cx - dp(11f), cy - dp(7f))
        path.lineTo(cx - dp(4f), cy - dp(7f))
        path.lineTo(cx - dp(4f), cy + dp(13f))
        path.lineTo(cx + dp(4f), cy + dp(13f))
        path.lineTo(cx + dp(4f), cy - dp(7f))
        path.lineTo(cx + dp(11f), cy - dp(7f))
        path.close()
        c.drawPath(path, paint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isRunning) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private fun withAlpha(v: Int, a: Int): Int =
        (v and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
