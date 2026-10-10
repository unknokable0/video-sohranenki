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
 * Five independent, consistently sized illustrations for SOHR onboarding.
 * Each page has its own hand-drawn vector artwork; nothing morphs between
 * unrelated shapes. Page changes use only a subtle alpha crossfade.
 */
class SohrIntroMorphView(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var palette: ThemePalette? = null
    private var pageProgress = 0f
    private var animationsEnabled = true

    fun setPalette(value: ThemePalette) { palette = value; invalidate() }
    fun setAnimationsEnabled(value: Boolean) { animationsEnabled = value; invalidate() }
    fun setPageProgress(value: Float) {
        pageProgress = value.coerceIn(0f, 4f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val theme = palette ?: return
        if (width == 0 || height == 0) return
        val saved = canvas.save()
        val scale = minOf(width, height) / 160f
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(scale, scale)
        canvas.translate(-80f, -80f)

        val progress = if (animationsEnabled) pageProgress
            else pageProgress.roundToInt().toFloat()
        val left = floor(progress).toInt().coerceIn(0, 4)
        val right = (left + 1).coerceAtMost(4)
        val fraction = (progress - left).coerceIn(0f, 1f)
        if (left == right || fraction <= 0.001f) {
            drawIcon(canvas, left, 1f, theme)
        } else {
            drawIcon(canvas, left, 1f - fraction, theme)
            drawIcon(canvas, right, fraction, theme)
        }
        canvas.restoreToCount(saved)
    }

    private fun drawIcon(canvas: Canvas, page: Int, alpha: Float, theme: ThemePalette) {
        if (alpha <= 0f) return
        val layer = canvas.saveLayerAlpha(
            0f, 0f, 160f, 160f,
            (255f * alpha).roundToInt().coerceIn(0, 255)
        )
        val accent = theme.accent
        when (page) {
            0 -> drawSavedVideos(canvas, accent)
            1 -> drawProgressCalendar(canvas, accent)
            2 -> drawVideoPlayer(canvas, accent)
            3 -> drawDownloads(canvas, accent)
            else -> drawPersonalize(canvas, accent)
        }
        canvas.restoreToCount(layer)
    }

    private fun light(accent: Int, amount: Float) =
        ColorUtils.blendARGB(accent, Color.WHITE, amount)
    private fun deep(accent: Int, amount: Float) =
        ColorUtils.blendARGB(accent, Color.rgb(40, 24, 86), amount)

    private fun solid(canvas: Canvas, box: RectF, radius: Float, color: Int) {
        fill.shader = null
        fill.color = color
        canvas.drawRoundRect(box, radius, radius, fill)
    }

    private fun solid(canvas: Canvas, l: Float, t: Float, r: Float, b: Float,
                      radius: Float, color: Int) {
        solid(canvas, RectF(l, t, r, b), radius, color)
    }

    private fun disc(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        fill.shader = null
        fill.color = color
        canvas.drawCircle(x, y, radius, fill)
    }

    private fun gradient(canvas: Canvas, box: RectF, radius: Float,
                         top: Int, bottom: Int, border: Boolean = true) {
        fill.color = Color.WHITE
        fill.shader = LinearGradient(
            box.left, box.top, box.right, box.bottom,
            top, bottom, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(box, radius, radius, fill)
        fill.shader = null
        if (border) {
            outline.color = ColorUtils.setAlphaComponent(Color.WHITE, 52)
            outline.strokeWidth = 0.8f
            canvas.drawRoundRect(box, radius, radius, outline)
        }
    }

    private fun card(canvas: Canvas, l: Float, t: Float, r: Float, b: Float,
                     radius: Float, accent: Int, brightness: Float = 0f) {
        val box = RectF(l, t, r, b)
        val depth = RectF(l, t + 3f, r, b + 3f)
        solid(canvas, depth, radius, ColorUtils.setAlphaComponent(deep(accent, 0.88f), 205))
        gradient(
            canvas, box, radius,
            light(accent, 0.28f + brightness * 0.45f),
            deep(accent, 0.55f - brightness * 0.25f)
        )
    }

    private fun triangle(canvas: Canvas, x: Float, y: Float, size: Float,
                         color: Int = Color.WHITE) {
        val p = Path().apply {
            moveTo(x - size * 0.38f, y - size * 0.56f)
            quadTo(x - size * 0.39f, y - size * 0.66f,
                x - size * 0.17f, y - size * 0.58f)
            lineTo(x + size * 0.64f, y - size * 0.11f)
            quadTo(x + size * 0.83f, y,
                x + size * 0.64f, y + size * 0.11f)
            lineTo(x - size * 0.17f, y + size * 0.58f)
            quadTo(x - size * 0.39f, y + size * 0.66f,
                x - size * 0.38f, y + size * 0.56f)
            close()
        }
        fill.shader = null
        fill.color = color
        canvas.drawPath(p, fill)
    }

    // Page 1: three clearly separated stacked media cards, not one blob.
    private fun drawSavedVideos(canvas: Canvas, accent: Int) {
        canvas.save()
        canvas.rotate(7f, 84f, 79f)
        card(canvas, 43f, 29f, 137f, 119f, 21f, accent, 0.27f)
        canvas.restore()

        canvas.save()
        canvas.rotate(-6f, 75f, 83f)
        card(canvas, 27f, 34f, 125f, 132f, 22f, accent, 0.13f)
        canvas.restore()

        card(canvas, 30f, 48f, 128f, 139f, 22f, accent)
        // Distinct dark center ensures the play symbol is crisp and recognizable.
        gradient(
            canvas, RectF(43f, 59f, 115f, 125f), 16f,
            deep(accent, 0.74f), deep(accent, 0.94f), false
        )
        triangle(canvas, 81f, 92f, 32f, light(accent, 0.92f))
    }

    // Page 2: rounded calendar with regular grid and a circular streak badge.
    private fun drawProgressCalendar(canvas: Canvas, accent: Int) {
        card(canvas, 26f, 29f, 131f, 136f, 22f, accent)
        gradient(
            canvas, RectF(27f, 30f, 130f, 66f), 19f,
            light(accent, 0.56f), light(accent, 0.19f), false
        )
        // Cover the lower rounding of the header, keeping the upper corners round.
        solid(canvas, 27f, 55f, 130f, 67f, 0f, light(accent, 0.20f))
        gradient(canvas, RectF(45f, 20f, 55f, 52f), 5f,
            light(accent, 0.89f), accent, false)
        gradient(canvas, RectF(102f, 20f, 112f, 52f), 5f,
            light(accent, 0.89f), accent, false)
        for (row in 0..2) {
            for (col in 0..3) {
                val x = 41f + col * 20f
                val y = 77f + row * 18f
                solid(canvas, x, y, x + 13f, y + 12f, 3.3f,
                    if (row == 1 && col == 2) light(accent, 0.70f)
                    else deep(accent, 0.56f))
            }
        }
        disc(canvas, 118f, 119f, 23f, deep(accent, 0.88f))
        disc(canvas, 118f, 116f, 23f, light(accent, 0.14f))
        drawFlame(canvas, 118f, 116f, 14f, light(accent, 0.95f))
    }

    // Page 3: upright video window, mountain preview and balanced controls.
    private fun drawVideoPlayer(canvas: Canvas, accent: Int) {
        card(canvas, 19f, 34f, 141f, 131f, 22f, accent)
        val preview = RectF(30f, 45f, 130f, 107f)
        drawLandscape(canvas, preview, accent)
        // Deep oval under the translucent white play glyph.
        disc(canvas, 80f, 77f, 23f, ColorUtils.setAlphaComponent(deep(accent, 0.84f), 135))
        triangle(canvas, 82f, 77f, 25f, light(accent, 0.96f))
        val lineColor = deep(accent, 0.81f)
        solid(canvas, 32f, 118f, 129f, 124f, 3f, lineColor)
        solid(canvas, 32f, 118f, 79f, 124f, 3f, light(accent, 0.86f))
        disc(canvas, 79f, 121f, 7.4f, deep(accent, 0.62f))
        disc(canvas, 79f, 119f, 6.6f, light(accent, 0.82f))
    }

    // Page 4: one symmetric cloud with a down arrow, no extra outlines.
    private fun drawDownloads(canvas: Canvas, accent: Int) {
        val shape = Path().apply {
            moveTo(39f, 127f)
            cubicTo(20f, 127f, 15f, 111f, 21f, 96f)
            cubicTo(26f, 82f, 38f, 76f, 50f, 77f)
            cubicTo(54f, 53f, 73f, 35f, 96f, 38f)
            cubicTo(119f, 40f, 130f, 57f, 129f, 77f)
            cubicTo(146f, 81f, 149f, 96f, 144f, 110f)
            cubicTo(140f, 121f, 131f, 127f, 117f, 127f)
            close()
        }
        canvas.save()
        canvas.translate(0f, 3f)
        fill.shader = null
        fill.color = deep(accent, 0.90f)
        canvas.drawPath(shape, fill)
        canvas.restore()
        fill.color = Color.WHITE
        fill.shader = LinearGradient(
            40f, 42f, 125f, 131f,
            light(accent, 0.78f), accent, Shader.TileMode.CLAMP
        )
        canvas.drawPath(shape, fill)
        fill.shader = null
        outline.color = ColorUtils.setAlphaComponent(Color.WHITE, 68)
        outline.strokeWidth = 0.9f
        canvas.drawPath(shape, outline)
        // Integrated download arrow has generous clearance around all edges.
        solid(canvas, 75f, 69f, 85f, 105f, 5f, light(accent, 0.96f))
        val arrow = Path().apply {
            moveTo(59f, 100f)
            quadTo(57f, 97f, 62f, 96f)
            lineTo(98f, 96f)
            quadTo(103f, 97f, 100f, 101f)
            lineTo(83f, 118f)
            quadTo(80f, 121f, 77f, 118f)
            close()
        }
        fill.color = light(accent, 0.96f)
        canvas.drawPath(arrow, fill)
    }

    // Page 5: a genuine eight-tooth gear and two uniform sliders.
    private fun drawPersonalize(canvas: Canvas, accent: Int) {
        card(canvas, 21f, 31f, 139f, 133f, 23f, accent)
        gradient(
            canvas, RectF(31f, 42f, 129f, 122f), 17f,
            deep(accent, 0.65f), deep(accent, 0.86f), false
        )
        drawGear(canvas, 61f, 75f, 22f, light(accent, 0.88f), deep(accent, 0.98f))
        val track = deep(accent, 0.96f)
        val selected = light(accent, 0.57f)
        solid(canvas, 91f, 65f, 121f, 71f, 3f, track)
        solid(canvas, 91f, 65f, 107f, 71f, 3f, selected)
        disc(canvas, 107f, 68f, 8f, light(accent, 0.88f))
        solid(canvas, 91f, 91f, 121f, 97f, 3f, track)
        solid(canvas, 91f, 91f, 116f, 97f, 3f, selected)
        disc(canvas, 116f, 94f, 8f, light(accent, 0.88f))
    }

    private fun drawGear(canvas: Canvas, cx: Float, cy: Float, r: Float,
                         color: Int, hole: Int) {
        val points = 48
        val p = Path()
        for (i in 0 until points) {
            val section = i % 6
            val rad = when (section) {
                0, 1 -> r * 0.79f
                2, 3 -> r
                else -> r * 0.79f
            }
            val angle = -PI / 2.0 + i * 2.0 * PI / points
            val x = cx + cos(angle).toFloat() * rad
            val y = cy + sin(angle).toFloat() * rad
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        fill.shader = null
        fill.color = color
        canvas.drawPath(p, fill)
        disc(canvas, cx, cy, 8.2f, hole)
    }

    private fun drawFlame(canvas: Canvas, cx: Float, cy: Float, size: Float,
                          color: Int) {
        val p = Path().apply {
            moveTo(cx + size * 0.11f, cy - size)
            cubicTo(cx + size * 0.18f, cy - size * 0.20f,
                cx + size * 0.75f, cy - size * 0.20f,
                cx + size * 0.72f, cy + size * 0.32f)
            cubicTo(cx + size * 0.70f, cy + size,
                cx - size * 0.65f, cy + size,
                cx - size * 0.73f, cy + size * 0.25f)
            cubicTo(cx - size * 0.86f, cy - size * 0.27f,
                cx - size * 0.12f, cy - size * 0.44f,
                cx + size * 0.11f, cy - size)
            close()
        }
        fill.shader = null
        fill.color = color
        canvas.drawPath(p, fill)
    }

    private fun drawLandscape(canvas: Canvas, rect: RectF, accent: Int) {
        val saved = canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, 12f, 12f, Path.Direction.CW) })
        fill.color = Color.WHITE
        fill.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            light(accent, 0.76f), light(accent, 0.19f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, fill)
        fill.shader = null
        disc(canvas, rect.right - rect.width() * 0.22f, rect.top + rect.height() * 0.31f,
            rect.height() * 0.11f, light(accent, 0.86f))
        fun hill(yFactor: Float, ampFactor: Float, color: Int) {
            val y = rect.top + rect.height() * yFactor
            val a = rect.height() * ampFactor
            val p = Path().apply {
                moveTo(rect.left, rect.bottom)
                lineTo(rect.left, y)
                cubicTo(rect.left + rect.width() * 0.2f, y - a,
                    rect.left + rect.width() * 0.28f, y - a,
                    rect.left + rect.width() * 0.45f, y)
                cubicTo(rect.left + rect.width() * 0.66f, y + a * 0.63f,
                    rect.left + rect.width() * 0.82f, y - a * 0.55f,
                    rect.right, y - a * 0.1f)
                lineTo(rect.right, rect.bottom)
                close()
            }
            fill.shader = null
            fill.color = color
            canvas.drawPath(p, fill)
        }
        hill(0.58f, 0.36f, light(accent, 0.40f))
        hill(0.78f, 0.25f, deep(accent, 0.32f))
        hill(0.97f, 0.19f, deep(accent, 0.85f))
        canvas.restoreToCount(saved)
    }
}
