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
import kotlin.math.floor
import kotlin.math.roundToInt

/** Crisp five-icon SOHR vector onboarding. No neon, distorted polygon morphs or timers. */
class SohrIntroMorphView(context: Context) : View(context) {
    private data class HeroFrame(val l: Float, val t: Float, val r: Float, val b: Float, val radius: Float)
    private val frames = listOf(
        HeroFrame(24f, 24f, 136f, 136f, 56f),
        HeroFrame(20f, 43f, 140f, 123f, 20f),
        HeroFrame(18f, 26f, 142f, 134f, 22f),
        HeroFrame(26f, 30f, 134f, 136f, 20f),
        HeroFrame(27f, 24f, 133f, 136f, 23f)
    )
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.9f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var palette: ThemePalette? = null
    private var progress = 0f
    private var animationsEnabled = true

    fun setPalette(value: ThemePalette) { palette = value; invalidate() }
    fun setAnimationsEnabled(value: Boolean) { animationsEnabled = value; invalidate() }
    fun setPageProgress(value: Float) { progress = value.coerceIn(0f, 4f); invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val theme = palette ?: return
        if (width == 0 || height == 0) return
        val saved = canvas.save()
        val unit = minOf(width, height).toFloat() / 160f
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(unit, unit)
        canvas.translate(-80f, -80f)

        val page = if (animationsEnabled) progress else progress.roundToInt().toFloat()
        val from = floor(page).toInt().coerceIn(0, 4)
        val to = (from + 1).coerceAtMost(4)
        val t = smooth(page - from)
        drawFrame(canvas, frameBetween(frames[from], frames[to], t), theme)
        if (from == to || t == 0f) {
            drawContent(canvas, from, 1f, theme)
        } else {
            drawContent(canvas, from, 1f - t, theme)
            drawContent(canvas, to, t, theme)
        }
        canvas.restoreToCount(saved)
    }

