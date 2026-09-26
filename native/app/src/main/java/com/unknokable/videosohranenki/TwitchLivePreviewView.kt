package com.unknokable.videosohranenki

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
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
    private val loader = LoadingWaveView(context, palette.accent)
    private val audioToggle = LiveAudioToggleView(
        context,
        palette.accent,
        palette.accentSoft,
        Color.WHITE
    )

    private var player: ExoPlayer? = null
    private var released = false
    private var muted = true

    init {
        background = rounded(Color.BLACK, 24)
        clipToOutline = true
        isClickable = true
        isFocusable = true
        elevation = dp(1).toFloat()

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

        val shade = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.parseColor("#72000000"), Color.TRANSPARENT)
            )
        }
        addView(
            shade,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(90), Gravity.BOTTOM)
        )

        loader.alpha = 0.94f
        addView(loader, LayoutParams(dp(44), dp(44), Gravity.CENTER))

        addView(
            buildLiveBadge(),
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(10)
                topMargin = dp(10)
            }
        )

        audioToggle.apply {
            setMuted(true, animate = false)
            onMutedChanged = { nowMuted ->
                muted = nowMuted
                player?.volume = if (nowMuted) 0f else 1f
            }
        }
        addView(
            audioToggle,
            LayoutParams(dp(40), dp(40), Gravity.TOP or Gravity.END).apply {
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
            setPadding(dp(10), 0, dp(10), 0)
            background = rounded(Color.parseColor("#9A17151D"), 12)
        }
        addView(
            viewers,
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30), Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = dp(10)
                bottomMargin = dp(10)
            }
        )

        setOnClickListener {
            if (animationsEnabled) {
                animate().cancel()
                animate()
                    .scaleX(0.985f)
                    .scaleY(0.985f)
                    .setDuration(55L)
                    .withEndAction {
                        animate().scaleX(1f).scaleY(1f).setDuration(130L).start()
                        onOpen()
                    }
                    .start()
            } else {
                onOpen()
            }
        }

        start()
    }

    private fun buildLiveBadge(): View {
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(11), 0)
            background = rounded(Color.parseColor("#B43B3943"), 14)
        }

        val pulse = LivePulseView(context).apply {
            setState(true, animationsEnabled)
        }
        pill.addView(
            pulse,
            LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                marginEnd = dp(4)
            }
        )
        pill.addView(TextView(context).apply {
            text = "В эфире"
            textSize = 10.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        return pill
    }

    private fun start() {
        scope.launch {
            try {
                val hls = TwitchVodResolver.resolveLive(live.login)
                if (released) return@launch

                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(3_000, 12_000, 300, 650)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()

                val renderersFactory = DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(true)

                val exo = ExoPlayer.Builder(context, renderersFactory)
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
                            .setDuration(if (animationsEnabled) 170L else 0L)
                            .withEndAction { poster.visibility = View.GONE }
                            .start()
                    }
                })
            } catch (_: Exception) {
                loader.visibility = View.GONE
            }
        }
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
