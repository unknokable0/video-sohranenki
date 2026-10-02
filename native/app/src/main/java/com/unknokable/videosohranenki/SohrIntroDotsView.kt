package com.unknokable.videosohranenki

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.abs
import kotlin.math.floor

class SohrIntroDotsView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var count = 5
    private var progress = 0f
    private var active = Color.WHITE
    private var inactive = 0x66FFFFFF

    fun setCount(value: Int) {
        count = value.coerceAtLeast(1)
        invalidate()
    }

    fun setPalette(active: Int, inactive: Int) {
        this.active = active
        this.inactive = inactive
        invalidate()
    }

    fun setPageProgress(value: Float) {
        progress = value.coerceIn(0f, (count - 1).toFloat())
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val dot = dp(6f)
        val gap = dp(12f)
        val total = (count - 1) * gap
        val start = width / 2f - total / 2f

        for (i in 0 until count) {
            val distance = abs(progress - i).coerceIn(0f, 1f)
            val selected = 1f - distance
            val radius = dot / 2f + dp(1.5f) * selected

            paint.color = blend(inactive, active, selected)
            canvas.drawCircle(start + i * gap, height / 2f, radius, paint)
        }

        val base = floor(progress).toInt().coerceIn(0, count - 1)
        val next = (base + 1).coerceAtMost(count - 1)
        val t = progress - base
        val x0 = start + base * gap
        val x1 = start + next * gap

        paint.color = active
        paint.strokeWidth = dp(4f)
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x0, height / 2f, x0 + (x1 - x0) * t, height / 2f, paint)
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val inv = 1f - t
        return Color.argb(
            (Color.alpha(a) * inv + Color.alpha(b) * t).toInt(),
            (Color.red(a) * inv + Color.red(b) * t).toInt(),
            (Color.green(a) * inv + Color.green(b) * t).toInt(),
            (Color.blue(a) * inv + Color.blue(b) * t).toInt()
        )
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}
