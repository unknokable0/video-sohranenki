package com.unknokable.videosohranenki

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Five original 1254 x 1254 lossless 3D icons. Every resource is loaded
 * exactly once as a stable ImageView. A swipe changes only alpha, never
 * ImageView contents, preventing old-page flashes and mid-swipe decoding.
 */
class SohrIntroMorphView(context: Context) : FrameLayout(context) {
    private val artwork = intArrayOf(
        R.drawable.sohr_intro_saved,
        R.drawable.sohr_intro_streak,
        R.drawable.sohr_intro_player,
        R.drawable.sohr_intro_downloads,
        R.drawable.sohr_intro_settings
    )

    private val descriptions = arrayOf(
        "Сохранённые видео",
        "Streak и прогресс",
        "Удобный просмотр",
        "Загрузки и коллекции",
        "Настройка под себя"
    )

    private val layers: Array<ImageView> = Array(artwork.size) {
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = false
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }
    }

    private var pageProgress = 0f
    private var animationsEnabled = true

    init {
        clipChildren = false
        clipToPadding = false

        // The heavy lossless artworks are decoded during initialization,
        // never inside onPageScrolled. No view gets a different picture.
        layers.forEachIndexed { index, image ->
            image.setImageResource(artwork[index])
            image.alpha = if (index == 0) 1f else 0f
            image.visibility = if (index == 0) View.VISIBLE else View.INVISIBLE
            addView(image)
        }
        contentDescription = descriptions[0]
    }

    fun setPalette(@Suppress("UNUSED_PARAMETER") palette: ThemePalette) {
        // Keep exact original artwork colors in both themes.
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        animationsEnabled = enabled
        updateArtwork()
    }

    fun setPageProgress(value: Float) {
        val clamped = value.coerceIn(0f, (artwork.size - 1).toFloat())
        pageProgress = clamped
        contentDescription = descriptions[clamped.roundToInt().coerceIn(0, artwork.lastIndex)]
        updateArtwork()
    }

    private fun updateArtwork() {
        val position = if (animationsEnabled) {
            pageProgress
        } else {
            pageProgress.roundToInt().toFloat()
        }
        val from = floor(position).toInt().coerceIn(0, artwork.lastIndex)
        val to = (from + 1).coerceAtMost(artwork.lastIndex)
        val fraction = (position - from).coerceIn(0f, 1f)

        // Stable pages; no image swaps at the integer page boundary.
        // Both forward and backward swipes use the same progress math.
        layers.forEachIndexed { index, image ->
            val targetAlpha = when {
                index == from -> if (from == to) 1f else 1f - fraction
                index == to -> fraction
                else -> 0f
            }.coerceIn(0f, 1f)

            // Set alpha before visibility so a newly revealed layer can
            // never briefly draw at its previous opacity.
            if (abs(image.alpha - targetAlpha) > 0.001f) {
                image.alpha = targetAlpha
            }
            val desiredVisibility =
                if (targetAlpha <= 0.001f) View.INVISIBLE else View.VISIBLE
            if (image.visibility != desiredVisibility) {
                image.visibility = desiredVisibility
            }
        }
    }
}
