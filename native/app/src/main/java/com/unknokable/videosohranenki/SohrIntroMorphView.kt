package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

class SohrIntroMorphView(
    context: Context
) : View(context) {
    private val drawables: List<Drawable> =
        listOf(
            R.drawable.ill_intro_cloud_morph,
            R.drawable.ill_intro_feed_morph,
            R.drawable.ill_intro_watch_morph,
            R.drawable.ill_intro_streak_morph,
            R.drawable.ill_intro_tune_morph
        ).mapNotNull {
            ContextCompat.getDrawable(context, it)?.mutate()
        }

    private val accentBridge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bridgeRect = RectF()

    private var palette: ThemePalette? = null
    private var pageProgress = 0f
    private var ambient = 0f
    private var animationsEnabled = true

    private val ambientAnimator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 5_000L
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
            if (!ambientAnimator.isRunning) {
                ambientAnimator.start()
            }
        } else {
            ambientAnimator.cancel()
            ambient = 0f
        }
        invalidate()
    }

    fun setPageProgress(value: Float) {
        pageProgress =
            value.coerceIn(
                0f,
                (drawables.size - 1).toFloat()
            )
        invalidate()
    }

    override fun onDetachedFromWindow() {
        ambientAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animationsEnabled &&
            !ambientAnimator.isRunning
        ) {
            ambientAnimator.start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (drawables.isEmpty()) return

        val base =
            floor(pageProgress)
                .toInt()
                .coerceIn(
                    0,
                    drawables.lastIndex
                )

        val next =
            (base + 1)
                .coerceAtMost(
                    drawables.lastIndex
                )

        val raw =
            (pageProgress - base)
                .coerceIn(0f, 1f)

        val t = smooth(raw)

        if (base == next) {
            drawIcon(
                canvas = canvas,
                drawable = drawables[base],
                page = base,
                alpha = 1f,
                progress = 0f,
                entering = false
            )
            return
        }

        drawBridge(canvas, t)

        drawIcon(
            canvas = canvas,
            drawable = drawables[base],
            page = base,
            alpha = 1f - t,
            progress = t,
            entering = false
        )

        drawIcon(
            canvas = canvas,
            drawable = drawables[next],
            page = next,
            alpha = t,
            progress = 1f - t,
            entering = true
        )
    }

    private fun drawIcon(
        canvas: Canvas,
        drawable: Drawable,
        page: Int,
        alpha: Float,
        progress: Float,
        entering: Boolean
    ) {
        if (alpha <= 0.002f) return

        val cx = width / 2f
        val cy = height / 2f

        val baseSize =
            dp(
                when (page) {
                    0 -> 148f
                    1 -> 154f
                    2 -> 154f
                    3 -> 150f
                    else -> 148f
                }
            )

        val direction =
            if (entering) 1f else -1f

        val squeeze =
            1f - 0.12f * progress

        val stretch =
            1f + 0.05f * progress

        val transitionX =
            dp(18f) *
                progress *
                direction

        val transitionRotation =
            4.2f *
                progress *
                direction

        val ambientPhase =
            ambient * PI.toFloat() * 2f

        val floatY =
            if (animationsEnabled) {
                sin(
                    ambientPhase +
                        page * 0.72f
                ) * dp(1.7f)
            } else {
                0f
            }

        val ambientScale =
            if (animationsEnabled) {
                1f +
                    sin(
                        ambientPhase +
                            page * 0.48f
                    ) * 0.008f
            } else {
                1f
            }

        val centerX = cx + transitionX
        val centerY = cy + floatY

        val left =
            (centerX - baseSize / 2f).toInt()
        val top =
            (centerY - baseSize / 2f).toInt()

        drawable.bounds =
            Rect(
                left,
                top,
                (left + baseSize).toInt(),
                (top + baseSize).toInt()
            )

        val previousAlpha = drawable.alpha
        drawable.alpha =
            (255f * alpha)
                .toInt()
                .coerceIn(0, 255)

        canvas.save()
        canvas.rotate(
            transitionRotation,
            centerX,
            centerY
        )
        canvas.scale(
            squeeze * ambientScale,
            stretch * ambientScale,
            centerX,
            centerY
        )
        drawable.draw(canvas)
        canvas.restore()

        drawable.alpha = previousAlpha
    }

    private fun drawBridge(
        canvas: Canvas,
        t: Float
    ) {
        val p = palette ?: return

        val mid =
            1f -
                kotlin.math.abs(
                    t - 0.5f
                ) * 2f

        if (mid <= 0f) return

        val cx = width / 2f
        val cy = height / 2f

        val widthPx =
            dp(56f + 54f * mid)
        val heightPx =
            dp(18f + 26f * mid)

        bridgeRect.set(
            cx - widthPx / 2f,
            cy - heightPx / 2f,
            cx + widthPx / 2f,
            cy + heightPx / 2f
        )

        accentBridge.color =
            ColorUtils.setAlphaComponent(
                p.accent,
                (54f * mid)
                    .toInt()
                    .coerceIn(0, 54)
            )

        canvas.drawRoundRect(
            bridgeRect,
            heightPx / 2f,
            heightPx / 2f,
            accentBridge
        )

        accentBridge.color =
            ColorUtils.setAlphaComponent(
                Color.WHITE,
                (14f * mid)
                    .toInt()
                    .coerceIn(0, 14)
            )

        val inner =
            heightPx * 0.18f

        canvas.drawCircle(
            cx,
            cy,
            inner,
            accentBridge
        )
    }

    private fun smooth(value: Float): Float =
        value * value * (3f - 2f * value)

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}
