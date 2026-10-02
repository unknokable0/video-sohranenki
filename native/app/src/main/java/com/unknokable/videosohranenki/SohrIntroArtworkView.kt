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
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
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
        duration = 5_200L
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
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = palette ?: return

        val base = floor(pageProgress).toInt().coerceIn(0, 4)
        val next = (base + 1).coerceAtMost(4)
        val t = smooth((pageProgress - base).coerceIn(0f, 1f))

        val cx = width / 2f
        val cy = height / 2f

        if (base == next) {
            drawScene(canvas, base, cx, cy, 1f, 0f, 1f, p)
            return
        }

        drawScene(
            canvas,
            base,
            cx,
            cy,
            1f - t,
            -dp(20f) * t,
            1f - 0.035f * t,
            p
        )
        drawScene(
            canvas,
            next,
            cx,
            cy,
            t,
            dp(20f) * (1f - t),
            0.965f + 0.035f * t,
            p
        )
    }

    private fun drawScene(
        canvas: Canvas,
        page: Int,
        cx: Float,
        cy: Float,
        alpha: Float,
        translateX: Float,
        scale: Float,
        p: ThemePalette
    ) {
        if (alpha <= 0.002f) return

        canvas.save()
        canvas.translate(translateX, 0f)
        canvas.scale(scale, scale, cx, cy)

        when (page) {
            0 -> drawCloud(canvas, cx, cy, alpha, p)
            1 -> drawFeed(canvas, cx, cy, alpha, p)
            2 -> drawPlayer(canvas, cx, cy, alpha, p)
            3 -> drawStreak(canvas, cx, cy, alpha, p)
            else -> drawCustomize(canvas, cx, cy, alpha, p)
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
        val y = cy + dp(8f)
        val gradient = LinearGradient(
            cx - dp(115f),
            y - dp(62f),
            cx + dp(115f),
            y + dp(58f),
            ColorUtils.blendARGB(p.accent, Color.rgb(94, 167, 255), 0.34f),
            ColorUtils.blendARGB(p.accent, Color.rgb(182, 91, 255), 0.16f),
            Shader.TileMode.CLAMP
        )

        fill.shader = gradient
        fill.alpha = (255 * alpha).toInt().coerceIn(0, 255)

        canvas.drawCircle(cx - dp(62f), y + dp(5f), dp(49f), fill)
        canvas.drawCircle(cx + dp(58f), y + dp(7f), dp(47f), fill)
        canvas.drawCircle(cx, y - dp(35f), dp(66f), fill)
        canvas.drawRoundRect(
            RectF(
                cx - dp(105f),
                y - dp(12f),
                cx + dp(105f),
                y + dp(57f)
            ),
            dp(34f),
            dp(34f),
            fill
        )
        fill.shader = null
        fill.alpha = 255

        val pulse =
            0.5f + 0.5f * sin((ambient * PI * 2).toFloat())

        path.reset()
        path.moveTo(cx - dp(13f), y - dp(34f))
        path.lineTo(cx + dp(29f), y - dp(9f))
        path.lineTo(cx - dp(13f), y + dp(16f))
        path.close()
        fill.color = ColorUtils.setAlphaComponent(
            Color.WHITE,
            (245 * alpha).toInt().coerceIn(0, 255)
        )
        canvas.drawPath(path, fill)

        stroke.strokeWidth = dp(3.6f)
        stroke.color = ColorUtils.setAlphaComponent(
            Color.WHITE,
            (150 * alpha).toInt().coerceIn(0, 255)
        )
        canvas.drawArc(
            RectF(
                cx - dp(45f),
                y - dp(61f),
                cx + dp(45f),
                y + dp(28f)
            ),
            205f,
            110f + pulse * 16f,
            false,
            stroke
        )

        fill.color = ColorUtils.setAlphaComponent(
            p.text,
            (72 * alpha).toInt().coerceIn(0, 255)
        )
        canvas.drawCircle(
            cx + dp(106f),
            y - dp(65f),
            dp(7f + pulse),
            fill
        )
    }

    private fun drawFeed(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        alpha: Float,
        p: ThemePalette
    ) {
        val float =
            sin((ambient * PI * 2).toFloat()) * dp(2.5f)

        drawFeedCard(
            canvas,
            cx - dp(60f),
            cy - dp(45f) + float,
            dp(132f),
            dp(84f),
            -5f,
            alpha * 0.55f,
            p
        )
        drawFeedCard(
            canvas,
            cx + dp(58f),
            cy - dp(35f) - float,
            dp(136f),
            dp(88f),
            5f,
            alpha * 0.62f,
            p
        )
        drawFeedCard(
            canvas,
            cx,
            cy + dp(32f),
            dp(196f),
            dp(116f),
            0f,
            alpha,
            p
        )
    }

    private fun drawFeedCard(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        rotation: Float,
        alpha: Float,
        p: ThemePalette
    ) {
        canvas.save()
        canvas.rotate(rotation, cx, cy)

        val rect =
            RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)

        fill.color = ColorUtils.setAlphaComponent(
            p.surfaceAlt,
            (245 * alpha).toInt().coerceIn(0, 255)
        )
        canvas.drawRoundRect(rect, dp(18f), dp(18f), fill)

        val thumb = RectF(
            rect.left + dp(9f),
            rect.top + dp(9f),
            rect.right - dp(9f),
            rect.top + h * 0.61f
        )
        fill.shader = LinearGradient(
            thumb.left,
            thumb.top,
            thumb.right,
            thumb.bottom,
            ColorUtils.blendARGB(p.accent, Color.CYAN, 0.27f),
            ColorUtils.blendARGB(p.accent, Color.BLUE, 0.12f),
            Shader.TileMode.CLAMP
        )
        fill.alpha = (245 * alpha).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(thumb, dp(11f), dp(11f), fill)
        fill.shader = null
        fill.alpha = 255

        fill.color = ColorUtils.setAlphaComponent(
            p.text,
            (205 * alpha).toInt().coerceIn(0, 255)
        )
        canvas.drawRoundRect(
            RectF(
                rect.left + dp(12f),
                thumb.bottom + dp(9f),
                rect.right - dp(40f),
                thumb.bottom + dp(14f)
            ),
            dp(3f),
            dp(3f),
            fill
        )

        fill.color = ColorUtils.setAlphaComponent(
            p.muted,
            (118 * alpha).toInt().coerceIn(0, 255)
        )
        canvas.drawRoundRect(
            RectF(
                rect.left + dp(12f),
                thumb.bottom + dp(20f),
                rect.right - dp(68f),
                thumb.bottom + dp(24f)
            ),
            dp(2f),
            dp(2f),
            fill
        )

        canvas.restore()
    }

    private fun drawPlayer(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        alpha: Float,
        p: ThemePalette
    ) {
        val rect = RectF(
            cx - dp(112f),
            cy - dp(70f),
            cx + dp(112f),
            cy + dp(56f)
        )

        fill.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            ColorUtils.blendARGB(p.accent, Color.BLUE, 0.18f),
            ColorUtils.blendARGB(p.accent, Color.CYAN, 0.18f),
            Shader.TileMode.CLAMP
        )
        fill.alpha = (248 * alpha).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect, dp(24f), dp(24f), fill)
        fill.shader = null
        fill.alpha = 255

        fill.color = ColorUtils.setAlphaComponent(
            Color.WHITE,
            (242 * alpha).toInt().coerceIn(0, 255)
        )
        path.reset()
        path.moveTo(cx - dp(12f), cy - dp(30f))
        path.lineTo(cx + dp(26f), cy - dp(7f))
        path.lineTo(cx - dp(12f), cy + dp(16f))
        path.close()
        canvas.drawPath(path, fill)

        val lineY = rect.bottom + dp(19f)
        stroke.strokeWidth = dp(5f)
        stroke.color = ColorUtils.setAlphaComponent(
            p.surfaceAlt,
            (255 * alpha).toInt().coerceIn(0, 255)
        )
        canvas.drawLine(
            rect.left + dp(9f),
            lineY,
            rect.right - dp(9f),
            lineY,
            stroke
        )

        val progress =
            0.38f + 0.10f * (
                0.5f + 0.5f * sin((ambient * PI * 2).toFloat())
            )
        val progressX =
            rect.left + dp(9f) +
                (rect.width() - dp(18f)) * progress

        stroke.color = ColorUtils.setAlphaComponent(
            p.accent,
            (250 * alpha).toInt().coerceIn(0, 255)
        )
        canvas.drawLine(
            rect.left + dp(9f),
            lineY,
            progressX,
            lineY,
            stroke
        )
        fill.color = ColorUtils.setAlphaComponent(
            p.accent,
            (255 * alpha).toInt().coerceIn(0, 255)
        )
        canvas.drawCircle(progressX, lineY, dp(7f), fill)

        drawControlDot(
            canvas,
            cx - dp(72f),
            cy + dp(96f),
            alpha,
            p
        )
        drawControlDot(
            canvas,
            cx,
            cy + dp(96f),
            alpha,
            p
        )
        drawControlDot(
            canvas,
            cx + dp(72f),
            cy + dp(96f),
            alpha,
            p
        )
    }

    private fun drawControlDot(
        canvas: Canvas,
        x: Float,
        y: Float,
        alpha: Float,
        p: ThemePalette
    ) {
        fill.color = ColorUtils.setAlphaComponent(
            p.surfaceAlt,
            (255 * alpha).toInt().coerceIn(0, 255)
        )
        canvas.drawCircle(x, y, dp(15f), fill)
        fill.color = ColorUtils.setAlphaComponent(
            p.accent,
            (235 * alpha).toInt().coerceIn(0, 255)
        )
        canvas.drawCircle(x, y, dp(5f), fill)
    }

    private fun drawStreak(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        alpha: Float,
        p: ThemePalette
    ) {
        val ringRadius = dp(74f)
        stroke.strokeWidth = dp(14f)
        stroke.color = ColorUtils.setAlphaComponent(
            p.surfaceAlt,
            (255 * alpha).toInt().coerceIn(0, 255)
        )
        canvas.drawCircle(cx, cy - dp(18f), ringRadius, stroke)

        val sweep =
            265f + 24f * (
                0.5f + 0.5f * sin((ambient * PI * 2).toFloat())
            )
        stroke.color = ColorUtils.setAlphaComponent(
            p.accent,
            (248 * alpha).toInt().coerceIn(0, 255)
        )
        canvas.drawArc(
            RectF(
                cx - ringRadius,
                cy - dp(18f) - ringRadius,
                cx + ringRadius,
                cy - dp(18f) + ringRadius
            ),
            -90f,
            sweep,
            false,
            stroke
        )

        val fy = cy - dp(17f)
        path.reset()
        path.moveTo(cx + dp(2f), fy - dp(47f))
        path.cubicTo(
            cx - dp(28f),
            fy - dp(18f),
            cx - dp(28f),
            fy + dp(17f),
            cx,
            fy + dp(37f)
        )
        path.cubicTo(
            cx + dp(29f),
            fy + dp(17f),
            cx + dp(31f),
            fy - dp(19f),
            cx + dp(2f),
            fy - dp(47f)
        )
        path.close()

        fill.shader = LinearGradient(
            cx,
            fy - dp(47f),
            cx,
            fy + dp(37f),
            ColorUtils.blendARGB(p.accent, Color.WHITE, 0.18f),
            p.accent,
            Shader.TileMode.CLAMP
        )
        fill.alpha = (250 * alpha).toInt().coerceIn(0, 255)
        canvas.drawPath(path, fill)
        fill.shader = null
        fill.alpha = 255

        fill.color = ColorUtils.setAlphaComponent(
            Color.WHITE,
            (245 * alpha).toInt().coerceIn(0, 255)
        )
        canvas.drawCircle(cx, fy + dp(12f), dp(8f), fill)

        val startX = cx - dp(72f)
        val dayY = cy + dp(90f)
        for (i in 0 until 7) {
            fill.color = ColorUtils.setAlphaComponent(
                if (i < 5) p.accent else p.surfaceAlt,
                (255 * alpha).toInt().coerceIn(0, 255)
            )
            canvas.drawCircle(
                startX + i * dp(24f),
                dayY,
                dp(if (i == 4) 7f else 5f),
                fill
            )
        }
    }

    private fun drawCustomize(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        alpha: Float,
        p: ThemePalette
    ) {
        val top = cy - dp(70f)
        val widths = listOf(0.32f, 0.68f, 0.46f)

        for (i in 0..2) {
            val y = top + i * dp(52f)
            val left = cx - dp(102f)
            val right = cx + dp(102f)

            stroke.strokeWidth = dp(6f)
            stroke.color = ColorUtils.setAlphaComponent(
                p.surfaceAlt,
                (255 * alpha).toInt().coerceIn(0, 255)
            )
            canvas.drawLine(left, y, right, y, stroke)

            val wobble =
                sin(((ambient + i * 0.15f) * PI * 2).toFloat()) *
                    dp(2.5f)
            val knobX =
                left + (right - left) * widths[i] + wobble

            stroke.color = ColorUtils.setAlphaComponent(
                p.accent,
                (245 * alpha).toInt().coerceIn(0, 255)
            )
            canvas.drawLine(left, y, knobX, y, stroke)

            fill.color = ColorUtils.setAlphaComponent(
                p.accent,
                (255 * alpha).toInt().coerceIn(0, 255)
            )
            canvas.drawCircle(knobX, y, dp(12f), fill)

            fill.color = ColorUtils.setAlphaComponent(
                Color.WHITE,
                (245 * alpha).toInt().coerceIn(0, 255)
            )
            canvas.drawCircle(knobX, y, dp(4.5f), fill)
        }

        val paletteY = cy + dp(92f)
        val colors = listOf(
            ColorUtils.blendARGB(p.accent, Color.BLUE, 0.35f),
            p.accent,
            ColorUtils.blendARGB(p.accent, Color.MAGENTA, 0.24f)
        )

        colors.forEachIndexed { index, color ->
            fill.color = ColorUtils.setAlphaComponent(
                color,
                (255 * alpha).toInt().coerceIn(0, 255)
            )
            canvas.drawCircle(
                cx + dp((index - 1) * 42f),
                paletteY,
                dp(if (index == 1) 15f else 13f),
                fill
            )
        }
    }

    private fun smooth(value: Float): Float =
        value * value * (3f - 2f * value)

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}
