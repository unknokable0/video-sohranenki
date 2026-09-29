package com.unknokable.videosohranenki

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
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
    private val onOpen: () -> Unit,
    private val onBlocked: (TwitchLiveStream) -> Unit = {}
) : FrameLayout(context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val playerView = LayoutInflater.from(context).inflate(
        R.layout.view_sohr_player,
        this,
        false
    ) as PlayerView
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
    private var blockedReported = false

    private val commercialGuardRunnable: Runnable = object : Runnable {
        override fun run() {
            if (released || blockedReported || !live.testStream) return
            val self = this
            scope.launch {
                val commercial = runCatching {
                    TwitchVodResolver.isCommercialBreak(live.login)
                }.getOrDefault(false)

                if (released || blockedReported) return@launch
                if (commercial) {
                    reportBlocked()
                } else {
                    handler.postDelayed(self, 12_000L)
                }
            }
        }
    }

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
            setKeepContentOnPlayerReset(true)
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
        addView(loader, LayoutParams(dp(38), dp(38), Gravity.CENTER))

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
            LayoutParams(dp(36), dp(36), Gravity.TOP or Gravity.END).apply {
                rightMargin = dp(9)
                topMargin = dp(9)
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
                val hls = TwitchVodResolver.resolveLiveChecked(live.login)
                if (released) return@launch

                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(1_800, 6_500, 350, 650)
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
                exo.setMediaItem(
                    MediaItem.Builder()
                        .setUri(hls)
                        .setLiveConfiguration(
                            MediaItem.LiveConfiguration.Builder()
                                .setTargetOffsetMs(1_200L)
                                .setMinOffsetMs(650L)
                                .setMaxOffsetMs(3_000L)
                                .setMinPlaybackSpeed(0.995f)
                                .setMaxPlaybackSpeed(1.10f)
                                .build()
                        )
                        .build()
                )
                exo.prepare()

                exo.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        loader.visibility = View.GONE
                        if (live.testStream) {
                            handler.removeCallbacks(commercialGuardRunnable)
                            handler.postDelayed(commercialGuardRunnable, 8_000L)
                        }
                        poster.animate().cancel()
                        poster.animate()
                            .alpha(0f)
                            .setDuration(if (animationsEnabled) 170L else 0L)
                            .withEndAction { poster.visibility = View.GONE }
                            .start()
                    }
                })
            } catch (_: TwitchCommercialBreakException) {
                loader.visibility = View.GONE
                reportBlocked()
            } catch (_: Exception) {
                loader.visibility = View.GONE
            }
        }
    }

    private fun reportBlocked() {
        if (blockedReported || released) return
        blockedReported = true
        handler.removeCallbacks(commercialGuardRunnable)
        player?.pause()
        onBlocked(live)
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    fun release() {
        if (released) return
        released = true
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        val oldPlayer = player
        player = null
        playerView.player = null
        runCatching { oldPlayer?.release() }
        animate().cancel()
        alpha = 1f
        translationX = 0f
        translationY = 0f
        scaleX = 1f
        scaleY = 1f
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
