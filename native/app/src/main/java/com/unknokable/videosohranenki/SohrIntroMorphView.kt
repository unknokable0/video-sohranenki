package com.unknokable.videosohranenki

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Approved SOHR hero illustrations from the original five-screen preview.
 *
 * Version 6.9.38 embeds a 3x high-quality, alpha-cleaned bitmap directly
 * in assets. The old 14 KB low-quality compressed base64 atlas is no longer
 * used. A ViewPager scroll changes only opacity between independent icons.
 */
class SohrIntroMorphView(context: Context) : View(context) {
    private val bitmapPaint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG
    )

    private var pageProgress = 0f
    private var animationsEnabled = true
    private var palette: ThemePalette? = null

    private val iconAtlas: Bitmap? by lazy(LazyThreadSafetyMode.NONE) {
        runCatching {
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.assets.open("sohr_intro_highquality.webp").use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }?.takeIf { atlas ->
                atlas.width == 3_810 && atlas.height == 744
            }
        }.getOrNull()
    }

    fun setPalette(value: ThemePalette) {
        palette = value
        invalidate()
    }

    fun setAnimationsEnabled(value: Boolean) {
        animationsEnabled = value
        invalidate()
    }

    fun setPageProgress(value: Float) {
        pageProgress = value.coerceIn(0f, 4f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (palette == null || width <= 0 || height <= 0) return
        val atlas = iconAtlas ?: return

        val progress = if (animationsEnabled) pageProgress
            else pageProgress.roundToInt().toFloat()
        val from = floor(progress).toInt().coerceIn(0, 4)
        val to = (from + 1).coerceAtMost(4)
        val fraction = (progress - from).coerceIn(0f, 1f)

        // Preserve exact aspect ratio: no stretching, masks or shadow filters.
        if (fraction <= 0.001f || from == to) {
            drawIcon(canvas, atlas, from, 1f)
        } else {
            drawIcon(canvas, atlas, from, 1f - fraction)
            drawIcon(canvas, atlas, to, fraction)
        }
        bitmapPaint.alpha = 255
    }

    private fun drawIcon(
        canvas: Canvas,
        atlas: Bitmap,
        index: Int,
        opacity: Float
    ) {
        if (opacity <= 0.001f) return
        val tileWidth = atlas.width / 5
        val source = Rect(
            index * tileWidth,
            0,
            (index + 1) * tileWidth,
            atlas.height
        )

        val ratio = tileWidth.toFloat() / atlas.height
        val maxWidth = minOf(width.toFloat(), height.toFloat() * ratio)
        val fittedHeight = maxWidth / ratio
        val destination = RectF(
            (width - maxWidth) * 0.5f,
            (height - fittedHeight) * 0.5f,
            (width + maxWidth) * 0.5f,
            (height + fittedHeight) * 0.5f
        )

        bitmapPaint.alpha = (255f * opacity).roundToInt().coerceIn(0, 255)
        canvas.drawBitmap(atlas, source, destination, bitmapPaint)
    }
}
