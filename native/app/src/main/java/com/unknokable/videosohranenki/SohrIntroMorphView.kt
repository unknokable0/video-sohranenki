package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
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

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val coreRect = RectF()

    private var palette: ThemePalette? = null
    private var pageProgress = 0f
    private var ambient = 0f
    private var animationsEnabled = true

    private val ambientAnimator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 5_600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                ambient = it.animatedValue as Float
                invalidate()
            }
        }

    companion object {
        private const val COLLAPSE_END = 0.44f
        private const val EXPAND_START = 0.56f
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
        if (animationsEnabled && !ambientAnimator.isRunning) {
            ambientAnimator.start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (drawables.isEmpty()) return

        val base =
            floor(pageProgress)
                .toInt()
                .coerceIn(0, drawables.lastIndex)

        val next =
            (base + 1)
                .coerceAtMost(drawables.lastIndex)

        val raw =
            (pageProgress - base)
                .coerceIn(0f, 1f)

        if (base == next || raw <= 0.0001f) {
            drawSettledIcon(canvas, base)
            return
        }

        drawTransition(
            canvas = canvas,
            from = base,
            to = next,
            progress = smooth(raw)
        )
    }

    /**
     * Two-phase morph. The completed old and new illustrations are never
     * painted in the same frame.
     */
    private fun drawTransition(
        canvas: Canvas,
        from: Int,
        to: Int,
        progress: Float
    ) {
        when {
            progress < COLLAPSE_END -> {
                val p = smooth(progress / COLLAPSE_END)

                drawTransformedIcon(
                    canvas = canvas,
                    page = from,
                    alpha = 1f - smoothStep(0.76f, 1f, p),
                    scaleX = lerp(1f, 0.13f, p),
                    scaleY = lerp(1f, 0.82f, p),
                    rotation = -2.4f * p
                )

                drawMorphCore(
                    canvas = canvas,
                    alpha = smoothStep(0.70f, 1f, p),
                    phase = p
                )
            }

            progress <= EXPAND_START -> {
                val middle =
                    (progress - COLLAPSE_END) /
                        (EXPAND_START - COLLAPSE_END)

                drawMorphCore(
                    canvas = canvas,
                    alpha = 1f,
                    phase = 0.88f + 0.12f * middle
                )
            }

            else -> {
                val p =
                    smooth(
                        (progress - EXPAND_START) /
                            (1f - EXPAND_START)
                    )

                drawMorphCore(
                    canvas = canvas,
                    alpha = 1f - smoothStep(0f, 0.30f, p),
                    phase = 1f - p
                )

                drawTransformedIcon(
                    canvas = canvas,
                    page = to,
                    alpha = smoothStep(0.14f, 0.42f, p),
                    scaleX = lerp(0.13f, 1f, p),
                    scaleY = lerp(0.82f, 1f, p),
                    rotation = 2.4f * (1f - p)
                )
            }
        }
    }

    private fun drawSettledIcon(
        canvas: Canvas,
        page: Int
    ) {
        drawTransformedIcon(
            canvas = canvas,
            page = page,
            alpha = 1f,
            scaleX = 1f,
            scaleY = 1f,
            rotation = 0f
        )
    }

    private fun drawTransformedIcon(
        canvas: Canvas,
        page: Int,
        alpha: Float,
        scaleX: Float,
        scaleY: Float,
        rotation: Float
    ) {
        if (alpha <= 0.002f) return

        val drawable =
            drawables[page.coerceIn(0, drawables.lastIndex)]

        val cx = width / 2f
        val cy = height / 2f

        val size = dp(164f)

        val ambientPhase =
            ambient * PI.toFloat() * 2f

        val floatY =
            if (animationsEnabled) {
                sin(
                    ambientPhase +
                        page * 0.62f
                ) * dp(1.25f)
            } else {
                0f
            }

        val breathe =
            if (animationsEnabled) {
                1f +
                    sin(
                        ambientPhase +
                            page * 0.41f
                    ) * 0.0045f
            } else {
                1f
            }

        val centerY = cy + floatY

        drawable.bounds =
            Rect(
                (cx - size / 2f).toInt(),
                (centerY - size / 2f).toInt(),
                (cx + size / 2f).toInt(),
                (centerY + size / 2f).toInt()
            )

        val oldAlpha = drawable.alpha
        drawable.alpha =
            (alpha * 255f)
                .toInt()
                .coerceIn(0, 255)

        canvas.save()
        canvas.rotate(rotation, cx, centerY)
        canvas.scale(
            scaleX * breathe,
            scaleY * breathe,
            cx,
            centerY
        )
        drawable.draw(canvas)
        canvas.restore()

        drawable.alpha = oldAlpha
    }

    private fun drawMorphCore(
        canvas: Canvas,
        alpha: Float,
        phase: Float
    ) {
        val p = palette ?: return
        if (alpha <= 0.002f) return

        val cx = width / 2f
        val cy = height / 2f

        val pulse =
            if (animationsEnabled) {
                0.5f +
                    0.5f *
                    sin(ambient * PI.toFloat() * 2f)
            } else {
                0.5f
            }

        val w =
            dp(
                42f +
                    34f * (1f - kotlin.math.abs(phase - 0.5f) * 2f) +
                    2f * pulse
            )

        val h =
            dp(
                78f -
                    18f * (1f - kotlin.math.abs(phase - 0.5f) * 2f)
            )

        coreRect.set(
            cx - w / 2f,
            cy - h / 2f,
            cx + w / 2f,
            cy + h / 2f
        )

        corePaint.shader =
            LinearGradient(
                coreRect.left,
                coreRect.top,
                coreRect.right,
                coreRect.bottom,
                Color.rgb(181, 198, 255),
                p.accent,
                Shader.TileMode.CLAMP
            )

        corePaint.alpha =
            (255f * alpha)
                .toInt()
                .coerceIn(0, 255)

        canvas.drawRoundRect(
            coreRect,
            minOf(w, h) * 0.42f,
            minOf(w, h) * 0.42f,
            corePaint
        )

        corePaint.shader = null
        corePaint.alpha = 255
    }

    private fun lerp(
        from: Float,
        to: Float,
        t: Float
    ): Float =
        from + (to - from) * t

    private fun smooth(value: Float): Float =
        value * value * (3f - 2f * value)

    private fun smoothStep(
        edge0: Float,
        edge1: Float,
        value: Float
    ): Float {
        if (edge0 == edge1) {
            return if (value >= edge1) 1f else 0f
        }
        val t =
            ((value - edge0) / (edge1 - edge0))
                .coerceIn(0f, 1f)
        return smooth(t)
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}
