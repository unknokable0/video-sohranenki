package com.unknokable.videosohranenki

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The five original 1254 x 1254 3D illustrations approved for SOHR.
 *
 * Images are pixel-identical lossless WebP, placed in drawable-nodpi.
 * FIT_CENTER preserves the exact artwork geometry and transparent margins.
 * Only the opacity of two real ImageViews changes during pager swipes.
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

    private fun image(): ImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = false
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        )
    }

    private val current = image()
    private val upcoming = image()
    private var currentIndex = -1
    private var upcomingIndex = -1
    private var progress = 0f
    private var animationsEnabled = true

    init {
        clipChildren = false
        clipToPadding = false
        addView(current)
        addView(upcoming)
        current.alpha = 1f
        upcoming.alpha = 0f
        contentDescription = descriptions[0]
    }

    fun setPalette(@Suppress("UNUSED_PARAMETER") palette: ThemePalette) {
        // The original standalone illustrations have their own approved colors.
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        animationsEnabled = enabled
        showCurrentProgress()
    }

    fun setPageProgress(value: Float) {
        progress = value.coerceIn(0f, 4f)
        contentDescription = descriptions[progress.roundToInt().coerceIn(0, 4)]
        showCurrentProgress()
    }

    private fun showCurrentProgress() {
        val position = if (animationsEnabled) progress
            else progress.roundToInt().toFloat()
        val from = floor(position).toInt().coerceIn(0, 4)
        val next = (from + 1).coerceAtMost(4)
        val amount = (position - from).coerceIn(0f, 1f)

        // Never re-decode on each animation frame: only on page changes.
        if (from != currentIndex) {
            current.setImageResource(artwork[from])
            currentIndex = from
        }
        if (next != upcomingIndex) {
            upcoming.setImageResource(artwork[next])
            upcomingIndex = next
        }
        current.alpha = 1f - amount
        upcoming.alpha = if (from == next) 0f else amount
    }
}
