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
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
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

        // Final states never use transition geometry. This is intentionally
        // strict: a settled page renders one exact finished icon only.
        if (base == next || raw <= ENDPOINT_EPS) {
            drawExactIcon(canvas, base, p)
            return
        }
        if (raw >= 1f - ENDPOINT_EPS) {
            drawExactIcon(canvas, next, p)
            return
        }

        drawTransition(canvas, base, next, smooth(raw), p)
    }

    private fun drawExactIcon(
        canvas: Canvas,
        page: Int,
        palette: ThemePalette
    ) {
        val ambientPhase = ambient * PI.toFloat() * 2f
        val floatY = if (animationsEnabled) {
            sin(ambientPhase + page * 0.65f) * dp(0.55f)
        } else {
            0f
        }

        canvas.save()
        canvas.translate(0f, floatY)
        when (page) {
            0 -> drawFinalCloud(canvas, palette)
            1 -> drawFinalFeed(canvas, palette)
            2 -> drawFinalPlayer(canvas, palette)
            3 -> drawFinalCalendar(canvas, palette)
            else -> drawFinalSettings(canvas, palette)
        }
        canvas.restore()
    }

    // ---------------------------------------------------------------------
    // Exact final icons. These are deliberately independent from Scene.
    // ---------------------------------------------------------------------

    private fun drawFinalCloud(
        canvas: Canvas,
        palette: ThemePalette
    ) {
        val cloud = Path().apply {
            moveTo(px(31f), py(108f))
            cubicTo(px(24f), py(102f), px(23f), py(91f), px(28f), py(82f))
            cubicTo(px(33f), py(73f), px(42f), py(69f), px(51f), py(70f))
            cubicTo(px(55f), py(56f), px(68f), py(47f), px(82f), py(48f))
            cubicTo(px(96f), py(48f), px(107f), py(57f), px(111f), py(70f))
            cubicTo(px(124f), py(69f), px(136f), py(78f), px(137f), py(91f))
            cubicTo(px(139f), py(105f), px(128f), py(117f), px(113f), py(119f))
            lineTo(px(49f), py(119f))
            cubicTo(px(41f), py(119f), px(35f), py(115f), px(31f), py(108f))
            close()
        }

        canvas.save()
        canvas.translate(0f, dp(4f))
        fill.shader = null
        fill.color = Color.argb(52, 0, 0, 0)
        canvas.drawPath(cloud, fill)
        canvas.restore()

        fill.shader = LinearGradient(
            px(66f), py(48f), px(104f), py(121f),
            Color.rgb(241, 211, 255),
            ColorUtils.blendARGB(
                Color.rgb(111, 75, 235),
                palette.accent,
                0.18f
            ),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(cloud, fill)
        fill.shader = null

        stroke.strokeWidth = dp(0.8f)
        stroke.color = Color.argb(48, 255, 255, 255)
        val topHighlight = Path().apply {
            moveTo(px(45f), py(72f))
            cubicTo(px(53f), py(62f), px(64f), py(56f), px(77f), py(55f))
            cubicTo(px(91f), py(54f), px(101f), py(61f), px(105f), py(72f))
        }
        canvas.drawPath(topHighlight, stroke)

        drawPlayGlyph(
            canvas = canvas,
            x = 82f,
            y = 89f,
            size = 31f,
            alpha = 1f,
            palette = palette,
            dark = true
        )
    }

    private fun drawFinalFeed(
        canvas: Canvas,
        palette: ThemePalette
    ) {
        drawLogicalPanel(
            canvas, 82f, 56f, 98f, 48f, 14f, -8f,
            alpha = 0.34f, tone = 0.46f, shadow = 0.20f, palette = palette
        )
        drawLogicalPanel(
            canvas, 80f, 117f, 98f, 44f, 14f, 5f,
            alpha = 0.30f, tone = 0.34f, shadow = 0.14f, palette = palette
        )

        val cx = px(82f)
        val cy = py(86f)
        canvas.save()
        canvas.rotate(-4f, cx, cy)

        val main = logicalRect(82f, 86f, 116f, 62f)
        drawPanelRect(
            canvas, main, n(15f),
            alpha = 1f, tone = 0.86f, shadow = 0.68f, palette = palette
        )

        val preview = logicalRect(49f, 86f, 39f, 38f)
        drawArtworkRect(canvas, preview, n(9f), 1f)

        drawSolidRoundRect(
            canvas, logicalRect(103f, 76f, 43f, 7f), n(3.5f),
            Color.rgb(205, 170, 255), 0.96f
        )
        drawSolidRoundRect(
            canvas, logicalRect(97f, 94f, 31f, 6f), n(3f),
            Color.rgb(150, 111, 232), 0.92f
        )

        canvas.restore()
    }

    private fun drawFinalPlayer(
        canvas: Canvas,
        palette: ThemePalette
    ) {
        drawLogicalPanel(
            canvas, 70f, 81f, 118f, 82f, 18f, -6f,
            alpha = 0.28f, tone = 0.28f, shadow = 0.16f, palette = palette
        )

        val main = logicalRect(80f, 84f, 130f, 92f)
        drawPanelRect(
            canvas, main, n(18f),
            alpha = 1f, tone = 0.74f, shadow = 0.76f, palette = palette
        )

        val artwork = logicalRect(80f, 69f, 118f, 62f)
        drawArtworkRect(canvas, artwork, n(14f), 1f)

        drawPlayGlyph(
            canvas = canvas,
            x = 80f,
            y = 69f,
            size = 29f,
            alpha = 1f,
            palette = palette,
            dark = false
        )

        val bar = logicalRect(80f, 119f, 108f, 6f)
        drawSolidRoundRect(
            canvas, bar, n(3f),
            Color.rgb(84, 64, 139), 1f
        )
        val played = RectF(
            bar.left,
            bar.top,
            px(91f),
            bar.bottom
        )
        drawSolidRoundRect(
            canvas, played, n(3f),
            Color.rgb(220, 172, 255), 1f
        )
        drawStaticDot(
            canvas, 91f, 119f, 6.2f, palette
        )
    }

    private fun drawFinalCalendar(
        canvas: Canvas,
        palette: ThemePalette
    ) {
        val body = logicalRect(80f, 86f, 96f, 112f)
        drawPanelRect(
            canvas, body, n(20f),
            alpha = 1f, tone = 0.60f, shadow = 0.72f, palette = palette
        )

        val header = RectF(
            body.left,
            body.top,
            body.right,
            py(58f)
        )
        drawSolidRoundRect(
            canvas, header, n(18f),
            Color.rgb(86, 62, 145), 1f
        )
        // Flatten the lower corners of the header without creating a ghost layer.
        fill.color = Color.rgb(86, 62, 145)
        canvas.drawRect(
            header.left,
            py(49f),
            header.right,
            header.bottom,
            fill
        )

        drawVerticalBinding(canvas, 58f, 25f, palette)
        drawVerticalBinding(canvas, 102f, 25f, palette)

        drawFlameGlyph(
            canvas = canvas,
            x = 80f,
            y = 88f,
            size = 40f,
            alpha = 1f,
            palette = palette
        )
    }

    private fun drawFinalSettings(
        canvas: Canvas,
        palette: ThemePalette
    ) {
        val panel = logicalRect(80f, 82f, 100f, 100f)
        drawPanelRect(
            canvas, panel, n(22f),
            alpha = 1f, tone = 0.66f, shadow = 0.72f, palette = palette
        )

        val topTrack = logicalRect(80f, 65f, 68f, 9f)
        val bottomTrack = logicalRect(80f, 98f, 68f, 9f)
        drawSolidRoundRect(
            canvas, topTrack, n(4.5f),
            Color.rgb(204, 161, 255), 0.96f
        )
        drawSolidRoundRect(
            canvas, bottomTrack, n(4.5f),
            Color.rgb(180, 133, 248), 0.96f
        )

        drawStaticDot(canvas, 101f, 65f, 12f, palette)
        drawStaticDot(canvas, 62f, 98f, 12f, palette)
    }

    private fun drawVerticalBinding(
        canvas: Canvas,
        x: Float,
        y: Float,
        palette: ThemePalette
    ) {
        val rect = logicalRect(x, y, 11f, 27f)
        drawPanelRect(
            canvas, rect, n(5.5f),
            alpha = 1f, tone = 0.98f, shadow = 0.18f, palette = palette
        )
    }

    // ---------------------------------------------------------------------
    // Transition-only parametric geometry.
    // ---------------------------------------------------------------------

    private fun drawTransition(
        canvas: Canvas,
        from: Int,
        to: Int,
        t: Float,
        palette: ThemePalette
    ) {
        val ambientPhase = ambient * PI.toFloat() * 2f
        val floatY = if (animationsEnabled) {
            sin(ambientPhase + pageProgress * 0.65f) * dp(0.55f)
        } else {
            0f
        }

        canvas.save()
        canvas.translate(0f, floatY)
        val current = interpolateScene(scenes[from], scenes[to], t)
        drawScene(canvas, current, palette, from, to, t)
        canvas.restore()
    }

    private fun cloudScene(): Scene = Scene(
        main = box(80f, 101f, 116f, 48f, 24f, 0f, 1f, 0.96f, 0.52f),
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
        playX = 82f, playY = 89f, playSize = 31f, playAlpha = 1f,
        flameX = 80f, flameY = 86f, flameSize = 0f, flameAlpha = 0f,
        mountainAlpha = 0f, gridAlpha = 0f
    )

    private fun feedScene(): Scene = Scene(
        main = box(82f, 86f, 116f, 62f, 15f, -4f, 1f, 0.86f, 0.60f),
        back1 = box(82f, 56f, 98f, 48f, 14f, -8f, 0.34f, 0.46f, 0.20f),
        back2 = box(80f, 117f, 98f, 44f, 14f, 5f, 0.30f, 0.34f, 0.14f),
        back3 = hiddenBox(),
        preview = box(49f, 86f, 39f, 38f, 9f, -4f, 1f, 1f, 0.10f),
        line1 = box(103f, 76f, 43f, 7f, 3.5f, -4f, 0.96f, 0.95f, 0f),
        line2 = box(97f, 94f, 31f, 6f, 3f, -4f, 0.92f, 0.72f, 0f),
        timeline = hiddenBox(),
        timelineThumb = hiddenDot(),
        loop1 = hiddenBox(),
        loop2 = hiddenBox(),
        slider1 = hiddenBox(),
        slider2 = hiddenBox(),
        sliderThumb1 = hiddenDot(),
        sliderThumb2 = hiddenDot(),
        playX = 49f, playY = 86f, playSize = 7f, playAlpha = 0f,
        flameX = 80f, flameY = 85f, flameSize = 0f, flameAlpha = 0f,
        mountainAlpha = 1f, gridAlpha = 0f
    )

    private fun playerScene(): Scene = Scene(
        main = box(80f, 84f, 130f, 92f, 18f, 0f, 1f, 0.74f, 0.72f),
        back1 = box(70f, 81f, 118f, 82f, 18f, -6f, 0.28f, 0.28f, 0.16f),
        back2 = hiddenBox(),
        back3 = hiddenBox(),
        preview = box(80f, 69f, 118f, 62f, 14f, 0f, 1f, 0.95f, 0.04f),
        line1 = hiddenBox(),
        line2 = hiddenBox(),
        timeline = box(80f, 119f, 108f, 6f, 3f, 0f, 1f, 0.74f, 0f),
        timelineThumb = dot(91f, 119f, 6.2f, 1f, 1f),
        loop1 = hiddenBox(),
        loop2 = hiddenBox(),
        slider1 = hiddenBox(),
        slider2 = hiddenBox(),
        sliderThumb1 = hiddenDot(),
        sliderThumb2 = hiddenDot(),
        playX = 80f, playY = 69f, playSize = 29f, playAlpha = 1f,
        flameX = 80f, flameY = 84f, flameSize = 34f, flameAlpha = 0f,
        mountainAlpha = 1f, gridAlpha = 0f
    )

    private fun calendarScene(): Scene = Scene(
        main = box(80f, 86f, 96f, 112f, 20f, 0f, 1f, 0.60f, 0.72f),
        back1 = hiddenBox(),
        back2 = hiddenBox(),
        back3 = hiddenBox(),
        preview = box(80f, 45f, 96f, 28f, 16f, 0f, 1f, 0.62f, 0f),
        line1 = hiddenBox(),
        line2 = hiddenBox(),
        timeline = hiddenBox(),
        timelineThumb = hiddenDot(),
        loop1 = box(58f, 25f, 11f, 27f, 5.5f, 0f, 1f, 0.98f, 0.16f),
        loop2 = box(102f, 25f, 11f, 27f, 5.5f, 0f, 1f, 0.98f, 0.16f),
        slider1 = hiddenBox(),
        slider2 = hiddenBox(),
        sliderThumb1 = hiddenDot(),
        sliderThumb2 = hiddenDot(),
        playX = 80f, playY = 84f, playSize = 34f, playAlpha = 0f,
        flameX = 80f, flameY = 88f, flameSize = 40f, flameAlpha = 1f,
        mountainAlpha = 0f, gridAlpha = 0f
    )

    private fun settingsScene(): Scene = Scene(
        main = box(80f, 82f, 100f, 100f, 22f, 0f, 1f, 0.66f, 0.72f),
        back1 = hiddenBox(),
        back2 = hiddenBox(),
        back3 = hiddenBox(),
        preview = hiddenBox(),
        line1 = hiddenBox(),
        line2 = hiddenBox(),
        timeline = hiddenBox(),
        timelineThumb = hiddenDot(),
        loop1 = hiddenBox(),
        loop2 = hiddenBox(),
        slider1 = box(80f, 65f, 68f, 9f, 4.5f, 0f, 1f, 0.90f, 0f),
        slider2 = box(80f, 98f, 68f, 9f, 4.5f, 0f, 1f, 0.90f, 0f),
        sliderThumb1 = dot(101f, 65f, 12f, 1f, 1f),
        sliderThumb2 = dot(62f, 98f, 12f, 1f, 1f),
        playX = 80f, playY = 82f, playSize = 0f, playAlpha = 0f,
        flameX = 80f, flameY = 82f, flameSize = 0f, flameAlpha = 0f,
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
        drawGlassBox(canvas, scene.back3, palette)
        drawGlassBox(canvas, scene.back2, palette)
        drawGlassBox(canvas, scene.back1, palette)
        drawGlassBox(canvas, scene.main, palette)
        drawGlassBox(canvas, scene.preview, palette)
        drawPreviewArtwork(canvas, scene.preview, scene.mountainAlpha)
        drawGlassBox(canvas, scene.line1, palette)
        drawGlassBox(canvas, scene.line2, palette)
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
                scene.playSize, scene.playAlpha, palette,
                dark = transitionFrom == 0
            )
            drawFlameGlyph(
                canvas, scene.flameX, scene.flameY,
                scene.flameSize, scene.flameAlpha, palette
            )
        }
    }

    private fun drawGlassBox(
        canvas: Canvas,
        spec: Box,
        palette: ThemePalette
    ) {
        if (spec.alpha <= 0.002f || spec.w <= 0f || spec.h <= 0f) return
        val rect = mapRect(spec)
        canvas.save()
        canvas.rotate(spec.rotation, rect.centerX(), rect.centerY())

        drawPanelRect(
            canvas = canvas,
            rect = rect,
            radius = n(spec.radius),
            alpha = spec.alpha,
            tone = spec.tone,
            shadow = spec.shadow,
            palette = palette
        )
        canvas.restore()
    }

    private fun drawLogicalPanel(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        radius: Float,
        rotation: Float,
        alpha: Float,
        tone: Float,
        shadow: Float,
        palette: ThemePalette
    ) {
        val rect = logicalRect(cx, cy, w, h)
        canvas.save()
        canvas.rotate(rotation, rect.centerX(), rect.centerY())
        drawPanelRect(
            canvas, rect, n(radius),
            alpha, tone, shadow, palette
        )
        canvas.restore()
    }

    private fun drawPanelRect(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        alpha: Float,
        tone: Float,
        shadow: Float,
        palette: ThemePalette
    ) {
        if (alpha <= 0.002f) return

        if (shadow > 0f) {
            val shadowRect = RectF(rect)
            shadowRect.offset(0f, dp(3f))
            fill.shader = null
            fill.color = ColorUtils.setAlphaComponent(
                Color.BLACK,
                (58f * shadow * alpha).toInt().coerceIn(0, 58)
            )
            canvas.drawRoundRect(shadowRect, radius, radius, fill)
        }

        val baseDark = ColorUtils.blendARGB(
            Color.rgb(45, 34, 82),
            palette.surfaceAlt,
            0.08f
        )
        val accent = ColorUtils.blendARGB(
            Color.rgb(126, 84, 239),
            palette.accent,
            0.16f
        )
        val light = Color.rgb(230, 204, 255)
        val topColor = ColorUtils.blendARGB(
            baseDark, light, 0.22f + 0.58f * tone.coerceIn(0f, 1f)
        )
        val bottomColor = ColorUtils.blendARGB(
            baseDark, accent, 0.34f + 0.58f * tone.coerceIn(0f, 1f)
        )
        val a = (255f * alpha).toInt().coerceIn(0, 255)

        // Keep this global vertical range stable for the transition geometry.
        fill.shader = LinearGradient(
            width / 2f,
            height * 0.14f,
            width / 2f,
            height * 0.86f,
            ColorUtils.setAlphaComponent(topColor, a),
            ColorUtils.setAlphaComponent(bottomColor, a),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, fill)
        fill.shader = null

        stroke.strokeWidth = dp(0.7f)
        stroke.color = ColorUtils.setAlphaComponent(
            Color.WHITE,
            (32f * alpha).toInt().coerceIn(0, 32)
        )
        val inset = radius * 0.55f
        canvas.drawLine(
            rect.left + inset,
            rect.top + dp(0.8f),
            rect.right - inset,
            rect.top + dp(0.8f),
            stroke
        )
    }

    private fun drawSolidRoundRect(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        color: Int,
        alpha: Float
    ) {
        fill.shader = null
        fill.color = ColorUtils.setAlphaComponent(
            color,
            (255f * alpha).toInt().coerceIn(0, 255)
        )
        canvas.drawRoundRect(rect, radius, radius, fill)
    }

    private fun drawStaticDot(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        palette: ThemePalette
    ) {
        val cx = px(x)
        val cy = py(y)
        val r = n(radius)

        fill.shader = null
        fill.color = Color.argb(44, 0, 0, 0)
        canvas.drawCircle(cx, cy + dp(2f), r * 1.03f, fill)

        fill.shader = LinearGradient(
            cx - r, cy - r, cx + r, cy + r,
            Color.rgb(244, 220, 255),
            ColorUtils.blendARGB(
                Color.rgb(146, 92, 255),
                palette.accent,
                0.16f
            ),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, fill)
        fill.shader = null

        fill.color = Color.argb(76, 255, 255, 255)
        canvas.drawCircle(cx - r * 0.28f, cy - r * 0.30f, r * 0.20f, fill)
    }

    private fun drawDot(
        canvas: Canvas,
        spec: Dot,
        palette: ThemePalette
    ) {
        if (spec.alpha <= 0.002f || spec.radius <= 0f) return
        val cx = px(spec.cx)
        val cy = py(spec.cy)
        val r = n(spec.radius)
        val a = (255f * spec.alpha).toInt().coerceIn(0, 255)

        fill.shader = LinearGradient(
            cx - r, cy - r, cx + r, cy + r,
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
        canvas.drawCircle(cx, cy, r, fill)
        fill.shader = null
    }

    private fun drawArtworkRect(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        alpha: Float
    ) {
        if (alpha <= 0.002f) return
        val a = alpha.coerceIn(0f, 1f)

        canvas.save()
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.clipPath(path)

        fill.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            ColorUtils.setAlphaComponent(
                Color.rgb(235, 178, 255),
                (255f * a).toInt()
            ),
            ColorUtils.setAlphaComponent(
                Color.rgb(73, 52, 151),
                (255f * a).toInt()
            ),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, fill)
        fill.shader = null

        drawMountainLayer(
            canvas, rect, 0.52f,
            ColorUtils.setAlphaComponent(
                Color.rgb(154, 105, 211),
                (230f * a).toInt()
            )
        )
        drawMountainLayer(
            canvas, rect, 0.67f,
            ColorUtils.setAlphaComponent(
                Color.rgb(99, 68, 177),
                (245f * a).toInt()
            )
        )
        drawMountainLayer(
            canvas, rect, 0.79f,
            ColorUtils.setAlphaComponent(
                Color.rgb(56, 42, 111),
                (255f * a).toInt()
            )
        )
        canvas.restore()
    }

    private fun drawPreviewArtwork(
        canvas: Canvas,
        preview: Box,
        alpha: Float
    ) {
        if (alpha <= 0.002f || preview.alpha <= 0.002f) return
        val rect = mapRect(preview)
        canvas.save()
        canvas.rotate(preview.rotation, rect.centerX(), rect.centerY())
        drawArtworkRect(
            canvas,
            rect,
            n(preview.radius),
            alpha * preview.alpha
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
        path.cubicTo(
            rect.left + rect.width() * 0.08f,
            y - rect.height() * 0.05f,
            rect.left + rect.width() * 0.13f,
            y - rect.height() * 0.18f,
            rect.left + rect.width() * 0.22f,
            y - rect.height() * 0.20f
        )
        path.cubicTo(
            rect.left + rect.width() * 0.31f,
            y - rect.height() * 0.16f,
            rect.left + rect.width() * 0.36f,
            y - rect.height() * 0.06f,
            rect.left + rect.width() * 0.44f,
            y - rect.height() * 0.08f
        )
        path.cubicTo(
            rect.left + rect.width() * 0.54f,
            y - rect.height() * 0.23f,
            rect.left + rect.width() * 0.61f,
            y - rect.height() * 0.26f,
            rect.left + rect.width() * 0.69f,
            y - rect.height() * 0.14f
        )
        path.cubicTo(
            rect.left + rect.width() * 0.77f,
            y - rect.height() * 0.05f,
            rect.left + rect.width() * 0.84f,
            y - rect.height() * 0.16f,
            rect.right,
            y - rect.height() * 0.10f
        )
        path.lineTo(rect.right, rect.bottom)
        path.close()
        fill.shader = null
        fill.color = color
        canvas.drawPath(path, fill)
    }

    private fun drawPlayGlyph(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        alpha: Float,
        palette: ThemePalette,
        dark: Boolean = false
    ) {
        if (alpha <= 0.002f || size <= 0f) return
        val glyph = smoothClosedPath(roundedTrianglePoints(x, y, size))
        val a = (255f * alpha).toInt().coerceIn(0, 255)

        val top = if (dark) {
            Color.rgb(83, 59, 144)
        } else {
            Color.rgb(255, 239, 255)
        }
        val bottom = if (dark) {
            Color.rgb(43, 31, 82)
        } else {
            ColorUtils.blendARGB(
                Color.rgb(220, 188, 255),
                palette.accent,
                0.08f
            )
        }

        fill.shader = LinearGradient(
            px(x - size * 0.45f),
            py(y - size * 0.55f),
            px(x + size * 0.55f),
            py(y + size * 0.55f),
            ColorUtils.setAlphaComponent(top, a),
            ColorUtils.setAlphaComponent(bottom, a),
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
        val glyph = smoothClosedPath(flamePoints(x, y, size))
        val a = (255f * alpha).toInt().coerceIn(0, 255)

        fill.shader = LinearGradient(
            px(x - size * 0.30f),
            py(y - size * 0.70f),
            px(x + size * 0.34f),
            py(y + size * 0.70f),
            ColorUtils.setAlphaComponent(Color.rgb(248, 228, 255), a),
            ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(
                    Color.rgb(175, 116, 255),
                    palette.accent,
                    0.10f
                ),
                a
            ),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(glyph, fill)
        fill.shader = null

        val inner = flamePoints(
            x + size * 0.02f,
            y + size * 0.18f,
            size * 0.37f
        )
        fill.color = ColorUtils.setAlphaComponent(
            Color.rgb(53, 37, 101),
            (242f * alpha).toInt().coerceIn(0, 242)
        )
        canvas.drawPath(smoothClosedPath(inner), fill)
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
            px(80f), py(52f), px(80f), py(116f),
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
        canvas.drawPath(smoothClosedPath(points), fill)
        fill.shader = null

        if (tt > 0.60f) {
            val innerAlpha = smoothStep(0.60f, 1f, tt)
            val inner = flamePoints(
                x,
                y + size * 0.15f,
                size * 0.37f * innerAlpha
            )
            fill.color = ColorUtils.setAlphaComponent(
                Color.rgb(53, 37, 101),
                (230f * innerAlpha).toInt().coerceIn(0, 230)
            )
            canvas.drawPath(smoothClosedPath(inner), fill)
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

        val firstMid = midpoint(points.last(), points.first())
        result.moveTo(px(firstMid.x), py(firstMid.y))

        for (i in points.indices) {
            val current = points[i]
            val next = points[(i + 1) % points.size]
            val mid = midpoint(current, next)
            result.quadTo(
                px(current.x), py(current.y),
                px(mid.x), py(mid.y)
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

    private fun logicalRect(
        cx: Float,
        cy: Float,
        w: Float,
        h: Float
    ): RectF {
        val centerX = px(cx)
        val centerY = py(cy)
        val width = n(w)
        val height = n(h)
        return RectF(
            centerX - width / 2f,
            centerY - height / 2f,
            centerX + width / 2f,
            centerY + height / 2f
        )
    }

    private fun mapRect(spec: Box): RectF =
        logicalRect(spec.cx, spec.cy, spec.w, spec.h)

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

    private companion object {
        const val ENDPOINT_EPS = 0.001f
    }
}
