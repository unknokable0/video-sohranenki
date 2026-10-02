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
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * A single, parameter-driven onboarding illustration.
 *
 * Every page is a keyframe in the same 200 x 200 coordinate system. The same
 * shells, artwork, lines and glyph contours are moved and reshaped between
 * keyframes, so there is never a pair of complete icons to cross-fade.
 */
class SohrIntroMorphView(
    context: Context
) : View(context) {
    private data class Part(
        val cx: Float,
        val cy: Float,
        val width: Float,
        val height: Float,
        val radius: Float,
        val rotation: Float = 0f
    )

    private data class Point(
        val x: Float,
        val y: Float
    )

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    private var palette: ThemePalette? = null
    private var pageProgress = 0f
    private var shimmer = 0f
    private var animationsEnabled = true

    private val shimmerAnimator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4_800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                shimmer = it.animatedValue as Float
                invalidate()
            }
        }

    /* Four persistent outer pieces: cloud lobes become depth cards, player
       layers, calendar bindings/header and finally the settings glass. */
    private val backOne = arrayOf(
        Part(101f, 77f, 76f, 76f, 38f),
        Part(101f, 61f, 126f, 62f, 15f, -7f),
        Part(98f, 65f, 146f, 86f, 18f, -7f),
        Part(68f, 40f, 18f, 43f, 9f),
        Part(69f, 68f, 0f, 0f, 0f)
    )
    private val backTwo = arrayOf(
        Part(57f, 105f, 70f, 62f, 31f),
        Part(100f, 78f, 138f, 67f, 15f, 5f),
        Part(102f, 73f, 151f, 91f, 19f, 4f),
        Part(132f, 40f, 18f, 43f, 9f),
        Part(131f, 68f, 0f, 0f, 0f)
    )
    private val mainShell = arrayOf(
        Part(101f, 112f, 148f, 64f, 32f),
        Part(100f, 105f, 151f, 78f, 16f, -3f),
        Part(100f, 101f, 157f, 109f, 19f, -2f),
        Part(100f, 105f, 129f, 130f, 22f),
        Part(100f, 102f, 129f, 126f, 23f)
    )
    private val contentPanel = arrayOf(
        Part(145f, 108f, 67f, 59f, 30f),
        Part(61f, 105f, 53f, 45f, 11f, -3f),
        Part(100f, 88f, 149f, 76f, 15f, -2f),
        Part(100f, 67f, 129f, 41f, 20f),
        Part(100f, 102f, 113f, 109f, 20f)
    )

    /* Text rows continuously become the player timeline, calendar divider and
       the two settings tracks. Zero-size states collapse instead of fading. */
    private val lineOne = arrayOf(
        Part(98f, 105f, 0f, 0f, 0f),
        Part(119f, 92f, 49f, 7f, 3.5f, -3f),
        Part(100f, 133f, 126f, 7f, 3.5f, -2f),
        Part(100f, 76f, 91f, 5f, 2.5f),
        Part(100f, 84f, 89f, 8f, 4f)
    )
    private val lineTwo = arrayOf(
        Part(98f, 105f, 0f, 0f, 0f),
        Part(114f, 109f, 40f, 7f, 3.5f, -3f),
        Part(77f, 133f, 80f, 7f, 3.5f, -2f),
        Part(100f, 99f, 69f, 6f, 3f),
        Part(100f, 117f, 89f, 8f, 4f)
    )
    private val lineThree = arrayOf(
        Part(98f, 105f, 0f, 0f, 0f),
        Part(109f, 125f, 30f, 6f, 3f, -3f),
        Part(100f, 133f, 0f, 0f, 0f),
        Part(100f, 121f, 58f, 6f, 3f),
        Part(100f, 117f, 0f, 0f, 0f)
    )

    private val knobOne = arrayOf(
        Part(98f, 105f, 0f, 0f, 0f),
        Part(70f, 96f, 9f, 9f, 4.5f, -3f),
        Part(111f, 133f, 15f, 15f, 7.5f, -2f),
        Part(77f, 99f, 8f, 8f, 4f),
        Part(128f, 84f, 24f, 24f, 12f)
    )
    private val knobTwo = arrayOf(
        Part(98f, 105f, 0f, 0f, 0f),
        Part(53f, 112f, 0f, 0f, 0f),
        Part(111f, 133f, 0f, 0f, 0f),
        Part(100f, 99f, 8f, 8f, 4f),
        Part(72f, 117f, 24f, 24f, 12f)
    )

    private val mountainBack = arrayOf(
        collapsedPolygon(98f, 105f),
        polygon(39f, 117f, 48f, 106f, 55f, 112f, 65f, 100f, 80f, 118f, 39f, 118f),
        polygon(26f, 112f, 50f, 89f, 66f, 104f, 89f, 78f, 122f, 113f, 26f, 113f),
        collapsedPolygon(100f, 99f),
        collapsedPolygon(100f, 102f)
    )
    private val mountainFront = arrayOf(
        collapsedPolygon(98f, 105f),
        polygon(40f, 118f, 51f, 109f, 59f, 115f, 70f, 105f, 81f, 119f, 40f, 119f),
        polygon(25f, 117f, 47f, 99f, 66f, 113f, 90f, 93f, 125f, 118f, 25f, 118f),
        collapsedPolygon(100f, 101f),
        collapsedPolygon(100f, 103f)
    )

    private val primaryGlyph = arrayOf(
        sampleTriangle(101f, 105f, 44f, 51f),
        sampleCircle(70f, 96f, 5f, 5f),
        sampleTriangle(101f, 87f, 39f, 46f),
        sampleFlame(),
        sampleCircle(128f, 84f, 12f, 12f)
    )
    private val secondaryGlyph = arrayOf(
        sampleCircle(99f, 109f, 0f, 0f),
        sampleCircle(70f, 96f, 0f, 0f),
        sampleCircle(101f, 93f, 0f, 0f),
        sampleInnerFlame(),
        sampleCircle(72f, 117f, 12f, 12f)
    )

    private val primaryTopColors = intArrayOf(
        Color.rgb(247, 229, 255),
        Color.rgb(230, 202, 255),
        Color.rgb(215, 151, 255),
        Color.rgb(153, 104, 244),
        Color.rgb(151, 103, 243)
    )
    private val primaryBottomColors = intArrayOf(
        Color.rgb(121, 82, 255),
        Color.rgb(105, 67, 219),
        Color.rgb(69, 52, 164),
        Color.rgb(48, 35, 99),
        Color.rgb(49, 37, 102)
    )
    private val glyphColors = intArrayOf(
        Color.rgb(55, 41, 113),
        Color.rgb(249, 225, 255),
        Color.rgb(255, 240, 255),
        Color.rgb(230, 194, 255),
        Color.rgb(235, 207, 255)
    )

    init {
        shimmerAnimator.start()
    }

    fun setPalette(value: ThemePalette) {
        palette = value
        invalidate()
    }

    fun setAnimationsEnabled(value: Boolean) {
        animationsEnabled = value
        if (value) {
            if (!shimmerAnimator.isRunning) shimmerAnimator.start()
        } else {
            shimmerAnimator.cancel()
            shimmer = 0f
        }
        invalidate()
    }

    fun setPageProgress(value: Float) {
        pageProgress = value.coerceIn(0f, LAST_PAGE.toFloat())
        invalidate()
    }

    override fun onDetachedFromWindow() {
        shimmerAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animationsEnabled && !shimmerAnimator.isRunning) shimmerAnimator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val theme = palette ?: return

        val from = floor(pageProgress).toInt().coerceIn(0, LAST_PAGE)
        val to = (from + 1).coerceAtMost(LAST_PAGE)
        val raw = (pageProgress - from).coerceIn(0f, 1f)
        val t = referenceEase(raw)
        val pulse = sin(PI.toFloat() * t)

        val sceneScale = min(width, height) / SCENE_SIZE
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(sceneScale, sceneScale)
        canvas.translate(-SCENE_SIZE / 2f, -SCENE_SIZE / 2f)

        val topColor = themedColor(blend(primaryTopColors[from], primaryTopColors[to], t), theme, 0.12f)
        val bottomColor = themedColor(blend(primaryBottomColors[from], primaryBottomColors[to], t), theme, 0.20f)
        val rearDepth = lerp(REAR_DEPTH[from], REAR_DEPTH[to], t)
        val rearTop = blend(topColor, Color.rgb(104, 77, 196), rearDepth)
        val rearBottom = blend(bottomColor, Color.rgb(32, 25, 72), rearDepth * 0.8f)

        val one = interpolate(backOne[from], backOne[to], t, 4.5f * pulse)
        val two = interpolate(backTwo[from], backTwo[to], t, -4f * pulse)
        val shell = interpolate(mainShell[from], mainShell[to], t, 1.4f * pulse)
        val panel = interpolate(contentPanel[from], contentPanel[to], t, -1.2f * pulse)

        drawShadow(canvas, one, 0.62f)
        drawShadow(canvas, two, 0.72f)
        drawShadow(canvas, shell, 1f)

        drawGlassPart(canvas, one, rearTop, rearBottom, 0.70f)
        drawGlassPart(canvas, two, rearTop, rearBottom, 0.80f)
        drawGlassPart(canvas, shell, topColor, bottomColor, 1f)

        val panelTop = blend(topColor, Color.WHITE, if (to <= 2) 0.15f else 0.04f)
        val panelBottom = blend(bottomColor, Color.rgb(50, 32, 117), 0.18f)
        drawGlassPart(canvas, panel, panelTop, panelBottom, 0.94f)

        drawMountains(canvas, from, to, t, theme)
        drawDetails(canvas, from, to, t, theme)
        drawGlyphs(canvas, from, to, t, theme)
        drawSpecular(canvas, shell, panel, theme)

        canvas.restore()
    }

    private fun drawDetails(
        canvas: Canvas,
        from: Int,
        to: Int,
        t: Float,
        theme: ThemePalette
    ) {
        val pulse = sin(PI.toFloat() * t)
        val a = interpolate(lineOne[from], lineOne[to], t, -1.5f * pulse)
        val b = interpolate(lineTwo[from], lineTwo[to], t, 1.8f * pulse)
        val c = interpolate(lineThree[from], lineThree[to], t, -1f * pulse)

        val track = themedColor(Color.rgb(226, 210, 255), theme, 0.10f)
        drawSolidPart(canvas, a, track, 0.91f)
        drawSolidPart(canvas, b, blend(track, Color.rgb(164, 127, 247), 0.24f), 0.86f)
        drawSolidPart(canvas, c, blend(track, Color.rgb(104, 77, 203), 0.38f), 0.77f)

        val k1 = interpolate(knobOne[from], knobOne[to], t, 2f * pulse)
        val k2 = interpolate(knobTwo[from], knobTwo[to], t, -2f * pulse)
        val knobColor = blend(Color.rgb(247, 222, 255), theme.accent, 0.18f)
        drawSolidPart(canvas, k1, knobColor, 0.98f)
        drawSolidPart(canvas, k2, knobColor, 0.98f)

        if (k1.width > 5f) drawKnobHighlight(canvas, k1)
        if (k2.width > 5f) drawKnobHighlight(canvas, k2)
    }

    private fun drawMountains(
        canvas: Canvas,
        from: Int,
        to: Int,
        t: Float,
        theme: ThemePalette
    ) {
        val back = interpolatePoints(mountainBack[from], mountainBack[to], t)
        val front = interpolatePoints(mountainFront[from], mountainFront[to], t)

        fill.shader = LinearGradient(
            35f,
            80f,
            120f,
            120f,
            blend(theme.accent, Color.rgb(243, 173, 255), 0.38f),
            blend(theme.accent, Color.rgb(70, 52, 142), 0.48f),
            Shader.TileMode.CLAMP
        )
        fill.alpha = 190
        drawSmoothPolygon(canvas, back, fill)
        fill.alpha = 225
        drawSmoothPolygon(canvas, front, fill)
        fill.shader = null
        fill.alpha = 255
    }

    private fun drawGlyphs(
        canvas: Canvas,
        from: Int,
        to: Int,
        t: Float,
        theme: ThemePalette
    ) {
        val outer = interpolatePoints(primaryGlyph[from], primaryGlyph[to], t)
        val inner = interpolatePoints(secondaryGlyph[from], secondaryGlyph[to], t)
        val glyphColor = themedColor(blend(glyphColors[from], glyphColors[to], t), theme, 0.08f)

        fill.shader = LinearGradient(
            78f,
            78f,
            128f,
            151f,
            blend(glyphColor, Color.WHITE, 0.22f),
            glyphColor,
            Shader.TileMode.CLAMP
        )
        fill.alpha = 255
        drawSmoothPolygon(canvas, outer, fill)
        fill.shader = null

        val innerColor = if (to == LAST_PAGE && t > 0.45f) {
            blend(Color.rgb(57, 40, 112), Color.rgb(238, 209, 255), window(t, 0.45f, 1f))
        } else {
            Color.rgb(53, 38, 105)
        }
        fill.color = innerColor
        drawSmoothPolygon(canvas, inner, fill)

        stroke.strokeWidth = 1.05f
        stroke.color = Color.argb(92, 255, 255, 255)
        drawSmoothPolygon(canvas, outer, stroke)
    }

    private fun drawGlassPart(
        canvas: Canvas,
        part: Part,
        topColor: Int,
        bottomColor: Int,
        alpha: Float
    ) {
        if (part.width <= 0.05f || part.height <= 0.05f) return
        withPartRotation(canvas, part) {
            setRect(part)
            val shimmerOffset = if (animationsEnabled) sin(shimmer * PI.toFloat() * 2f) * 3f else 0f
            fill.shader = LinearGradient(
                rect.left + shimmerOffset,
                rect.top,
                rect.right,
                rect.bottom,
                topColor,
                bottomColor,
                Shader.TileMode.CLAMP
            )
            fill.alpha = (255f * alpha).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(rect, part.radius, part.radius, fill)
            fill.shader = null
            fill.alpha = 255

            stroke.strokeWidth = 1.1f
            stroke.color = Color.argb((68f * alpha).toInt(), 255, 255, 255)
            canvas.drawRoundRect(rect, part.radius, part.radius, stroke)
        }
    }

    private fun drawSolidPart(
        canvas: Canvas,
        part: Part,
        color: Int,
        alpha: Float
    ) {
        if (part.width <= 0.05f || part.height <= 0.05f) return
        withPartRotation(canvas, part) {
            setRect(part)
            fill.color = ColorUtils.setAlphaComponent(color, (255f * alpha).toInt().coerceIn(0, 255))
            canvas.drawRoundRect(rect, part.radius, part.radius, fill)
        }
    }

    private fun drawShadow(
        canvas: Canvas,
        part: Part,
        strength: Float
    ) {
        if (part.width <= 0.05f || part.height <= 0.05f) return
        withPartRotation(canvas, part) {
            rect.set(
                part.cx - part.width / 2f - 2.8f,
                part.cy - part.height / 2f + 4.2f,
                part.cx + part.width / 2f + 2.8f,
                part.cy + part.height / 2f + 9.8f
            )
            fill.color = Color.argb((25f * strength).toInt(), 20, 10, 52)
            canvas.drawRoundRect(rect, part.radius + 3f, part.radius + 3f, fill)
            rect.inset(2.1f, 2.1f)
            fill.color = Color.argb((25f * strength).toInt(), 99, 55, 224)
            canvas.drawRoundRect(rect, part.radius + 1f, part.radius + 1f, fill)
        }
    }

    private fun drawSpecular(
        canvas: Canvas,
        shell: Part,
        panel: Part,
        theme: ThemePalette
    ) {
        val target = if (panel.width > 35f && panel.height > 28f) panel else shell
        if (target.width <= 1f || target.height <= 1f) return
        withPartRotation(canvas, target) {
            val inset = min(8f, target.width * 0.08f)
            rect.set(
                target.cx - target.width / 2f + inset,
                target.cy - target.height / 2f + inset,
                target.cx + target.width * 0.10f,
                target.cy - target.height / 2f + inset + 3.2f
            )
            fill.color = ColorUtils.setAlphaComponent(blend(Color.WHITE, theme.accent, 0.12f), 66)
            canvas.drawRoundRect(rect, 1.6f, 1.6f, fill)
        }
    }

    private fun drawKnobHighlight(canvas: Canvas, part: Part) {
        val radius = min(part.width, part.height) * 0.13f
        fill.color = Color.argb(124, 255, 255, 255)
        canvas.drawCircle(
            part.cx - part.width * 0.17f,
            part.cy - part.height * 0.18f,
            radius,
            fill
        )
    }

    private fun setRect(part: Part) {
        rect.set(
            part.cx - part.width / 2f,
            part.cy - part.height / 2f,
            part.cx + part.width / 2f,
            part.cy + part.height / 2f
        )
    }

    private inline fun withPartRotation(
        canvas: Canvas,
        part: Part,
        draw: () -> Unit
    ) {
        canvas.save()
        canvas.rotate(part.rotation, part.cx, part.cy)
        draw()
        canvas.restore()
    }

    private fun drawSmoothPolygon(
        canvas: Canvas,
        points: Array<Point>,
        paint: Paint
    ) {
        if (points.isEmpty()) return
        path.reset()
        val last = points.last()
        val first = points.first()
        path.moveTo((last.x + first.x) / 2f, (last.y + first.y) / 2f)
        points.forEachIndexed { index, point ->
            val next = points[(index + 1) % points.size]
            path.quadTo(point.x, point.y, (point.x + next.x) / 2f, (point.y + next.y) / 2f)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun interpolate(
        a: Part,
        b: Part,
        t: Float,
        rotationArc: Float = 0f
    ): Part =
        Part(
            cx = lerp(a.cx, b.cx, t),
            cy = lerp(a.cy, b.cy, t),
            width = lerp(a.width, b.width, t),
            height = lerp(a.height, b.height, t),
            radius = lerp(a.radius, b.radius, t),
            rotation = lerp(a.rotation, b.rotation, t) + rotationArc
        )

    private fun interpolatePoints(
        a: Array<Point>,
        b: Array<Point>,
        t: Float
    ): Array<Point> =
        Array(a.size) { index ->
            Point(
                lerp(a[index].x, b[index].x, t),
                lerp(a[index].y, b[index].y, t)
            )
        }

    private fun themedColor(
        color: Int,
        theme: ThemePalette,
        amount: Float
    ): Int = ColorUtils.blendARGB(color, theme.accent, amount)

    private fun blend(a: Int, b: Int, t: Float): Int =
        ColorUtils.blendARGB(a, b, t.coerceIn(0f, 1f))

    private fun referenceEase(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * t * (t * (t * 6f - 15f) + 10f)
    }

    private fun window(value: Float, start: Float, end: Float): Float =
        ((value - start) / (end - start)).coerceIn(0f, 1f)

    private fun lerp(from: Float, to: Float, t: Float): Float =
        from + (to - from) * t

    private fun polygon(vararg coordinates: Float): Array<Point> =
        Array(coordinates.size / 2) { index ->
            Point(coordinates[index * 2], coordinates[index * 2 + 1])
        }

    private fun collapsedPolygon(x: Float, y: Float): Array<Point> =
        Array(MOUNTAIN_POINTS) { Point(x, y) }

    private fun sampleTriangle(
        cx: Float,
        cy: Float,
        width: Float,
        height: Float
    ): Array<Point> {
        val vertices = arrayOf(
            Point(cx - width * 0.42f, cy - height / 2f),
            Point(cx + width * 0.58f, cy),
            Point(cx - width * 0.42f, cy + height / 2f)
        )
        return Array(GLYPH_POINTS) { index ->
            val position = index * 3f / GLYPH_POINTS
            val edge = floor(position).toInt().coerceAtMost(2)
            val local = position - edge
            val start = vertices[edge]
            val end = vertices[(edge + 1) % 3]
            Point(lerp(start.x, end.x, local), lerp(start.y, end.y, local))
        }
    }

    private fun sampleCircle(
        cx: Float,
        cy: Float,
        radiusX: Float,
        radiusY: Float
    ): Array<Point> =
        Array(GLYPH_POINTS) { index ->
            val angle = -PI.toFloat() / 2f + PI.toFloat() * 2f * index / GLYPH_POINTS
            Point(cx + cos(angle) * radiusX, cy + sin(angle) * radiusY)
        }

    private fun sampleFlame(): Array<Point> = arrayOf(
        Point(101f, 80f), Point(105f, 91f), Point(101f, 103f), Point(97f, 111f),
        Point(105f, 108f), Point(114f, 99f), Point(114f, 114f), Point(123f, 109f),
        Point(129f, 120f), Point(130f, 132f), Point(125f, 143f), Point(115f, 150f),
        Point(102f, 153f), Point(89f, 150f), Point(79f, 141f), Point(73f, 129f),
        Point(74f, 117f), Point(80f, 107f), Point(89f, 98f), Point(97f, 89f)
    )

    private fun sampleInnerFlame(): Array<Point> = arrayOf(
        Point(103f, 119f), Point(107f, 123f), Point(110f, 128f), Point(111f, 134f),
        Point(110f, 139f), Point(107f, 143f), Point(103f, 145f), Point(99f, 145f),
        Point(95f, 143f), Point(92f, 139f), Point(91f, 134f), Point(92f, 129f),
        Point(95f, 125f), Point(99f, 122f), Point(101f, 126f), Point(102f, 130f),
        Point(104f, 133f), Point(106f, 130f), Point(106f, 126f), Point(104f, 122f)
    )

    companion object {
        private const val LAST_PAGE = 4
        private const val SCENE_SIZE = 200f
        private const val GLYPH_POINTS = 20
        private const val MOUNTAIN_POINTS = 6
        private val REAR_DEPTH = floatArrayOf(0f, 0.48f, 0.58f, 0.34f, 0f)
    }
}
