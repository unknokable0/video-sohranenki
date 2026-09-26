package com.unknokable.videosohranenki

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class TwitchLivePlayerScreen(
    private val activity: Activity,
    private val live: TwitchLiveStream,
    private val accessToken: String,
    private val accountLogin: String,
    private val palette: ThemePalette,
    private val animationsEnabled: Boolean,
    private val onBack: () -> Unit,
    private val onFullscreen: (Boolean) -> Unit,
    private val onChatScopeMissing: () -> Unit
) {
    val root = LinearLayout(activity)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val chatClient = TwitchChatClient()
    private val handler = Handler(Looper.getMainLooper())

    private val playerCard = FrameLayout(activity)
    private val playerView = PlayerView(activity)
    private val loader = LoadingWaveView(activity, palette.accent)
    private val controlsOverlay = FrameLayout(activity)
    private val playPause = ImageButton(activity)
    private val audioToggle = LiveAudioToggleView(activity, palette.accent)
    private val fullscreenButton = ImageButton(activity)

    private val chatStatus = TextView(activity)
    private val chatScroll = ScrollView(activity)
    private val chatList = LinearLayout(activity)
    private val chatEmpty = TextView(activity)
    private val chatRows = mutableListOf<View>()

    private var player: ExoPlayer? = null
    private var muted = false
    private var fullscreen = false
    private var destroyed = false
    private var controlsVisible = true
    private var chatHasMessages = false

    private val hideControlsRunnable = Runnable { hideControls() }
    private val liveEdgeGuardRunnable = object : Runnable {
        override fun run() {
            val p = player
            if (!destroyed && p != null) {
                val offset = p.currentLiveOffset
                if (
                    p.isPlaying &&
                    offset != C.TIME_UNSET &&
                    offset > 6_500L
                ) {
                    p.seekToDefaultPosition()
                }
                handler.postDelayed(this, 4_000L)
            }
        }
    }

    val isFullscreen: Boolean
        get() = fullscreen

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(palette.background)

        root.addView(buildHeader())

        playerCard.apply {
            background = rounded(Color.BLACK, 24)
            clipToOutline = true
            isClickable = true
            isFocusable = true
            elevation = dp(1).toFloat()
            setOnClickListener {
                if (controlsVisible) {
                    hideControls()
                } else {
                    showControls(autoHide = player?.isPlaying == true)
                }
            }
        }

        playerView.apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setBackgroundColor(Color.BLACK)
        }
        playerCard.addView(
            playerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        loader.alpha = 0.95f
        playerCard.addView(
            loader,
            FrameLayout.LayoutParams(dp(50), dp(50), Gravity.CENTER)
        )

        playerCard.addView(
            buildLiveStatusPill(),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(32),
                Gravity.TOP or Gravity.START
            ).apply {
                leftMargin = dp(10)
                topMargin = dp(10)
            }
        )

        buildPlayerControls()
        playerCard.addView(
            controlsOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val width = activity.resources.displayMetrics.widthPixels
        root.addView(
            playerCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (width * 9f / 16f).toInt()
            ).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
            }
        )

        root.addView(
            buildStreamInfoCard(),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
                topMargin = dp(10)
            }
        )

        root.addView(
            buildChatCard(),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
                topMargin = dp(10)
                bottomMargin = dp(12)
            }
        )

        startPlayer()
        startChat()
    }

    private fun buildHeader(): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
        }

        val back = iconButton(
            resId = R.drawable.ic_back,
            backgroundColor = palette.surface,
            tintColor = palette.text,
            size = 40
        ).apply {
            contentDescription = "Назад"
            setOnClickListener {
                pulseButton(this)
                onBack()
            }
        }

        val pulse = LivePulseView(activity).apply {
            setState(true, animationsEnabled)
        }

        val titles = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
        titles.addView(TextView(activity).apply {
            text = live.displayName.ifBlank { live.login }
            textSize = 17f
            maxLines = 1
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })
        titles.addView(TextView(activity).apply {
            text = "@" + live.login + "  •  прямой эфир"
            textSize = 11.5f
            maxLines = 1
            setTextColor(palette.muted)
            setPadding(0, dp(2), 0, 0)
        })

        row.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        row.addView(
            pulse,
            LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                marginStart = dp(11)
            }
        )
        row.addView(
            titles,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        return row
    }

    private fun buildLiveStatusPill(): View {
        val pill = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(11), 0)
            background = rounded(Color.parseColor("#B43A3842"), 14)
        }

        val pulse = LivePulseView(activity).apply {
            setState(true, animationsEnabled)
        }
        pill.addView(
            pulse,
            LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                marginEnd = dp(4)
            }
        )
        pill.addView(TextView(activity).apply {
            text = "В эфире"
            textSize = 10.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        return pill
    }

    private fun buildPlayerControls() {
        controlsOverlay.background = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(
                Color.parseColor("#7A000000"),
                Color.parseColor("#19000000"),
                Color.parseColor("#06000000")
            )
        )
        controlsOverlay.alpha = 1f
        controlsOverlay.visibility = View.VISIBLE

        playPause.apply {
            setImageResource(R.drawable.ic_pause)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = rounded(withAlpha(palette.accent, 232), 25)
            setPadding(dp(13), dp(13), dp(13), dp(13))
            contentDescription = "Пауза"
            setOnClickListener {
                val p = player ?: return@setOnClickListener
                pulseButton(this)
                if (p.isPlaying) {
                    p.pause()
                    showControls(autoHide = false)
                } else {
                    jumpToLiveEdgeAndPlay()
                    showControls(autoHide = true)
                }
            }
        }
        controlsOverlay.addView(
            playPause,
            FrameLayout.LayoutParams(dp(50), dp(50), Gravity.CENTER)
        )

        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        audioToggle.apply {
            setMuted(false, animate = false)
            onMutedChanged = { nowMuted ->
                muted = nowMuted
                player?.volume = if (nowMuted) 0f else 1f
                showControls(autoHide = player?.isPlaying == true)
            }
        }
        actions.addView(audioToggle, LinearLayout.LayoutParams(dp(40), dp(40)))

        fullscreenButton.apply {
            setImageResource(R.drawable.ic_fullscreen)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = rounded(withAlpha(palette.accent, 205), 20)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            contentDescription = "Полный экран"
            setOnClickListener {
                pulseButton(this)
                setFullscreenMode(!fullscreen)
                showControls(autoHide = player?.isPlaying == true)
            }
        }
        actions.addView(
            fullscreenButton,
            LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                marginStart = dp(8)
            }
        )

        controlsOverlay.addView(
            actions,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(42),
                Gravity.BOTTOM or Gravity.END
            ).apply {
                rightMargin = dp(10)
                bottomMargin = dp(10)
            }
        )
    }

    private fun buildStreamInfoCard(): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(13), dp(15), dp(13))
            background = roundedStroke(
                color = palette.surface,
                strokeColor = palette.stroke,
                radiusDp = 22
            )

            addView(TextView(activity).apply {
                text = live.title.ifBlank { live.displayName + " в эфире" }
                textSize = 17f
                maxLines = 2
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.text)
            })

            addView(TextView(activity).apply {
                text = listOfNotNull(
                    live.gameName.takeIf { it.isNotBlank() },
                    live.viewerCount.toString() + " зрителей"
                ).joinToString("  •  ")
                textSize = 11.8f
                maxLines = 1
                setTextColor(palette.muted)
                setPadding(0, dp(6), 0, 0)
            })
        }
    }

    private fun buildChatCard(): View {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedStroke(
                color = palette.surface,
                strokeColor = palette.stroke,
                radiusDp = 24
            )
            clipToOutline = true
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(12), dp(12), dp(9))
        }

        val titles = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        titles.addView(TextView(activity).apply {
            text = "Чат"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })
        titles.addView(TextView(activity).apply {
            text = "Сообщения в реальном времени"
            textSize = 10.5f
            setTextColor(palette.muted)
            setPadding(0, dp(2), 0, 0)
        })

        header.addView(
            titles,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        chatStatus.apply {
            text = "Подключаем…"
            textSize = 10f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.accent)
            setPadding(dp(10), 0, dp(10), 0)
            background = rounded(palette.accentSoft, 13)
            setOnClickListener {
                if (text.toString().contains("переподключ", ignoreCase = true)) {
                    onChatScopeMissing()
                }
            }
        }
        header.addView(
            chatStatus,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(28)
            )
        )

        box.addView(header)

        val divider = View(activity).apply {
            setBackgroundColor(withAlpha(palette.stroke, 150))
        }
        box.addView(
            divider,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                marginStart = dp(14)
                marginEnd = dp(14)
            }
        )

        chatList.orientation = LinearLayout.VERTICAL
        chatList.setPadding(dp(14), dp(8), dp(14), dp(12))

        chatEmpty.apply {
            text = "Сообщения появятся здесь"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(palette.muted)
            setPadding(0, dp(22), 0, dp(22))
        }
        chatList.addView(
            chatEmpty,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        chatScroll.apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                chatList,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        box.addView(
            chatScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        return box
    }

    private fun startPlayer() {
        scope.launch {
            try {
                val hlsUrl = TwitchVodResolver.resolveLive(live.login)
                if (destroyed) return@launch

                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(2_500, 12_000, 300, 650)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()

                val exo = ExoPlayer.Builder(activity)
                    .setLoadControl(loadControl)
                    .build()

                player = exo
                playerView.player = exo
                exo.volume = if (muted) 0f else 1f
                exo.playWhenReady = true

                val mediaItem = MediaItem.Builder()
                    .setUri(hlsUrl)
                    .setLiveConfiguration(
                        MediaItem.LiveConfiguration.Builder()
                            .setTargetOffsetMs(2_800L)
                            .setMinPlaybackSpeed(0.97f)
                            .setMaxPlaybackSpeed(1.08f)
                            .build()
                    )
                    .build()

                exo.setMediaItem(mediaItem)
                exo.prepare()

                exo.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playPause.setImageResource(
                            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        )
                        playPause.contentDescription =
                            if (isPlaying) "Пауза" else "Продолжить"

                        if (isPlaying) scheduleControlsHide()
                        else showControls(autoHide = false)
                    }

                    override fun onRenderedFirstFrame() {
                        loader.visibility = View.GONE
                        scheduleControlsHide()
                        startLiveEdgeGuard()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                            exo.seekToDefaultPosition()
                            exo.prepare()
                            exo.play()
                        } else {
                            showPlayerError(error.message ?: "Не удалось продолжить эфир")
                        }
                    }
                })
            } catch (e: Exception) {
                loader.visibility = View.GONE
                showPlayerError(e.message ?: "Не удалось открыть эфир")
            }
        }
    }

    private fun startChat() {
        if (accessToken.isBlank() || accountLogin.isBlank()) {
            updateChatStatus("Переподключи Twitch", connected = false)
            return
        }

        chatClient.connect(
            accessToken = accessToken,
            accountLogin = accountLogin,
            channelLogin = live.login,
            onStatus = { status ->
                activity.runOnUiThread {
                    if (!destroyed) {
                        updateChatStatus(
                            text = when (status) {
                                "Чат подключён" -> "Онлайн"
                                "Подключаем чат…" -> "Подключаем…"
                                else -> status
                            },
                            connected = status == "Чат подключён"
                        )
                    }
                }
            },
            onMessage = { message ->
                activity.runOnUiThread {
                    if (!destroyed) appendChat(message)
                }
            }
        )
    }

    private fun updateChatStatus(text: String, connected: Boolean) {
        chatStatus.text = text
        chatStatus.setTextColor(if (connected) palette.accent else palette.muted)
        chatStatus.background = rounded(
            if (connected) palette.accentSoft else palette.surfaceAlt,
            13
        )
    }

    private fun appendChat(message: TwitchChatMessage) {
        if (!chatHasMessages) {
            chatHasMessages = true
            chatList.removeView(chatEmpty)
        }

        val text = SpannableStringBuilder()
            .append(message.displayName)
            .append("  ")
            .append(message.text)

        val nameEnd = message.displayName.length.coerceAtMost(text.length)
        if (nameEnd > 0) {
            text.setSpan(
                ForegroundColorSpan(message.color),
                0,
                nameEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            text.setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                nameEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, 0)
        }

        row.addView(TextView(activity).apply {
            this.text = text
            textSize = 13f
            setTextColor(palette.text)
            setLineSpacing(0f, 1.08f)
            maxLines = 5
        })

        row.addView(View(activity).apply {
            setBackgroundColor(withAlpha(palette.stroke, 110))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(1)
        ).apply {
            topMargin = dp(7)
        })

        chatList.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        if (animationsEnabled) {
            row.alpha = 0f
            row.translationY = dp(6).toFloat()
            row.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(170L)
                .start()
        }

        chatRows.add(row)
        while (chatRows.size > 80) {
            val old = chatRows.removeAt(0)
            chatList.removeView(old)
        }

        chatScroll.post {
            chatScroll.smoothScrollTo(0, chatList.height)
        }
    }

    private fun showPlayerError(message: String) {
        val error = TextView(activity).apply {
            text = message
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = rounded(withAlpha(palette.surfaceAlt, 235), 15)
        }

        playerCard.addView(
            error,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
    }

    fun setFullscreenMode(enabled: Boolean) {
        if (fullscreen == enabled) return
        fullscreen = enabled

        activity.requestedOrientation =
            if (enabled) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        val params = playerCard.layoutParams as LinearLayout.LayoutParams
        if (enabled) {
            root.setBackgroundColor(Color.BLACK)
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                child.visibility = if (child === playerCard) View.VISIBLE else View.GONE
            }
            playerCard.background = null
            playerCard.clipToOutline = false
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = 0
            params.weight = 1f
            params.marginStart = 0
            params.marginEnd = 0
        } else {
            root.setBackgroundColor(palette.background)
            for (i in 0 until root.childCount) {
                root.getChildAt(i).visibility = View.VISIBLE
            }
            playerCard.background = rounded(Color.BLACK, 24)
            playerCard.clipToOutline = true
            val width = activity.resources.displayMetrics.widthPixels
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = (width * 9f / 16f).toInt()
            params.weight = 0f
            params.marginStart = dp(12)
            params.marginEnd = dp(12)
        }

        playerCard.layoutParams = params
        onFullscreen(enabled)
        showControls(autoHide = player?.isPlaying == true)
    }

    fun exitFullscreen() {
        if (fullscreen) setFullscreenMode(false)
    }

    private fun jumpToLiveEdgeAndPlay() {
        val p = player ?: return
        p.seekToDefaultPosition()
        if (p.playbackState == Player.STATE_IDLE) p.prepare()
        p.play()
    }

    private fun startLiveEdgeGuard() {
        handler.removeCallbacks(liveEdgeGuardRunnable)
        handler.postDelayed(liveEdgeGuardRunnable, 4_000L)
    }

    private fun showControls(autoHide: Boolean) {
        handler.removeCallbacks(hideControlsRunnable)
        controlsVisible = true
        controlsOverlay.visibility = View.VISIBLE
        controlsOverlay.animate().cancel()
        controlsOverlay.animate()
            .alpha(1f)
            .setDuration(if (animationsEnabled) 150L else 0L)
            .start()

        if (autoHide) scheduleControlsHide()
    }

    private fun hideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        if (!controlsVisible) return

        controlsVisible = false
        controlsOverlay.animate().cancel()
        controlsOverlay.animate()
            .alpha(0f)
            .setDuration(if (animationsEnabled) 170L else 0L)
            .withEndAction {
                if (!controlsVisible) controlsOverlay.visibility = View.GONE
            }
            .start()
    }

    private fun scheduleControlsHide() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 2_100L)
    }

    private fun pulseButton(view: View) {
        if (!animationsEnabled) return

        view.animate().cancel()
        view.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(55L)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(135L)
                    .start()
            }
            .start()
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        handler.removeCallbacksAndMessages(null)

        if (fullscreen) {
            fullscreen = false
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            onFullscreen(false)
        }

        chatClient.close()
        scope.cancel()
        playerView.player = null
        player?.release()
        player = null
    }

    private fun iconButton(
        resId: Int,
        backgroundColor: Int,
        tintColor: Int,
        size: Int
    ): ImageButton = ImageButton(activity).apply {
        setImageResource(resId)
        imageTintList = ColorStateList.valueOf(tintColor)
        background = rounded(backgroundColor, size / 2)
        setPadding(dp(10), dp(10), dp(10), dp(10))
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun roundedStroke(
        color: Int,
        strokeColor: Int,
        radiusDp: Int
    ) = GradientDrawable().apply {
        setColor(color)
        setStroke(dp(1), strokeColor)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
