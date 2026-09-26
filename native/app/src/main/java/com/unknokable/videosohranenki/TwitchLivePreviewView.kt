package com.unknokable.videosohranenki

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
class TwitchLivePreviewView(
    context: Context,
    private val live: TwitchLiveStream,
    private val palette: ThemePalette,
    private val animationsEnabled: Boolean,
    private val onOpen: () -> Unit
) : FrameLayout(context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playerView = PlayerView(context)
    private val poster = ImageView(context)
    private val loader = LoadingWaveView(context, Color.parseColor("#FF5F7E"))
    private val muteButton = ImageButton(context)
    private var player: ExoPlayer? = null
    private var released = false
    private var muted = true

    init {
        setBackgroundColor(Color.BLACK)
        background = rounded(Color.BLACK, 18)
        clipToOutline = true
        isClickable = true
        isFocusable = true

        playerView.apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            setBackgroundColor(Color.BLACK)
        }
        addView(
            playerView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        poster.apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.BLACK)
            if (live.thumbnailUrl.isNotBlank()) {
                load(live.thumbnailUrl) {
                    crossfade(animationsEnabled)
                }
            }
        }
        addView(
            poster,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        loader.alpha = 0.92f
        addView(loader, LayoutParams(dp(46), dp(46), Gravity.CENTER))

        val liveBadge = TextView(context).apply {
            text = "LIVE"
            textSize = 10.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(9), 0, dp(9), 0)
            background = rounded(Color.parseColor("#E84F70"), 11)
        }
        addView(
            liveBadge,
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(10)
                topMargin = dp(10)
            }
        )

        val pulse = LivePulseView(context).apply {
            setState(true, animationsEnabled)
        }
        addView(
            pulse,
            LayoutParams(dp(24), dp(24), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(58)
                topMargin = dp(12)
            }
        )

        muteButton.apply {
            setImageResource(R.drawable.ic_volume_off)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = rounded(Color.parseColor("#8A0A0A0E"), 19)
            setPadding(dp(9), dp(9), dp(9), dp(9))
            contentDescription = "Включить звук"
            setOnClickListener {
                toggleMute()
            }
        }
        addView(
            muteButton,
            LayoutParams(dp(38), dp(38), Gravity.TOP or Gravity.END).apply {
                rightMargin = dp(10)
                topMargin = dp(10)
            }
        )

        val viewers = TextView(context).apply {
            text = live.viewerCount.toString() + " зрителей"
            textSize = 10.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(9), 0, dp(9), 0)
            background = rounded(Color.parseColor("#8A0A0A0E"), 11)
        }
        addView(
            viewers,
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28), Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = dp(10)
                bottomMargin = dp(10)
            }
        )

        setOnClickListener { onOpen() }
        start()
    }

    private fun start() {
        scope.launch {
            try {
                val hls = TwitchVodResolver.resolveLive(live.login)
                if (released) return@launch

                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(5_000, 20_000, 350, 800)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()

                val exo = ExoPlayer.Builder(context)
                    .setLoadControl(loadControl)
                    .build()
                player = exo
                playerView.player = exo
                exo.volume = 0f
                exo.playWhenReady = true
                exo.setMediaItem(MediaItem.fromUri(hls))
                exo.prepare()
                exo.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        loader.visibility = View.GONE
                        poster.animate().cancel()
                        poster.animate()
                            .alpha(0f)
                            .setDuration(if (animationsEnabled) 140L else 0L)
                            .withEndAction { poster.visibility = View.GONE }
                            .start()
                    }
                })
            } catch (_: Exception) {
                loader.visibility = View.GONE
            }
        }
    }

    private fun toggleMute() {
        muted = !muted
        player?.volume = if (muted) 0f else 1f
        muteButton.setImageResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on)
        muteButton.contentDescription = if (muted) "Включить звук" else "Выключить звук"
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    fun release() {
        if (released) return
        released = true
        scope.cancel()
        playerView.player = null
        player?.release()
        player = null
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
