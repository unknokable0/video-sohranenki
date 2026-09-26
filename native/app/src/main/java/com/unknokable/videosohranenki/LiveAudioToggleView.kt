package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator

class LiveAudioToggleView(
    context: Context,
    private val accentColor: Int
) : View(context) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.8f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }
    private val speakerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val speakerPath = Path()
    private var mutedProgress = 0f
    private var animator: ValueAnimator? = null
    var isMuted: Boolean = false
        private set

    var onMutedChanged: ((Boolean) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Выключить звук"
    }

    fun setMuted(muted: Boolean, animate: Boolean = true, notify: Boolean = false) {
        if (isMuted == muted && ((muted && mutedProgress >= .999f) || (!muted && mutedProgress <= .001f))) return
        isMuted = muted
        contentDescription = if (muted) "Включить звук" else "Выключить звук"

        animator?.cancel()
        val target = if (muted) 1f else 0f
        if (!animate) {
            mutedProgress = target
            invalidate()
            if (notify) onMutedChanged?.invoke(muted)
            return
        }

        animator = ValueAnimator.ofFloat(mutedProgress, target).apply {
            duration = 180L
            interpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)
            addUpdateListener {
                mutedProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        if (notify) onMutedChanged?.invoke(muted)
    }

    override fun performClick(): Boolean {
        super.performClick()
        setMuted(!isMuted, animate = true, notify = true)
        animate().cancel()
        scaleX = 0.94f
        scaleY = 0.94f
        animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(150L)
            .setInterpolator(PathInterpolator(0.22f, 1f, 0.36f, 1f))
            .start()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animate().cancel()
                animate().scaleX(0.94f).scaleY(0.94f).setDuration(55L).start()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isPointInside(event.x, event.y)) performClick()
                else animateBack()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                animateBack()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val radius = minOf(w, h) * .5f
        bgPaint.color = withAlpha(accentColor, 200)
        canvas.drawCircle(w * .5f, h * .5f, radius, bgPaint)

        val cx = w * .5f
        val cy = h * .5f
        val unit = minOf(w, h) / 42f

        speakerPath.reset()
        speakerPath.moveTo(cx - 9f * unit, cy - 4f * unit)
        speakerPath.lineTo(cx - 5f * unit, cy - 4f * unit)
        speakerPath.lineTo(cx + 1f * unit, cy - 9f * unit)
        speakerPath.lineTo(cx + 1f * unit, cy + 9f * unit)
        speakerPath.lineTo(cx - 5f * unit, cy + 4f * unit)
        speakerPath.lineTo(cx - 9f * unit, cy + 4f * unit)
        speakerPath.close()
        canvas.drawPath(speakerPath, speakerPaint)

        val waveAlpha = ((1f - mutedProgress) * 255f).toInt().coerceIn(0, 255)
        iconPaint.color = withAlpha(Color.WHITE, waveAlpha)
        iconPaint.strokeWidth = 1.7f * unit
        val wave1 = RectF(cx - 1f * unit, cy - 7f * unit, cx + 9f * unit, cy + 7f * unit)
        canvas.drawArc(wave1, -48f, 96f, false, iconPaint)
        val wave2 = RectF(cx - 2f * unit, cy - 11f * unit, cx + 15f * unit, cy + 11f * unit)
        canvas.drawArc(wave2, -43f, 86f, false, iconPaint)

        if (mutedProgress > .001f) {
            iconPaint.color = withAlpha(Color.WHITE, (mutedProgress * 255f).toInt().coerceIn(0, 255))
            iconPaint.strokeWidth = 2.1f * unit
            val shift = (1f - mutedProgress) * 3f * unit
            canvas.drawLine(
                cx - 8f * unit + shift,
                cy - 9f * unit,
                cx + 11f * unit + shift,
                cy + 9f * unit,
                iconPaint
            )
        }
    }

    private fun animateBack() {
        animate().cancel()
        animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(140L)
            .setInterpolator(PathInterpolator(0.22f, 1f, 0.36f, 1f))
            .start()
    }

    private fun isPointInside(x: Float, y: Float): Boolean =
        x >= 0f && x <= width && y >= 0f && y <= height

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
