package com.unknokable.videosohranenki

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Native resolution-independent onboarding illustrations.
 *
 * Five standalone illustrations drawn with antialiased vector geometry.
 * Nothing is loaded from an atlas/screenshot and no image is upscaled.
 * The only page-to-page animation is a brief alpha crossfade.
 */
class SohrIntroMorphView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.85f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private var palette: ThemePalette? = null
    private var pageProgress = 0f
    private var animationsEnabled = true
    private val descriptions = listOf(
        "Сохранённые видео",
        "Календарь активности",
        "Видеоплеер",
        "Загрузки",
        "Настройки"
    )

    fun setPalette(value: ThemePalette) {
        palette = value
        invalidate()
    }

    fun setAnimationsEnabled(value: Boolean) {
        animationsEnabled = value
        invalidate()
    }

    fun setPageProgress(value: Float) {
        pageProgress = value.coerceIn(0f, 4f)
        contentDescription = descriptions[pageProgress.roundToInt().coerceIn(0, 4)]
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val theme = palette ?: return
        if (width <= 0 || height <= 0) return

        val root = canvas.save()
        val scale = minOf(width, height).toFloat() / 166f
        canvas.translate(width * 0.5f, height * 0.5f)
        canvas.scale(scale, scale)
        canvas.translate(-80f, -80f)

        val position = if (animationsEnabled) pageProgress
            else pageProgress.roundToInt().toFloat()
        val from = floor(position).toInt().coerceIn(0, 4)
        val to = (from + 1).coerceAtMost(4)
        val raw = (position - from).coerceIn(0f, 1f)
        // Ease the opacity only; distinct illustrations never deform.
        val t = raw * raw * (3f - 2f * raw)
        if (from == to || t <= 0.001f) {
            drawPage(canvas, from, 1f, theme.accent)
        } else {
            drawPage(canvas, from, 1f - t, theme.accent)
            drawPage(canvas, to, t, theme.accent)
        }
        canvas.restoreToCount(root)
    }

    private fun drawPage(canvas: Canvas, index: Int, opacity: Float, accent: Int) {
        if (opacity <= 0.001f) return
        val layer = canvas.saveLayerAlpha(
            0f, 0f, 160f, 160f, (opacity * 255f).roundToInt().coerceIn(0, 255)
        )
        when (index) {
            0 -> savedVideos(canvas, accent)
            1 -> calendar(canvas, accent)
            2 -> player(canvas, accent)
            3 -> cloud(canvas, accent)
            else -> customize(canvas, accent)
        }
        canvas.restoreToCount(layer)
    }

    private fun brighten(color: Int, amount: Float): Int =
        ColorUtils.blendARGB(color, Color.WHITE, amount.coerceIn(0f, 1f))
    private fun darken(color: Int, amount: Float): Int =
        ColorUtils.blendARGB(color, Color.rgb(39, 25, 77), amount.coerceIn(0f, 1f))
    private fun tinted(color: Int, alpha: Int): Int =
        ColorUtils.setAlphaComponent(color, alpha.coerceIn(0, 255))

    private fun round(canvas: Canvas, l: Float, t: Float, r: Float, b: Float,
                      rad: Float, color: Int) {
        paint.shader = null
        paint.color = color
        canvas.drawRoundRect(l, t, r, b, rad, rad, paint)
    }

    private fun dot(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        paint.shader = null
        paint.color = color
        canvas.drawCircle(x, y, radius, paint)
    }

    private fun gradientRound(
        canvas: Canvas, box: RectF, radius: Float, upper: Int, lower: Int,
        rim: Boolean = true
    ) {
        paint.color = Color.WHITE
        paint.shader = LinearGradient(
            box.left, box.top, box.right, box.bottom,
            upper, lower, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(box, radius, radius, paint)
        paint.shader = null
        if (rim) {
            border.color = tinted(Color.WHITE, 54)
            canvas.drawRoundRect(box, radius, radius, border)
        }
    }

    private fun panel(
        canvas: Canvas, box: RectF, radius: Float, accent: Int,
        elevation: Float = 1f
    ) {
        // Crisp native layers rather than bitmap shadows or bloom/neon.
        round(
            canvas, box.left, box.top + 3.5f * elevation,
            box.right, box.bottom + 3.5f * elevation, radius,
            tinted(darken(accent, 0.89f), (82f * elevation).roundToInt())
        )
        gradientRound(
            canvas, box, radius,
            brighten(accent, 0.49f), darken(accent, 0.49f)
        )
        val saved = canvas.save()
        val clip = Path().apply {
            addRoundRect(box, radius, radius, Path.Direction.CW)
        }
        canvas.clipPath(clip)
        // Very subtle top glass reflection, clipped within the actual icon.
        val stripe = RectF(
            box.left + 4f, box.top + 3f,
            box.right - 4f, box.top + 13f
        )
        gradientRound(
            canvas, stripe, minOf(8f, radius),
            tinted(Color.WHITE, 47), tinted(Color.WHITE, 0), false
        )
        canvas.restoreToCount(saved)
    }

    private fun play(canvas: Canvas, cx: Float, cy: Float, size: Float,
                     color: Int = Color.WHITE) {
        val path = Path().apply {
            moveTo(cx - size * 0.38f, cy - size * 0.56f)
            quadTo(cx - size * 0.42f, cy - size * 0.68f,
                cx - size * 0.17f, cy - size * 0.58f)
            lineTo(cx + size * 0.61f, cy - size * 0.12f)
            quadTo(cx + size * 0.81f, cy, cx + size * 0.61f, cy + size * 0.12f)
            lineTo(cx - size * 0.17f, cy + size * 0.58f)
            quadTo(cx - size * 0.42f, cy + size * 0.68f,
                cx - size * 0.38f, cy + size * 0.56f)
            close()
        }
        paint.shader = null
        paint.color = color
        canvas.drawPath(path, paint)
    }

    private fun savedVideos(canvas: Canvas, accent: Int) {
        // Three visibly distinct cards, made from precisely rounded geometry.
        canvas.save()
        canvas.rotate(8f, 98f, 79f)
        panel(canvas, RectF(54f, 29f, 135f, 119f), 18f, darken(accent, 0.18f), 0.75f)
        canvas.restore()

        canvas.save()
        canvas.rotate(-7f, 63f, 83f)
        panel(canvas, RectF(29f, 37f, 114f, 125f), 20f, brighten(accent, 0.06f), 0.8f)
        canvas.restore()

        panel(canvas, RectF(34f, 51f, 129f, 139f), 21f, accent)
        val inner = RectF(43f, 59f, 120f, 129f)
        gradientRound(
            canvas, inner, 16f,
            darken(accent, 0.68f), darken(accent, 0.90f), false
        )
        play(canvas, 82f, 94f, 30f, brighten(accent, 0.92f))
    }

    private fun calendar(canvas: Canvas, accent: Int) {
        panel(canvas, RectF(25f, 33f, 134f, 138f), 21f, accent)
        gradientRound(
            canvas, RectF(26f, 33f, 133f, 68f), 20f,
            brighten(accent, 0.75f), brighten(accent, 0.35f), false
        )
        // Square off the bottom of the header, not its rounded upper edge.
        round(canvas, 26f, 56f, 133f, 69f, 0f, brighten(accent, 0.34f))
        gradientRound(
            canvas, RectF(42f, 20f, 53f, 51f), 5.5f,
            brighten(accent, 0.90f), brighten(accent, 0.30f), false
        )
        gradientRound(
            canvas, RectF(105f, 20f, 116f, 51f), 5.5f,
            brighten(accent, 0.90f), brighten(accent, 0.30f), false
        )
        for (row in 0..2) {
            for (col in 0..3) {
                val x = 38f + col * 20f
                val y = 79f + row * 17.5f
                val selected = row == 1 && col == 2
                round(
                    canvas, x, y, x + 13.5f, y + 11.5f, 3.4f,
                    if (selected) brighten(accent, 0.84f)
                    else darken(accent, 0.66f)
                )
            }
        }
        // Streak badge exactly anchored to the lower-right corner.
        dot(canvas, 120f, 118f, 21f, darken(accent, 0.92f))
        dot(canvas, 120f, 116f, 21f, brighten(accent, 0.21f))
        flame(canvas, 120f, 116f, 14f, brighten(accent, 0.96f))
    }

    private fun flame(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        val p = Path().apply {
            moveTo(cx + 0.02f * size, cy - size)
            cubicTo(cx + size * 0.10f, cy - size * 0.37f,
                cx + size * 0.65f, cy - size * 0.24f,
                cx + size * 0.54f, cy + size * 0.22f)
            cubicTo(cx + size * 0.79f, cy - size * 0.08f,
                cx + size * 0.92f, cy + size * 0.46f,
                cx + size * 0.50f, cy + size * 0.78f)
            cubicTo(cx - size * 0.40f, cy + size * 1.07f,
                cx - size * 0.83f, cy + size * 0.39f,
                cx - size * 0.65f, cy - size * 0.10f)
            cubicTo(cx - size * 0.42f, cy - size * 0.49f,
                cx - size * 0.15f, cy - size * 0.61f,
                cx + size * 0.02f, cy - size)
            close()
        }
        paint.shader = null
        paint.color = color
        canvas.drawPath(p, paint)
    }

    private fun landscape(canvas: Canvas, bounds: RectF, accent: Int) {
        val save = canvas.save()
        canvas.clipPath(Path().apply {
            addRoundRect(bounds, 11f, 11f, Path.Direction.CW)
        })
        paint.color = Color.WHITE
        paint.shader = LinearGradient(
            bounds.left, bounds.top, bounds.right, bounds.bottom,
            brighten(accent, 0.85f), brighten(accent, 0.18f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(bounds, paint)
        paint.shader = null
        dot(
            canvas,
            bounds.right - bounds.width() * 0.22f,
            bounds.top + bounds.height() * 0.25f,
            bounds.height() * 0.11f,
            brighten(accent, 0.84f)
        )

        fun hill(y: Float, bump: Float, color: Int) {
            val width = bounds.width()
            val wave = Path().apply {
                moveTo(bounds.left, bounds.bottom)
                lineTo(bounds.left, y)
                cubicTo(
                    bounds.left + width * 0.19f, y - bump,
                    bounds.left + width * 0.29f, y - bump,
                    bounds.left + width * 0.43f, y
                )
                cubicTo(
                    bounds.left + width * 0.62f, y + bump * 0.70f,
                    bounds.left + width * 0.81f, y - bump * 0.61f,
                    bounds.right, y - bump * 0.12f
                )
                lineTo(bounds.right, bounds.bottom)
                close()
            }
            paint.shader = null
            paint.color = color
            canvas.drawPath(wave, paint)
        }
        hill(bounds.top + bounds.height() * 0.57f, bounds.height() * 0.32f,
            brighten(accent, 0.45f))
        hill(bounds.top + bounds.height() * 0.79f, bounds.height() * 0.23f,
            darken(accent, 0.18f))
        hill(bounds.top + bounds.height() * 0.95f, bounds.height() * 0.15f,
            darken(accent, 0.78f))
        canvas.restoreToCount(save)
    }

    private fun player(canvas: Canvas, accent: Int) {
        panel(canvas, RectF(20f, 33f, 140f, 135f), 22f, accent)
        landscape(canvas, RectF(30f, 43f, 130f, 104f), accent)

        // A properly centered button, with clean opaque triangle.
        dot(canvas, 80f, 73f, 22f, tinted(darken(accent, 0.88f), 146))
        play(canvas, 82f, 73f, 24f, Color.WHITE)

        round(canvas, 33f, 116f, 127f, 122f, 3f, darken(accent, 0.75f))
        round(canvas, 33f, 116f, 87f, 122f, 3f, brighten(accent, 0.79f))
        dot(canvas, 87f, 119f, 7.1f, darken(accent, 0.66f))
        dot(canvas, 87f, 117.7f, 6.1f, brighten(accent, 0.82f))
    }

    private fun cloud(canvas: Canvas, accent: Int) {
        val shape = Path().apply {
            moveTo(39f, 132f)
            cubicTo(23f, 132f, 17f, 122f, 17f, 108f)
            cubicTo(17f, 92f, 31f, 83f, 45f, 84f)
            cubicTo(49f, 57f, 68f, 37f, 92f, 37f)
            cubicTo(117f, 37f, 132f, 57f, 131f, 82f)
            cubicTo(146f, 85f, 151f, 96f, 151f, 109f)
            cubicTo(151f, 123f, 141f, 132f, 125f, 132f)
            close()
        }
        canvas.save()
        canvas.translate(0f, 3f)
        paint.shader = null
        paint.color = tinted(darken(accent, 0.91f), 96)
        canvas.drawPath(shape, paint)
        canvas.restore()

        paint.color = Color.WHITE
        paint.shader = LinearGradient(
            48f, 41f, 123f, 133f,
            brighten(accent, 0.79f), darken(accent, 0.11f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(shape, paint)
        paint.shader = null
        border.color = tinted(Color.WHITE, 67)
        canvas.drawPath(shape, border)

        // Download shaft and arrow are native path geometry with no glyph font.
        round(canvas, 74.5f, 65f, 85.5f, 105f, 5.5f,
            brighten(accent, 0.96f))
        val arrow = Path().apply {
            moveTo(56f, 95f)
            quadTo(59f, 92f, 62f, 95f)
            lineTo(80f, 113f)
            lineTo(98f, 95f)
            quadTo(101f, 92f, 104f, 95f)
            quadTo(107f, 98f, 104f, 101f)
            lineTo(84f, 122f)
            quadTo(80f, 126f, 76f, 122f)
            lineTo(56f, 101f)
            quadTo(53f, 98f, 56f, 95f)
            close()
        }
        paint.shader = null
        paint.color = brighten(accent, 0.96f)
        canvas.drawPath(arrow, paint)
    }

    private fun gear(canvas: Canvas, cx: Float, cy: Float, radius: Float,
                     color: Int, inside: Int) {
        val shape = Path()
        // 8 equal teeth, each with a flat, perfectly consistent tip.
        val steps = 8 * 8
        for (i in 0 until steps) {
            val phase = i % 8
            val r = if (phase in 2..5) radius else radius * 0.79f
            val angle = 2.0 * PI * i / steps - PI / 2.0
            val x = cx + cos(angle).toFloat() * r
            val y = cy + sin(angle).toFloat() * r
            if (i == 0) shape.moveTo(x, y) else shape.lineTo(x, y)
        }
        shape.close()
        paint.shader = null
        paint.color = color
        canvas.drawPath(shape, paint)
        dot(canvas, cx, cy, radius * 0.33f, inside)
    }

    private fun customize(canvas: Canvas, accent: Int) {
        panel(canvas, RectF(22f, 32f, 138f, 135f), 22f, accent)
        gradientRound(
            canvas, RectF(31f, 42f, 129f, 125f), 16f,
            darken(accent, 0.57f), darken(accent, 0.86f), false
        )
        gear(
            canvas, 61f, 80f, 22f,
            brighten(accent, 0.90f), darken(accent, 0.95f)
        )
        val track = darken(accent, 0.90f)
        val progress = brighten(accent, 0.70f)
        round(canvas, 92f, 68f, 119f, 74f, 3f, track)
        round(canvas, 92f, 68f, 108f, 74f, 3f, progress)
        dot(canvas, 108f, 71f, 8.3f, brighten(accent, 0.93f))
        round(canvas, 92f, 98f, 119f, 104f, 3f, track)
        round(canvas, 92f, 98f, 116f, 104f, 3f, progress)
        dot(canvas, 116f, 101f, 8.3f, brighten(accent, 0.93f))
    }
}
