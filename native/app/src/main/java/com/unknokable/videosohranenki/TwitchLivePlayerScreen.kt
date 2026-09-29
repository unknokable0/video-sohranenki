package com.unknokable.videosohranenki

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
    private val profileImageUrl: String,
    private val showPartnerBadge: Boolean,
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
    private val lowLatencyProxy = TwitchLowLatencyProxy()
    private val handler = Handler(Looper.getMainLooper())

    private val playerCard = FrameLayout(activity)
    private val playerView = activity.layoutInflater.inflate(
        R.layout.view_sohr_player,
        playerCard,
        false
    ) as PlayerView
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
    private val latencyDot = LatencyPulseView(activity)
    private val latencyLabel = TextView(activity)
    private val latencyPill = LinearLayout(activity)

    private val chatStatus = TextView(activity)
    private val chatScroll = ScrollView(activity)
    private val chatList = LinearLayout(activity)
    private val chatEmpty = TextView(activity)
    private val chatInput = EditText(activity)
    private val chatSendButton = TextView(activity)
    private val chatRows = mutableListOf<View>()

    private var player: ExoPlayer? = null
    private var muted = false
    private var qualitySelection = 0
    private var fullscreen = false
    private var exiting = false
    private var destroyed = false
    private var controlsVisible = true
    private var chatHasMessages = false
    private var chatConnected = false
    private var chatWasNearBottomBeforeFullscreen = true
    private var lastChatRowAnimationAt = 0L
    private var latencyBucket = ""
    private var streamRecoveryAttempts = 0
    private var recoveringStream = false
    private var playerErrorView: TextView? = null
    private var currentHlsUrl: String? = null
    private var commercialRecoveryActive = false
    private var commercialOverlay: LinearLayout? = null

    private val commercialGuardRunnable: Runnable = object : Runnable {
        override fun run() {
            if (destroyed || exiting || commercialRecoveryActive) return
            val url = currentHlsUrl
            if (url.isNullOrBlank()) {
                handler.postDelayed(this, 2_500L)
                return
            }

            val self = this
            scope.launch {
                val commercial = runCatching {
                    TwitchVodResolver.isCommercialBreakUrl(url)
                }.getOrDefault(false)

                if (destroyed || exiting || commercialRecoveryActive) return@launch
                if (commercial) {
                    handleCommercialBreak()
                } else {
                    handler.postDelayed(self, 2_500L)
                }
            }
        }
    }

    private val hideControlsRunnable = Runnable { hideControls() }

    private val latencyRunnable = object : Runnable {
        override fun run() {
            if (destroyed || exiting) return
            updateLatencyIndicator()
            handler.postDelayed(this, 900L)
        }
    }

    val isFullscreen: Boolean
        get() = fullscreen

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(palette.background)

        root.addView(buildHeader())

        playerCard.apply {
            setBackgroundColor(Color.BLACK)
            clipToOutline = false
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
            setKeepContentOnPlayerReset(true)
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
            FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER)
        )

        buildPlayerControls()
        playerCard.addView(
            controlsOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        playerCard.addView(
            buildLatencyIndicator(),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(30),
                Gravity.TOP or Gravity.START
            ).apply {
                leftMargin = dp(10)
                topMargin = dp(10)
            }
        )

        playerCard.addView(
            buildLiveBadge(),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(34),
                Gravity.TOP or Gravity.END
            ).apply {
                rightMargin = dp(10)
                topMargin = dp(10)
            }
        )

        playerCard.addView(
            buildViewerBadge(),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(34),
                Gravity.BOTTOM or Gravity.START
            ).apply {
                leftMargin = dp(10)
                bottomMargin = dp(10)
            }
        )

        playerCard.addView(
            audioToggle,
            FrameLayout.LayoutParams(
                dp(46),
                dp(46),
                Gravity.BOTTOM or Gravity.END
            ).apply {
                rightMargin = dp(10)
                bottomMargin = dp(8)
            }
        )

        val width = activity.resources.displayMetrics.widthPixels
        root.addView(
            playerCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (width * 9f / 16f).toInt()
            )
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

        lowLatencyProxy.ensureStarted()
        updateFullscreenIcon()
        updateLatencyIndicator()
        handler.post(latencyRunnable)
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
        controlsOverlay.setBackgroundColor(Color.parseColor("#12000000"))
        controlsOverlay.alpha = 1f
        controlsOverlay.visibility = View.VISIBLE

        playPause.apply {
            setImageResource(R.drawable.ic_pause)
            setBackgroundColor(Color.TRANSPARENT)
            background = rounded(Color.parseColor("#C9181620"), 28)
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

        qualityButton = TextView(activity).apply {
            text = "Авто"
            textSize = 10.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(10), 0, dp(10), 0)
            background = rounded(Color.parseColor("#B5181620"), 15)
            setOnClickListener {
                pulse(this)
                showQualityPicker()
            }
        }

        val settingsButton = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_player_settings)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(Color.parseColor("#B5181620"), 19)
            contentDescription = "Настройки плеера"
            setOnClickListener {
                pulse(this)
                showLiveSettings()
            }
        }

        fullscreenButton.apply {
            setImageResource(R.drawable.ic_fullscreen)
            setBackgroundColor(Color.TRANSPARENT)
            background = rounded(Color.parseColor("#B5181620"), 19)
            setPadding(dp(10), dp(10), dp(10), dp(10))
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
                if (!commercialRecoveryActive) {
                    player?.volume = if (nowMuted) 0f else 1f
                }
                showControls(autoHide = player?.isPlaying == true)
            }
        }

        val secondary = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
        secondary.addView(
            qualityButton,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38))
        )
        secondary.addView(
            settingsButton,
            LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginStart = dp(5) }
        )
        secondary.addView(
            fullscreenButton,
            LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginStart = dp(5) }
        )

        controlsOverlay.addView(
            secondary,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(42),
                Gravity.BOTTOM or Gravity.END
            ).apply {
                rightMargin = dp(58)
                bottomMargin = dp(10)
            }
        )
    }

    private fun buildLiveBadge(): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(11), 0)
            background = rounded(Color.parseColor("#B8151418"), 17)
        }

        row.addView(
            View(activity).apply {
                background = rounded(Color.parseColor("#FF1744"), 5)
            },
            LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                marginEnd = dp(7)
            }
        )
        row.addView(TextView(activity).apply {
            text = "В эфире"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        return row
    }

    private fun buildViewerBadge(): View =
        TextView(activity).apply {
            text = formatViewerCount(live.viewerCount) + " зрителей"
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(10), 0, dp(10), 0)
            background = rounded(Color.parseColor("#B8151418"), 12)
        }

    private fun buildLatencyIndicator(): View {
        latencyPill.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), 0, dp(10), 0)
            background = rounded(Color.parseColor("#B5181620"), 15)
            isClickable = false
            isFocusable = false
        }

        latencyDot.setState(palette.accent, true, animationsEnabled)

        latencyLabel.apply {
            text = "Подключаемся…"
            textSize = 10.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            maxLines = 1
        }

        latencyPill.addView(
            latencyDot,
            LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                marginEnd = dp(3)
            }
        )
        latencyPill.addView(latencyLabel)
        return latencyPill
    }

    private fun buildStreamInfoCard(): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))

            val channelRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val avatarFrame = FrameLayout(activity).apply {
                background = rounded(palette.surfaceAlt, 28)
                clipToOutline = true
            }

            avatarFrame.addView(
                TextView(activity).apply {
                    text = live.displayName.ifBlank { live.login }
                        .trim()
                        .take(1)
                        .uppercase()
                    textSize = 20f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    background = rounded(palette.accent, 28)
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            if (profileImageUrl.isNotBlank()) {
                avatarFrame.addView(
                    ImageView(activity).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        background = rounded(palette.surfaceAlt, 28)
                        clipToOutline = true
                        load(profileImageUrl) { crossfade(animationsEnabled) }
                    },
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }

            channelRow.addView(
                avatarFrame,
                LinearLayout.LayoutParams(dp(56), dp(56))
            )

            val names = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }

            val nameLine = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            nameLine.addView(TextView(activity).apply {
                text = live.displayName.ifBlank { live.login }
                textSize = 22f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.text)
                maxLines = 1
            })

            if (showPartnerBadge) {
                nameLine.addView(
                    TextView(activity).apply {
                        text = "✓"
                        textSize = 11f
                        gravity = Gravity.CENTER
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(Color.WHITE)
                        background = rounded(palette.accent, 9)
                    },
                    LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                        marginStart = dp(7)
                    }
                )
            }

            names.addView(nameLine)
            names.addView(TextView(activity).apply {
                text = "@" + live.login
                textSize = 12.5f
                setTextColor(palette.muted)
                setPadding(0, dp(3), 0, 0)
            })

            channelRow.addView(
                names,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(channelRow)

            addView(TextView(activity).apply {
                text = live.title.ifBlank { live.displayName + " в эфире" }
                textSize = 18f
                maxLines = 3
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.text)
                setPadding(0, dp(13), 0, 0)
            })

            val chips = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, 0)
            }

            chips.addView(TextView(activity).apply {
                text = "ⓘ  Контент может включать рекламу"
                textSize = 11.5f
                gravity = Gravity.CENTER
                setTextColor(palette.text)
                setPadding(dp(10), 0, dp(10), 0)
                background = rounded(palette.surfaceAlt, 14)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(30)
            ))

            if (live.gameName.isNotBlank()) {
                chips.addView(TextView(activity).apply {
                    text = live.gameName
                    textSize = 11.5f
                    gravity = Gravity.CENTER
                    setTextColor(palette.text)
                    setPadding(dp(10), 0, dp(10), 0)
                    background = rounded(palette.surfaceAlt, 14)
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(30)
                ).apply {
                    marginStart = dp(8)
                })
            }

            addView(chips)
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

        box.addView(
            View(activity).apply {
                setBackgroundColor(withAlpha(palette.stroke, 150))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                marginStart = dp(14)
                marginEnd = dp(14)
            }
        )

        val composer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(9), dp(10), dp(10))
        }

        chatInput.apply {
            hint = "Написать в чат"
            textSize = 13f
            setSingleLine(true)
            setTextColor(palette.text)
            setHintTextColor(palette.muted)
            setPadding(dp(13), 0, dp(13), 0)
            background = rounded(palette.surfaceAlt, 16)
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendChatMessage()
                    true
                } else {
                    false
                }
            }
        }

        chatSendButton.apply {
            text = "Отправить"
            textSize = 11.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(palette.accent, 16)
            alpha = 0.45f
            isEnabled = false
            setOnClickListener {
                pulse(this)
                sendChatMessage()
            }
        }

        composer.addView(chatInput, LinearLayout.LayoutParams(0, dp(40), 1f))
        composer.addView(
            chatSendButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(40)
            ).apply { marginStart = dp(7) }
        )
        box.addView(composer)
        return box
    }

    private fun startPlayer() {
        scope.launch {
            try {
                val hlsUrl = TwitchVodResolver.resolveLiveChecked(live.login)
                if (destroyed) return@launch
                currentHlsUrl = hlsUrl

                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(1_800, 7_000, 350, 650)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()

                val liveSpeedControl = DefaultLivePlaybackSpeedControl.Builder()
                    .setFallbackMinPlaybackSpeed(0.995f)
                    .setFallbackMaxPlaybackSpeed(1.10f)
                    .setTargetLiveOffsetIncrementOnRebufferMs(300L)
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

                exo.setMediaItem(createLiveMediaItem(playbackUrl(hlsUrl)))
                exo.prepare()

                exo.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (exiting || destroyed) return
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
                        hideCommercialOverlay()
                        scheduleControlsHide()
                        handler.removeCallbacks(commercialGuardRunnable)
                        handler.postDelayed(commercialGuardRunnable, 2_500L)
                        // Media3 owns live-edge correction; no manual seek loop.
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (exiting || destroyed) return
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
                        if (exiting || destroyed) return
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
        if (destroyed || exiting || commercialRecoveryActive) return

        commercialRecoveryActive = true
        handler.removeCallbacks(commercialGuardRunnable)
        clearPlayerError()
        loader.visibility = View.GONE
        showCommercialOverlay()

        // Stay on the same Twitch channel. During the server-side ad break,
        // poll for this channel's normal live playlist and jump back to its newest live edge.
        pollSameChannelAfterCommercial()
    }

    private fun pollSameChannelAfterCommercial() {
        if (destroyed || exiting || !commercialRecoveryActive) return

        scope.launch {
            delay(1_800L)
            if (destroyed || exiting || !commercialRecoveryActive) return@launch

            try {
                val freshUrl = TwitchVodResolver.resolveLiveChecked(
                    login = live.login,
                    connectTimeoutMs = 5_000,
                    readTimeoutMs = 6_000
                )
                if (destroyed || exiting || !commercialRecoveryActive) return@launch

                currentHlsUrl = freshUrl
                val exo = player
                if (exo == null) {
                    commercialRecoveryActive = false
                    hideCommercialOverlay()
                    startPlayer()
                    return@launch
                }

                exo.setMediaItem(createLiveMediaItem(playbackUrl(freshUrl)), true)
                exo.prepare()
                exo.seekToDefaultPosition()
                exo.play()

                commercialRecoveryActive = false
                handler.removeCallbacks(commercialGuardRunnable)
                handler.postDelayed(commercialGuardRunnable, 2_500L)
            } catch (_: TwitchCommercialBreakException) {
                pollSameChannelAfterCommercial()
            } catch (_: Exception) {
                pollSameChannelAfterCommercial()
            }
        }
    }

    private fun showCommercialOverlay() {
        playerView.alpha = 0f
        player?.volume = 0f

        val overlay = commercialOverlay ?: LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(Color.parseColor("#E8181620"), 17)

            val pulse = LatencyPulseView(activity).apply {
                setState(palette.accent, true, animationsEnabled)
            }
            addView(
                pulse,
                LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                    marginEnd = dp(7)
                }
            )

            addView(TextView(activity).apply {
                text = "Синхронизация LIVE…"
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })

            commercialOverlay = this
            playerCard.addView(
                this,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }

        overlay.visibility = View.VISIBLE
        if (animationsEnabled) {
            overlay.animate().cancel()
            overlay.alpha = 0f
            overlay.scaleX = 0.97f
            overlay.scaleY = 0.97f
            overlay.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(150L)
                .start()
        }
    }

    private fun hideCommercialOverlay() {
        playerView.animate().cancel()
        playerView.alpha = 1f
        player?.volume = if (muted) 0f else 1f

        commercialOverlay?.let { overlay ->
            overlay.animate().cancel()
            if (animationsEnabled && overlay.visibility == View.VISIBLE) {
                overlay.animate()
                    .alpha(0f)
                    .setDuration(120L)
                    .withEndAction {
                        overlay.visibility = View.GONE
                        overlay.alpha = 1f
                    }
                    .start()
            } else {
                overlay.visibility = View.GONE
                overlay.alpha = 1f
            }
        }
    }

    private fun startChat() {
        if (accessToken.isBlank() || accountLogin.isBlank()) {
            updateChatStatus("Переподключите Twitch", connected = false)
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
            },
            onNotice = { notice ->
                activity.runOnUiThread {
                    if (!destroyed && !exiting) {
                        Toast.makeText(activity, notice, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun updateChatStatus(text: String, connected: Boolean) {
        chatConnected = connected
        chatStatus.text = text
        chatStatus.setTextColor(if (connected) palette.accent else palette.muted)
        chatStatus.background = rounded(
            if (connected) palette.accentSoft else palette.surfaceAlt,
            13
        )
        chatSendButton.isEnabled = connected
        chatSendButton.alpha = if (connected) 1f else 0.45f
    }

    private fun appendChat(
        message: TwitchChatMessage,
        forceScroll: Boolean = false
    ) {
        val shouldStickToBottom =
            forceScroll || !chatHasMessages || isChatNearBottom()

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

        row.addView(
            View(activity).apply {
                setBackgroundColor(withAlpha(palette.stroke, 110))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply { topMargin = dp(7) }
        )

        chatList.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val now = SystemClock.uptimeMillis()
        if (animationsEnabled && now - lastChatRowAnimationAt > 140L) {
            lastChatRowAnimationAt = now
            row.alpha = 0f
            row.translationY = dp(4).toFloat()
            row.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(120L)
                .start()
        }

        chatRows.add(row)
        while (chatRows.size > 100) {
            val old = chatRows.removeAt(0)
            chatList.removeView(old)
        }

        if (shouldStickToBottom) scrollChatToBottom()
    }

    private fun isChatNearBottom(): Boolean {
        if (chatScroll.height <= 0 || chatScroll.childCount == 0) return true
        val child = chatScroll.getChildAt(0)
        val remaining = child.height - (chatScroll.scrollY + chatScroll.height)
        return remaining <= dp(88)
    }

    private fun scrollChatToBottom() {
        chatScroll.post {
            if (!destroyed) chatScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun sendChatMessage() {
        val message = chatInput.text?.toString().orEmpty().trim()
        if (message.isBlank()) return

        if (!chatConnected) {
            Toast.makeText(activity, "Чат ещё подключается", Toast.LENGTH_SHORT).show()
            return
        }

        chatSendButton.isEnabled = false
        chatSendButton.alpha = 0.55f

        chatClient.sendMessage(message) { success, error ->
            activity.runOnUiThread {
                if (destroyed || exiting) return@runOnUiThread

                chatSendButton.isEnabled = chatConnected
                chatSendButton.alpha = if (chatConnected) 1f else 0.45f

                if (success) {
                    chatInput.text?.clear()
                    appendChat(
                        TwitchChatMessage(
                            displayName = accountLogin.ifBlank { "Вы" },
                            text = message,
                            color = palette.accent
                        ),
                        forceScroll = true
                    )
                } else {
                    Toast.makeText(
                        activity,
                        error ?: "Не удалось отправить сообщение",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun updateLatencyIndicator() {
        val p = player
        val offset = p?.currentLiveOffset ?: C.TIME_UNSET

        val bucket: String
        val label: String
        val color: Int
        val active: Boolean

        when {
            p == null -> {
                bucket = "connecting"
                label = "Подключаемся…"
                color = palette.accent
                active = true
            }
            p.playbackState == Player.STATE_BUFFERING -> {
                bucket = "buffering"
                label = "Стабилизируем поток"
                color = Color.parseColor("#F0B24A")
                active = true
            }
            !p.isPlaying && p.playbackState == Player.STATE_READY -> {
                bucket = "paused"
                label = "Пауза"
                color = Color.parseColor("#8E91A0")
                active = false
            }
            offset == C.TIME_UNSET -> {
                bucket = "live"
                label = "LIVE"
                color = Color.parseColor("#43D18D")
                active = true
            }
            offset <= 2_000L -> {
                bucket = "low"
                label = "LIVE • %.1fс".format(offset / 1000.0)
                color = Color.parseColor("#43D18D")
                active = true
            }
            offset <= 3_500L -> {
                bucket = "medium"
                label = "Задержка • %.1fс".format(offset / 1000.0)
                color = Color.parseColor("#F0B24A")
                active = true
            }
            else -> {
                bucket = "high"
                label = "Отставание • %.1fс".format(offset / 1000.0)
                color = Color.parseColor("#FF637B")
                active = true
            }
        }

        latencyDot.setState(color, active, animationsEnabled)
        if (latencyBucket != bucket && animationsEnabled) {
            latencyBucket = bucket
            latencyPill.animate().cancel()
            latencyPill.alpha = 0.72f
            latencyPill.animate()
                .alpha(1f)
                .setDuration(150L)
                .start()
        } else {
            latencyBucket = bucket
        }
        latencyLabel.text = label
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

    private fun playbackUrl(upstreamUrl: String): String =
        if (lowLatencyProxy.ensureStarted()) {
            lowLatencyProxy.playlistUrl(upstreamUrl)
        } else {
            upstreamUrl
        }

    private fun createLiveMediaItem(url: String): MediaItem =
        MediaItem.Builder()
            .setUri(url)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(900L)
                    .setMinOffsetMs(350L)
                    .setMaxOffsetMs(2_400L)
                    .setMinPlaybackSpeed(0.995f)
                    .setMaxPlaybackSpeed(1.10f)
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
                currentHlsUrl = freshUrl

                exo.setMediaItem(createLiveMediaItem(playbackUrl(freshUrl)), true)
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

    private fun formatViewerCount(value: Int): String =
        when {
            value >= 1_000_000 -> "%.1f млн".format(value / 1_000_000.0)
            value >= 10_000 -> "%.1f тыс.".format(value / 1_000.0)
            value >= 1_000 -> "%.1f тыс.".format(value / 1_000.0)
            else -> value.toString()
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

        if (enabled) {
            chatWasNearBottomBeforeFullscreen = isChatNearBottom()
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(chatInput.windowToken, 0)
            chatInput.clearFocus()
        }

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
            applyInlinePlayerLayout(params)
        }

        playerCard.layoutParams = params
        root.requestLayout()
        chatScroll.requestLayout()
        onFullscreen(enabled)

        if (!enabled) {
            root.post { restoreInlineLayoutAfterFullscreen() }
            handler.postDelayed({
                if (!destroyed && !fullscreen) restoreInlineLayoutAfterFullscreen()
            }, 180L)
        }

        showControls(autoHide = player?.isPlaying == true)
    }

    private fun applyInlinePlayerLayout(params: LinearLayout.LayoutParams) {
        val metrics = activity.resources.displayMetrics
        val inlineWidth =
            if (shouldForceLandscape()) {
                minOf(metrics.widthPixels, metrics.heightPixels)
            } else {
                root.width.takeIf { it > 0 } ?: metrics.widthPixels
            }

        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = (inlineWidth * 9f / 16f).toInt()
        params.weight = 0f
        params.marginStart = dp(12)
        params.marginEnd = dp(12)
    }

    private fun restoreInlineLayoutAfterFullscreen() {
        val params = playerCard.layoutParams as? LinearLayout.LayoutParams ?: return
        applyInlinePlayerLayout(params)
        playerCard.layoutParams = params
        root.requestLayout()
        playerCard.requestLayout()
        chatScroll.isEnabled = true
        chatScroll.requestLayout()
        chatList.requestLayout()
        if (chatWasNearBottomBeforeFullscreen) scrollChatToBottom()
    }

    fun exitFullscreen() {
        if (fullscreen) setFullscreenMode(false)
    }

    fun prepareForExit() {
        if (destroyed || exiting) return
        exiting = true
        handler.removeCallbacks(hideControlsRunnable)
        handler.removeCallbacks(commercialGuardRunnable)
        commercialRecoveryActive = false
        controlsOverlay.animate().cancel()
        playerErrorView?.animate()?.cancel()
        playerView.keepScreenOn = false
        runCatching { player?.pause() }
        runCatching { chatClient.close() }
    }

    private fun jumpToLiveEdgeAndPlay() {
        val p = player ?: return
        p.seekToDefaultPosition()
        if (p.playbackState == Player.STATE_IDLE) p.prepare()
        p.play()
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
        exiting = true
        handler.removeCallbacksAndMessages(null)

        if (fullscreen) {
            fullscreen = false
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (!activity.isFinishing && !activity.isDestroyed) onFullscreen(false)
        }

        controlsOverlay.animate().cancel()
        playerCard.animate().cancel()
        root.animate().cancel()
        chatClient.close()
        runCatching { lowLatencyProxy.stop() }
        scope.cancel()

        val oldPlayer = player
        player = null
        playerView.keepScreenOn = false
        playerView.player = null
        runCatching { oldPlayer?.release() }

        root.alpha = 1f
        root.translationX = 0f
        root.translationY = 0f
        root.scaleX = 1f
        root.scaleY = 1f
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
