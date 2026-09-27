package com.unknokable.videosohranenki

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
    private val onChatScopeMissing: () -> Unit,
    private val onTestStreamBlocked: (TwitchLiveStream) -> Unit = {}
) {
    val root = LinearLayout(activity)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val chatClient = TwitchChatClient()
    private val handler = Handler(Looper.getMainLooper())

    private val playerCard = FrameLayout(activity)
    private val playerView = PlayerView(activity)
    private val posterImage = ImageView(activity)
    private val loader = LoadingWaveView(activity, palette.accent)
    private val controlsOverlay = FrameLayout(activity)
    private val playPause = ImageButton(activity)
    private val audioToggle = LiveAudioToggleView(
        activity,
        palette.accent,
        Color.parseColor("#66181322"),
        Color.WHITE
    )
    private val fullscreenButton = ImageButton(activity)
    private lateinit var qualityButton: TextView

    private val chatStatus = TextView(activity)
    private val chatScroll = ScrollView(activity)
    private val chatList = LinearLayout(activity)
    private val chatEmpty = TextView(activity)
    private val chatRows = mutableListOf<View>()

    private var player: ExoPlayer? = null
    private var muted = false
    private var qualitySelection = 0
    private var farBehindSamples = 0
    private var fullscreen = false
    private var destroyed = false
    private var controlsVisible = true
    private var chatHasMessages = false
    private var streamRecoveryAttempts = 0
    private var recoveringStream = false
    private var playerErrorView: TextView? = null
    private var commercialSwitchRequested = false

    private val commercialGuardRunnable: Runnable = object : Runnable {
        override fun run() {
            if (destroyed || commercialSwitchRequested || !live.testStream) return
            val self = this
            scope.launch {
                val commercial = runCatching {
                    TwitchVodResolver.isCommercialBreak(live.login)
                }.getOrDefault(false)

                if (destroyed || commercialSwitchRequested) return@launch
                if (commercial) {
                    handleCommercialBreak()
                } else {
                    handler.postDelayed(self, 12_000L)
                }
            }
        }
    }

    private val hideControlsRunnable = Runnable { hideControls() }
    private val liveEdgeGuardRunnable = object : Runnable {
        override fun run() {
            val p = player
            if (!destroyed && p != null) {
                val offset = p.currentLiveOffset
                val genuinelyFarBehind =
                    p.isPlaying &&
                    p.playbackState == Player.STATE_READY &&
                    offset != C.TIME_UNSET &&
                    offset > 10_000L

                farBehindSamples = if (genuinelyFarBehind) farBehindSamples + 1 else 0

                // ExoPlayer's live-speed control handles normal 2–6 second drift smoothly.
                // Only hard-jump after several consecutive checks when the stream is truly stale.
                if (farBehindSamples >= 3) {
                    farBehindSamples = 0
                    p.seekToDefaultPosition()
                    if (!p.isPlaying) p.play()
                }

                handler.postDelayed(this, 5_000L)
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
            background = rounded(Color.BLACK, 18)
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
            keepScreenOn = true
        }
        playerCard.addView(
            playerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        posterImage.apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.BLACK)
            if (live.thumbnailUrl.isNotBlank()) {
                load(live.thumbnailUrl) { crossfade(animationsEnabled) }
            }
        }
        playerCard.addView(
            posterImage,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        loader.alpha = 0.92f
        playerCard.addView(
            loader,
            FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER)
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
            )
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

        updateFullscreenIcon()
        startPlayer()
        startChat()
    }

    private fun buildHeader(): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(palette.background)
        }

        val back = iconButton(R.drawable.ic_back, Color.parseColor("#181322"), 42).apply {
            setOnClickListener {
                pulse(this)
                onBack()
            }
        }

        val title = TextView(activity).apply {
            text = live.displayName.ifBlank { live.login }
            textSize = 15f
            setTextColor(palette.text)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(9), 0, dp(9), 0)
        }

        row.addView(back, LinearLayout.LayoutParams(dp(42), dp(42)))
        row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(View(activity), LinearLayout.LayoutParams(dp(42), dp(42)))
        return row
    }

    private fun buildPlayerControls() {
        controlsOverlay.setBackgroundColor(Color.parseColor("#24000000"))
        controlsOverlay.alpha = 1f
        controlsOverlay.visibility = View.VISIBLE

        playPause.apply {
            setImageResource(R.drawable.ic_pause)
            setBackgroundColor(Color.TRANSPARENT)
            background = rounded(palette.accent, 27)
            setPadding(dp(15), dp(15), dp(15), dp(15))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener {
                val p = player
                if (p != null) {
                    if (p.isPlaying) p.pause() else jumpToLiveEdgeAndPlay()
                    updatePlayIcon()
                    pulse(this)
                    showControls(autoHide = p.isPlaying)
                }
            }
        }
        controlsOverlay.addView(
            playPause,
            FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER)
        )

        val bottom = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(6), dp(14), dp(10))
        }

        val liveBar = SohrTimeBar(activity).apply {
            setProgress(1L, 1L, 1L)
            isClickable = false
            isFocusable = false
            setOnTouchListener { _, _ -> true }
        }

        val times = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val liveLabel = timeLabel("В ЭФИРЕ").apply {
            minWidth = dp(58)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        val spacer = View(activity)

        qualityButton = TextView(activity).apply {
            text = "Авто"
            textSize = 10.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(10), 0, dp(10), 0)
            background = rounded(Color.parseColor("#66181322"), 14)
            setOnClickListener {
                pulse(this)
                showQualityPicker()
            }
        }

        val settingsButton = TextView(activity).apply {
            text = "⚙"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.parseColor("#66181322"), 20)
            setOnClickListener {
                pulse(this)
                showLiveSettings()
            }
        }

        fullscreenButton.apply {
            setImageResource(R.drawable.ic_fullscreen)
            setBackgroundColor(Color.TRANSPARENT)
            background = rounded(Color.parseColor("#66181322"), 20)
            setPadding(dp(11), dp(11), dp(11), dp(11))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener {
                setFullscreenMode(!fullscreen)
                pulse(this)
            }
        }

        audioToggle.apply {
            setMuted(false, animate = false)
            onMutedChanged = { nowMuted ->
                muted = nowMuted
                player?.volume = if (nowMuted) 0f else 1f
                showControls(autoHide = player?.isPlaying == true)
            }
        }

        times.addView(liveLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)))
        times.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        times.addView(qualityButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)))
        times.addView(audioToggle, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginStart = dp(4) })
        times.addView(settingsButton, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginStart = dp(4) })
        times.addView(fullscreenButton, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginStart = dp(4) })

        bottom.addView(liveBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)))
        bottom.addView(times)

        controlsOverlay.addView(
            bottom,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
    }

    private fun buildStreamInfoCard(): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(12))

            addView(TextView(activity).apply {
                text = live.title.ifBlank { live.displayName + " в эфире" }
                textSize = 21f
                maxLines = 2
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.text)
            })

            addView(TextView(activity).apply {
                text = listOfNotNull(
                    "LIVE",
                    live.gameName.takeIf { it.isNotBlank() },
                    live.viewerCount.toString() + " зрителей",
                    "@" + live.login
                ).joinToString(" • ")
                textSize = 13f
                maxLines = 2
                setTextColor(palette.muted)
                setPadding(0, dp(7), 0, 0)
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
                val hlsUrl = TwitchVodResolver.resolveLiveChecked(live.login)
                if (destroyed) return@launch

                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(5_000, 20_000, 700, 1_500)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()

                val liveSpeedControl = DefaultLivePlaybackSpeedControl.Builder()
                    .setFallbackMinPlaybackSpeed(0.98f)
                    .setFallbackMaxPlaybackSpeed(1.03f)
                    .setTargetLiveOffsetIncrementOnRebufferMs(1_200L)
                    .build()

                val renderersFactory = DefaultRenderersFactory(activity)
                    .setEnableDecoderFallback(true)
                    .forceEnableMediaCodecAsynchronousQueueing()

                val exo = ExoPlayer.Builder(activity, renderersFactory)
                    .setLoadControl(loadControl)
                    .setLivePlaybackSpeedControl(liveSpeedControl)
                    .build()

                player = exo
                playerView.player = exo
                exo.volume = if (muted) 0f else 1f
                exo.playWhenReady = true

                exo.setMediaItem(createLiveMediaItem(hlsUrl))
                exo.prepare()

                exo.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updatePlayIcon()
                        if (isPlaying) {
                            scheduleControlsHide()
                        } else if (exo.playbackState == Player.STATE_READY) {
                            // Only show paused controls for a real pause, not for network buffering.
                            showControls(autoHide = false)
                        }
                    }

                    override fun onRenderedFirstFrame() {
                        loader.visibility = View.GONE
                        posterImage.animate().cancel()
                        posterImage.animate()
                            .alpha(0f)
                            .setDuration(if (animationsEnabled) 120L else 0L)
                            .withEndAction { posterImage.visibility = View.GONE }
                            .start()
                        streamRecoveryAttempts = 0
                        recoveringStream = false
                        clearPlayerError()
                        scheduleControlsHide()
                        handler.removeCallbacks(liveEdgeGuardRunnable)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                handler.postDelayed({
                                    if (!destroyed && exo.playbackState == Player.STATE_BUFFERING) {
                                        loader.visibility = View.VISIBLE
                                    }
                                }, 1_200L)
                            }
                            Player.STATE_READY -> {
                                loader.visibility = View.GONE
                                updateQualityLabel()
                            }
                            Player.STATE_ENDED -> if (!destroyed) {
                                recoverLiveStream(exo, "Эфир временно прервался")
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                            exo.seekToDefaultPosition()
                            exo.prepare()
                            exo.play()
                        } else {
                            recoverLiveStream(
                                exo,
                                error.message ?: "Не удалось продолжить эфир"
                            )
                        }
                    }
                })
            } catch (_: TwitchCommercialBreakException) {
                handleCommercialBreak()
            } catch (e: Exception) {
                loader.visibility = View.GONE
                showPlayerError(e.message ?: "Не удалось открыть эфир")
            }
        }
    }

    private fun handleCommercialBreak() {
        if (destroyed || commercialSwitchRequested) return

        if (live.testStream) {
            commercialSwitchRequested = true
            handler.removeCallbacks(commercialGuardRunnable)
            player?.pause()
            loader.visibility = View.VISIBLE
            showPlayerError("На этом эфире реклама Twitch • переключаем…")
            scope.launch {
                delay(220L)
                if (!destroyed) onTestStreamBlocked(live)
            }
        } else {
            loader.visibility = View.GONE
            showPlayerError("На канале сейчас реклама Twitch. Попробуем снова автоматически.")
            handler.postDelayed({
                if (!destroyed) {
                    clearPlayerError()
                    loader.visibility = View.VISIBLE
                    startPlayer()
                }
            }, 12_000L)
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
        val error = playerErrorView ?: TextView(activity).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = rounded(withAlpha(palette.surfaceAlt, 242), 15)
            playerErrorView = this
            playerCard.addView(
                this,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }

        error.text = message
        error.visibility = View.VISIBLE
        if (animationsEnabled) {
            error.alpha = 0f
            error.animate().alpha(1f).setDuration(140L).start()
        }
    }

    private fun clearPlayerError() {
        playerErrorView?.let { error ->
            error.animate().cancel()
            error.visibility = View.GONE
        }
    }

    private fun createLiveMediaItem(url: String): MediaItem =
        MediaItem.Builder()
            .setUri(url)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(4_500L)
                    .setMinOffsetMs(3_000L)
                    .setMaxOffsetMs(9_000L)
                    .setMinPlaybackSpeed(0.98f)
                    .setMaxPlaybackSpeed(1.03f)
                    .build()
            )
            .build()

    private fun recoverLiveStream(exo: ExoPlayer, lastError: String) {
        if (destroyed || recoveringStream) return

        if (streamRecoveryAttempts >= 3) {
            loader.visibility = View.GONE
            showPlayerError("Эфир временно недоступен. Повторим при следующем обновлении потока.")
            return
        }

        recoveringStream = true
        streamRecoveryAttempts += 1
        loader.visibility = View.VISIBLE
        clearPlayerError()

        scope.launch {
            delay(
                when (streamRecoveryAttempts) {
                    1 -> 450L
                    2 -> 900L
                    else -> 1_600L
                }
            )

            try {
                val freshUrl = TwitchVodResolver.resolveLiveChecked(live.login)
                if (destroyed) return@launch

                exo.setMediaItem(createLiveMediaItem(freshUrl), true)
                exo.prepare()
                exo.seekToDefaultPosition()
                exo.play()
                recoveringStream = false
            } catch (_: TwitchCommercialBreakException) {
                recoveringStream = false
                handleCommercialBreak()
            } catch (_: Exception) {
                recoveringStream = false
                if (streamRecoveryAttempts >= 3) {
                    loader.visibility = View.GONE
                    showPlayerError(lastError)
                } else {
                    recoverLiveStream(exo, lastError)
                }
            }
        }
    }

    private data class QualityOption(
        val label: String,
        val group: TrackGroup,
        val trackIndex: Int
    )

    private fun availableQualityOptions(): List<QualityOption> {
        val p = player ?: return emptyList()
        val result = mutableListOf<QualityOption>()
        for (group in p.currentTracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO || !group.isSupported) continue
            for (index in 0 until group.length) {
                if (!group.isTrackSupported(index)) continue
                val format = group.getTrackFormat(index)
                val height = format.height
                val bitrate = format.bitrate
                val resolution = if (height > 0) "${height}p" else "Оригинал"
                val bitrateText = if (bitrate > 0) " • %.1f Мбит/с".format(bitrate / 1_000_000.0) else ""
                result += QualityOption(resolution + bitrateText, group.mediaTrackGroup, index)
            }
        }
        return result.distinctBy { it.label }
    }

    private fun showQualityPicker() {
        val p = player ?: return
        val options = availableQualityOptions()
        if (options.isEmpty()) {
            ModernDialogs.showChoices(
                activity,
                palette,
                "Качество видео",
                listOf("Оригинал • лучшее доступное"),
                0
            ) { }
            return
        }

        val labels = mutableListOf("Авто • лучшее доступное")
        labels.addAll(options.map { it.label })

        ModernDialogs.showChoices(activity, palette, "Качество видео", labels, qualitySelection) { which ->
            qualitySelection = which
            if (which == 0) {
                p.trackSelectionParameters = p.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .build()
            } else {
                val option = options[which - 1]
                p.trackSelectionParameters = p.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex))
                    .build()
            }
            updateQualityLabel()
            showControls(autoHide = p.isPlaying)
        }
    }

    private fun showLiveSettings() {
        val labels = listOf(
            "Качество • " + qualityButton.text,
            "Вернуться в прямой эфир"
        )
        ModernDialogs.showChoices(activity, palette, "Настройки видео", labels, -1) { which ->
            when (which) {
                0 -> showQualityPicker()
                1 -> {
                    jumpToLiveEdgeAndPlay()
                    showControls(autoHide = true)
                }
            }
        }
    }

    private fun updateQualityLabel() {
        if (!::qualityButton.isInitialized) return
        val height = player?.videoFormat?.height ?: 0
        qualityButton.text = if (height > 0) "${height}p" else "Авто"
    }

    private fun updatePlayIcon() {
        playPause.setImageResource(
            if (player?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun updateFullscreenIcon() {
        fullscreenButton.setImageResource(
            if (fullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen
        )
    }

    private fun pulse(view: View) {
        if (!animationsEnabled) return
        val ease = android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
        view.animate().cancel()
        view.animate()
            .scaleX(0.94f)
            .scaleY(0.94f)
            .setDuration(55L)
            .setInterpolator(ease)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120L)
                    .setInterpolator(ease)
                    .start()
            }
            .start()
    }

    private fun iconButton(resId: Int, backgroundColor: Int, size: Int = 48): ImageButton =
        ImageButton(activity).apply {
            setImageResource(resId)
            setBackgroundColor(Color.TRANSPARENT)
            background = rounded(backgroundColor, size / 2)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

    private fun timeLabel(value: String): TextView =
        TextView(activity).apply {
            text = value
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }

    fun setFullscreenMode(enabled: Boolean) {
        if (fullscreen == enabled) return
        fullscreen = enabled

        activity.requestedOrientation =
            when {
                !enabled -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                shouldForceLandscape() -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }

        updateFullscreenIcon()

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
            playerCard.background = rounded(Color.BLACK, 18)
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
        // Media3's LivePlaybackSpeedControl owns normal live-edge correction.
        handler.removeCallbacks(liveEdgeGuardRunnable)
        farBehindSamples = 0
    }

    private fun showControls(autoHide: Boolean) {
        handler.removeCallbacks(hideControlsRunnable)
        controlsVisible = true
        controlsOverlay.visibility = View.VISIBLE
        controlsOverlay.animate().cancel()
        controlsOverlay.animate()
            .alpha(1f)
            .setDuration(if (animationsEnabled) 240L else 0L)
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
            .setDuration(if (animationsEnabled) 240L else 0L)
            .withEndAction {
                if (!controlsVisible) controlsOverlay.visibility = View.GONE
            }
            .start()
    }

    private fun scheduleControlsHide() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 3_000L)
    }

    private fun shouldForceLandscape(): Boolean {
        val sw = activity.resources.configuration.smallestScreenWidthDp
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val model = Build.MODEL.orEmpty()
        val fingerprint = Build.FINGERPRINT.orEmpty()

        val emulatorLike =
            manufacturer.contains("bluestacks", ignoreCase = true) ||
            brand.contains("bluestacks", ignoreCase = true) ||
            model.contains("bluestacks", ignoreCase = true) ||
            fingerprint.contains("generic", ignoreCase = true) ||
            model.contains("sdk", ignoreCase = true)

        return !emulatorLike && sw < 600
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
