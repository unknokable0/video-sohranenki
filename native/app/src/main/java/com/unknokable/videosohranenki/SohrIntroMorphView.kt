package com.unknokable.videosohranenki

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.floor

class SohrIntroMorphView(context: Context) : View(context) {
    private data class ShapeLayer(
        val points: List<PointF>,
        val rotation: Float,
        val alpha: Float,
        val tone: Float,
        val brightness: Float,
        val shadow: Float
    )

    private data class BoxLayer(
        val cx: Float,
        val cy: Float,
        val w: Float,
        val h: Float,
        val radius: Float,
        val rotation: Float,
        val alpha: Float,
        val tone: Float,
        val brightness: Float,
        val shadow: Float
    )

    private data class DotLayer(
        val cx: Float,
        val cy: Float,
        val radius: Float,
        val alpha: Float,
        val brightness: Float
    )

    private data class GlyphLayer(
        val outer: List<PointF>,
        val inner: List<PointF>,
        val alpha: Float,
        val innerAlpha: Float,
        val darkness: Float
    )

    private data class CanonicalState(
        val main: ShapeLayer,
        val depth1: ShapeLayer,
        val depth2: ShapeLayer,
        val preview: BoxLayer,
        val artworkAlpha: Float,
        val line1: BoxLayer,
        val line2: BoxLayer,
        val accentBar: BoxLayer,
        val timeline: BoxLayer,
        val timelineProgress: Float,
        val timelineThumb: DotLayer,
        val pill1: BoxLayer,
        val pill2: BoxLayer,
        val glyph: GlyphLayer
    )

    private sealed interface NamedIconState {
        val frame: CanonicalState
    }

    private data class CloudState(
        override val frame: CanonicalState
    ) : NamedIconState

    private data class FeedState(
        override val frame: CanonicalState
    ) : NamedIconState

    private data class PlayerState(
        override val frame: CanonicalState
    ) : NamedIconState

    private data class CalendarState(
        override val frame: CanonicalState
    ) : NamedIconState

    private data class SettingsState(
        override val frame: CanonicalState
    ) : NamedIconState

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    private var palette: ThemePalette? = null
    private var pageProgress = 0f
    private var animationsEnabled = true

    private val states: List<NamedIconState> by lazy {
        listOf(
            CloudState(cloudState()),
            FeedState(feedState()),
            PlayerState(playerState()),
            CalendarState(calendarState()),
            SettingsState(settingsState())
        )
    }

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
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val colors = palette ?: return

        val base = floor(pageProgress).toInt().coerceIn(0, 4)
        if (base == 4) {
            render(canvas, states[4].frame, colors)
            return
        }

        val raw = (pageProgress - base).coerceIn(0f, 1f)

        // There is only one renderer for both transition and rest.
        // At an integer page position raw == 0 and the exact stored state is
        // rendered. interpolate() returns the stored A/B object at its exact
        // endpoints, so no second "final icon" can snap in afterward.
        val state = if (!animationsEnabled || raw <= 0f) {
            states[base].frame
        } else {
            interpolate(
                states[base].frame,
                states[base + 1].frame,
                smoother(raw)
            )
        }

