package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

class SohrIntroMorphView(context: Context) : View(context) {
    private data class Box(
        val cx: Float, val cy: Float, val w: Float, val h: Float,
        val radius: Float, val rotation: Float, val alpha: Float,
        val tone: Float, val shadow: Float
    )

    private data class Dot(
        val cx: Float, val cy: Float, val radius: Float,
        val alpha: Float, val tone: Float
    )

    private data class Scene(
        val main: Box,
        val back1: Box,
        val back2: Box,
        val back3: Box,
        val preview: Box,
        val line1: Box,
        val line2: Box,
        val timeline: Box,
        val timelineThumb: Dot,
        val loop1: Box,
        val loop2: Box,
        val slider1: Box,
        val slider2: Box,
        val sliderThumb1: Dot,
        val sliderThumb2: Dot,
        val playX: Float,
        val playY: Float,
        val playSize: Float,
        val playAlpha: Float,
        val flameX: Float,
        val flameY: Float,
        val flameSize: Float,
        val flameAlpha: Float,
        val mountainAlpha: Float,
        val gridAlpha: Float
    )

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val path = Path()

    private var palette: ThemePalette? = null
    private var pageProgress = 0f
    private var ambient = 0f
    private var animationsEnabled = true

    private val scenes: List<Scene> by lazy {
        listOf(
            cloudScene(),
            feedScene(),
            playerScene(),
            calendarScene(),
            settingsScene()
        )
    }

