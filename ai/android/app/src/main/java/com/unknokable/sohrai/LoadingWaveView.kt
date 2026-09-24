package com.unknokable.sohrai

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.sin

class LoadingWaveView(context: Context, private var color: Int) : View(context) {
    private val basePath = Path()
    private val segmentPath = Path()
    private val measure = PathMeasure()
    private var phase = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1750L
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }

    init { animator.start() }

    fun setWaveColor(value: Int) { color = value; invalidate() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val wf = w.toFloat()
        val hf = h.toFloat()
        val left = wf * 0.24f
        val right = wf * 0.76f
        val top = hf * 0.18f
        val bottom = hf * 0.82f
        basePath.reset()
        basePath.moveTo(right, top)
        basePath.cubicTo(wf * 0.49f, hf * 0.14f, left, hf * 0.25f, left, hf * 0.37f)
        basePath.cubicTo(left, hf * 0.49f, right, hf * 0.48f, right, hf * 0.59f)
        basePath.cubicTo(right, hf * 0.72f, wf * 0.56f, hf * 0.84f, left, bottom)
        measure.setPath(basePath, false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0 || measure.length <= 0f) return
        val breathe = 1f + sin((phase * Math.PI * 2).toFloat()) * 0.012f
        canvas.save()
        canvas.scale(breathe, breathe, width / 2f, height / 2f)
        paint.color = (color and 0x00FFFFFF) or (42 shl 24)
        paint.strokeWidth = dp(5.2f)
        canvas.drawPath(basePath, paint)
        val start = phase * measure.length
        val end = start + measure.length * 0.34f
        segmentPath.reset()
        if (end <= measure.length) {
            measure.getSegment(start, end, segmentPath, true)
        } else {
            measure.getSegment(start, measure.length, segmentPath, true)
            measure.getSegment(0f, end - measure.length, segmentPath, true)
        }
        paint.color = color
        paint.strokeWidth = dp(6.8f)
        canvas.drawPath(segmentPath, paint)
        canvas.restore()
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
