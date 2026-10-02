package com.unknokable.videosohranenki

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import kotlin.math.hypot

object SohrThemeTransition {
    fun reveal(
        host: FrameLayout,
        anchor: View,
        enabled: Boolean,
        applyTheme: () -> Unit
    ) {
        if (!enabled || host.width <= 0 || host.height <= 0) {
            applyTheme()
            return
        }

        val snapshot = runCatching {
            Bitmap.createBitmap(
                host.width,
                host.height,
                Bitmap.Config.ARGB_8888
            ).also { bitmap ->
                host.draw(Canvas(bitmap))
            }
        }.getOrNull()

        if (snapshot == null) {
            applyTheme()
            return
        }

        val hostLocation = IntArray(2)
        val anchorLocation = IntArray(2)
        host.getLocationInWindow(hostLocation)
        anchor.getLocationInWindow(anchorLocation)

        val centerX =
            anchorLocation[0] - hostLocation[0] +
                anchor.width / 2f
        val centerY =
            anchorLocation[1] - hostLocation[1] +
                anchor.height / 2f

        applyTheme()

        val overlay = ThemeSnapshotView(
            host.context,
            snapshot,
            centerX,
            centerY
        )

        host.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        overlay.bringToFront()
        overlay.start {
            if (overlay.parent === host) {
                host.removeView(overlay)
            }
            snapshot.recycle()
        }
    }

    private class ThemeSnapshotView(
        context: android.content.Context,
        private val bitmap: Bitmap,
        private val centerX: Float,
        private val centerY: Float
    ) : View(context) {
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        private var radius = 0f

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        fun start(onEnd: () -> Unit) {
            post {
                val maxRadius = hypot(
                    maxOf(centerX, width - centerX).toDouble(),
                    maxOf(centerY, height - centerY).toDouble()
                ).toFloat() + 8f

                ValueAnimator.ofFloat(0f, maxRadius).apply {
                    duration = 390L
                    interpolator =
                        PathInterpolator(0.2f, 0f, 0f, 1f)
                    addUpdateListener {
                        radius = it.animatedValue as Float
                        invalidate()
                    }
                    addListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(
                                animation: Animator
                            ) {
                                onEnd()
                            }
                        }
                    )
                    start()
                }
            }
        }

        override fun onDraw(canvas: Canvas) {
            val layer = canvas.saveLayer(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                null
            )
            canvas.drawBitmap(bitmap, 0f, 0f, bitmapPaint)
            canvas.drawCircle(
                centerX,
                centerY,
                radius,
                clearPaint
            )
            canvas.restoreToCount(layer)
        }
    }
}
