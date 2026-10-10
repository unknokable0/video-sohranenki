package com.unknokable.videosohranenki

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Base64
import android.view.View
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The five ACTUAL illustrations cropped from the approved SOHR screen preview.
 *
 * The reference image is stored losslessly at the Git object level as six
 * consecutive ASCII Base64 asset chunks. It is decoded once per view and
 * rendered with a subtle page crossfade. No hand-drawn substitute icons.
 */
class SohrIntroMorphView(context: Context) : View(context) {
    private val bitmapPaint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
    )

    private var pageProgress = 0f
    private var animationsEnabled = true
    private var palette: ThemePalette? = null

    // 5 x 254 px icon tiles, each 248 px tall.
    private val iconAtlas: Bitmap? by lazy(LazyThreadSafetyMode.NONE) {
        runCatching {
            val encoded = buildString(16_196) {
                for (part in 1..6) {
                    val file = "sohr_intro_reference_${part}.b64"
                    context.assets.open(file).bufferedReader(Charsets.US_ASCII).use {
                        append(it.readText().trim())
                    }
                }
            }
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?.takeIf { it.width == 1_270 && it.height == 248 }
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
        if (palette == null) return
        val atlas = iconAtlas ?: return
        if (width <= 0 || height <= 0) return

        val progress = if (animationsEnabled) {
            pageProgress
        } else {
            pageProgress.roundToInt().toFloat()
        }
        val from = floor(progress).toInt().coerceIn(0, 4)
        val to = (from + 1).coerceAtMost(4)
        val fraction = (progress - from).coerceIn(0f, 1f)

        // Both source tiles have identical bounds and a fixed aspect ratio.
        // No interpolation of paths or individual parts of the illustration.
        if (fraction < 0.001f || from == to) {
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
        if (opacity < 0.001f) return
        val tileWidth = atlas.width / 5
        val source = Rect(
            index * tileWidth,
            0,
            (index + 1) * tileWidth,
            atlas.height
        )

        // Fit inside the hero view without stretching or clipping.
        val desiredRatio = tileWidth.toFloat() / atlas.height
        val square = minOf(width, height).toFloat()
        val fitHeight = square / desiredRatio
        val destination = RectF(
            (width - square) * 0.5f,
            (height - fitHeight) * 0.5f,
            (width + square) * 0.5f,
            (height + fitHeight) * 0.5f
        )
        bitmapPaint.alpha = (opacity * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawBitmap(atlas, source, destination, bitmapPaint)
    }
}
