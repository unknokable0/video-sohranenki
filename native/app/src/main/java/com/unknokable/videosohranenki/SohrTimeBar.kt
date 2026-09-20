package com.unknokable.videosohranenki

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToLong

/** Lightweight YouTube-like timeline: played + buffered + scrubber with a larger touch target. */
class SohrTimeBar(context: Context) : View(context) {
    interface Listener {
        fun onScrubStart(positionMs: Long)
        fun onScrubMove(positionMs: Long, fraction: Float)
        fun onScrubStop(positionMs: Long, canceled: Boolean)
    }

    var listener: Listener? = null
    private var durationMs = 0L
    private var positionMs = 0L
    private var bufferedMs = 0L
    private var scrubbing = false
    private var scrubPositionMs = 0L

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(105, 255, 255, 255) }
    private val bufferPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 255, 255, 255) }
    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(167, 139, 250) }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(167, 139, 250) }
    private val rect = RectF()

    fun setProgress(positionMs: Long, durationMs: Long, bufferedPositionMs: Long) {
        this.durationMs = durationMs.coerceAtLeast(0L)
        if (!scrubbing) this.positionMs = positionMs.coerceIn(0L, this.durationMs.coerceAtLeast(0L))
        this.bufferedMs = bufferedPositionMs.coerceIn(0L, this.durationMs.coerceAtLeast(0L))
        invalidate()
    }

    private fun dp(v: Float) = v * density
    private fun fractionFor(ms: Long): Float = if (durationMs > 0) (ms.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    private fun positionFor(x: Float): Long {
        if (durationMs <= 0L) return 0L
        val fraction = (x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
        return (durationMs * fraction).roundToLong()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = dp(30f).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(desired, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0) return
        val centerY = height / 2f
        val trackH = dp(if (scrubbing) 4f else 3f)
        val radius = trackH / 2f
        val played = if (scrubbing) scrubPositionMs else positionMs
        val playedX = width * fractionFor(played)
        val bufferedX = width * fractionFor(bufferedMs)

        rect.set(0f, centerY - trackH / 2f, width.toFloat(), centerY + trackH / 2f)
        canvas.drawRoundRect(rect, radius, radius, trackPaint)
        if (bufferedX > 0f) {
            rect.right = bufferedX
            canvas.drawRoundRect(rect, radius, radius, bufferPaint)
        }
        if (playedX > 0f) {
            rect.right = playedX
            canvas.drawRoundRect(rect, radius, radius, playedPaint)
        }
        if (scrubbing) {
            canvas.drawCircle(playedX.coerceIn(dp(7f), width - dp(7f)), centerY, dp(7f), thumbPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (durationMs <= 0L || width <= 0) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                scrubbing = true
                scrubPositionMs = positionFor(event.x)
                listener?.onScrubStart(scrubPositionMs)
                listener?.onScrubMove(scrubPositionMs, fractionFor(scrubPositionMs))
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                scrubPositionMs = positionFor(event.x)
                listener?.onScrubMove(scrubPositionMs, fractionFor(scrubPositionMs))
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val canceled = event.actionMasked == MotionEvent.ACTION_CANCEL
                if (!canceled) scrubPositionMs = positionFor(event.x)
                positionMs = if (canceled) positionMs else scrubPositionMs
                scrubbing = false
                parent?.requestDisallowInterceptTouchEvent(false)
                listener?.onScrubStop(positionMs, canceled)
                invalidate()
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
