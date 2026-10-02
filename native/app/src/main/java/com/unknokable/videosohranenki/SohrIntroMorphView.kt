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

    private val ambientAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 6_400L
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
        val colors = palette ?: return

        val base = floor(pageProgress).toInt().coerceIn(0, 4)
        val next = (base + 1).coerceAtMost(4)
        val raw = (pageProgress - base).coerceIn(0f, 1f)

        if (base == next || raw <= ENDPOINT_EPS) {
            drawExactIcon(canvas, base, colors)
            return
        }
        if (raw >= 1f - ENDPOINT_EPS) {
            drawExactIcon(canvas, next, colors)
            return
        }

        val t = smoother(raw)
        drawMorph(canvas, base, next, t, colors)
    }

    private fun drawExactIcon(
        canvas: Canvas,
        page: Int,
        colors: ThemePalette
    ) {
        val phase = ambient * PI.toFloat() * 2f
        val floatY = if (animationsEnabled) {
            sin(phase + page * 0.52f) * dp(0.42f)
        } else {
            0f
        }

        canvas.save()
        canvas.translate(0f, floatY)
        when (page) {
            0 -> drawFinalCloud(canvas, colors)
            1 -> drawFinalFeed(canvas, colors)
            2 -> drawFinalPlayer(canvas, colors)
            3 -> drawFinalCalendar(canvas, colors)
            else -> drawFinalSettings(canvas, colors)
        }
        canvas.restore()
    }

    // ---------------------------------------------------------------------
    // CLEAN FINAL STATIC STATES
    // These functions never use temporary morph geometry.
    // ---------------------------------------------------------------------

    private fun drawFinalCloud(
        canvas: Canvas,
        colors: ThemePalette
    ) {
        val cloud = finalCloudPath()

        drawPathShadow(canvas, cloud, 0f, dp(5f), 58)

        fill.shader = LinearGradient(
            px(48f), py(44f),
            px(112f), py(126f),
            intArrayOf(
                Color.rgb(250, 224, 255),
                Color.rgb(196, 151, 255),
                ColorUtils.blendARGB(
                    Color.rgb(103, 68, 244),
                    colors.accent,
                    0.12f
                )
            ),
            floatArrayOf(0f, 0.46f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(cloud, fill)
        fill.shader = null

        // Soft specular lighting only. No outline/stroke: this intentionally
        // avoids the thin construction arc visible in 6.9.25.
        canvas.save()
        canvas.clipPath(cloud)
        fill.shader = LinearGradient(
            px(52f), py(49f),
            px(82f), py(89f),
            Color.argb(118, 255, 255, 255),
            Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        canvas.drawOval(
            px(31f), py(39f),
            px(103f), py(87f),
            fill
        )
        fill.shader = null

        fill.shader = LinearGradient(
            px(80f), py(91f),
            px(80f), py(126f),
            Color.argb(0, 72, 45, 169),
            Color.argb(72, 62, 39, 171),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(
            px(18f), py(86f),
            px(142f), py(126f),
            fill
        )
        fill.shader = null
        canvas.restore()

        drawPlayGlyph(
            canvas = canvas,
            x = 81f,
            y = 88f,
            size = 35f,
            alpha = 1f,
            colors = colors,
            dark = true
        )
    }

    private fun drawFinalFeed(
        canvas: Canvas,
        colors: ThemePalette
    ) {
        drawPremiumPanel(
            canvas,
            cx = 82f, cy = 52f,
            w = 111f, h = 48f,
            radius = 14f, rotation = -9f,
            alpha = 0.42f, tone = 0.38f,
            colors = colors, shadow = 0.18f
        )
        drawPremiumPanel(
            canvas,
            cx = 79f, cy = 119f,
            w = 108f, h = 46f,
            radius = 14f, rotation = 5f,
            alpha = 0.38f, tone = 0.32f,
            colors = colors, shadow = 0.15f
        )

        val cx = px(82f)
        val cy = py(85f)
        canvas.save()
        canvas.rotate(-4f, cx, cy)

        val card = logicalRect(82f, 85f, 126f, 67f)
        drawPremiumPanelRect(
            canvas, card, n(16f),
            alpha = 1f, tone = 0.98f,
            colors = colors, shadow = 0.72f
        )

        val preview = logicalRect(48f, 85f, 43f, 42f)
        drawArtworkRect(
            canvas, preview, n(10f),
            alpha = 1f,
            compact = true
        )

        drawSolidRoundRect(
            canvas,
            logicalRect(103f, 74f, 49f, 8f),
            n(4f),
            Color.rgb(229, 194, 255),
            1f
        )
        drawSolidRoundRect(
            canvas,
            logicalRect(98f, 94f, 36f, 7f),
            n(3.5f),
            Color.rgb(184, 137, 250),
            1f
        )

        canvas.restore()
    }

    private fun drawFinalPlayer(
        canvas: Canvas,
        colors: ThemePalette
    ) {
        drawPremiumPanel(
            canvas,
            cx = 69f, cy = 81f,
            w = 123f, h = 86f,
            radius = 19f, rotation = -6f,
            alpha = 0.30f, tone = 0.25f,
            colors = colors, shadow = 0.14f
        )
        drawPremiumPanel(
            canvas,
            cx = 91f, cy = 82f,
            w = 121f, h = 84f,
            radius = 19f, rotation = 5f,
            alpha = 0.24f, tone = 0.20f,
            colors = colors, shadow = 0.10f
        )

        val panel = logicalRect(80f, 84f, 138f, 98f)
        drawPremiumPanelRect(
            canvas, panel, n(20f),
            alpha = 1f, tone = 0.78f,
            colors = colors, shadow = 0.78f
        )

        val artwork = logicalRect(80f, 67f, 126f, 66f)
        drawArtworkRect(
            canvas, artwork, n(15f),
            alpha = 1f,
            compact = false
        )

        drawPlayGlyph(
            canvas = canvas,
            x = 80f,
            y = 67f,
            size = 31f,
            alpha = 1f,
            colors = colors,
            dark = false
        )

        val baseTrack = logicalRect(80f, 121f, 116f, 7f)
        drawSolidRoundRect(
            canvas, baseTrack, n(3.5f),
            Color.rgb(82, 62, 136), 1f
        )
        drawSolidRoundRect(
            canvas,
            RectF(
                baseTrack.left,
                baseTrack.top,
                px(92f),
                baseTrack.bottom
            ),
            n(3.5f),
            Color.rgb(231, 182, 255), 1f
        )
        drawThumb(canvas, 92f, 121f, 6.8f, colors)
    }

    private fun drawFinalCalendar(
        canvas: Canvas,
        colors: ThemePalette
    ) {
        val body = logicalRect(80f, 87f, 108f, 120f)
        drawPremiumPanelRect(
            canvas, body, n(22f),
            alpha = 1f, tone = 0.66f,
            colors = colors, shadow = 0.76f
        )

        val header = RectF(
            body.left,
            body.top,
            body.right,
            py(59f)
        )
        fill.shader = LinearGradient(
            header.left, header.top,
            header.right, header.bottom,
            Color.rgb(150, 109, 223),
            Color.rgb(91, 67, 154),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            header, n(20f), n(20f), fill
        )
        fill.shader = null
        fill.color = Color.rgb(91, 67, 154)
        canvas.drawRect(
            header.left,
            py(50f),
            header.right,
            header.bottom,
            fill
        )

        drawBinding(canvas, 57f, 23f, colors)
        drawBinding(canvas, 103f, 23f, colors)

        drawFlameGlyph(
            canvas = canvas,
            x = 80f,
            y = 89f,
            size = 49f,
            alpha = 1f,
            colors = colors
        )
    }

    private fun drawFinalSettings(
        canvas: Canvas,
        colors: ThemePalette
    ) {
        val panel = logicalRect(80f, 82f, 110f, 110f)
        drawPremiumPanelRect(
            canvas, panel, n(24f),
            alpha = 1f, tone = 0.74f,
            colors = colors, shadow = 0.78f
        )

        drawSliderTrack(
            canvas,
            cx = 80f, cy = 64f,
            w = 78f, h = 10f,
            color = Color.rgb(220, 178, 255)
        )
        drawSliderTrack(
            canvas,
            cx = 80f, cy = 100f,
            w = 78f, h = 10f,
            color = Color.rgb(192, 143, 252)
        )

        drawThumb(canvas, 103f, 64f, 14f, colors)
        drawThumb(canvas, 59f, 100f, 14f, colors)
    }

    // ---------------------------------------------------------------------
    // SPECIALIZED MORPH TRANSITIONS
    // One composition is deformed through all five states.
    // ---------------------------------------------------------------------

    private fun drawMorph(
        canvas: Canvas,
        from: Int,
        to: Int,
        t: Float,
        colors: ThemePalette
    ) {
        val phase = ambient * PI.toFloat() * 2f
        val floatY = if (animationsEnabled) {
            sin(phase + pageProgress * 0.52f) * dp(0.38f)
        } else {
            0f
        }

        canvas.save()
        canvas.translate(0f, floatY)

        when {
            from == 0 && to == 1 ->
                drawCloudToFeed(canvas, t, colors)

            from == 1 && to == 2 ->
                drawFeedToPlayer(canvas, t, colors)

            from == 2 && to == 3 ->
                drawPlayerToCalendar(canvas, t, colors)

            from == 3 && to == 4 ->
                drawCalendarToSettings(canvas, t, colors)

            else -> drawExactIcon(canvas, from, colors)
        }

        canvas.restore()
    }

    private fun drawCloudToFeed(
        canvas: Canvas,
        t: Float,
        colors: ThemePalette
    ) {
        val frontPath = morphPoints(
            cloudMorphPoints(),
            roundedPanelPoints(
                cx = 82f, cy = 85f,
                w = 126f, h = 67f
            ),
            t
        )
        drawMorphPanel(
            canvas,
            smoothClosedPath(frontPath),
            tone = lerp(1f, 0.98f, t),
            alpha = 1f,
            colors = colors
        )

        val depth = smoothStep(0.16f, 0.88f, t)
        if (depth > 0f) {
            drawPremiumPanel(
                canvas,
                cx = lerp(80f, 82f, t),
                cy = lerp(84f, 52f, t),
                w = lerp(70f, 111f, t),
                h = lerp(40f, 48f, t),
                radius = lerp(20f, 14f, t),
                rotation = lerp(0f, -9f, t),
                alpha = 0.42f * depth,
                tone = 0.38f,
                colors = colors,
                shadow = 0.12f
            )
            drawPremiumPanel(
                canvas,
                cx = lerp(80f, 79f, t),
                cy = lerp(98f, 119f, t),
                w = lerp(72f, 108f, t),
                h = lerp(36f, 46f, t),
                radius = lerp(18f, 14f, t),
                rotation = lerp(0f, 5f, t),
                alpha = 0.38f * depth,
                tone = 0.32f,
                colors = colors,
                shadow = 0.10f
            )
        }

        val previewGrow = smoothStep(0.22f, 0.88f, t)
        if (previewGrow > 0f) {
            val preview = logicalRect(
                lerp(81f, 48f, previewGrow),
                lerp(88f, 85f, previewGrow),
                lerp(8f, 43f, previewGrow),
                lerp(8f, 42f, previewGrow)
            )
            drawArtworkRect(
                canvas,
                preview,
                n(lerp(4f, 10f, previewGrow)),
                previewGrow,
                compact = true
            )
        }

        val lineAlpha = smoothStep(0.38f, 0.92f, t)
        if (lineAlpha > 0f) {
            drawSolidRoundRect(
                canvas,
                logicalRect(
                    lerp(85f, 103f, t),
                    lerp(82f, 74f, t),
                    lerp(8f, 49f, t),
                    lerp(5f, 8f, t)
                ),
                n(4f),
                Color.rgb(229, 194, 255),
                lineAlpha
            )
            drawSolidRoundRect(
                canvas,
                logicalRect(
                    lerp(84f, 98f, t),
                    lerp(94f, 94f, t),
                    lerp(5f, 36f, t),
                    lerp(4f, 7f, t)
                ),
                n(3.5f),
                Color.rgb(184, 137, 250),
                lineAlpha
            )
        }

        drawPlayGlyph(
            canvas,
            x = lerp(81f, 48f, t),
            y = lerp(88f, 85f, t),
            size = lerp(35f, 8f, t),
            alpha = 1f - smoothStep(0.48f, 0.92f, t),
            colors = colors,
            dark = true
        )
    }

    private fun drawFeedToPlayer(
        canvas: Canvas,
        t: Float,
        colors: ThemePalette
    ) {
        drawPremiumPanel(
            canvas,
            cx = lerp(82f, 69f, t),
            cy = lerp(52f, 81f, t),
            w = lerp(111f, 123f, t),
            h = lerp(48f, 86f, t),
            radius = lerp(14f, 19f, t),
            rotation = lerp(-9f, -6f, t),
            alpha = lerp(0.42f, 0.30f, t),
            tone = lerp(0.38f, 0.25f, t),
            colors = colors,
            shadow = 0.14f
        )
        drawPremiumPanel(
            canvas,
            cx = lerp(79f, 91f, t),
            cy = lerp(119f, 82f, t),
            w = lerp(108f, 121f, t),
            h = lerp(46f, 84f, t),
            radius = lerp(14f, 19f, t),
            rotation = lerp(5f, 5f, t),
            alpha = lerp(0.38f, 0.24f, t),
            tone = lerp(0.32f, 0.20f, t),
            colors = colors,
            shadow = 0.10f
        )

        val mainRect = logicalRect(
            lerp(82f, 80f, t),
            lerp(85f, 84f, t),
            lerp(126f, 138f, t),
            lerp(67f, 98f, t)
        )
        canvas.save()
        canvas.rotate(
            lerp(-4f, 0f, t),
            mainRect.centerX(),
            mainRect.centerY()
        )
        drawPremiumPanelRect(
            canvas,
            mainRect,
            n(lerp(16f, 20f, t)),
            alpha = 1f,
            tone = lerp(0.98f, 0.78f, t),
            colors = colors,
            shadow = 0.74f
        )
        canvas.restore()

        val previewT = smoother(t)
        val preview = logicalRect(
            lerp(48f, 80f, previewT),
            lerp(85f, 67f, previewT),
            lerp(43f, 126f, previewT),
            lerp(42f, 66f, previewT)
        )
        drawArtworkRect(
            canvas,
            preview,
            n(lerp(10f, 15f, previewT)),
            1f,
            compact = t < 0.45f
        )

        val textFade = 1f - smoothStep(0.18f, 0.72f, t)
        if (textFade > 0f) {
            drawSolidRoundRect(
                canvas,
                logicalRect(
                    lerp(103f, 101f, t),
                    lerp(74f, 101f, t),
                    lerp(49f, 28f, t),
                    lerp(8f, 5f, t)
                ),
                n(4f),
                Color.rgb(229, 194, 255),
                textFade
            )
            drawSolidRoundRect(
                canvas,
                logicalRect(
                    lerp(98f, 92f, t),
                    lerp(94f, 108f, t),
                    lerp(36f, 24f, t),
                    lerp(7f, 5f, t)
                ),
                n(3.5f),
                Color.rgb(184, 137, 250),
                textFade
            )
        }

        val playerAlpha = smoothStep(0.28f, 0.86f, t)
        drawPlayGlyph(
            canvas,
            x = lerp(48f, 80f, t),
            y = lerp(85f, 67f, t),
            size = lerp(8f, 31f, t),
            alpha = playerAlpha,
            colors = colors,
            dark = false
        )

        if (playerAlpha > 0f) {
            val track = logicalRect(
                80f,
                lerp(102f, 121f, t),
                lerp(42f, 116f, t),
                lerp(5f, 7f, t)
            )
            drawSolidRoundRect(
                canvas,
                track,
                n(3.5f),
                Color.rgb(82, 62, 136),
                playerAlpha
            )
            drawSolidRoundRect(
                canvas,
                RectF(
                    track.left,
                    track.top,
                    lerp(track.left, px(92f), t),
                    track.bottom
                ),
                n(3.5f),
                Color.rgb(231, 182, 255),
                playerAlpha
            )
            drawThumb(
                canvas,
                lerp(70f, 92f, t),
                lerp(102f, 121f, t),
                lerp(2f, 6.8f, t),
                colors,
                playerAlpha
            )
        }
    }

    private fun drawPlayerToCalendar(
        canvas: Canvas,
        t: Float,
        colors: ThemePalette
    ) {
        val backAlpha = (1f - smoothStep(0.10f, 0.62f, t))
        if (backAlpha > 0f) {
            drawPremiumPanel(
                canvas,
                cx = lerp(69f, 80f, t),
                cy = lerp(81f, 86f, t),
                w = lerp(123f, 108f, t),
                h = lerp(86f, 112f, t),
                radius = lerp(19f, 22f, t),
                rotation = lerp(-6f, 0f, t),
                alpha = 0.30f * backAlpha,
                tone = 0.25f,
                colors = colors,
                shadow = 0.10f
            )
        }

        val panel = logicalRect(
            80f,
            lerp(84f, 87f, t),
            lerp(138f, 108f, t),
            lerp(98f, 120f, t)
        )
        drawPremiumPanelRect(
            canvas,
            panel,
            n(lerp(20f, 22f, t)),
            alpha = 1f,
            tone = lerp(0.78f, 0.66f, t),
            colors = colors,
            shadow = 0.76f
        )

        val artAlpha = 1f - smoothStep(0.18f, 0.68f, t)
        if (artAlpha > 0f) {
            val artwork = logicalRect(
                80f,
                lerp(67f, 54f, t),
                lerp(126f, 104f, t),
                lerp(66f, 31f, t)
            )
            drawArtworkRect(
                canvas,
                artwork,
                n(lerp(15f, 18f, t)),
                artAlpha,
                compact = false
            )
        }

        val headerAlpha = smoothStep(0.42f, 0.88f, t)
        if (headerAlpha > 0f) {
            val header = logicalRect(
                80f,
                lerp(60f, 45f, t),
                lerp(95f, 108f, t),
                lerp(12f, 30f, t)
            )
            drawPremiumPanelRect(
                canvas,
                header,
                n(lerp(6f, 18f, t)),
                headerAlpha,
                0.78f,
                colors,
                0f
            )
        }

        val trackAlpha = 1f - smoothStep(0.16f, 0.70f, t)
        if (trackAlpha > 0f) {
            val track = logicalRect(
                80f,
                lerp(121f, 111f, t),
                lerp(116f, 70f, t),
                lerp(7f, 5f, t)
            )
            drawSolidRoundRect(
                canvas,
                track,
                n(3.5f),
                Color.rgb(231, 182, 255),
                trackAlpha
            )
            drawThumb(
                canvas,
                lerp(92f, 80f, t),
                lerp(121f, 111f, t),
                lerp(6.8f, 2f, t),
                colors,
                trackAlpha
            )
        }

        drawPlayToFlameMorph(
            canvas = canvas,
            t = t,
            fromX = 80f,
            fromY = 67f,
            fromSize = 31f,
            toX = 80f,
            toY = 89f,
            toSize = 49f,
            colors = colors
        )

        val bind = smoothStep(0.40f, 0.94f, t)
        if (bind > 0f) {
            drawBindingMorph(
                canvas,
                x = 57f,
                y = lerp(45f, 23f, bind),
                height = lerp(3f, 28f, bind),
                alpha = bind,
                colors = colors
            )
            drawBindingMorph(
                canvas,
                x = 103f,
                y = lerp(45f, 23f, bind),
                height = lerp(3f, 28f, bind),
                alpha = bind,
                colors = colors
            )
        }
    }

    private fun drawCalendarToSettings(
        canvas: Canvas,
        t: Float,
        colors: ThemePalette
    ) {
        val panel = logicalRect(
            80f,
            lerp(87f, 82f, t),
            lerp(108f, 110f, t),
            lerp(120f, 110f, t)
        )
        drawPremiumPanelRect(
            canvas,
            panel,
            n(lerp(22f, 24f, t)),
            alpha = 1f,
            tone = lerp(0.66f, 0.74f, t),
            colors = colors,
            shadow = 0.78f
        )

        val header = logicalRect(
            80f,
            lerp(45f, 64f, t),
            lerp(108f, 78f, t),
            lerp(30f, 10f, t)
        )
        drawPremiumPanelRect(
            canvas,
            header,
            n(lerp(18f, 5f, t)),
            alpha = 1f,
            tone = lerp(0.78f, 1f, t),
            colors = colors,
            shadow = 0f
        )

        val flameAlpha = 1f - smoothStep(0.12f, 0.68f, t)
        if (flameAlpha > 0f) {
            drawFlameGlyph(
                canvas,
                x = 80f,
                y = lerp(89f, 88f, t),
                size = lerp(49f, 18f, t),
                alpha = flameAlpha,
                colors = colors
            )
        }

        val secondTrackAlpha = smoothStep(0.28f, 0.82f, t)
        if (secondTrackAlpha > 0f) {
            drawSliderTrack(
                canvas,
                cx = 80f,
                cy = lerp(91f, 100f, t),
                w = lerp(20f, 78f, t),
                h = lerp(5f, 10f, t),
                color = Color.rgb(192, 143, 252),
                alpha = secondTrackAlpha
            )
        }

        val thumbsT = smoothStep(0.18f, 0.94f, t)
        drawBindingToThumb(
            canvas,
            fromX = 57f,
            fromY = 23f,
            toX = 103f,
            toY = 64f,
            t = thumbsT,
            colors = colors
        )
        drawBindingToThumb(
            canvas,
            fromX = 103f,
            fromY = 23f,
            toX = 59f,
            toY = 100f,
            t = thumbsT,
            colors = colors
        )
    }

    // ---------------------------------------------------------------------
    // PREMIUM DRAWING PRIMITIVES
    // ---------------------------------------------------------------------

    private fun finalCloudPath(): Path = Path().apply {
        moveTo(px(26f), py(111f))
        cubicTo(px(18f), py(103f), px(18f), py(90f), px(24f), py(80f))
        cubicTo(px(30f), py(70f), px(41f), py(66f), px(52f), py(68f))
        cubicTo(px(56f), py(53f), px(69f), py(43f), px(83f), py(44f))
        cubicTo(px(98f), py(44f), px(109f), py(54f), px(113f), py(68f))
        cubicTo(px(126f), py(67f), px(138f), py(76f), px(141f), py(89f))
        cubicTo(px(145f), py(105f), px(133f), py(120f), px(116f), py(122f))
        lineTo(px(47f), py(122f))
        cubicTo(px(38f), py(122f), px(31f), py(118f), px(26f), py(111f))
        close()
    }

    private fun cloudMorphPoints(): List<PointF> = listOf(
        PointF(24f, 107f),
        PointF(22f, 87f),
        PointF(38f, 69f),
        PointF(54f, 69f),
        PointF(66f, 47f),
        PointF(89f, 43f),
        PointF(111f, 68f),
        PointF(130f, 68f),
        PointF(142f, 88f),
        PointF(137f, 111f),
        PointF(113f, 122f),
        PointF(47f, 122f)
    )

    private fun roundedPanelPoints(
        cx: Float,
        cy: Float,
        w: Float,
        h: Float
    ): List<PointF> {
        val l = cx - w / 2f
        val r = cx + w / 2f
        val t = cy - h / 2f
        val b = cy + h / 2f
        val k = minOf(w, h) * 0.18f
        return listOf(
            PointF(l + k, t),
            PointF(cx, t),
            PointF(r - k, t),
            PointF(r, t + k),
            PointF(r, cy),
            PointF(r, b - k),
            PointF(r - k, b),
            PointF(cx, b),
            PointF(l + k, b),
            PointF(l, b - k),
            PointF(l, cy),
            PointF(l, t + k)
        )
    }

    private fun drawPremiumPanel(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        radius: Float,
        rotation: Float,
        alpha: Float,
        tone: Float,
        colors: ThemePalette,
        shadow: Float
    ) {
        val rect = logicalRect(cx, cy, w, h)
        canvas.save()
        canvas.rotate(rotation, rect.centerX(), rect.centerY())
        drawPremiumPanelRect(
            canvas, rect, n(radius),
            alpha, tone, colors, shadow
        )
        canvas.restore()
    }

    private fun drawPremiumPanelRect(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        alpha: Float,
        tone: Float,
        colors: ThemePalette,
        shadow: Float
    ) {
        if (alpha <= 0.002f) return

        if (shadow > 0f) {
            val sr = RectF(rect)
            sr.offset(0f, dp(4f))
            fill.shader = null
            fill.color = ColorUtils.setAlphaComponent(
                Color.BLACK,
                (66f * shadow * alpha).toInt().coerceIn(0, 66)
            )
            canvas.drawRoundRect(sr, radius, radius, fill)
        }

        val dark = Color.rgb(55, 41, 99)
        val purple = ColorUtils.blendARGB(
            Color.rgb(137, 91, 244),
            colors.accent,
            0.14f
        )
        val lavender = Color.rgb(240, 215, 255)
        val tt = tone.coerceIn(0f, 1f)
        val top = ColorUtils.blendARGB(
            dark, lavender, 0.26f + tt * 0.60f
        )
        val bottom = ColorUtils.blendARGB(
            dark, purple, 0.42f + tt * 0.50f
        )
        val a = (255f * alpha).toInt().coerceIn(0, 255)

        fill.shader = LinearGradient(
            rect.left, rect.top,
            rect.right, rect.bottom,
            intArrayOf(
                ColorUtils.setAlphaComponent(top, a),
                ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(top, purple, 0.38f), a
                ),
                ColorUtils.setAlphaComponent(bottom, a)
            ),
            floatArrayOf(0f, 0.50f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, fill)
        fill.shader = null

        canvas.save()
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.clipPath(path)

        fill.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.left,
            rect.top + rect.height() * 0.48f,
            ColorUtils.setAlphaComponent(
                Color.WHITE,
                (54f * alpha).toInt().coerceIn(0, 54)
            ),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, fill)
        fill.shader = null
        canvas.restore()
    }

    private fun drawMorphPanel(
        canvas: Canvas,
        panel: Path,
        tone: Float,
        alpha: Float,
        colors: ThemePalette
    ) {
        drawPathShadow(canvas, panel, 0f, dp(4f), (52f * alpha).toInt())

        val dark = Color.rgb(63, 45, 111)
        val purple = ColorUtils.blendARGB(
            Color.rgb(139, 91, 247),
            colors.accent,
            0.14f
        )
        val lavender = Color.rgb(243, 218, 255)
        val tt = tone.coerceIn(0f, 1f)
        val a = (255f * alpha).toInt().coerceIn(0, 255)

        fill.shader = LinearGradient(
            px(43f), py(45f),
            px(121f), py(124f),
            ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(dark, lavender, 0.40f + tt * 0.48f), a
            ),
            ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(dark, purple, 0.58f + tt * 0.34f), a
            ),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(panel, fill)
        fill.shader = null
    }

    private fun drawArtworkRect(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        alpha: Float,
        compact: Boolean
    ) {
        if (alpha <= 0.002f) return
        val a = alpha.coerceIn(0f, 1f)

        canvas.save()
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.clipPath(path)

        fill.shader = LinearGradient(
            rect.left, rect.top,
            rect.right, rect.bottom,
            intArrayOf(
                ColorUtils.setAlphaComponent(
                    Color.rgb(247, 184, 255),
                    (255f * a).toInt()
                ),
                ColorUtils.setAlphaComponent(
                    Color.rgb(173, 112, 232),
                    (255f * a).toInt()
                ),
                ColorUtils.setAlphaComponent(
                    Color.rgb(75, 52, 151),
                    (255f * a).toInt()
                )
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, fill)
        fill.shader = null

        val scale = if (compact) 0.92f else 1f
        drawMountainLayer(
            canvas, rect,
            horizon = 0.54f,
            amplitude = 0.22f * scale,
            color = ColorUtils.setAlphaComponent(
                Color.rgb(164, 104, 211),
                (232f * a).toInt()
            )
        )
        drawMountainLayer(
            canvas, rect,
            horizon = 0.69f,
            amplitude = 0.26f * scale,
            color = ColorUtils.setAlphaComponent(
                Color.rgb(103, 67, 179),
                (246f * a).toInt()
            )
        )
        drawMountainLayer(
            canvas, rect,
            horizon = 0.82f,
            amplitude = 0.18f * scale,
            color = ColorUtils.setAlphaComponent(
                Color.rgb(52, 39, 105),
                (255f * a).toInt()
            )
        )

        canvas.restore()
    }

    private fun drawMountainLayer(
        canvas: Canvas,
        rect: RectF,
        horizon: Float,
        amplitude: Float,
        color: Int
    ) {
        val y = rect.top + rect.height() * horizon
        val a = rect.height() * amplitude

        path.reset()
        path.moveTo(rect.left, rect.bottom)
        path.lineTo(rect.left, y)
        path.cubicTo(
            rect.left + rect.width() * 0.08f, y - a * 0.15f,
            rect.left + rect.width() * 0.14f, y - a * 0.72f,
            rect.left + rect.width() * 0.23f, y - a
        )
        path.cubicTo(
            rect.left + rect.width() * 0.31f, y - a * 0.72f,
            rect.left + rect.width() * 0.38f, y - a * 0.25f,
            rect.left + rect.width() * 0.46f, y - a * 0.36f
        )
        path.cubicTo(
            rect.left + rect.width() * 0.55f, y - a * 0.88f,
            rect.left + rect.width() * 0.62f, y - a * 1.06f,
            rect.left + rect.width() * 0.70f, y - a * 0.58f
        )
        path.cubicTo(
            rect.left + rect.width() * 0.79f, y - a * 0.22f,
            rect.left + rect.width() * 0.87f, y - a * 0.63f,
            rect.right, y - a * 0.40f
        )
        path.lineTo(rect.right, rect.bottom)
        path.close()

        fill.shader = null
        fill.color = color
        canvas.drawPath(path, fill)
    }

    private fun drawSliderTrack(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        color: Int,
        alpha: Float = 1f
    ) {
        drawSolidRoundRect(
            canvas,
            logicalRect(cx, cy, w, h),
            n(h / 2f),
            color,
            alpha
        )
    }

    private fun drawBinding(
        canvas: Canvas,
        x: Float,
        y: Float,
        colors: ThemePalette
    ) {
        drawBindingMorph(
            canvas,
            x, y,
            height = 29f,
            alpha = 1f,
            colors = colors
        )
    }

    private fun drawBindingMorph(
        canvas: Canvas,
        x: Float,
        y: Float,
        height: Float,
        alpha: Float,
        colors: ThemePalette
    ) {
        val rect = logicalRect(x, y, 12f, height)
        fill.shader = LinearGradient(
            rect.left, rect.top,
            rect.right, rect.bottom,
            ColorUtils.setAlphaComponent(
                Color.rgb(249, 215, 255),
                (255f * alpha).toInt()
            ),
            ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(
                    Color.rgb(145, 84, 255),
                    colors.accent,
                    0.12f
                ),
                (255f * alpha).toInt()
            ),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            rect, n(6f), n(6f), fill
        )
        fill.shader = null
    }

    private fun drawBindingToThumb(
        canvas: Canvas,
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        t: Float,
        colors: ThemePalette
    ) {
        val x = lerp(fromX, toX, t)
        val y = lerp(fromY, toY, t)
        val width = lerp(12f, 28f, t)
        val height = lerp(29f, 28f, t)
        val rect = logicalRect(x, y, width, height)
        val radius = n(lerp(6f, 14f, t))

        fill.shader = LinearGradient(
            rect.left, rect.top,
            rect.right, rect.bottom,
            Color.rgb(249, 219, 255),
            ColorUtils.blendARGB(
                Color.rgb(143, 84, 255),
                colors.accent,
                0.12f
            ),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, fill)
        fill.shader = null
    }

    private fun drawThumb(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        colors: ThemePalette,
        alpha: Float = 1f
    ) {
        if (alpha <= 0.002f || radius <= 0f) return

        val cx = px(x)
        val cy = py(y)
        val r = n(radius)
        val a = (255f * alpha).toInt().coerceIn(0, 255)

        fill.shader = null
        fill.color = ColorUtils.setAlphaComponent(
            Color.BLACK,
            (44f * alpha).toInt().coerceIn(0, 44)
        )
        canvas.drawCircle(cx, cy + dp(2.5f), r * 1.04f, fill)

        fill.shader = LinearGradient(
            cx - r, cy - r,
            cx + r, cy + r,
            ColorUtils.setAlphaComponent(Color.rgb(250, 224, 255), a),
            ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(
                    Color.rgb(143, 85, 255),
                    colors.accent,
                    0.12f
                ),
                a
            ),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, fill)
        fill.shader = null

        fill.color = ColorUtils.setAlphaComponent(
            Color.WHITE,
            (92f * alpha).toInt().coerceIn(0, 92)
        )
        canvas.drawCircle(
            cx - r * 0.30f,
            cy - r * 0.32f,
            r * 0.20f,
            fill
        )
    }

    private fun drawPlayGlyph(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        alpha: Float,
        colors: ThemePalette,
        dark: Boolean
    ) {
        if (alpha <= 0.002f || size <= 0f) return

        val glyph = smoothClosedPath(
            roundedTrianglePoints(x, y, size)
        )
        val a = (255f * alpha).toInt().coerceIn(0, 255)

        val top = if (dark) {
            Color.rgb(83, 57, 146)
        } else {
            Color.rgb(255, 240, 255)
        }
        val bottom = if (dark) {
            Color.rgb(46, 31, 88)
        } else {
            ColorUtils.blendARGB(
                Color.rgb(211, 176, 255),
                colors.accent,
                0.07f
            )
        }

        drawPathShadow(
            canvas,
            glyph,
            0f,
            dp(2f),
            (30f * alpha).toInt()
        )

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
        colors: ThemePalette
    ) {
        if (alpha <= 0.002f || size <= 0f) return

        val outer = smoothClosedPath(
            flamePoints(x, y, size)
        )
        val a = (255f * alpha).toInt().coerceIn(0, 255)

        drawPathShadow(
            canvas,
            outer,
            0f,
            dp(2.5f),
            (32f * alpha).toInt()
        )

        fill.shader = LinearGradient(
            px(x - size * 0.36f),
            py(y - size * 0.72f),
            px(x + size * 0.38f),
            py(y + size * 0.70f),
            intArrayOf(
                ColorUtils.setAlphaComponent(Color.rgb(255, 230, 255), a),
                ColorUtils.setAlphaComponent(Color.rgb(204, 147, 255), a),
                ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(
                        Color.rgb(130, 75, 251),
                        colors.accent,
                        0.10f
                    ),
                    a
                )
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(outer, fill)
        fill.shader = null

        val inner = smoothClosedPath(
            flamePoints(
                x,
                y + size * 0.18f,
                size * 0.37f
            )
        )
        fill.color = ColorUtils.setAlphaComponent(
            Color.rgb(55, 37, 104),
            (248f * alpha).toInt().coerceIn(0, 248)
        )
        canvas.drawPath(inner, fill)
    }

    private fun drawPlayToFlameMorph(
        canvas: Canvas,
        t: Float,
        fromX: Float,
        fromY: Float,
        fromSize: Float,
        toX: Float,
        toY: Float,
        toSize: Float,
        colors: ThemePalette
    ) {
        val tt = smoother(t)
        val x = lerp(fromX, toX, tt)
        val y = lerp(fromY, toY, tt)
        val size = lerp(fromSize, toSize, tt)

        val play = roundedTrianglePoints(x, y, size)
        val flame = flamePoints(x, y, size)
        val points = morphPoints(play, flame, tt)
        val glyph = smoothClosedPath(points)

        fill.shader = LinearGradient(
            px(x - size * 0.35f),
            py(y - size * 0.70f),
            px(x + size * 0.35f),
            py(y + size * 0.70f),
            Color.rgb(255, 232, 255),
            ColorUtils.blendARGB(
                Color.rgb(133, 77, 251),
                colors.accent,
                0.10f
            ),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(glyph, fill)
        fill.shader = null

        val innerAlpha = smoothStep(0.58f, 1f, tt)
        if (innerAlpha > 0f) {
            val inner = smoothClosedPath(
                flamePoints(
                    x,
                    y + size * 0.18f,
                    size * 0.37f * innerAlpha
                )
            )
            fill.color = ColorUtils.setAlphaComponent(
                Color.rgb(55, 37, 104),
                (245f * innerAlpha).toInt().coerceIn(0, 245)
            )
            canvas.drawPath(inner, fill)
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

    private fun morphPoints(
        from: List<PointF>,
        to: List<PointF>,
        t: Float
    ): List<PointF> {
        require(from.size == to.size)
        return from.indices.map { index ->
            PointF(
                lerp(from[index].x, to[index].x, t),
                lerp(from[index].y, to[index].y, t)
            )
        }
    }

    private fun smoothClosedPath(
        points: List<PointF>
    ): Path {
        val result = Path()
        if (points.isEmpty()) return result

        fun midpoint(a: PointF, b: PointF) =
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

    private fun drawSolidRoundRect(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        color: Int,
        alpha: Float
    ) {
        if (alpha <= 0.002f) return
        fill.shader = null
        fill.color = ColorUtils.setAlphaComponent(
            color,
            (255f * alpha).toInt().coerceIn(0, 255)
        )
        canvas.drawRoundRect(
            rect, radius, radius, fill
        )
    }

    private fun drawPathShadow(
        canvas: Canvas,
        source: Path,
        dx: Float,
        dy: Float,
        alpha: Int
    ) {
        if (alpha <= 0) return
        canvas.save()
        canvas.translate(dx, dy)
        fill.shader = null
        fill.color = ColorUtils.setAlphaComponent(
            Color.BLACK,
            alpha.coerceIn(0, 255)
        )
        canvas.drawPath(source, fill)
        canvas.restore()
    }

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

    // 6.9.26: visual scale intentionally increased ~24% from the old 184
    // divisor. Combined with larger final geometry this produces ~25–35%
    // more visual weight while preserving the existing 228dp hero container.
    private fun unit() =
        minOf(width, height).toFloat() / 148f

    private fun px(x: Float) =
        width / 2f + (x - 80f) * unit()

    private fun py(y: Float) =
        height / 2f + (y - 80f) * unit()

    private fun n(value: Float) =
        value * unit()

    private fun dp(value: Float) =
        value * resources.displayMetrics.density

    private fun lerp(
        from: Float,
        to: Float,
        t: Float
    ) = from + (to - from) * t

    private fun smooth(
        value: Float
    ) = value * value * (3f - 2f * value)

    private fun smoother(
        value: Float
    ): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * t * (t * (t * 6f - 15f) + 10f)
    }

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