    private val ambientAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 5_600L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            ambient = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        ambientAnimator.start()
    }

    fun setPalette(value: ThemePalette) {
        palette = value
        invalidate()
    }

    fun setAnimationsEnabled(value: Boolean) {
        animationsEnabled = value
        if (value) {
            if (!ambientAnimator.isRunning) ambientAnimator.start()
        } else {
            ambientAnimator.cancel()
            ambient = 0f
        }
        invalidate()
    }

    fun setPageProgress(value: Float) {
        pageProgress = value.coerceIn(0f, 4f)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animationsEnabled && !ambientAnimator.isRunning) {
            ambientAnimator.start()
        }
    }

    override fun onDetachedFromWindow() {
        ambientAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = palette ?: return

        val base = floor(pageProgress).toInt().coerceIn(0, 4)
        val next = (base + 1).coerceAtMost(4)
        val raw = (pageProgress - base).coerceIn(0f, 1f)
        val t = smooth(raw)

        if (base == next) {
            drawScene(canvas, scenes[base], p, base, base, 0f)
            return
        }

        val current = interpolateScene(scenes[base], scenes[next], t)
        drawScene(canvas, current, p, base, next, t)
    }

    private fun cloudScene(): Scene = Scene(
        main = box(80f, 101f, 116f, 48f, 24f, 0f, 1f, 0.96f, 0.72f),
        back1 = box(83f, 68f, 74f, 74f, 37f, 0f, 1f, 0.96f, 0f),
        back2 = box(45f, 88f, 62f, 62f, 31f, 0f, 1f, 0.96f, 0f),
        back3 = box(119f, 91f, 58f, 58f, 29f, 0f, 1f, 0.96f, 0f),
        preview = hiddenBox(),
        line1 = hiddenBox(),
        line2 = hiddenBox(),
        timeline = hiddenBox(),
        timelineThumb = hiddenDot(),
        loop1 = hiddenBox(),
        loop2 = hiddenBox(),
        slider1 = hiddenBox(),
        slider2 = hiddenBox(),
        sliderThumb1 = hiddenDot(),
        sliderThumb2 = hiddenDot(),
        playX = 81f, playY = 87f, playSize = 31f, playAlpha = 1f,
        flameX = 80f, flameY = 86f, flameSize = 0f, flameAlpha = 0f,
        mountainAlpha = 0f, gridAlpha = 0f
    )

    private fun feedScene(): Scene = Scene(
        main = box(82f, 87f, 112f, 60f, 15f, -4f, 1f, 0.90f, 0.68f),
        back1 = box(83f, 57f, 96f, 49f, 15f, -9f, 0.98f, 0.50f, 0.46f),
        back2 = box(80f, 117f, 96f, 42f, 14f, 5f, 0.96f, 0.40f, 0.34f),
        back3 = hiddenBox(),
        preview = box(49f, 87f, 37f, 36f, 9f, -4f, 1f, 1f, 0.12f),
        line1 = box(101f, 77f, 39f, 7f, 3.5f, -4f, 1f, 0.94f, 0f),
        line2 = box(96f, 95f, 30f, 6f, 3f, -4f, 0.92f, 0.72f, 0f),
        timeline = hiddenBox(),
        timelineThumb = hiddenDot(),
        loop1 = hiddenBox(),
        loop2 = hiddenBox(),
        slider1 = hiddenBox(),
        slider2 = hiddenBox(),
        sliderThumb1 = hiddenDot(),
        sliderThumb2 = hiddenDot(),
        playX = 49f, playY = 87f, playSize = 7f, playAlpha = 0f,
        flameX = 80f, flameY = 85f, flameSize = 0f, flameAlpha = 0f,
        mountainAlpha = 0.85f, gridAlpha = 0f
    )

    private fun playerScene(): Scene = Scene(
        main = box(80f, 83f, 126f, 88f, 18f, 0f, 1f, 0.88f, 0.72f),
        back1 = box(69f, 78f, 116f, 80f, 18f, -7f, 0.92f, 0.38f, 0.32f),
        back2 = box(91f, 79f, 116f, 80f, 18f, 7f, 0.88f, 0.31f, 0.27f),
        back3 = hiddenBox(),
        preview = box(80f, 69f, 116f, 63f, 14f, 0f, 1f, 0.95f, 0.05f),
        line1 = hiddenBox(),
        line2 = hiddenBox(),
        timeline = box(80f, 119f, 108f, 7f, 3.5f, 0f, 1f, 0.74f, 0f),
        timelineThumb = dot(89f, 119f, 6f, 1f, 1f),
        loop1 = hiddenBox(),
        loop2 = hiddenBox(),
        slider1 = hiddenBox(),
        slider2 = hiddenBox(),
        sliderThumb1 = hiddenDot(),
        sliderThumb2 = hiddenDot(),
        playX = 80f, playY = 69f, playSize = 31f, playAlpha = 1f,
        flameX = 80f, flameY = 83f, flameSize = 34f, flameAlpha = 0f,
        mountainAlpha = 1f, gridAlpha = 0f
    )

    private fun calendarScene(): Scene = Scene(
        main = box(80f, 86f, 92f, 112f, 19f, 0f, 1f, 0.66f, 0.70f),
        back1 = hiddenBox(),
        back2 = hiddenBox(),
        back3 = hiddenBox(),
        preview = box(80f, 45f, 92f, 28f, 14f, 0f, 1f, 0.82f, 0f),
        line1 = hiddenBox(),
        line2 = hiddenBox(),
        timeline = hiddenBox(),
        timelineThumb = hiddenDot(),
        loop1 = box(58f, 24f, 11f, 26f, 5.5f, 0f, 1f, 0.98f, 0.15f),
        loop2 = box(102f, 24f, 11f, 26f, 5.5f, 0f, 1f, 0.98f, 0.15f),
        slider1 = hiddenBox(),
        slider2 = hiddenBox(),
        sliderThumb1 = dot(58f, 24f, 6f, 0f, 1f),
        sliderThumb2 = dot(102f, 24f, 6f, 0f, 1f),
        playX = 80f, playY = 84f, playSize = 34f, playAlpha = 0f,
        flameX = 80f, flameY = 87f, flameSize = 42f, flameAlpha = 1f,
        mountainAlpha = 0f, gridAlpha = 0f
    )

    private fun settingsScene(): Scene = Scene(
        main = box(80f, 82f, 96f, 96f, 21f, 0f, 1f, 0.68f, 0.70f),
        back1 = hiddenBox(),
        back2 = hiddenBox(),
        back3 = hiddenBox(),
        preview = hiddenBox(),
        line1 = hiddenBox(),
        line2 = hiddenBox(),
        timeline = hiddenBox(),
        timelineThumb = hiddenDot(),
        loop1 = box(98f, 65f, 16f, 16f, 8f, 0f, 0f, 1f, 0f),
        loop2 = box(62f, 97f, 16f, 16f, 8f, 0f, 0f, 1f, 0f),
        slider1 = box(80f, 65f, 66f, 10f, 5f, 0f, 1f, 0.90f, 0f),
        slider2 = box(80f, 97f, 66f, 10f, 5f, 0f, 1f, 0.90f, 0f),
        sliderThumb1 = dot(100f, 65f, 12f, 1f, 1f),
        sliderThumb2 = dot(62f, 97f, 12f, 1f, 1f),
        playX = 80f, playY = 82f, playSize = 0f, playAlpha = 0f,
        flameX = 80f, flameY = 82f, flameSize = 7f, flameAlpha = 0f,
        mountainAlpha = 0f, gridAlpha = 0f
    )

    private fun drawScene(
        canvas: Canvas,
        scene: Scene,
        palette: ThemePalette,
        transitionFrom: Int,
        transitionTo: Int,
        transitionT: Float
    ) {
        val ambientPhase = ambient * PI.toFloat() * 2f
        val floatY = if (animationsEnabled) {
            sin(ambientPhase + pageProgress * 0.65f) * dp(0.65f)
        } else {
            0f
        }

        canvas.save()
        canvas.translate(0f, floatY)

        drawGlassBox(canvas, scene.back3, palette)
        drawGlassBox(canvas, scene.back2, palette)
        drawGlassBox(canvas, scene.back1, palette)
        drawGlassBox(canvas, scene.main, palette)
        drawGlassBox(canvas, scene.preview, palette)
        drawPreviewArtwork(canvas, scene.preview, scene.mountainAlpha)
        drawGlassBox(canvas, scene.line1, palette)
        drawGlassBox(canvas, scene.line2, palette)
        drawCalendarGrid(canvas, scene, palette)
        drawGlassBox(canvas, scene.timeline, palette)
        drawDot(canvas, scene.timelineThumb, palette)
        drawGlassBox(canvas, scene.loop1, palette)
        drawGlassBox(canvas, scene.loop2, palette)
        drawGlassBox(canvas, scene.slider1, palette)
        drawGlassBox(canvas, scene.slider2, palette)
        drawDot(canvas, scene.sliderThumb1, palette)
        drawDot(canvas, scene.sliderThumb2, palette)

        if (transitionFrom == 2 && transitionTo == 3) {
            drawPlayToFlameMorph(
                canvas = canvas,
                t = transitionT,
                from = scenes[2],
                to = scenes[3],
                palette = palette
            )
        } else {
            drawPlayGlyph(
                canvas, scene.playX, scene.playY,
                scene.playSize, scene.playAlpha, palette
            )
            drawFlameGlyph(
                canvas, scene.flameX, scene.flameY,
                scene.flameSize, scene.flameAlpha, palette
            )
        }

        canvas.restore()
    }

    private fun drawGlassBox(
        canvas: Canvas,
        spec: Box,
        palette: ThemePalette
    ) {
        if (spec.alpha <= 0.002f || spec.w <= 0f || spec.h <= 0f) return

        val rect = mapRect(spec)
        val cx = rect.centerX()
        val cy = rect.centerY()

        val baseDark = ColorUtils.blendARGB(
            Color.rgb(68, 50, 121),
            palette.surfaceAlt,
            0.06f
        )
        val accent = ColorUtils.blendARGB(
            Color.rgb(130, 88, 249),
            palette.accent,
            0.18f
        )
        val light = Color.rgb(238, 218, 255)

        val tone = spec.tone.coerceIn(0f, 1f)
        val lift = 0.38f + tone * 0.62f
        val topColor = ColorUtils.blendARGB(baseDark, light, lift)
        val bottomColor = ColorUtils.blendARGB(
            baseDark,
            accent,
            0.52f + tone * 0.48f
        )
        val a = (255f * spec.alpha).toInt().coerceIn(0, 255)

        canvas.save()
        canvas.rotate(spec.rotation, cx, cy)

        if (spec.shadow > 0f) {
            val shadow = RectF(rect)
            shadow.offset(0f, dp(3f))
            fill.shader = null
            fill.color = ColorUtils.setAlphaComponent(
                Color.BLACK,
                (42f * spec.shadow * spec.alpha).toInt().coerceIn(0, 42)
            )
            canvas.drawRoundRect(
                shadow,
                n(spec.radius),
                n(spec.radius),
                fill
            )
        }

        // Global vertical gradient: overlapping cloud blobs share the exact
        // same color at the same Y, so they read as one solid cloud.
        fill.shader = LinearGradient(
            width / 2f,
            height * 0.14f,
            width / 2f,
            height * 0.86f,
            ColorUtils.setAlphaComponent(topColor, a),
            ColorUtils.setAlphaComponent(bottomColor, a),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            rect,
            n(spec.radius),
            n(spec.radius),
            fill
        )
        fill.shader = null

        // Tiny top highlight only; no full outline around every blob/card.
        stroke.strokeWidth = dp(0.65f)
        stroke.color = ColorUtils.setAlphaComponent(
            Color.WHITE,
            (18f * spec.alpha).toInt().coerceIn(0, 18)
        )
        canvas.drawLine(
            rect.left + n(spec.radius) * 0.62f,
            rect.top + dp(0.8f),
            rect.right - n(spec.radius) * 0.62f,
            rect.top + dp(0.8f),
            stroke
        )

        canvas.restore()
    }

    private fun drawDot(
        canvas: Canvas,
        spec: Dot,
        palette: ThemePalette
    ) {
        if (spec.alpha <= 0.002f || spec.radius <= 0f) return

        val x = px(spec.cx)
        val y = py(spec.cy)
        val r = n(spec.radius)
        val a = (255f * spec.alpha).toInt().coerceIn(0, 255)

        fill.shader = null
        fill.color = ColorUtils.setAlphaComponent(
            Color.BLACK,
            (30f * spec.alpha).toInt().coerceIn(0, 30)
        )
        canvas.drawCircle(x, y + dp(2f), r * 1.02f, fill)

        fill.shader = LinearGradient(
            x - r,
            y - r,
            x + r,
            y + r,
            ColorUtils.setAlphaComponent(Color.rgb(239, 220, 255), a),
            ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(
                    Color.rgb(148, 99, 255),
                    palette.accent,
                    0.16f
                ),
                a
            ),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, r, fill)
        fill.shader = null

        fill.color = ColorUtils.setAlphaComponent(
            Color.WHITE,
            (70f * spec.alpha).toInt().coerceIn(0, 70)
        )
        canvas.drawCircle(
            x - r * 0.28f,
            y - r * 0.30f,
            r * 0.22f,
            fill
        )
    }

    private fun drawPreviewArtwork(
        canvas: Canvas,
        preview: Box,
        alpha: Float
    ) {
        if (alpha <= 0.002f || preview.alpha <= 0.002f) return

        val rect = mapRect(preview)
        val a = alpha * preview.alpha

        canvas.save()
        canvas.rotate(
            preview.rotation,
            rect.centerX(),
            rect.centerY()
        )

        path.reset()
        path.addRoundRect(
            rect,
            n(preview.radius),
            n(preview.radius),
            Path.Direction.CW
        )
        canvas.clipPath(path)

        fill.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            ColorUtils.setAlphaComponent(
                Color.rgb(238, 188, 255),
                (220f * a).toInt().coerceIn(0, 220)
            ),
            ColorUtils.setAlphaComponent(
                Color.rgb(82, 61, 171),
                (245f * a).toInt().coerceIn(0, 245)
            ),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, fill)
        fill.shader = null

        drawMountainLayer(
            canvas, rect, 0.56f,
            ColorUtils.setAlphaComponent(
                Color.rgb(122, 90, 207),
                (190f * a).toInt().coerceIn(0, 190)
            )
        )
        drawMountainLayer(
            canvas, rect, 0.72f,
            ColorUtils.setAlphaComponent(
                Color.rgb(75, 55, 151),
                (220f * a).toInt().coerceIn(0, 220)
            )
        )
        canvas.restore()
    }

    private fun drawMountainLayer(
        canvas: Canvas,
        rect: RectF,
        horizon: Float,
        color: Int
    ) {
        val y = rect.top + rect.height() * horizon

        path.reset()
        path.moveTo(rect.left, rect.bottom)
        path.lineTo(rect.left, y)
        path.lineTo(
            rect.left + rect.width() * 0.18f,
            y - rect.height() * 0.18f
        )
        path.lineTo(
            rect.left + rect.width() * 0.32f,
            y - rect.height() * 0.06f
        )
        path.lineTo(
            rect.left + rect.width() * 0.47f,
            y - rect.height() * 0.27f
        )
        path.lineTo(
            rect.left + rect.width() * 0.64f,
            y - rect.height() * 0.08f
        )
        path.lineTo(
            rect.left + rect.width() * 0.80f,
            y - rect.height() * 0.22f
        )
        path.lineTo(
            rect.right,
            y - rect.height() * 0.04f
        )
        path.lineTo(rect.right, rect.bottom)
        path.close()

        fill.color = color
        canvas.drawPath(path, fill)
    }

    private fun drawCalendarGrid(
        canvas: Canvas,
        scene: Scene,
        palette: ThemePalette
    ) {
        val alpha = scene.gridAlpha * scene.main.alpha
        if (alpha <= 0.002f) return

        val main = mapRect(scene.main)
        val startX = main.left + main.width() * 0.20f
        val startY = main.top + main.height() * 0.44f
        val cell = main.width() * 0.12f
        val gap = main.width() * 0.08f

        fill.color = ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(
                palette.accent,
                Color.WHITE,
                0.18f
            ),
            (120f * alpha).toInt().coerceIn(0, 120)
        )

        for (row in 0 until 2) {
            for (col in 0 until 3) {
                val left = startX + col * (cell + gap)
                val top = startY + row * (cell + gap)
                canvas.drawRoundRect(
                    left,
                    top,
                    left + cell,
                    top + cell,
                    cell * 0.16f,
                    cell * 0.16f,
                    fill
                )
            }
        }
    }

    private fun drawPlayGlyph(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        alpha: Float,
        palette: ThemePalette
    ) {
        if (alpha <= 0.002f || size <= 0f) return

        val glyph = smoothClosedPath(
            roundedTrianglePoints(x, y, size)
        )

        val cloudWeight = (1f - pageProgress.coerceIn(0f, 1f))
        val top = ColorUtils.blendARGB(
            Color.WHITE,
            Color.rgb(83, 59, 144),
            cloudWeight
        )
        val bottom = ColorUtils.blendARGB(
            ColorUtils.blendARGB(
                Color.rgb(229, 218, 255),
                palette.accent,
                0.10f
            ),
            Color.rgb(43, 31, 82),
            cloudWeight
        )

        fill.shader = LinearGradient(
            px(x - size * 0.45f),
            py(y - size * 0.55f),
            px(x + size * 0.55f),
            py(y + size * 0.55f),
            ColorUtils.setAlphaComponent(
                top,
                (255f * alpha).toInt().coerceIn(0, 255)
            ),
            ColorUtils.setAlphaComponent(
                bottom,
                (255f * alpha).toInt().coerceIn(0, 255)
            ),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(glyph, fill)
        fill.shader = null
    }

    private fun drawFlameGlyph(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        alpha: Float,
        palette: ThemePalette
    ) {
        if (alpha <= 0.002f || size <= 0f) return

        val glyph = smoothClosedPath(
            flamePoints(x, y, size)
        )

        fill.shader = LinearGradient(
            px(x - size * 0.30f),
            py(y - size * 0.70f),
            px(x + size * 0.34f),
            py(y + size * 0.70f),
            ColorUtils.setAlphaComponent(
                Color.rgb(248, 228, 255),
                (255f * alpha).toInt().coerceIn(0, 255)
            ),
            ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(
                    Color.rgb(175, 116, 255),
                    palette.accent,
                    0.10f
                ),
                (255f * alpha).toInt().coerceIn(0, 255)
            ),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(glyph, fill)
        fill.shader = null

        val inner = flamePoints(
            x + size * 0.02f,
            y + size * 0.18f,
            size * 0.39f
        )
        fill.color = ColorUtils.setAlphaComponent(
            Color.rgb(53, 37, 101),
            (236f * alpha).toInt().coerceIn(0, 236)
        )
        canvas.drawPath(
            smoothClosedPath(inner),
            fill
        )
    }

    private fun drawPlayToFlameMorph(
        canvas: Canvas,
        t: Float,
        from: Scene,
        to: Scene,
        palette: ThemePalette
    ) {
        val tt = smooth(t)
        val x = lerp(from.playX, to.flameX, tt)
        val y = lerp(from.playY, to.flameY, tt)
        val size = lerp(from.playSize, to.flameSize, tt)

        val fromPoints = roundedTrianglePoints(x, y, size)
        val toPoints = flamePoints(x, y, size)

        val points = fromPoints.indices.map { i ->
            PointF(
                lerp(fromPoints[i].x, toPoints[i].x, tt),
                lerp(fromPoints[i].y, toPoints[i].y, tt)
            )
        }

        fill.shader = LinearGradient(
            px(80f),
            py(52f),
            px(80f),
            py(116f),
            ColorUtils.blendARGB(
                Color.WHITE,
                Color.rgb(232, 210, 255),
                tt
            ),
            ColorUtils.blendARGB(
                Color.rgb(220, 214, 255),
                palette.accent,
                0.32f + 0.30f * tt
            ),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(
            smoothClosedPath(points),
            fill
        )
        fill.shader = null

        if (tt > 0.58f) {
            val innerAlpha = smoothStep(0.58f, 1f, tt)
            val inner = flamePoints(
                x,
                y + size * 0.15f,
                size * 0.38f * innerAlpha
            )
            fill.color = ColorUtils.setAlphaComponent(
                Color.rgb(61, 43, 115),
                (205f * innerAlpha).toInt().coerceIn(0, 205)
            )
            canvas.drawPath(
                smoothClosedPath(inner),
                fill
            )
        }
    }

    private fun roundedTrianglePoints(
        x: Float,
        y: Float,
        size: Float
    ): List<PointF> {
        val s = size
        return listOf(
            PointF(x - s * 0.34f, y - s * 0.56f),
            PointF(x - s * 0.14f, y - s * 0.54f),
            PointF(x + s * 0.10f, y - s * 0.38f),
            PointF(x + s * 0.40f, y - s * 0.17f),
            PointF(x + s * 0.52f, y),
            PointF(x + s * 0.40f, y + s * 0.17f),
            PointF(x + s * 0.10f, y + s * 0.38f),
            PointF(x - s * 0.14f, y + s * 0.54f),
            PointF(x - s * 0.34f, y + s * 0.56f),
            PointF(x - s * 0.46f, y + s * 0.30f),
            PointF(x - s * 0.46f, y),
            PointF(x - s * 0.46f, y - s * 0.30f)
        )
    }

    private fun flamePoints(
        x: Float,
        y: Float,
        size: Float
    ): List<PointF> {
        val s = size
        return listOf(
            PointF(x + s * 0.02f, y - s * 0.63f),
            PointF(x + s * 0.25f, y - s * 0.40f),
            PointF(x + s * 0.23f, y - s * 0.12f),
            PointF(x + s * 0.46f, y - s * 0.27f),
            PointF(x + s * 0.56f, y + s * 0.03f),
            PointF(x + s * 0.46f, y + s * 0.38f),
            PointF(x + s * 0.18f, y + s * 0.62f),
            PointF(x - s * 0.02f, y + s * 0.46f),
            PointF(x - s * 0.22f, y + s * 0.62f),
            PointF(x - s * 0.50f, y + s * 0.34f),
            PointF(x - s * 0.55f, y - s * 0.03f),
            PointF(x - s * 0.25f, y - s * 0.37f)
        )
    }

    private fun smoothClosedPath(points: List<PointF>): Path {
        val result = Path()
        if (points.isEmpty()) return result

        fun midpoint(a: PointF, b: PointF): PointF =
            PointF(
                (a.x + b.x) * 0.5f,
                (a.y + b.y) * 0.5f
            )

        val firstMid = midpoint(
            points.last(),
            points.first()
        )
        result.moveTo(
            px(firstMid.x),
            py(firstMid.y)
        )

        for (i in points.indices) {
            val current = points[i]
            val next = points[(i + 1) % points.size]
            val mid = midpoint(current, next)
            result.quadTo(
                px(current.x),
                py(current.y),
                px(mid.x),
                py(mid.y)
            )
        }
        result.close()
        return result
    }

    private fun interpolateScene(
        a: Scene,
        b: Scene,
        t: Float
    ): Scene = Scene(
        main = mix(a.main, b.main, t),
        back1 = mix(a.back1, b.back1, t),
        back2 = mix(a.back2, b.back2, t),
        back3 = mix(a.back3, b.back3, t),
        preview = mix(a.preview, b.preview, t),
        line1 = mix(a.line1, b.line1, t),
        line2 = mix(a.line2, b.line2, t),
        timeline = mix(a.timeline, b.timeline, t),
        timelineThumb = mix(a.timelineThumb, b.timelineThumb, t),
        loop1 = mix(a.loop1, b.loop1, t),
        loop2 = mix(a.loop2, b.loop2, t),
        slider1 = mix(a.slider1, b.slider1, t),
        slider2 = mix(a.slider2, b.slider2, t),
        sliderThumb1 = mix(a.sliderThumb1, b.sliderThumb1, t),
        sliderThumb2 = mix(a.sliderThumb2, b.sliderThumb2, t),
        playX = lerp(a.playX, b.playX, t),
        playY = lerp(a.playY, b.playY, t),
        playSize = lerp(a.playSize, b.playSize, t),
        playAlpha = lerp(a.playAlpha, b.playAlpha, t),
        flameX = lerp(a.flameX, b.flameX, t),
        flameY = lerp(a.flameY, b.flameY, t),
        flameSize = lerp(a.flameSize, b.flameSize, t),
        flameAlpha = lerp(a.flameAlpha, b.flameAlpha, t),
        mountainAlpha = lerp(a.mountainAlpha, b.mountainAlpha, t),
        gridAlpha = lerp(a.gridAlpha, b.gridAlpha, t)
    )

    private fun mix(a: Box, b: Box, t: Float): Box =
        Box(
            lerp(a.cx, b.cx, t),
            lerp(a.cy, b.cy, t),
            lerp(a.w, b.w, t),
            lerp(a.h, b.h, t),
            lerp(a.radius, b.radius, t),
            lerp(a.rotation, b.rotation, t),
            lerp(a.alpha, b.alpha, t),
            lerp(a.tone, b.tone, t),
            lerp(a.shadow, b.shadow, t)
        )

    private fun mix(a: Dot, b: Dot, t: Float): Dot =
        Dot(
            lerp(a.cx, b.cx, t),
            lerp(a.cy, b.cy, t),
            lerp(a.radius, b.radius, t),
            lerp(a.alpha, b.alpha, t),
            lerp(a.tone, b.tone, t)
        )

    private fun box(
        cx: Float, cy: Float, w: Float, h: Float,
        radius: Float, rotation: Float, alpha: Float,
        tone: Float, shadow: Float
    ) = Box(cx, cy, w, h, radius, rotation, alpha, tone, shadow)

    private fun dot(
        cx: Float, cy: Float, radius: Float,
        alpha: Float, tone: Float
    ) = Dot(cx, cy, radius, alpha, tone)

    private fun hiddenBox() =
        box(80f, 80f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

    private fun hiddenDot() =
        dot(80f, 80f, 0f, 0f, 0f)

    private fun mapRect(spec: Box): RectF {
        val cx = px(spec.cx)
        val cy = py(spec.cy)
        val w = n(spec.w)
        val h = n(spec.h)
        return RectF(
            cx - w / 2f,
            cy - h / 2f,
            cx + w / 2f,
            cy + h / 2f
        )
    }

    private fun unit() =
        minOf(width, height).toFloat() / 184f

    private fun px(x: Float) =
        width / 2f + (x - 80f) * unit()

    private fun py(y: Float) =
        height / 2f + (y - 80f) * unit()

    private fun n(value: Float) =
        value * unit()

    private fun dp(value: Float) =
        value * resources.displayMetrics.density

    private fun lerp(from: Float, to: Float, t: Float) =
        from + (to - from) * t

    private fun smooth(value: Float) =
        value * value * (3f - 2f * value)

    private fun smoothStep(
        edge0: Float,
        edge1: Float,
        value: Float
    ): Float {
        if (edge0 == edge1) {
            return if (value >= edge1) 1f else 0f
        }
        val t = ((value - edge0) / (edge1 - edge0))
            .coerceIn(0f, 1f)
        return smooth(t)
    }
}
