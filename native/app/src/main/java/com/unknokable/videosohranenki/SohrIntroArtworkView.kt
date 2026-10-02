package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

class SohrIntroArtworkView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    private var palette: ThemePalette? = null
    private var pageProgress = 0f
    private var ambient = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 4_800L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            ambient = it.animatedValue as Float
            invalidate()
        }
        start()
    }

    fun setPalette(value: ThemePalette) {
        palette = value
        invalidate()
    }

    fun setPageProgress(value: Float) {
        pageProgress = value.coerceIn(0f, 4f)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = palette ?: return

        val base = floor(pageProgress).toInt().coerceIn(0, 4)
        val next = (base + 1).coerceAtMost(4)
        val t = (pageProgress - base).coerceIn(0f, 1f)

        val cx = width / 2f
        val cy = height / 2f

        if (base == next) {
            drawScene(canvas, base, cx, cy, 1f, 0f, 1f, p)
        } else {
            drawScene(
                canvas,
                base,
                cx,
                cy,
                1f - smooth(t),
                -dp(18f) * smooth(t),
                1f - 0.06f * smooth(t),
                p
            )
            drawScene(
                canvas,
                next,
                cx,
                cy,
                smooth(t),
                dp(18f) * (1f - smooth(t)),
                0.92f + 0.08f * smooth(t),
                p
            )
        }
    }

    private fun drawScene(
        canvas: Canvas,
        page: Int,
        cx: Float,
        cy: Float,
        alpha: Float,
        translateX: Float,
        scale: Float,
        palette: ThemePalette
    ) {
        if (alpha <= 0.001f) return
        paint.alpha = 255
        stroke.alpha = 255

        canvas.save()
        canvas.translate(cx + translateX, cy)
        canvas.scale(scale, scale)
        canvas.translate(-cx, -cy)

        when (page) {
            0 -> drawCloud(canvas, cx, cy, alpha, palette)
            1 -> drawFeed(canvas, cx, cy, alpha, palette)
            2 -> drawWatch(canvas, cx, cy, alpha, palette)
            3 -> drawStreak(canvas, cx, cy, alpha, palette)
            else -> drawCustomize(canvas, cx, cy, alpha, palette)
        }

        canvas.restore()
    }

    private fun drawCloud(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        alpha: Float,
        p: ThemePalette
    ) {
        val accent2 = ColorUtils.blendARGB(p.accent, Color.WHITE, 0.22f)
        val accent3 = ColorUtils.blendARGB(p.accent, Color.CYAN, 0.18f)

        val w = dp(224f)
        val h = dp(132f)
        val left = cx - w / 2f
        val top = cy - h / 2f + dp(4f)

        path.reset()
        path.moveTo(left + dp(47f), top + h)
        path.cubicTo(left + dp(18f), top + h, left + dp(5f), top + dp(102f), left + dp(19f), top + dp(78f))
        path.cubicTo(left + dp(28f), top + dp(62f), left + dp(44f), top + dp(55f), left + dp(64f), top + dp(56f))
        path.cubicTo(left + dp(76f), top + dp(21f), left + dp(108f), top + dp(6f), left + dp(140f), top + dp(23f))
        path.cubicTo(left + dp(157f), top + dp(32f), left + dp(168f), top + dp(45f), left + dp(171f), top + dp(61f))
        path.cubicTo(left + dp(206f), top + dp(60f), left + dp(224f), top + dp(77f), left + dp(224f), top + dp(96f))
        path.cubicTo(left + dp(224f), top + dp(117f), left + dp(207f), top + h, left + dp(183f), top + h)
        path.close()

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            left, top, left + w, top + h,
            intArrayOf(accent3, p.accent, accent2),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.alpha = (255 * alpha).toInt()
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.alpha = 255

        stroke.color = Color.WHITE
        stroke.strokeWidth = dp(5.5f)
        stroke.alpha = (238 * alpha).toInt()
        path.reset()
        path.moveTo(cx - dp(58f), cy + dp(10f))
        path.cubicTo(cx - dp(34f), cy - dp(22f), cx - dp(1f), cy - dp(25f), cx + dp(19f), cy - dp(6f))
        path.cubicTo(cx + dp(35f), cy + dp(9f), cx + dp(30f), cy + dp(27f), cx + dp(10f), cy + dp(34f))
        path.cubicTo(cx - dp(10f), cy + dp(41f), cx - dp(37f), cy + dp(31f), cx - dp(49f), cy + dp(17f))
        canvas.drawPath(path, stroke)

        val pulse = 0.5f + 0.5f * sin((ambient * PI * 2).toFloat())
        paint.color = ColorUtils.setAlphaComponent(Color.WHITE, ((50 + 35 * pulse) * alpha).toInt().coerceIn(0, 255))
        canvas.drawCircle(cx + dp(73f), cy - dp(43f), dp(5.5f + 1.5f * pulse), paint)
    }

    private fun drawFeed(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        alpha: Float,
        p: ThemePalette
    ) {
        val float = sin((ambient * PI * 2).toFloat()) * dp(3f)

        drawFeedCard(canvas, cx - dp(66f), cy - dp(57f) + float, dp(132f), dp(88f), -6f, alpha * 0.68f, p, true)
        drawFeedCard(canvas, cx + dp(54f), cy - dp(38f) - float * 0.6f, dp(146f), dp(96f), 7f, alpha * 0.72f, p, true)
        drawFeedCard(canvas, cx, cy + dp(25f), dp(190f), dp(118f), 0f, alpha, p, false)
    }

    private fun drawFeedCard(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        rotation: Float,
        alpha: Float,
        p: ThemePalette,
        compact: Boolean
    ) {
        canvas.save()
        canvas.rotate(rotation, cx, cy)

        val rect = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        paint.style = Paint.Style.FILL
        paint.color = ColorUtils.setAlphaComponent(p.surfaceAlt, (255 * alpha).toInt().coerceIn(0, 255))
        canvas.drawRoundRect(rect, dp(18f), dp(18f), paint)

        val thumb = RectF(
            rect.left + dp(10f),
            rect.top + dp(10f),
            rect.right - dp(10f),
            rect.top + h * if (compact) 0.60f else 0.62f
        )
        paint.shader = LinearGradient(
            thumb.left, thumb.top, thumb.right, thumb.bottom,
            ColorUtils.blendARGB(p.accent, Color.CYAN, 0.22f),
            p.accent,
            Shader.TileMode.CLAMP
        )
        paint.alpha = (235 * alpha).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(thumb, dp(12f), dp(12f), paint)
        paint.shader = null
        paint.alpha = 255

        paint.color = ColorUtils.setAlphaComponent(p.text, (205 * alpha).toInt().coerceIn(0, 255))
        canvas.drawRoundRect(
            RectF(rect.left + dp(12f), thumb.bottom + dp(9f), rect.right - dp(if (compact) 32f else 48f), thumb.bottom + dp(14f)),
            dp(3f), dp(3f), paint
        )

        paint.color = ColorUtils.setAlphaComponent(p.muted, (125 * alpha).toInt().coerceIn(0, 255))
        canvas.drawRoundRect(
            RectF(rect.left + dp(12f), thumb.bottom + dp(20f), rect.right - dp(if (compact) 50f else 72f), thumb.bottom + dp(24f)),
            dp(2f), dp(2f), paint
        )

        canvas.restore()
    }

    private fun drawWatch(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        alpha: Float,
        p: ThemePalette
    ) {
        val rect = RectF(cx - dp(112f), cy - dp(72f), cx + dp(112f), cy + dp(64f))

        paint.style = Paint.Style.FILL
        paint.color = ColorUtils.setAlphaComponent(p.surfaceAlt, (255 * alpha).toInt().coerceIn(0, 255))
        canvas.drawRoundRect(rect, dp(24f), dp(24f), paint)

        val screen = RectF(rect.left + dp(10f), rect.top + dp(10f), rect.right - dp(10f), rect.bottom - dp(30f))
        paint.shader = LinearGradient(
            screen.left, screen.top, screen.right, screen.bottom,
            ColorUtils.blendARGB(p.accent, Color.BLUE, 0.18f),
            ColorUtils.blendARGB(p.accent, Color.CYAN, 0.15f),
            Shader.TileMode.CLAMP
        )
        paint.alpha = (240 * alpha).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(screen, dp(17f), dp(17f), paint)
        paint.shader = null
        paint.alpha = 255

        path.reset()
        path.moveTo(cx - dp(11f), cy - dp(26f))
        path.lineTo(cx + dp(24f), cy - dp(6f))
        path.lineTo(cx - dp(11f), cy + dp(14f))
        path.close()
        paint.color = ColorUtils.setAlphaComponent(Color.WHITE, (245 * alpha).toInt().coerceIn(0, 255))
        canvas.drawPath(path, paint)

        val lineY = rect.bottom - dp(17f)
        paint.color = ColorUtils.setAlphaComponent(p.muted, (85 * alpha).toInt().coerceIn(0, 255))
        canvas.drawRoundRect(RectF(rect.left + dp(16f), lineY, rect.right - dp(16f), lineY + dp(4f)), dp(2f), dp(2f), paint)

        val progress = 0.42f + 0.08f * sin((ambient * PI * 2).toFloat())
        paint.color = ColorUtils.setAlphaComponent(p.accent, (240 * alpha).toInt().coerceIn(0, 255))
        canvas.drawRoundRect(
            RectF(rect.left + dp(16f), lineY, rect.left + dp(16f) + (rect.width() - dp(32f)) * progress, lineY + dp(4f)),
            dp(2f), dp(2f), paint
        )
        canvas.drawCircle(rect.left + dp(16f) + (rect.width() - dp(32f)) * progress, lineY + dp(2f), dp(5f), paint)

        stroke.color = ColorUtils.setAlphaComponent(Color.WHITE, (220 * alpha).toInt().coerceIn(0, 255))
        stroke.strokeWidth = dp(3f)
        path.reset()
        path.moveTo(cx + dp(75f), cy - dp(62f))
        path.lineTo(cx + dp(96f), cy - dp(62f))
        path.lineTo(cx + dp(96f), cy - dp(41f))
        canvas.drawPath(path, stroke)
        path.reset()
        path.moveTo(cx + dp(96f), cy - dp(62f))
        path.lineTo(cx + dp(72f), cy - dp(38f))
        canvas.drawPath(path, stroke)
    }

    private fun drawStreak(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        alpha: Float,
        p: ThemePalette
    ) {
        val y = cy + dp(22f)
        val spacing = dp(34f)
        val startX = cx - spacing * 2f

        for (i in 0..4) {
            val phase = ((ambient + i * 0.12f) % 1f)
            val lift = if (i == 2) dp(5f) * sin((phase * PI * 2).toFloat()) else 0f
            paint.color = ColorUtils.setAlphaComponent(
                if (i <= 2) p.accent else p.surfaceAlt,
                (255 * alpha).toInt().coerceIn(0, 255)
            )
            canvas.drawCircle(startX + spacing * i, y - lift, dp(if (i == 2) 10f else 8f), paint)
        }

        path.reset()
        path.moveTo(cx, cy - dp(82f))
        path.cubicTo(cx - dp(44f), cy - dp(64f), cx - dp(67f), cy - dp(32f), cx - dp(60f), cy + dp(3f))
        path.cubicTo(cx - dp(52f), cy + dp(42f), cx - dp(21f), cy + dp(63f), cx, cy + dp(74f))
        path.cubicTo(cx + dp(21f), cy + dp(63f), cx + dp(52f), cy + dp(42f), cx + dp(60f), cy + dp(3f))
        path.cubicTo(cx + dp(67f), cy - dp(32f), cx + dp(44f), cy - dp(64f), cx, cy - dp(82f))
        path.close()

        paint.shader = LinearGradient(
            cx, cy - dp(82f), cx, cy + dp(74f),
            ColorUtils.blendARGB(p.accent, Color.WHITE, 0.10f),
            p.accent,
            Shader.TileMode.CLAMP
        )
        paint.alpha = (235 * alpha).toInt().coerceIn(0, 255)
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.alpha = 255

        path.reset()
        path.moveTo(cx + dp(2f), cy - dp(34f))
        path.cubicTo(cx - dp(25f), cy - dp(8f), cx - dp(21f), cy + dp(22f), cx + dp(2f), cy + dp(30f))
        path.cubicTo(cx + dp(26f), cy + dp(21f), cx + dp(27f), cy - dp(8f), cx + dp(2f), cy - dp(34f))
        path.close()
        paint.color = ColorUtils.setAlphaComponent(Color.WHITE, (245 * alpha).toInt().coerceIn(0, 255))
        canvas.drawPath(path, paint)
    }

    private fun drawCustomize(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        alpha: Float,
        p: ThemePalette
    ) {
        val ys = listOf(cy - dp(55f), cy, cy + dp(55f))
        val knobs = listOf(0.31f, 0.70f, 0.46f)

        for (i in ys.indices) {
            val y = ys[i]
            val left = cx - dp(102f)
            val right = cx + dp(102f)

            stroke.strokeWidth = dp(6f)
            stroke.color = ColorUtils.setAlphaComponent(p.surfaceAlt, (255 * alpha).toInt().coerceIn(0, 255))
            canvas.drawLine(left, y, right, y, stroke)

            val wobble = sin(((ambient + i * 0.16f) * PI * 2).toFloat()) * dp(3f)
            val x = left + (right - left) * knobs[i] + wobble

            stroke.color = ColorUtils.setAlphaComponent(p.accent, (245 * alpha).toInt().coerceIn(0, 255))
            canvas.drawLine(left, y, x, y, stroke)

            paint.color = ColorUtils.setAlphaComponent(p.accent, (255 * alpha).toInt().coerceIn(0, 255))
            canvas.drawCircle(x, y, dp(13f), paint)

            paint.color = ColorUtils.setAlphaComponent(Color.WHITE, (245 * alpha).toInt().coerceIn(0, 255))
            canvas.drawCircle(x, y, dp(5f), paint)
        }

        val swatchY = cy + dp(98f)
        val colors = listOf(
            ColorUtils.blendARGB(p.accent, Color.CYAN, 0.25f),
            p.accent,
            ColorUtils.blendARGB(p.accent, Color.MAGENTA, 0.20f)
        )
        colors.forEachIndexed { i, color ->
            paint.color = ColorUtils.setAlphaComponent(color, (245 * alpha).toInt().coerceIn(0, 255))
            canvas.drawCircle(cx + dp((i - 1) * 34f), swatchY, dp(10f), paint)
        }
    }

    private fun smooth(value: Float): Float =
        value * value * (3f - 2f * value)

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}