        render(canvas, state, colors)
    }

    // ---------------------------------------------------------------------
    // SINGLE SOURCE OF TRUTH: 5 FINAL STATES
    // ---------------------------------------------------------------------

    private fun cloudState(): CanonicalState {
        val center = PointF(80f, 82f)
        return CanonicalState(
            main = shape(
                cloudPoints(),
                rotation = 0f,
                alpha = 1f,
                tone = 1f,
                brightness = 1f,
                shadow = 0.88f
            ),
            depth1 = hiddenShape(center.x, center.y),
            depth2 = hiddenShape(center.x, center.y),
            preview = hiddenBox(81f, 85f),
            artworkAlpha = 0f,
            line1 = hiddenBox(88f, 80f),
            line2 = hiddenBox(88f, 92f),
            accentBar = hiddenBox(80f, 48f),
            timeline = hiddenBox(80f, 116f),
            timelineProgress = 0f,
            timelineThumb = hiddenDot(80f, 116f),
            pill1 = hiddenBox(57f, 27f),
            pill2 = hiddenBox(103f, 27f),
            glyph = glyph(
                outer = roundedTrianglePoints(80f, 84f, 39f),
                inner = roundedTrianglePoints(80f, 84f, 1f),
                alpha = 1f,
                innerAlpha = 0f,
                darkness = 1f
            )
        )
    }

    private fun feedState(): CanonicalState {
        return CanonicalState(
            main = shape(
                roundedPanelPoints(82f, 83f, 132f, 64f),
                rotation = -4f,
                alpha = 1f,
                tone = 0.98f,
                brightness = 1f,
                shadow = 0.92f
            ),
            depth1 = shape(
                roundedPanelPoints(82f, 52f, 114f, 47f),
                rotation = -9f,
                alpha = 1f,
                tone = 0.58f,
                brightness = 0.44f,
                shadow = 0.34f
            ),
            depth2 = shape(
                roundedPanelPoints(80f, 114f, 110f, 43f),
                rotation = 5f,
                alpha = 1f,
                tone = 0.50f,
                brightness = 0.38f,
                shadow = 0.28f
            ),
            preview = box(
                48f, 83f, 45f, 43f,
                10f, -4f, 1f,
                1f, 1f, 0.08f
            ),
            artworkAlpha = 1f,
            line1 = box(
                104f, 72f, 51f, 8f,
                4f, -4f, 1f,
                1f, 1f, 0f
            ),
            line2 = box(
                99f, 93f, 38f, 7f,
                3.5f, -4f, 1f,
                0.92f, 0.96f, 0f
            ),
            accentBar = hiddenBox(80f, 48f),
            timeline = hiddenBox(80f, 116f),
            timelineProgress = 0f,
            timelineThumb = hiddenDot(80f, 116f),
            pill1 = hiddenBox(57f, 27f),
            pill2 = hiddenBox(103f, 27f),
            glyph = glyph(
                outer = roundedTrianglePoints(48f, 83f, 7f),
                inner = roundedTrianglePoints(48f, 83f, 1f),
                alpha = 0f,
                innerAlpha = 0f,
                darkness = 0.35f
            )
        )
    }

    private fun playerState(): CanonicalState {
        return CanonicalState(
            main = shape(
                roundedPanelPoints(80f, 82f, 142f, 91f),
                rotation = 0f,
                alpha = 1f,
                tone = 0.96f,
                brightness = 0.94f,
                shadow = 0.94f
            ),
            depth1 = shape(
                roundedPanelPoints(68f, 80f, 127f, 82f),
                rotation = -6f,
                alpha = 1f,
                tone = 0.50f,
                brightness = 0.38f,
                shadow = 0.32f
            ),
            depth2 = shape(
                roundedPanelPoints(92f, 80f, 125f, 81f),
                rotation = 5f,
                alpha = 1f,
                tone = 0.44f,
                brightness = 0.34f,
                shadow = 0.26f
            ),
            preview = box(
                80f, 64f, 130f, 62f,
                16f, 0f, 1f,
                1f, 1f, 0.06f
            ),
            artworkAlpha = 1f,
            line1 = hiddenBox(104f, 80f),
            line2 = hiddenBox(99f, 94f),
            accentBar = hiddenBox(80f, 48f),
            timeline = box(
                80f, 117f, 120f, 7.5f,
                3.75f, 0f, 1f,
                0.36f, 0.28f, 0f
            ),
            timelineProgress = 0.62f,
            timelineThumb = dot(
                94f, 117f, 7f,
                1f, 1f
            ),
            pill1 = hiddenBox(57f, 27f),
            pill2 = hiddenBox(103f, 27f),
            glyph = glyph(
                outer = roundedTrianglePoints(80f, 64f, 34f),
                inner = roundedTrianglePoints(80f, 64f, 1f),
                alpha = 1f,
                innerAlpha = 0f,
                darkness = 0f
            )
        )
    }

    private fun calendarState(): CanonicalState {
        return CanonicalState(
            main = shape(
                roundedPanelPoints(80f, 83f, 106f, 101f),
                rotation = 0f,
                alpha = 1f,
                tone = 0.94f,
                brightness = 0.92f,
                shadow = 0.94f
            ),
            depth1 = hiddenShape(80f, 83f),
            depth2 = hiddenShape(80f, 83f),
            preview = hiddenBox(80f, 64f),
            artworkAlpha = 0f,
            line1 = hiddenBox(80f, 64f),
            line2 = hiddenBox(80f, 99f),
            accentBar = box(
                80f, 47f, 106f, 27f,
                15f, 0f, 1f,
                1f, 0.94f, 0.12f
            ),
            timeline = hiddenBox(80f, 112f),
            timelineProgress = 0f,
            timelineThumb = hiddenDot(80f, 112f),
            pill1 = box(
                57f, 27f, 12f, 26f,
                6f, 0f, 1f,
                1f, 1f, 0.14f
            ),
            pill2 = box(
                103f, 27f, 12f, 26f,
                6f, 0f, 1f,
                1f, 1f, 0.14f
            ),
            glyph = glyph(
                outer = flamePoints(80f, 84f, 51f),
                inner = flamePoints(80f, 93f, 18f),
                alpha = 1f,
                innerAlpha = 1f,
                darkness = 0f
            )
        )
    }

    private fun settingsState(): CanonicalState {
        return CanonicalState(
            main = shape(
                roundedPanelPoints(80f, 82f, 107f, 107f),
                rotation = 0f,
                alpha = 1f,
                tone = 0.96f,
                brightness = 0.94f,
                shadow = 0.94f
            ),
            depth1 = hiddenShape(80f, 82f),
            depth2 = hiddenShape(80f, 82f),
            preview = hiddenBox(80f, 64f),
            artworkAlpha = 0f,
            line1 = hiddenBox(80f, 64f),
            line2 = box(
                80f, 99f, 78f, 10f,
                5f, 0f, 1f,
                1f, 1f, 0f
            ),
            accentBar = box(
                80f, 64f, 78f, 10f,
                5f, 0f, 1f,
                1f, 1f, 0f
            ),
            timeline = hiddenBox(80f, 112f),
            timelineProgress = 0f,
            timelineThumb = hiddenDot(80f, 112f),
            pill1 = box(
                104f, 64f, 28f, 28f,
                14f, 0f, 1f,
                1f, 1f, 0.20f
            ),
            pill2 = box(
                58f, 99f, 28f, 28f,
                14f, 0f, 1f,
                1f, 1f, 0.20f
            ),
            glyph = glyph(
                outer = flamePoints(80f, 84f, 1f),
                inner = flamePoints(80f, 84f, 1f),
                alpha = 0f,
                innerAlpha = 0f,
                darkness = 0f
            )
        )
    }

    // ---------------------------------------------------------------------
    // ONE RENDERER USED BY STATIC STATES AND EVERY MORPH FRAME
    // ---------------------------------------------------------------------

    private fun render(
        canvas: Canvas,
        state: CanonicalState,
        colors: ThemePalette
    ) {
        drawShape(canvas, state.depth2, colors)
        drawShape(canvas, state.depth1, colors)
        drawShape(canvas, state.main, colors)

        drawBox(canvas, state.accentBar, colors)

        drawArtwork(
            canvas,
            state.preview,
            state.artworkAlpha
        )

        drawBox(canvas, state.line1, colors)
        drawBox(canvas, state.line2, colors)

        drawTimeline(
            canvas,
            state.timeline,
            state.timelineProgress,
            state.timelineThumb,
            colors
        )

        drawBox(canvas, state.pill1, colors)
        drawBox(canvas, state.pill2, colors)

        drawGlyph(canvas, state.glyph, colors)
    }

    private fun drawShape(
        canvas: Canvas,
        layer: ShapeLayer,
        colors: ThemePalette
    ) {
        if (layer.alpha <= 0.002f) return

        val shapePath = smoothClosedPath(layer.points)
        val bounds = RectF()
        shapePath.computeBounds(bounds, true)

        canvas.save()
        canvas.rotate(
            layer.rotation,
            bounds.centerX(),
            bounds.centerY()
        )

        if (layer.shadow > 0f) {
            canvas.save()
            canvas.translate(0f, dp(4f))
            fill.shader = null
            fill.color = ColorUtils.setAlphaComponent(
                Color.BLACK,
                (74f * layer.shadow * layer.alpha)
                    .toInt()
                    .coerceIn(0, 74)
            )
            canvas.drawPath(shapePath, fill)
            canvas.restore()
        }

        val a = if (layer.alpha >= 0.995f) {
            255
        } else {
            (255f * layer.alpha).toInt().coerceIn(0, 255)
        }
        val brightness = layer.brightness.coerceIn(0f, 1f)
        val tone = layer.tone.coerceIn(0f, 1f)

        val top = ColorUtils.blendARGB(
            Color.rgb(137, 99, 214),
            Color.rgb(246, 214, 255),
            0.30f + brightness * 0.58f
        )
        val middle = ColorUtils.blendARGB(
            Color.rgb(118, 77, 209),
            Color.rgb(196, 137, 255),
            0.34f + brightness * 0.48f
        )
        val bottom = ColorUtils.blendARGB(
            Color.rgb(77, 50, 161),
            Color.rgb(119, 75, 240),
            0.34f + tone * 0.50f
        )

        fill.shader = LinearGradient(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            intArrayOf(
                ColorUtils.setAlphaComponent(top, a),
                ColorUtils.setAlphaComponent(middle, a),
                ColorUtils.setAlphaComponent(bottom, a)
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(shapePath, fill)
        fill.shader = null

        canvas.save()
        canvas.clipPath(shapePath)
        fill.shader = LinearGradient(
            bounds.left,
            bounds.top,
            bounds.left + bounds.width() * 0.52f,
            bounds.top + bounds.height() * 0.58f,
            ColorUtils.setAlphaComponent(
                Color.WHITE,
                (82f * brightness * layer.alpha)
                    .toInt()
                    .coerceIn(0, 82)
            ),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(bounds, fill)
        fill.shader = null
        canvas.restore()

        canvas.restore()
    }

    private fun drawBox(
        canvas: Canvas,
        layer: BoxLayer,
        colors: ThemePalette
    ) {
        if (
            layer.alpha <= 0.002f ||
            layer.w <= 0.01f ||
            layer.h <= 0.01f
        ) return

        val rect = logicalRect(
            layer.cx,
            layer.cy,
            layer.w,
            layer.h
        )

        canvas.save()
        canvas.rotate(
            layer.rotation,
            rect.centerX(),
            rect.centerY()
        )

        if (layer.shadow > 0f) {
            val shadowRect = RectF(rect)
            shadowRect.offset(0f, dp(2.5f))
            fill.shader = null
            fill.color = ColorUtils.setAlphaComponent(
                Color.BLACK,
                (52f * layer.shadow * layer.alpha)
                    .toInt()
                    .coerceIn(0, 52)
            )
            canvas.drawRoundRect(
                shadowRect,
                n(layer.radius),
                n(layer.radius),
                fill
            )
        }

        val a = if (layer.alpha >= 0.995f) {
            255
        } else {
            (255f * layer.alpha).toInt().coerceIn(0, 255)
        }
        val bright = layer.brightness.coerceIn(0f, 1f)
        val tone = layer.tone.coerceIn(0f, 1f)

        val top = ColorUtils.blendARGB(
            Color.rgb(143, 99, 219),
            Color.rgb(249, 218, 255),
            0.20f + bright * 0.68f
        )
        val bottom = ColorUtils.blendARGB(
            Color.rgb(89, 58, 170),
            Color.rgb(166, 101, 250),
            0.22f + tone * 0.60f
        )

        fill.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            ColorUtils.setAlphaComponent(top, a),
            ColorUtils.setAlphaComponent(bottom, a),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            rect,
            n(layer.radius),
            n(layer.radius),
            fill
        )
        fill.shader = null

        if (bright > 0.75f) {
            fill.shader = LinearGradient(
                rect.left,
                rect.top,
                rect.left + rect.width() * 0.55f,
                rect.top + rect.height() * 0.45f,
                ColorUtils.setAlphaComponent(
                    Color.WHITE,
                    (64f * layer.alpha).toInt().coerceIn(0, 64)
                ),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(
                rect,
                n(layer.radius),
                n(layer.radius),
                fill
            )
            fill.shader = null
        }

        canvas.restore()
    }

    private fun drawArtwork(
        canvas: Canvas,
        box: BoxLayer,
        artworkAlpha: Float
    ) {
        val alpha = artworkAlpha * box.alpha
        if (
            alpha <= 0.002f ||
            box.w <= 0.01f ||
            box.h <= 0.01f
        ) return

        val rect = logicalRect(
            box.cx,
            box.cy,
            box.w,
            box.h
        )

        canvas.save()
        canvas.rotate(
            box.rotation,
            rect.centerX(),
            rect.centerY()
        )

        path.reset()
        path.addRoundRect(
            rect,
            n(box.radius),
            n(box.radius),
            Path.Direction.CW
        )
        canvas.clipPath(path)

        val a = alpha.coerceIn(0f, 1f)

        fill.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(
                ColorUtils.setAlphaComponent(
                    Color.rgb(255, 226, 255),
                    (255f * a).toInt()
                ),
                ColorUtils.setAlphaComponent(
                    Color.rgb(235, 166, 255),
                    (255f * a).toInt()
                ),
                ColorUtils.setAlphaComponent(
                    Color.rgb(166, 105, 246),
                    (255f * a).toInt()
                ),
                ColorUtils.setAlphaComponent(
                    Color.rgb(71, 47, 151),
                    (255f * a).toInt()
                )
            ),
            floatArrayOf(0f, 0.34f, 0.66f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, fill)
        fill.shader = null

        // Soft sun/moon gives the thumbnail the same layered scene language
        // as the PNG references instead of a flat pair of triangles.
        fill.color = ColorUtils.setAlphaComponent(
            Color.rgb(255, 224, 255),
            (188f * a).toInt().coerceIn(0, 188)
        )
        canvas.drawCircle(
            rect.left + rect.width() * 0.69f,
            rect.top + rect.height() * 0.28f,
            rect.height() * 0.12f,
            fill
        )

        drawMountainLayer(
            canvas,
            rect,
            horizon = 0.55f,
            amplitude = 0.25f,
            color = ColorUtils.setAlphaComponent(
                Color.rgb(201, 132, 241),
                (244f * a).toInt()
            )
        )
        drawMountainLayer(
            canvas,
            rect,
            horizon = 0.70f,
            amplitude = 0.28f,
            color = ColorUtils.setAlphaComponent(
                Color.rgb(128, 77, 205),
                (251f * a).toInt()
            )
        )
        drawMountainLayer(
            canvas,
            rect,
            horizon = 0.83f,
            amplitude = 0.20f,
            color = ColorUtils.setAlphaComponent(
                Color.rgb(49, 34, 103),
                (255f * a).toInt()
            )
        )

        fill.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.left + rect.width() * 0.58f,
            rect.top + rect.height() * 0.44f,
            ColorUtils.setAlphaComponent(
                Color.WHITE,
                (70f * a).toInt().coerceIn(0, 70)
            ),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, fill)
        fill.shader = null

        rim.strokeWidth = dp(1f)
        rim.color = ColorUtils.setAlphaComponent(
            Color.WHITE,
            (130f * a).toInt().coerceIn(0, 130)
        )
        canvas.drawRoundRect(
            rect,
            n(box.radius),
            n(box.radius),
            rim
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
        val amp = rect.height() * amplitude

        path.reset()
        path.moveTo(rect.left, rect.bottom)
        path.lineTo(rect.left, y)
        path.cubicTo(
            rect.left + rect.width() * 0.08f,
            y - amp * 0.15f,
            rect.left + rect.width() * 0.14f,
            y - amp * 0.72f,
            rect.left + rect.width() * 0.23f,
            y - amp
        )
        path.cubicTo(
            rect.left + rect.width() * 0.31f,
            y - amp * 0.72f,
            rect.left + rect.width() * 0.38f,
            y - amp * 0.25f,
            rect.left + rect.width() * 0.46f,
            y - amp * 0.36f
        )
        path.cubicTo(
            rect.left + rect.width() * 0.55f,
            y - amp * 0.88f,
            rect.left + rect.width() * 0.62f,
            y - amp * 1.06f,
            rect.left + rect.width() * 0.70f,
            y - amp * 0.58f
        )
        path.cubicTo(
            rect.left + rect.width() * 0.79f,
            y - amp * 0.22f,
            rect.left + rect.width() * 0.87f,
            y - amp * 0.63f,
            rect.right,
            y - amp * 0.40f
        )
        path.lineTo(rect.right, rect.bottom)
        path.close()

        fill.shader = null
        fill.color = color
        canvas.drawPath(path, fill)
    }

    private fun drawTimeline(
        canvas: Canvas,
        track: BoxLayer,
        progress: Float,
        thumb: DotLayer,
        colors: ThemePalette
    ) {
        if (track.alpha > 0.002f) {
            val rect = logicalRect(
                track.cx,
                track.cy,
                track.w,
                track.h
            )

            canvas.save()
            canvas.rotate(
                track.rotation,
                rect.centerX(),
                rect.centerY()
            )

            fill.shader = null
            fill.color = ColorUtils.setAlphaComponent(
                Color.rgb(88, 58, 160),
                (255f * track.alpha).toInt().coerceIn(0, 255)
            )
            canvas.drawRoundRect(
                rect,
                n(track.radius),
                n(track.radius),
                fill
            )

            val played = RectF(
                rect.left,
                rect.top,
                lerp(
                    rect.left,
                    rect.right,
                    progress.coerceIn(0f, 1f)
                ),
                rect.bottom
            )
            fill.shader = LinearGradient(
                played.left,
                played.top,
                played.right,
                played.bottom,
                ColorUtils.setAlphaComponent(
                    Color.rgb(255, 226, 255),
                    (255f * track.alpha).toInt().coerceIn(0, 255)
                ),
                ColorUtils.setAlphaComponent(
                    Color.rgb(190, 123, 255),
                    (255f * track.alpha).toInt().coerceIn(0, 255)
                ),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(
                played,
                n(track.radius),
                n(track.radius),
                fill
            )
            fill.shader = null

            rim.strokeWidth = dp(0.9f)
            rim.color = ColorUtils.setAlphaComponent(
                Color.WHITE,
                (88f * track.alpha).toInt().coerceIn(0, 88)
            )
            canvas.drawRoundRect(
                rect,
                n(track.radius),
                n(track.radius),
                rim
            )

            canvas.restore()
        }

        drawDot(canvas, thumb, colors)
    }

    private fun drawDot(
        canvas: Canvas,
        dot: DotLayer,
        colors: ThemePalette
    ) {
        if (dot.alpha <= 0.002f || dot.radius <= 0.01f) return

        val cx = px(dot.cx)
        val cy = py(dot.cy)
        val r = n(dot.radius)
        val a = if (dot.alpha >= 0.995f) {
            255
        } else {
            (255f * dot.alpha).toInt().coerceIn(0, 255)
        }

        fill.shader = null
        fill.color = ColorUtils.setAlphaComponent(
            Color.BLACK,
            (34f * dot.alpha).toInt().coerceIn(0, 34)
        )
        canvas.drawCircle(
            cx,
            cy + dp(2f),
            r * 1.04f,
            fill
        )

        fill.shader = LinearGradient(
            cx - r,
            cy - r,
            cx + r,
            cy + r,
            ColorUtils.setAlphaComponent(
                Color.rgb(250, 220, 255),
                a
            ),
            ColorUtils.setAlphaComponent(
                Color.rgb(147, 86, 255),
                a
            ),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, fill)
        fill.shader = null

        fill.color = ColorUtils.setAlphaComponent(
            Color.WHITE,
            (92f * dot.brightness * dot.alpha)
                .toInt()
                .coerceIn(0, 92)
        )
        canvas.drawCircle(
            cx - r * 0.28f,
            cy - r * 0.30f,
            r * 0.17f,
            fill
        )
    }

    private fun drawGlyph(
        canvas: Canvas,
        glyph: GlyphLayer,
        colors: ThemePalette
    ) {
        if (glyph.alpha <= 0.002f) return

        val outer = smoothClosedPath(glyph.outer)
        val bounds = RectF()
        outer.computeBounds(bounds, true)
        val a = if (glyph.alpha >= 0.995f) {
            255
        } else {
            (255f * glyph.alpha).toInt().coerceIn(0, 255)
        }

        fill.shader = null
        fill.color = ColorUtils.setAlphaComponent(
            Color.BLACK,
            (24f * glyph.alpha).toInt().coerceIn(0, 24)
        )
        canvas.save()
        canvas.translate(0f, dp(2f))
        canvas.drawPath(outer, fill)
        canvas.restore()

        val dark = glyph.darkness.coerceIn(0f, 1f)

        val lightTop = Color.rgb(255, 239, 255)
        val lightBottom = Color.rgb(194, 132, 255)
        val darkTop = Color.rgb(87, 59, 151)
        val darkBottom = Color.rgb(48, 31, 95)

        val top = ColorUtils.blendARGB(
            lightTop,
            darkTop,
            dark
        )
        val bottom = ColorUtils.blendARGB(
            lightBottom,
            darkBottom,
            dark
        )

        fill.shader = LinearGradient(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            ColorUtils.setAlphaComponent(top, a),
            ColorUtils.setAlphaComponent(bottom, a),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(outer, fill)
        fill.shader = null

        if (glyph.innerAlpha > 0.002f) {
            val inner = smoothClosedPath(glyph.inner)
            fill.color = ColorUtils.setAlphaComponent(
                Color.rgb(62, 39, 116),
                (255f * glyph.innerAlpha * glyph.alpha)
                    .toInt()
                    .coerceIn(0, 255)
            )
            canvas.drawPath(inner, fill)
        }
    }

    // ---------------------------------------------------------------------
    // STATE INTERPOLATION
    // ---------------------------------------------------------------------

    private fun interpolate(
        a: CanonicalState,
        b: CanonicalState,
        t: Float
    ): CanonicalState {
        if (t <= 0f) return a
        if (t >= 1f) return b

        return CanonicalState(
            main = mix(a.main, b.main, t),
            depth1 = mix(a.depth1, b.depth1, t),
            depth2 = mix(a.depth2, b.depth2, t),
            preview = mix(a.preview, b.preview, t),
            artworkAlpha = lerp(
                a.artworkAlpha,
                b.artworkAlpha,
                t
            ),
            line1 = mix(a.line1, b.line1, t),
            line2 = mix(a.line2, b.line2, t),
            accentBar = mix(a.accentBar, b.accentBar, t),
            timeline = mix(a.timeline, b.timeline, t),
            timelineProgress = lerp(
                a.timelineProgress,
                b.timelineProgress,
                t
            ),
            timelineThumb = mix(
                a.timelineThumb,
                b.timelineThumb,
                t
            ),
            pill1 = mix(a.pill1, b.pill1, t),
            pill2 = mix(a.pill2, b.pill2, t),
            glyph = mix(a.glyph, b.glyph, t)
        )
    }

    private fun mix(
        a: ShapeLayer,
        b: ShapeLayer,
        t: Float
    ): ShapeLayer = ShapeLayer(
        points = mixPoints(a.points, b.points, t),
        rotation = lerp(a.rotation, b.rotation, t),
        alpha = lerp(a.alpha, b.alpha, t),
        tone = lerp(a.tone, b.tone, t),
        brightness = lerp(
            a.brightness,
            b.brightness,
            t
        ),
        shadow = lerp(a.shadow, b.shadow, t)
    )

    private fun mix(
        a: BoxLayer,
        b: BoxLayer,
        t: Float
    ): BoxLayer = BoxLayer(
        cx = lerp(a.cx, b.cx, t),
        cy = lerp(a.cy, b.cy, t),
        w = lerp(a.w, b.w, t),
        h = lerp(a.h, b.h, t),
        radius = lerp(a.radius, b.radius, t),
        rotation = lerp(
            a.rotation,
            b.rotation,
            t
        ),
        alpha = lerp(a.alpha, b.alpha, t),
        tone = lerp(a.tone, b.tone, t),
        brightness = lerp(
            a.brightness,
            b.brightness,
            t
        ),
        shadow = lerp(a.shadow, b.shadow, t)
    )

    private fun mix(
        a: DotLayer,
        b: DotLayer,
        t: Float
    ): DotLayer = DotLayer(
        cx = lerp(a.cx, b.cx, t),
        cy = lerp(a.cy, b.cy, t),
        radius = lerp(a.radius, b.radius, t),
        alpha = lerp(a.alpha, b.alpha, t),
        brightness = lerp(
            a.brightness,
            b.brightness,
            t
        )
    )

    private fun mix(
        a: GlyphLayer,
        b: GlyphLayer,
        t: Float
    ): GlyphLayer = GlyphLayer(
        outer = mixPoints(
            a.outer,
            b.outer,
            t
        ),
        inner = mixPoints(
            a.inner,
            b.inner,
            t
        ),
        alpha = lerp(a.alpha, b.alpha, t),
        innerAlpha = lerp(
            a.innerAlpha,
            b.innerAlpha,
            t
        ),
        darkness = lerp(
            a.darkness,
            b.darkness,
            t
        )
    )

    private fun mixPoints(
        a: List<PointF>,
        b: List<PointF>,
        t: Float
    ): List<PointF> {
        require(a.size == b.size)
        return a.indices.map { index ->
            PointF(
                lerp(
                    a[index].x,
                    b[index].x,
                    t
                ),
                lerp(
                    a[index].y,
                    b[index].y,
                    t
                )
            )
        }
    }

    // ---------------------------------------------------------------------
    // GEOMETRY
    // ---------------------------------------------------------------------

    private fun cloudPoints(): List<PointF> = listOf(
        PointF(22f, 100f),
        PointF(23f, 84f),
        PointF(37f, 70f),
        PointF(54f, 69f),
        PointF(64f, 51f),
        PointF(81f, 43f),
        PointF(101f, 52f),
        PointF(111f, 70f),
        PointF(128f, 71f),
        PointF(140f, 87f),
        PointF(135f, 105f),
        PointF(46f, 115f)
    )

    private fun roundedPanelPoints(
        cx: Float,
        cy: Float,
        w: Float,
        h: Float
    ): List<PointF> {
        val left = cx - w / 2f
        val right = cx + w / 2f
        val top = cy - h / 2f
        val bottom = cy + h / 2f
        val corner = minOf(w, h) * 0.18f

        return listOf(
            PointF(left + corner, top),
            PointF(cx, top),
            PointF(right - corner, top),
            PointF(right, top + corner),
            PointF(right, cy),
            PointF(right, bottom - corner),
            PointF(right - corner, bottom),
            PointF(cx, bottom),
            PointF(left + corner, bottom),
            PointF(left, bottom - corner),
            PointF(left, cy),
            PointF(left, top + corner)
        )
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

    private fun smoothClosedPath(
        points: List<PointF>
    ): Path {
        val result = Path()
        if (points.isEmpty()) return result

        fun midpoint(
            a: PointF,
            b: PointF
        ) = PointF(
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

        for (index in points.indices) {
            val current = points[index]
            val next = points[
                (index + 1) % points.size
            ]
            val mid = midpoint(
                current,
                next
            )

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

    // ---------------------------------------------------------------------
    // CONSTRUCTORS
    // ---------------------------------------------------------------------

    private fun shape(
        points: List<PointF>,
        rotation: Float,
        alpha: Float,
        tone: Float,
        brightness: Float,
        shadow: Float
    ) = ShapeLayer(
        points,
        rotation,
        alpha,
        tone,
        brightness,
        shadow
    )

    private fun hiddenShape(
        cx: Float,
        cy: Float
    ) = ShapeLayer(
        roundedPanelPoints(
            cx,
            cy,
            1f,
            1f
        ),
        rotation = 0f,
        alpha = 0f,
        tone = 0f,
        brightness = 0f,
        shadow = 0f
    )

    private fun box(
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        radius: Float,
        rotation: Float,
        alpha: Float,
        tone: Float,
        brightness: Float,
        shadow: Float
    ) = BoxLayer(
        cx,
        cy,
        w,
        h,
        radius,
        rotation,
        alpha,
        tone,
        brightness,
        shadow
    )

    private fun hiddenBox(
        cx: Float,
        cy: Float
    ) = BoxLayer(
        cx = cx,
        cy = cy,
        w = 1f,
        h = 1f,
        radius = 0.5f,
        rotation = 0f,
        alpha = 0f,
        tone = 0f,
        brightness = 0f,
        shadow = 0f
    )

    private fun dot(
        cx: Float,
        cy: Float,
        radius: Float,
        alpha: Float,
        brightness: Float
    ) = DotLayer(
        cx,
        cy,
        radius,
        alpha,
        brightness
    )

    private fun hiddenDot(
        cx: Float,
        cy: Float
    ) = DotLayer(
        cx,
        cy,
        0.5f,
        0f,
        0f
    )

    private fun glyph(
        outer: List<PointF>,
        inner: List<PointF>,
        alpha: Float,
        innerAlpha: Float,
        darkness: Float
    ) = GlyphLayer(
        outer,
        inner,
        alpha,
        innerAlpha,
        darkness
    )

    // ---------------------------------------------------------------------
    // COORDINATES
    // ---------------------------------------------------------------------

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

    // Larger logical canvas than 6.9.26: the shapes themselves are also
    // widened, producing roughly 20–25% more visual weight without scaling
    // the Canvas or changing the onboarding layout.
    private fun unit() =
        minOf(width, height).toFloat() / 112f

    private fun px(x: Float) =
        width / 2f +
            (x - 80f) * unit()

    private fun py(y: Float) =
        height / 2f +
            (y - 80f) * unit()

    private fun n(value: Float) =
        value * unit()

    private fun dp(value: Float) =
        value * resources.displayMetrics.density

    private fun lerp(
        from: Float,
        to: Float,
        t: Float
    ) = from + (to - from) * t

    private fun smoother(
        value: Float
    ): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * t *
            (t * (t * 6f - 15f) + 10f)
    }
}
