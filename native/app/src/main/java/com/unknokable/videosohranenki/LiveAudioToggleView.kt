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
    private val accentColor: Int,
    private val controlBackgroundColor: Int = accentColor,
    private val controlIconColor: Int = Color.WHITE
) : View(context) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val speakerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = controlIconColor
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = controlIconColor
    }
    private val speaker = Path()

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

    fun setMuted(
        muted: Boolean,
        animate: Boolean = true,
        notify: Boolean = false
    ) {
        isMuted = muted
        contentDescription = if (muted) "Включить звук" else "Выключить звук"

        animator?.cancel()
        val target = if (muted) 1f else 0f

        if (!animate) {
            mutedProgress = target
            invalidate()
        } else {
            animator = ValueAnimator.ofFloat(mutedProgress, target).apply {
                duration = 190L
                interpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)
                addUpdateListener {
                    mutedProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        if (notify) onMutedChanged?.invoke(muted)
    }

    override fun performClick(): Boolean {
        super.performClick()
        setMuted(!isMuted, animate = true, notify = true)

        animate().cancel()
        animate()
            .scaleX(0.90f)
            .scaleY(0.90f)
            .setDuration(55L)
            .withEndAction {
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(145L)
                    .setInterpolator(PathInterpolator(0.22f, 1f, 0.36f, 1f))
                    .start()
            }
            .start()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                if (event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()) {
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = minOf(width, height).toFloat()
        if (size <= 0f) return

        val cx = width / 2f
        val cy = height / 2f
        val u = size / 44f

        backgroundPaint.color = controlBackgroundColor
        canvas.drawCircle(cx, cy, size * 0.5f, backgroundPaint)

        speaker.reset()
        speaker.moveTo(cx - 8.5f * u, cy - 4.2f * u)
        speaker.lineTo(cx - 4.4f * u, cy - 4.2f * u)
        speaker.lineTo(cx + 1.8f * u, cy - 9.0f * u)
        speaker.lineTo(cx + 1.8f * u, cy + 9.0f * u)
        speaker.lineTo(cx - 4.4f * u, cy + 4.2f * u)
        speaker.lineTo(cx - 8.5f * u, cy + 4.2f * u)
        speaker.close()
        canvas.drawPath(speaker, speakerPaint)

        linePaint.strokeWidth = 1.8f * u

        val soundAlpha = ((1f - mutedProgress) * 255f).toInt().coerceIn(0, 255)
        linePaint.color = withAlpha(controlIconColor, soundAlpha)

        val innerWave = RectF(
            cx - 1f * u,
            cy - 6f * u,
            cx + 8f * u,
            cy + 6f * u
        )
        canvas.drawArc(innerWave, -46f, 92f, false, linePaint)

        val outerWave = RectF(
            cx - 2f * u,
            cy - 10f * u,
            cx + 13f * u,
            cy + 10f * u
        )
        canvas.drawArc(outerWave, -42f, 84f, false, linePaint)

        if (mutedProgress > 0.001f) {
            linePaint.color = withAlpha(
                controlIconColor,
                (mutedProgress * 255f).toInt().coerceIn(0, 255)
            )
            linePaint.strokeWidth = 2.0f * u

            val xCenter = cx + 8.2f * u
            val arm = 3.6f * u
            canvas.drawLine(
                xCenter - arm,
                cy - arm,
                xCenter + arm,
                cy + arm,
                linePaint
            )
            canvas.drawLine(
                xCenter + arm,
                cy - arm,
                xCenter - arm,
                cy + arm,
                linePaint
            )
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}
