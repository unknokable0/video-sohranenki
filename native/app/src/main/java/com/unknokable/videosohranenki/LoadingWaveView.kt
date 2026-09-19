package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.sin

class LoadingWaveView(context: Context, private val color: Int) : View(context) {

    private val basePath = Path()
    private val segmentPath = Path()
    private val measure = PathMeasure()
    private val pos = FloatArray(2)
    private var phase = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1850L
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        animator.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rebuildPath(w.toFloat(), h.toFloat())
    }

    private fun rebuildPath(w: Float, h: Float) {
        val left = w * 0.23f
        val right = w * 0.77f
        val top = h * 0.16f
        val bottom = h * 0.84f

        basePath.reset()
        basePath.moveTo(right, top)
        basePath.cubicTo(
            w * 0.48f, top - h * 0.02f,
            left, h * 0.25f,
            left, h * 0.37f
        )
        basePath.cubicTo(
            left, h * 0.50f,
            right, h * 0.47f,
            right, h * 0.59f
        )
        basePath.cubicTo(
            right, h * 0.73f,
            w * 0.56f, bottom + h * 0.02f,
            left, bottom
        )

        measure.setPath(basePath, false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0 || measure.length <= 0f) return

        val breathe = 1f + sin((phase * Math.PI * 2).toFloat()) * 0.028f
        canvas.save()
        canvas.scale(breathe, breathe, width / 2f, height / 2f)

        paint.style = Paint.Style.STROKE
        paint.color = (color and 0x00FFFFFF) or (38 shl 24)
        paint.strokeWidth = dp(6.2f)
        paint.setShadowLayer(dp(8f), 0f, 0f, (color and 0x00FFFFFF) or (90 shl 24))
        canvas.drawPath(basePath, paint)

        drawMovingSegment(canvas, phase, 0.36f, 9.0f, color, 255, dp(9f))
        drawMovingSegment(canvas, (phase + 0.34f) % 1f, 0.24f, 5.8f, Color.WHITE, 205, dp(5f))
        drawMovingSegment(canvas, (phase + 0.67f) % 1f, 0.18f, 3.8f, color, 105, 0f)

        val headDistance = ((phase + 0.36f) % 1f) * measure.length
        if (measure.getPosTan(headDistance, pos, null)) {
            dotPaint.color = Color.WHITE
            dotPaint.setShadowLayer(dp(8f), 0f, 0f, Color.WHITE)
            canvas.drawCircle(pos[0], pos[1], dp(3.4f), dotPaint)

            dotPaint.color = color
            dotPaint.setShadowLayer(dp(11f), 0f, 0f, color)
            canvas.drawCircle(pos[0], pos[1], dp(5.8f), dotPaint)
        }

        canvas.restore()
    }

    private fun drawMovingSegment(
        canvas: Canvas,
        localPhase: Float,
        segmentFraction: Float,
        widthDp: Float,
        segmentColor: Int,
        alpha: Int,
        glow: Float
    ) {
        val length = measure.length
        val start = localPhase * length
        val end = start + segmentFraction * length

        segmentPath.reset()
        if (end <= length) {
            measure.getSegment(start, end, segmentPath, true)
        } else {
            measure.getSegment(start, length, segmentPath, true)
            measure.getSegment(0f, end - length, segmentPath, true)
        }

        paint.color = (segmentColor and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
        paint.strokeWidth = dp(widthDp)
        if (glow > 0f) {
            paint.setShadowLayer(glow, 0f, 0f, segmentColor)
        } else {
            paint.clearShadowLayer()
        }
        canvas.drawPath(segmentPath, paint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
