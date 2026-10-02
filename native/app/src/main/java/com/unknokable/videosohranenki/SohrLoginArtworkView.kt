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
import kotlin.math.sin

class SohrLoginArtworkView(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    private var palette: ThemePalette? = null
    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 4_200L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
        start()
    }

    fun setPalette(value: ThemePalette) {
        palette = value
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = palette ?: return
        val cx = width / 2f
        val cy = height / 2f + dp(3f)

        drawTelegramBubble(canvas, cx - dp(72f), cy, p)
        drawConnection(canvas, cx, cy, p)
        drawSohrCloud(canvas, cx + dp(74f), cy, p)
    }

    private fun drawTelegramBubble(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        p: ThemePalette
    ) {
        val rect = RectF(
            cx - dp(47f),
            cy - dp(41f),
            cx + dp(47f),
            cy + dp(36f)
        )
        fill.color = p.surfaceAlt
        canvas.drawRoundRect(rect, dp(24f), dp(24f), fill)

        path.reset()
        path.moveTo(cx - dp(18f), cy + dp(31f))
        path.lineTo(cx - dp(31f), cy + dp(50f))
        path.lineTo(cx - dp(7f), cy + dp(36f))
        path.close()
        canvas.drawPath(path, fill)

        fill.color = ColorUtils.blendARGB(
            p.accent,
            Color.rgb(64, 165, 255),
            0.46f
        )
        path.reset()
        path.moveTo(cx - dp(26f), cy - dp(8f))
        path.lineTo(cx + dp(29f), cy - dp(28f))
        path.lineTo(cx + dp(12f), cy + dp(25f))
        path.lineTo(cx - dp(2f), cy + dp(7f))
        path.close()
        canvas.drawPath(path, fill)

        stroke.strokeWidth = dp(2.5f)
        stroke.color = ColorUtils.setAlphaComponent(
            Color.WHITE,
            205
        )
        canvas.drawLine(
            cx - dp(2f),
            cy + dp(7f),
            cx + dp(21f),
            cy - dp(18f),
            stroke
        )
    }

    private fun drawConnection(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        p: ThemePalette
    ) {
        val travel =
            0.5f + 0.5f * sin((phase * PI * 2).toFloat())

        for (i in -1..1) {
            val x = cx + i * dp(18f)
            fill.color = ColorUtils.setAlphaComponent(
                if (i == 0) p.accent else p.muted,
                if (i == 0) 210 else 95
            )
            canvas.drawCircle(
                x,
                cy + dp(2f),
                dp(if (i == 0) 4.5f + travel else 3.5f),
                fill
            )
        }
    }

    private fun drawSohrCloud(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        p: ThemePalette
    ) {
        val gradient = LinearGradient(
            cx - dp(50f),
            cy - dp(46f),
            cx + dp(55f),
            cy + dp(46f),
            ColorUtils.blendARGB(p.accent, Color.BLUE, 0.18f),
            ColorUtils.blendARGB(p.accent, Color.CYAN, 0.18f),
            Shader.TileMode.CLAMP
        )
        fill.shader = gradient

        canvas.drawCircle(cx - dp(26f), cy + dp(7f), dp(31f), fill)
        canvas.drawCircle(cx + dp(27f), cy + dp(7f), dp(30f), fill)
        canvas.drawCircle(cx, cy - dp(20f), dp(40f), fill)
        canvas.drawRoundRect(
            RectF(
                cx - dp(55f),
                cy - dp(7f),
                cx + dp(55f),
                cy + dp(36f)
            ),
            dp(22f),
            dp(22f),
            fill
        )
        fill.shader = null

        val pulse =
            0.5f + 0.5f * sin((phase * PI * 2).toFloat())

        stroke.strokeWidth = dp(3.4f)
        stroke.color = ColorUtils.setAlphaComponent(
            Color.WHITE,
            (190 + 45 * pulse).toInt()
        )
        canvas.drawArc(
            RectF(
                cx - dp(27f),
                cy - dp(29f),
                cx + dp(29f),
                cy + dp(29f)
            ),
            205f,
            125f,
            false,
            stroke
        )

        fill.color = Color.WHITE
        canvas.drawCircle(
            cx + dp(29f),
            cy - dp(26f),
            dp(4f + pulse),
            fill
        )
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}