    private fun drawFrame(canvas: Canvas, f: HeroFrame, theme: ThemePalette) {
        fill.color = Color.WHITE
        fill.shader = LinearGradient(
            f.l, f.t, f.r, f.b,
            intArrayOf(
                ColorUtils.blendARGB(theme.accent, Color.WHITE, 0.68f),
                ColorUtils.blendARGB(theme.accent, Color.WHITE, 0.12f),
                ColorUtils.blendARGB(theme.accent, Color.rgb(58, 32, 118), 0.34f)
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        val rect = RectF(f.l, f.t, f.r, f.b)
        canvas.drawRoundRect(rect, f.radius, f.radius, fill)
        fill.shader = null
        stroke.color = ColorUtils.setAlphaComponent(Color.WHITE, 30)
        canvas.drawRoundRect(rect, f.radius, f.radius, stroke)
    }

    private fun drawContent(canvas: Canvas, page: Int, opacity: Float, theme: ThemePalette) {
        if (opacity <= 0.001f) return
        val layer = canvas.saveLayerAlpha(0f, 0f, 160f, 160f,
            (255f * opacity).roundToInt().coerceIn(0, 255))
        when (page) {
            0 -> drawWelcome(canvas)
            1 -> drawFeed(canvas, theme)
            2 -> drawPlayer(canvas, theme)
            3 -> drawCalendar(canvas, theme)
            4 -> drawSettings(canvas, theme)
        }
        canvas.restoreToCount(layer)
    }

    private fun drawWelcome(canvas: Canvas) {
        triangle(canvas, 84f, 80f, 29f)
    }

    private fun drawFeed(canvas: Canvas, theme: ThemePalette) {
        val light = ColorUtils.blendARGB(theme.accent, Color.WHITE, 0.82f)
        rounded(canvas, 29f, 53f, 82f, 112f, 10f,
            ColorUtils.blendARGB(theme.accent, Color.WHITE, 0.55f))
        landscape(canvas, RectF(33f, 57f, 78f, 108f), theme)
        rounded(canvas, 91f, 64f, 131f, 70f, 3f, light)
        rounded(canvas, 91f, 78f, 124f, 83f, 2.5f,
            ColorUtils.setAlphaComponent(light, 220))
        rounded(canvas, 91f, 91f, 116f, 96f, 2.5f,
            ColorUtils.setAlphaComponent(light, 190))
        rounded(canvas, 91f, 105f, 106f, 109f, 2f,
            ColorUtils.setAlphaComponent(Color.WHITE, 150))
    }

    private fun drawPlayer(canvas: Canvas, theme: ThemePalette) {
        landscape(canvas, RectF(27f, 36f, 133f, 102f), theme)
        triangle(canvas, 83f, 69f, 23f)
        val track = ColorUtils.blendARGB(theme.accent, Color.BLACK, 0.50f)
        val played = ColorUtils.blendARGB(theme.accent, Color.WHITE, 0.78f)
        rounded(canvas, 31f, 114f, 129f, 120f, 3f, track)
        rounded(canvas, 31f, 114f, 88f, 120f, 3f, played)
        circle(canvas, 88f, 117f, 6.5f, Color.WHITE)
        circle(canvas, 88f, 117f, 5.2f,
            ColorUtils.blendARGB(theme.accent, Color.WHITE, 0.42f))
    }

    private fun drawCalendar(canvas: Canvas, theme: ThemePalette) {
        val pale = ColorUtils.blendARGB(theme.accent, Color.WHITE, 0.80f)
        rounded(canvas, 26f, 30f, 134f, 61f, 18f, pale)
        rounded(canvas, 26f, 46f, 134f, 61f, 3f,
            ColorUtils.blendARGB(theme.accent, Color.WHITE, 0.54f))
        rounded(canvas, 43f, 17f, 54f, 48f, 5f, pale)
        rounded(canvas, 106f, 17f, 117f, 48f, 5f, pale)
        rounded(canvas, 42f, 57f, 118f, 60f, 1.5f,
            ColorUtils.setAlphaComponent(Color.WHITE, 82))
        // Hand-authored cubic flame. A polygonal spline used to turn this
        // symbol into a warped apple-shaped blob.
        val flame = Path().apply {
            moveTo(82f, 65f)
            cubicTo(82f, 78f, 96f, 83f, 96f, 95f)
            cubicTo(103f, 90f, 106f, 85f, 105f, 80f)
            cubicTo(119f, 93f, 119f, 110f, 107f, 120f)
            cubicTo(94f, 132f, 68f, 130f, 56f, 118f)
            cubicTo(44f, 106f, 49f, 92f, 62f, 81f)
            cubicTo(72f, 73f, 79f, 70f, 82f, 65f)
            close()
        }
        fill.color = Color.WHITE
        fill.shader = LinearGradient(
            60f, 70f, 106f, 125f,
            ColorUtils.blendARGB(theme.accent, Color.WHITE, 0.95f),
            ColorUtils.blendARGB(theme.accent, Color.WHITE, 0.58f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(flame, fill)
        fill.shader = null
        val inner = Path().apply {
            moveTo(83f, 96f)
            cubicTo(86f, 103f, 94f, 107f, 91f, 115f)
            cubicTo(87f, 125f, 73f, 122f, 70f, 113f)
            cubicTo(68f, 106f, 78f, 98f, 83f, 96f)
            close()
        }
        fill.color = ColorUtils.blendARGB(theme.accent, Color.rgb(56, 31, 111), 0.64f)
        canvas.drawPath(inner, fill)
    }

    private fun drawSettings(canvas: Canvas, theme: ThemePalette) {
        val pale = ColorUtils.blendARGB(theme.accent, Color.WHITE, 0.83f)
        val track = ColorUtils.blendARGB(theme.accent, Color.BLACK, 0.49f)
        rounded(canvas, 42f, 44f, 118f, 48f, 2f,
            ColorUtils.setAlphaComponent(pale, 175))
        rounded(canvas, 43f, 69f, 117f, 76f, 3.5f, track)
        rounded(canvas, 43f, 69f, 93f, 76f, 3.5f, pale)
        circle(canvas, 93f, 72.5f, 10f, Color.WHITE)
        circle(canvas, 93f, 72.5f, 8f, pale)
        rounded(canvas, 43f, 104f, 117f, 111f, 3.5f, track)
        rounded(canvas, 43f, 104f, 66f, 111f, 3.5f, pale)
        circle(canvas, 66f, 107.5f, 10f, Color.WHITE)
        circle(canvas, 66f, 107.5f, 8f, pale)
    }

    private fun landscape(canvas: Canvas, rect: RectF, theme: ThemePalette) {
        val saved = canvas.save()
        val clip = Path().apply { addRoundRect(rect, 9f, 9f, Path.Direction.CW) }
        canvas.clipPath(clip)
        fill.color = Color.WHITE
        fill.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            ColorUtils.blendARGB(theme.accent, Color.WHITE, 0.86f),
            ColorUtils.blendARGB(theme.accent, Color.WHITE, 0.32f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, fill)
        fill.shader = null
        circle(canvas, rect.left + rect.width() * 0.77f,
            rect.top + rect.height() * 0.26f,
            rect.height() * 0.11f,
            ColorUtils.blendARGB(theme.accent, Color.WHITE, 0.93f))
        fun hill(height: Float, peak: Float, color: Int) {
            val y = rect.top + rect.height() * height
            val p = Path().apply {
                moveTo(rect.left, rect.bottom)
                lineTo(rect.left, y)
                cubicTo(rect.left + rect.width() * 0.16f, y - peak,
                    rect.left + rect.width() * 0.25f, y - peak,
                    rect.left + rect.width() * 0.42f, y)
                cubicTo(rect.left + rect.width() * 0.60f, y + peak * 0.52f,
                    rect.left + rect.width() * 0.80f, y - peak * 0.72f,
                    rect.right, y - peak * 0.12f)
                lineTo(rect.right, rect.bottom)
                close()
            }
            fill.color = color
            canvas.drawPath(p, fill)
        }
        hill(0.60f, rect.height() * 0.27f,
            ColorUtils.blendARGB(theme.accent, Color.WHITE, 0.40f))
        hill(0.81f, rect.height() * 0.19f,
            ColorUtils.blendARGB(theme.accent, Color.BLACK, 0.25f))
        hill(0.99f, rect.height() * 0.15f,
            ColorUtils.blendARGB(theme.accent, Color.BLACK, 0.51f))
        canvas.restoreToCount(saved)
    }

    private fun triangle(canvas: Canvas, x: Float, y: Float, size: Float) {
        val p = Path().apply {
            moveTo(x - size * 0.48f, y - size * 0.62f)
            quadTo(x - size * 0.48f, y - size * 0.75f,
                x - size * 0.20f, y - size * 0.60f)
            lineTo(x + size * 0.63f, y - size * 0.13f)
            quadTo(x + size * 0.86f, y, x + size * 0.63f, y + size * 0.13f)
            lineTo(x - size * 0.20f, y + size * 0.60f)
            quadTo(x - size * 0.48f, y + size * 0.75f,
                x - size * 0.48f, y + size * 0.62f)
            close()
        }
        fill.color = Color.WHITE
        fill.shader = null
        canvas.drawPath(p, fill)
    }

    private fun rounded(canvas: Canvas, l: Float, t: Float, r: Float, b: Float,
                        rad: Float, color: Int) {
        fill.shader = null
        fill.color = color
        canvas.drawRoundRect(l, t, r, b, rad, rad, fill)
    }

    private fun circle(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        fill.shader = null
        fill.color = color
        canvas.drawCircle(x, y, radius, fill)
    }

    private fun frameBetween(a: HeroFrame, b: HeroFrame, t: Float) = HeroFrame(
        mix(a.l, b.l, t), mix(a.t, b.t, t),
        mix(a.r, b.r, t), mix(a.b, b.b, t),
        mix(a.radius, b.radius, t)
    )
    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun smooth(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
