package com.unknokable.videosohranenki

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator

class SohrLiveControlView(
    context: Context,
    private val mode: Mode,
    private val backgroundColor: Int,
    private val iconColor: Int = Color.WHITE
) : View(context) {

    enum class Mode {
        BACK,
        PLAY_PAUSE,
        FULLSCREEN
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val path = Path()

    private var playing = true
    private var fullscreen = false

    var onTap: (() -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        updateContentDescription()
    }

    fun setPlaying(value: Boolean) {
        if (playing == value) return
        playing = value
        updateContentDescription()
        invalidate()
    }

    fun setFullscreen(value: Boolean) {
        if (fullscreen == value) return
        fullscreen = value
        updateContentDescription()
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTap?.invoke()
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
            MotionEvent.ACTION_DOWN -> {
                animate().cancel()
                animate().scaleX(0.94f).scaleY(0.94f).setDuration(45L).start()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()) {
                    performClick()
                } else {
                    animateBack()
                }
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
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return

        val cx = width / 2f
        val cy = height / 2f
        val r = size * 0.5f

        bgPaint.color = backgroundColor
        canvas.drawCircle(cx, cy, r, bgPaint)

        iconPaint.color = iconColor
        fillPaint.color = iconColor

        when (mode) {
            Mode.BACK -> drawBack(canvas, cx, cy, size)
            Mode.PLAY_PAUSE -> drawPlayPause(canvas, cx, cy, size)
            Mode.FULLSCREEN -> drawFullscreen(canvas, cx, cy, size)
        }
    }

    private fun drawBack(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val u = size / 40f
        iconPaint.strokeWidth = 2.25f * u
        path.reset()
        path.moveTo(cx + 7f * u, cy - 9f * u)
        path.lineTo(cx - 3f * u, cy)
        path.lineTo(cx + 7f * u, cy + 9f * u)
        canvas.drawPath(path, iconPaint)
    }

    private fun drawPlayPause(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val u = size / 48f
        if (playing) {
            val halfW = 2.2f * u
            val halfH = 8.5f * u
            canvas.drawRoundRect(
                cx - 6.0f * u,
                cy - halfH,
                cx - 1.6f * u,
                cy + halfH,
                1.8f * u,
                1.8f * u,
                fillPaint
            )
            canvas.drawRoundRect(
                cx + 1.6f * u,
                cy - halfH,
                cx + 6.0f * u,
                cy + halfH,
                1.8f * u,
                1.8f * u,
                fillPaint
            )
        } else {
            path.reset()
            path.moveTo(cx - 5.5f * u, cy - 9f * u)
            path.lineTo(cx + 9f * u, cy)
            path.lineTo(cx - 5.5f * u, cy + 9f * u)
            path.close()
            canvas.drawPath(path, fillPaint)
        }
    }

    private fun drawFullscreen(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val u = size / 40f
        iconPaint.strokeWidth = 2.0f * u
        val outer = 9f * u
        val inner = 3f * u

        if (!fullscreen) {
            drawCorner(canvas, cx - outer, cy - outer, +1f, +1f, inner)
            drawCorner(canvas, cx + outer, cy - outer, -1f, +1f, inner)
            drawCorner(canvas, cx - outer, cy + outer, +1f, -1f, inner)
            drawCorner(canvas, cx + outer, cy + outer, -1f, -1f, inner)
        } else {
            drawCorner(canvas, cx - inner, cy - inner, -1f, -1f, outer - inner)
            drawCorner(canvas, cx + inner, cy - inner, +1f, -1f, outer - inner)
            drawCorner(canvas, cx - inner, cy + inner, -1f, +1f, outer - inner)
            drawCorner(canvas, cx + inner, cy + inner, +1f, +1f, outer - inner)
        }
    }

    private fun drawCorner(
        canvas: Canvas,
        x: Float,
        y: Float,
        xDir: Float,
        yDir: Float,
        len: Float
    ) {
        path.reset()
        path.moveTo(x, y + yDir * len)
        path.lineTo(x, y)
        path.lineTo(x + xDir * len, y)
        canvas.drawPath(path, iconPaint)
    }

    private fun updateContentDescription() {
        contentDescription = when (mode) {
            Mode.BACK -> "Назад"
            Mode.PLAY_PAUSE -> if (playing) "Пауза" else "Продолжить"
            Mode.FULLSCREEN -> if (fullscreen) "Выйти из полного экрана" else "Полный экран"
        }
    }

    private fun animateBack() {
        animate().cancel()
        animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(130L)
            .setInterpolator(PathInterpolator(0.22f, 1f, 0.36f, 1f))
            .start()
    }
}
