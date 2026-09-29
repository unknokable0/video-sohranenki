package com.unknokable.videosohranenki

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.SeekBar
import android.widget.ScrollView
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.Toast
import coil.load
import coil.transform.RoundedCornersTransformation
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(UnstableApi::class)
class PlayerScreen(
    private val activity: Activity,
    private val item: VideoItem,
    private val mediaUrl: String,
    private val previewDataSourceFactory: (() -> MediaDataSource)?,
    private val settings: AppSettings,
    private val startPositionMs: Long = 0L,
    private val nextItem: VideoItem? = null,
    private val queueItems: List<VideoItem> = emptyList(),
    private val onPlayNext: ((VideoItem) -> Unit)? = null,
    private val onDownloadRequested: ((VideoItem) -> Unit)? = null,
    private val isWatched: Boolean = false,
    private val onWatchedChange: ((VideoItem, Boolean) -> Unit)? = null,
    private val onBack: () -> Unit,
    private val onFullscreen: (Boolean) -> Unit,
    private val onPlaybackStarted: () -> Unit
) {
    val root = LinearLayout(activity)
    val player: ExoPlayer

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var playerCard: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var header: LinearLayout
    private lateinit var details: LinearLayout
    private lateinit var overlay: FrameLayout
    private lateinit var playPause: ImageButton
    private lateinit var seekBar: SohrTimeBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var qualityButton: TextView
    private lateinit var speedBadge: TextView
    private lateinit var seekFeedback: TextView
    private lateinit var bufferingLoader: LoadingWaveView
    private lateinit var actionsRow: LinearLayout
    private lateinit var socialActionsRow: LinearLayout
    private lateinit var nextVideosBlock: LinearLayout
    private lateinit var previewBubble: LinearLayout
    private lateinit var previewImage: ImageView
    private lateinit var previewTime: TextView
    private lateinit var posterImage: ImageView
    private lateinit var endOverlay: LinearLayout
    private lateinit var miniBar: LinearLayout
    private lateinit var miniVideoHost: FrameLayout
    private var miniMode = false
    private var gestureOverlay: PlayerGestureOverlay? = null
    private val autoHideControls = Runnable { if (player.isPlaying && !dragging) hideOverlay() }
    private val previewScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val previewMutex = Mutex()
    private var previewRetriever: MediaMetadataRetriever? = null
    private var previewDataSource: MediaDataSource? = null
    private var previewJob: Job? = null
    private var previewBitmap: Bitmap? = null
    private val previewCache = object : LinkedHashMap<Long, Bitmap>(10, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap>?): Boolean {
            if (size <= 10) return false
            eldest?.value?.takeIf { it !== previewBitmap }?.recycle()
            return true
        }
    }
    private var previewRequestId = 0L
    private var lastPreviewMs = -1L

    private var fullscreen = false
    private var fullscreenTransition = false
    private var fullscreenHost: FrameLayout? = null
    private var settingsOverlay: FrameLayout? = null
    private var settingsPanel: View? = null
    private var fullscreenOriginalIndex = -1
    private var fullscreenOriginalLayoutParams: LinearLayout.LayoutParams? = null
    private var pipMode = false
    private var pipHost: FrameLayout? = null
    private var pipOriginalParent: ViewGroup? = null
    private var pipOriginalIndex = -1
    private var pipOriginalLayoutParams: ViewGroup.LayoutParams? = null
    private var ambientAnimator: android.animation.ValueAnimator? = null
    private var ambientColor: Int = palette.accent
    private var dragging = false
    private var speed = settings.playbackSpeed
    private var sleepRunnable: Runnable? = null
    private var sleepSelection = 0
    private var qualitySelection = 0
    private var playbackCounted = false
    private var playbackRecoveryAttempts = 0
    private lateinit var mediaSessionBridge: SohrMediaSessionBridge
    private var destroyed = false
    private var lastProgressPersistAt = 0L
    private var speedActionButton: TextView? = null

    private data class PlayerActionPill(
        val root: LinearLayout,
        val icon: ImageView?,
        val label: TextView
    )
    private val showBufferingRunnable = Runnable {
        if (::bufferingLoader.isInitialized && player.playbackState == Player.STATE_BUFFERING) {
            bufferingLoader.visibility = View.VISIBLE
            bufferingLoader.animate().cancel()
            bufferingLoader.animate().alpha(0.92f).setDuration(90).start()
        }
    }
    private val palette get() = settings.palette()

    init {
        root.orientation = LinearLayout.VERTICAL
        root.background = ambientGradient(palette.accent)
        root.keepScreenOn = false

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                8_000,
                35_000,
                300,
                900
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Create ExoPlayer before building any child UI. Some child views query
        // playback state while they are being constructed.
        player = ExoPlayer.Builder(activity)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        mediaSessionBridge = SohrMediaSessionBridge(activity, player, item, mediaUrl)

        header = buildHeader()
        root.addView(header)

        playerCard = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            background = rounded("#000000", 16)
            clipToOutline = true
        }

        playerView = activity.layoutInflater.inflate(
            R.layout.view_sohr_player,
            playerCard,
            false
        ) as PlayerView
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

        posterImage = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.BLACK)
            val localThumb = item.thumbnailPath?.takeIf { it.isNotBlank() }
            when {
                localThumb != null -> load(java.io.File(localThumb)) {
                    crossfade(settings.animations)
                    listener(onSuccess = { _, result -> updateAmbientFromDrawable(result.drawable) })
                }
                !item.thumbnailUrl.isNullOrBlank() -> load(item.thumbnailUrl) {
                    crossfade(settings.animations)
                    listener(onSuccess = { _, result -> updateAmbientFromDrawable(result.drawable) })
                }
            }
        }
        playerCard.addView(
            posterImage,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        overlay = buildOverlay()
        playerCard.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        bufferingLoader = LoadingWaveView(activity, palette.accent).apply {
            visibility = View.GONE
            alpha = 0.92f
        }
        playerCard.addView(
            bufferingLoader,
            FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER)
        )

        speedBadge = TextView(activity).apply {
            text = "2×"
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(9), dp(5), dp(9), dp(5))
            background = rounded("#B0000000", 10)
            alpha = 0f
        }
        playerCard.addView(
            speedBadge,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { topMargin = dp(12) }
        )

        seekFeedback = TextView(activity).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(11), dp(16), dp(11))
            background = rounded("#B5120F1A", 22)
            alpha = 0f
            visibility = View.GONE
            isClickable = false
            isFocusable = false
            elevation = dp(10).toFloat()
        }
        playerCard.addView(
            seekFeedback,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        val width = activity.resources.displayMetrics.widthPixels
        val playerMargin = dp(16)
        val playerWidth = (width - playerMargin * 2).coerceAtLeast(dp(240))
        root.addView(
            playerCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (playerWidth * 9f / 16f).toInt()
            ).apply {
                marginStart = playerMargin
                marginEnd = playerMargin
                topMargin = dp(9)
            }
        )

        details = buildDetails()
        root.addView(details)
        socialActionsRow = buildSocialActions()
        root.addView(socialActionsRow)
        nextVideosBlock = buildNextVideosBlock()
        root.addView(nextVideosBlock)
        miniBar = buildMiniPlayer()
        root.addView(miniBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)))

        previewBubble = buildSeekPreview()
        playerCard.addView(
            previewBubble,
            FrameLayout.LayoutParams(dp(174), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(58)
            }
        )

        endOverlay = buildEndOverlay()
        playerCard.addView(
            endOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        playerView.player = player
        player.setMediaItem(mediaSessionBridge.mediaItem)
        if (startPositionMs > 0) player.seekTo(startPositionMs)
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.playbackParameters = PlaybackParameters(speed)
        player.playWhenReady = true
        player.prepare()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayIcon()
                root.keepScreenOn = isPlaying
                if (isPlaying && ::endOverlay.isInitialized) endOverlay.visibility = View.GONE
                if (isPlaying) {
                    playbackRecoveryAttempts = 0
                    handler.removeCallbacks(autoHideControls)
                    handler.postDelayed(autoHideControls, 3_000L)
                } else {
                    handler.removeCallbacks(autoHideControls)
                }
                if (isPlaying && !playbackCounted) {
                    playbackCounted = true
                    onPlaybackStarted()
                }
            }

            override fun onRenderedFirstFrame() {
                posterImage.animate().cancel()
                posterImage.animate()
                    .alpha(0f)
                    .setDuration(if (settings.animations) 120L else 0L)
                    .withEndAction { posterImage.visibility = View.GONE }
                    .start()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                recoverFromPlaybackError(error)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateProgress()

                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        handler.removeCallbacks(showBufferingRunnable)
                        handler.postDelayed(showBufferingRunnable, 220L)
                    }
                    Player.STATE_READY, Player.STATE_ENDED, Player.STATE_IDLE -> {
                        handler.removeCallbacks(showBufferingRunnable)
                        bufferingLoader.animate().cancel()
                        bufferingLoader.animate()
                            .alpha(0f)
                            .setDuration(120)
                            .withEndAction {
                                bufferingLoader.visibility = View.GONE
                                bufferingLoader.alpha = 0.92f
                            }
                            .start()
                    }
                }

                if (playbackState == Player.STATE_READY) {
                    updateDurationLabel()
                    updateQualityLabel()
                }

                if (playbackState == Player.STATE_ENDED) {
                    settings.clearPlaybackPosition(item.messageId)
                    root.keepScreenOn = false
                    if (settings.autoplay && nextItem != null && onPlayNext != null) onPlayNext.invoke(nextItem) else showEndOverlay()
                } else if (playbackState == Player.STATE_READY && !player.isPlaying) {
                    root.keepScreenOn = false
                }
            }
        })

        installGestures()
        scheduleProgress()
    }

    private fun recoverFromPlaybackError(error: androidx.media3.common.PlaybackException) {
        handler.removeCallbacks(showBufferingRunnable)
        if (destroyed) return

        val canRetry =
            playbackRecoveryAttempts < 2 &&
                error.errorCode in 2000..3999

        if (!canRetry) {
            showPlaybackFailure()
            return
        }

        playbackRecoveryAttempts++
        val resumeAt = player.currentPosition
            .coerceAtLeast(startPositionMs)
            .coerceAtLeast(0L)
        val delayMs = if (playbackRecoveryAttempts == 1) 180L else 550L

        bufferingLoader.animate().cancel()
        bufferingLoader.alpha = 0.92f
        bufferingLoader.visibility = View.VISIBLE

        handler.postDelayed({
            if (destroyed) return@postDelayed
            runCatching {
                player.stop()
                player.clearMediaItems()
                player.setMediaItem(mediaSessionBridge.mediaItem)
                if (resumeAt > 0L) player.seekTo(resumeAt)
                player.playbackParameters = PlaybackParameters(speed)
                player.playWhenReady = true
                player.prepare()
            }.onFailure {
                showPlaybackFailure()
            }
        }, delayMs)
    }

    private fun showPlaybackFailure() {
        if (destroyed) return
        handler.removeCallbacks(showBufferingRunnable)
        bufferingLoader.animate().cancel()
        bufferingLoader.visibility = View.GONE
        root.keepScreenOn = false
        Toast.makeText(
            activity,
            "Не удалось воспроизвести видео",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun buildHeader(): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setBackgroundColor(palette.background)
        }

        val back = iconButton(R.drawable.ic_back, "#181322", 38).apply {
            setOnClickListener { pulse(this); onBack() }
        }

        val title = TextView(activity).apply {
            text = cleanTitle(item.title)
            textSize = 13.5f
            setTextColor(palette.text)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            setPadding(dp(10), 0, dp(10), 0)
        }

        val spacer = View(activity)

        row.addView(back, LinearLayout.LayoutParams(dp(38), dp(38)))
        row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(spacer, LinearLayout.LayoutParams(dp(38), dp(38)))
        return row
    }

    private fun buildOverlay(): FrameLayout {
        val frame = FrameLayout(activity).apply {
            setBackgroundColor(Color.parseColor("#12000000"))
        }

        val center = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        playPause = iconButton(R.drawable.ic_play, "#8B5CF6", 48).apply {
            setOnClickListener {
                if (player.isPlaying) player.pause() else player.play()
                pulse(this)
            }
        }

        center.addView(playPause, LinearLayout.LayoutParams(dp(50), dp(50)))

        frame.addView(
            center,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        val bottom = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(2), dp(7), dp(3))
            background = rounded("#480A0810", 12)
        }

        seekBar = SohrTimeBar(activity).apply {
            listener = object : SohrTimeBar.Listener {
                override fun onScrubStart(positionMs: Long) {
                    dragging = true
                    player.setScrubbingModeEnabled(true)
                    showPreview()
                    updatePreviewUi(positionMs, 0f)
                }

                override fun onScrubMove(positionMs: Long, fraction: Float) {
                    updatePreviewUi(positionMs, fraction)
                    requestPreview(positionMs)
                }

                override fun onScrubStop(positionMs: Long, canceled: Boolean) {
                    if (!canceled) {
                        previewRequestId++
                        previewJob?.cancel()
                        previewJob = null
                        player.seekTo(positionMs)
                        currentTime.text = formatMs(positionMs)
                    }
                    dragging = false
                    player.setScrubbingModeEnabled(false)
                    handler.postDelayed({ hidePreview() }, 90L)
                }
            }
        }

        val times = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        currentTime = timeLabel("0:00").apply {
            minWidth = dp(38)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        totalTime = timeLabel(initialDurationLabel()).apply {
            minWidth = dp(42)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(5), 0)
        }
        val spacer = View(activity)

        qualityButton = TextView(activity).apply {
            text = "Авто"
            textSize = 10f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            minWidth = dp(40)
            setPadding(dp(6), 0, dp(6), 0)
            background = rounded("#42221A30", 12)
            contentDescription = "Качество видео"
            setOnClickListener { pulse(this); showQualityPicker() }
        }

        val settingsButton = iconButton(R.drawable.ic_player_settings, "#42221A30", 32).apply {
            contentDescription = "Настройки плеера"
            setOnClickListener { pulse(this); showSettingsSheet() }
        }

        val fullscreenButton = iconButton(R.drawable.ic_fullscreen, "#42221A30", 32).apply {
            contentDescription = "Полный экран"
            setOnClickListener {
                onFullscreen(!fullscreen)
                pulse(this)
            }
        }

        val actionGroup = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
            setBackgroundColor(Color.TRANSPARENT)
        }
        actionGroup.addView(
            qualityButton,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28))
        )
        actionGroup.addView(
            settingsButton,
            LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginStart = dp(2) }
        )
        actionGroup.addView(
            fullscreenButton,
            LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginStart = dp(2) }
        )

        times.addView(currentTime, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)))
        times.addView(totalTime, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)).apply {
            marginStart = dp(4)
        })
        times.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        times.addView(
            actionGroup,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28))
        )

        bottom.addView(seekBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)))
        bottom.addView(times)

        frame.addView(
            bottom,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                leftMargin = dp(7)
                rightMargin = dp(7)
                bottomMargin = dp(5)
            }
        )
        return frame
    }

    private fun buildSeekPreview(): LinearLayout {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(7), dp(7), dp(6))
            background = rounded("#E6110E19", 13)
            visibility = View.GONE
            elevation = dp(8).toFloat()
        }

        previewImage = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.BLACK)
        }

        previewTime = TextView(activity).apply {
            text = "0:00"
            textSize = 12f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(5), 0, 0)
        }

        box.addView(previewImage, LinearLayout.LayoutParams(dp(160), dp(90)))
        box.addView(previewTime, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return box
    }

    private fun updatePreviewUi(positionMs: Long, fraction: Float) {
        previewTime.text = formatMs(positionMs)
        currentTime.text = formatMs(positionMs)
        val maxShift = ((playerCard.width - dp(174)) / 2f).coerceAtLeast(0f)
        previewBubble.translationX = ((fraction.coerceIn(0f, 1f) - 0.5f) * 2f * maxShift)
    }

    private fun requestPreview(positionMs: Long) {
        if (positionMs < 0) return
        val bucket = (positionMs / 2_000L) * 2_000L
        previewCache[bucket]?.takeIf { !it.isRecycled }?.let { cached ->
            previewImage.setImageBitmap(cached)
            previewBitmap = cached
        }
    }

    private fun ensurePreviewRetriever() {
        if (previewRetriever != null) return
        val factory = previewDataSourceFactory ?: return
        val dataSource = factory()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(dataSource)
            previewDataSource = dataSource
            previewRetriever = retriever
        } catch (e: Exception) {
            runCatching { retriever.release() }
            runCatching { dataSource.close() }
            throw e
        }
    }

    private fun showPreview() {
        if (previewBubble.visibility == View.VISIBLE) return
        previewBubble.alpha = 0f
        previewBubble.scaleX = 0.96f
        previewBubble.scaleY = 0.96f
        previewBubble.visibility = View.VISIBLE
        previewBubble.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(if (settings.animations) 110 else 0)
            .start()
    }

    private fun hidePreview() {
        if (previewBubble.visibility != View.VISIBLE) return
        previewBubble.animate()
            .alpha(0f)
            .scaleX(0.97f)
            .scaleY(0.97f)
            .setDuration(if (settings.animations) 100 else 0)
            .withEndAction { previewBubble.visibility = View.GONE }
            .start()
    }

    private fun buildEndOverlay(): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.parseColor("#CC000000"))
            visibility = View.GONE

            val label = TextView(activity).apply {
                text = if (nextItem != null) "Видео закончилось" else "Конец видео"
                textSize = 15f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, dp(12))
            }
            val button = TextView(activity).apply {
                text = if (nextItem != null) "▶  Следующее видео" else "↻  Смотреть сначала"
                textSize = 14f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(dp(18), dp(12), dp(18), dp(12))
                background = rounded("#8B5CF6", 22)
                setOnClickListener {
                    pulse(this)
                    val next = nextItem
                    if (next != null && onPlayNext != null) {
                        onPlayNext.invoke(next)
                    } else {
                        endOverlay.visibility = View.GONE
                        player.seekTo(0L)
                        player.play()
                    }
                }
            }
            addView(label)
            addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun showEndOverlay() {
        hideOverlay()
        endOverlay.alpha = 0f
        endOverlay.visibility = View.VISIBLE
        endOverlay.animate().cancel()
        endOverlay.animate().alpha(1f).setDuration(if (settings.animations) 140L else 0L).start()
    }

    private fun buildDetails(): LinearLayout {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(9))
        }

        val title = TextView(activity).apply {
            text = cleanTitle(item.title)
            textSize = 17.5f
            includeFontPadding = false
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(palette.text)
            setTypeface(typeface, Typeface.BOLD)
        }
        box.addView(title)

        val descriptionText = cleanDescription(item.title)
        if (descriptionText.isNotBlank()) {
            box.addView(TextView(activity).apply {
                text = descriptionText
                textSize = 13.5f
                setLineSpacing(dp(2).toFloat(), 1f)
                includeFontPadding = false
                maxLines = 5
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(palette.text)
                alpha = 0.88f
                setPadding(0, dp(11), 0, 0)
            })
        }

        val meta = TextView(activity).apply {
            text = buildMeta()
            textSize = 12.5f
            includeFontPadding = false
            setTextColor(palette.muted)
            setPadding(0, dp(if (descriptionText.isBlank()) 7 else 11), 0, 0)
        }

        box.addView(meta)
        return box
    }

    private fun buildSocialActions(): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(3), dp(16), dp(11))
        }

        var watched = isWatched
        lateinit var watchedButton: PlayerActionPill

        fun syncWatchedAction(animated: Boolean = false) {
            val nextText = if (watched) "В сборники" else "В просмотренные"
            val nextIcon = if (watched) R.drawable.ic_action_restore else R.drawable.ic_check
            val nextBackground = if (watched) palette.accent else palette.surfaceAlt
            val nextTextColor = if (watched) Color.WHITE else palette.text

            watchedButton.root.animate().cancel()

            fun applyState() {
                watchedButton.label.text = nextText
                watchedButton.label.setTextColor(nextTextColor)
                watchedButton.root.background = roundedInt(nextBackground, 14)
                setActionIconSafely(watchedButton.icon, nextIcon, nextTextColor)
            }

            if (animated && settings.animations) {
                watchedButton.root.animate()
                    .alpha(0.56f)
                    .scaleX(0.965f)
                    .scaleY(0.965f)
                    .setDuration(70L)
                    .withEndAction {
                        applyState()
                        watchedButton.root.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(135L)
                            .setInterpolator(
                                android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
                            )
                            .start()
                    }
                    .start()
            } else {
                applyState()
                watchedButton.root.alpha = 1f
                watchedButton.root.scaleX = 1f
                watchedButton.root.scaleY = 1f
            }
        }

        watchedButton = actionPill(
            label = "",
            iconRes = if (watched) R.drawable.ic_action_restore else R.drawable.ic_check
        ) {
            watched = !watched
            onWatchedChange?.invoke(item, watched)
            syncWatchedAction(animated = true)
        }
        syncWatchedAction()

        if (item.source == "twitch") {
            row.addView(
                watchedButton.root,
                LinearLayout.LayoutParams(0, dp(42), 1f)
            )
        } else {
            val downloadButton = actionPill(
                "Скачать",
                R.drawable.ic_action_download
            ) { enqueueDownload() }

            row.addView(
                watchedButton.root,
                LinearLayout.LayoutParams(0, dp(42), 1.35f).apply {
                    marginEnd = dp(6)
                }
            )
            row.addView(
                downloadButton.root,
                LinearLayout.LayoutParams(0, dp(42), 0.85f)
            )
        }
        return row
    }

    private fun buildNextVideosBlock(): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(7), dp(16), dp(22))

            val candidates = (queueItems + listOfNotNull(nextItem))
                .filterNot { it.messageId == item.messageId }
                .distinctBy { it.messageId }
                .take(4)

            addView(TextView(activity).apply {
                text = if (queueItems.isNotEmpty()) "Далее • очередь" else "Следующие видео"
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.text)
                includeFontPadding = false
                setPadding(0, dp(5), 0, dp(11))
            })

            if (candidates.isEmpty()) {
                addView(TextView(activity).apply {
                    text = "Это последнее видео в сборнике"
                    textSize = 13f
                    setTextColor(palette.muted)
                    setPadding(dp(14), dp(14), dp(14), dp(14))
                    background = roundedInt(palette.surfaceAlt, 16)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            } else {
                candidates.forEachIndexed { index, next ->
                    val queued = queueItems.any { it.messageId == next.messageId }
                    addView(LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(13), dp(11), dp(12), dp(11))
                        minimumHeight = dp(82)
                        background = roundedInt(palette.surfaceAlt, 19)
                        isClickable = true
                        isFocusable = true

                        val thumb = ImageView(activity).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            background = roundedInt(palette.surface, 12)
                            clipToOutline = true
                            val localThumb = next.thumbnailPath?.takeIf { it.isNotBlank() }
                            when {
                                localThumb != null -> load(java.io.File(localThumb)) { crossfade(settings.animations) }
                                !next.thumbnailUrl.isNullOrBlank() -> load(next.thumbnailUrl) { crossfade(settings.animations) }
                            }
                        }
                        addView(thumb, LinearLayout.LayoutParams(dp(108), dp(61)).apply { marginEnd = dp(12) })

                        val textBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                        textBox.addView(TextView(activity).apply {
                            text = cleanTitle(next.title)
                            textSize = 13f
                            maxLines = 2
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            includeFontPadding = false
                            setTypeface(typeface, Typeface.BOLD)
                            setTextColor(palette.text)
                        })
                        textBox.addView(TextView(activity).apply {
                            text = formatMs(next.durationSeconds * 1000L) + "  •  " +
                                if (queued) "Очередь " + (index + 1) else "Следующее"
                            textSize = 11.5f
                            setTextColor(palette.muted)
                            setPadding(0, dp(3), 0, 0)
                        })
                        addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        addView(ImageView(activity).apply {
                            setImageResource(R.drawable.ic_play)
                            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                            scaleType = ImageView.ScaleType.CENTER_INSIDE
                            setPadding(dp(9), dp(9), dp(9), dp(9))
                            background = roundedInt(palette.accent, 18)
                        }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginStart = dp(10) })

                        setOnClickListener {
                            pulse(this)
                            if (settings.animations) {
                                animate().cancel()
                                animate()
                                    .scaleX(0.985f)
                                    .scaleY(0.985f)
                                    .setDuration(SohrMotion.FAST / 2)
                                    .setInterpolator(SohrMotion.smooth())
                                    .withEndAction {
                                        animate()
                                            .scaleX(1f)
                                            .scaleY(1f)
                                            .setDuration(SohrMotion.FAST)
                                            .setInterpolator(SohrMotion.smooth())
                                            .start()
                                        onPlayNext?.invoke(next)
                                    }
                                    .start()
                            } else {
                                onPlayNext?.invoke(next)
                            }
                        }
                    }, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { if (index < candidates.lastIndex) bottomMargin = dp(7) })
                }
            }
        }
    }

    private fun enqueueDownload() {
        if (onDownloadRequested != null) {
            onDownloadRequested.invoke(item)
            return
        }
        val safeTitle = cleanTitle(item.title)
            .replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
            .take(70)
            .ifBlank { "SOHR_${item.messageId}" }
        val fileName = if (safeTitle.endsWith(".mp4", true)) safeTitle else "$safeTitle.mp4"
        runCatching {
            val request = DownloadManager.Request(Uri.parse(mediaUrl))
                .setTitle(cleanTitle(item.title))
                .setDescription("SOHR • загрузка видео")
                .setMimeType(item.mimeType.ifBlank { "video/mp4" })
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(activity, "Загрузка началась", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(activity, "Не удалось начать загрузку", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildActions(): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), dp(14))
        }

        val speedLabel =
            if (speed == 1f) "1×  Скорость" else speed.toString() + "×  Скорость"
        val speedBtn = actionPill(
            speedLabel,
            R.drawable.ic_player_speed
        ) { showSpeedPicker() }
        speedActionButton = speedBtn.label

        val sleepBtn = actionPill(
            "Таймер",
            R.drawable.ic_player_timer
        ) { showSleepPicker() }

        row.addView(
            speedBtn.root,
            LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginEnd = dp(6)
            }
        )
        row.addView(
            sleepBtn.root,
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )
        return row
    }

    private fun actionPill(
        label: String,
        iconRes: Int? = null,
        onClick: (View) -> Unit
    ): PlayerActionPill {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), 0)
            background = roundedInt(palette.surfaceAlt, 14)
            isClickable = true
            isFocusable = true
        }

        val iconView = iconRes?.let { resId ->
            ImageView(activity).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setActionIconSafely(this, resId, palette.text)
            }.also {
                root.addView(
                    it,
                    LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                        marginEnd = dp(7)
                    }
                )
            }
        }

        val labelView = TextView(activity).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            maxLines = 1
        }
        root.addView(
            labelView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.setOnClickListener {
            pulse(root)
            onClick(root)
        }

        return PlayerActionPill(root, iconView, labelView)
    }

    private fun setActionIconSafely(target: ImageView?, resId: Int, tint: Int) {
        if (target == null) return
        val success = runCatching {
            target.setImageResource(resId)
            target.imageTintList =
                android.content.res.ColorStateList.valueOf(tint)
            target.visibility = View.VISIBLE
        }.isSuccess

        if (!success) {
            // A decorative icon must never be allowed to crash video playback.
            target.setImageDrawable(null)
            target.visibility = View.GONE
        }
    }

    private fun installGestures() {
        gestureOverlay = PlayerGestureOverlay(
            activity = activity,
            target = playerCard,
            player = player,
            durationProvider = { resolvedDurationMs() },
            progressZone = { y -> y >= playerCard.height - dp(72) },
            onSingleTap = { toggleOverlay() },
            onDoubleTap = { forward, seconds -> showSeekFeedback(forward, seconds) },
            onTemporarySpeed = { enabled ->
                if (enabled) { player.setPlaybackSpeed(2f); speedBadge.animate().alpha(1f).setDuration(220L).start() }
                else { player.setPlaybackSpeed(speed); speedBadge.animate().alpha(0f).setDuration(220L).start() }
            },
            onScrub = { positionMs, finished ->
                dragging = !finished
                if (!finished) {
                    player.setScrubbingModeEnabled(true)
                    showPreview()
                    updatePreviewUi(positionMs, (positionMs.toFloat()/resolvedDurationMs().coerceAtLeast(1L)).coerceIn(0f,1f))
                    requestPreview(positionMs)
                } else {
                    previewRequestId++
                    previewJob?.cancel()
                    previewJob = null
                    player.setScrubbingModeEnabled(false)
                    player.seekTo(positionMs)
                    hidePreview()
                }
            },
            onVolume = { showTransientIndicator("♪  $it%") },
            onFillMode = { fill ->
                playerView.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                showTransientIndicator(if (fill) "Заполнить экран" else "Уменьшить")
            },
            onSwipeDown = { if (fullscreen) onFullscreen(false) else enterMiniPlayer() },
            onSwipeUp = { if (fullscreen) onFullscreen(false); exitMiniPlayer(); nextVideosBlock.visibility = View.VISIBLE }
        )
        playerCard.setOnTouchListener(gestureOverlay)
    }


    private fun showSeekFeedback(forward: Boolean, seconds: Int = 10) {
        if (!::seekFeedback.isInitialized) return
        seekFeedback.animate().cancel()
        seekFeedback.text = if (forward) "+$seconds сек   ››" else "‹‹   −$seconds сек"
        seekFeedback.translationX = if (forward) playerCard.width * 0.23f else -playerCard.width * 0.23f
        seekFeedback.alpha = 0f
        seekFeedback.scaleX = 0.78f
        seekFeedback.scaleY = 0.78f
        seekFeedback.visibility = View.VISIBLE

        val duration = if (settings.animations) 170L else 0L
        seekFeedback.animate()
            .alpha(1f)
            .scaleX(1.06f)
            .scaleY(1.06f)
            .setDuration(duration)
            .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
            .withEndAction {
                seekFeedback.animate()
                    .alpha(0f)
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setStartDelay(if (settings.animations) 250L else 0L)
                    .setDuration(if (settings.animations) 170L else 0L)
                    .withEndAction { seekFeedback.visibility = View.GONE }
                    .start()
            }
            .start()
    }


    private fun showPlayerMenu() = showSettingsSheet()

    private fun showSettingsSheet() {
        if (fullscreen && fullscreenHost != null) {
            showFullscreenSettingsPanel()
            return
        }

        val dialog = BottomSheetDialog(activity)
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            background = roundedInt(palette.surface, 24)
        }
        scroll.addView(
            buildSettingsContent { index ->
                dialog.dismiss()
                handleSettingsAction(index)
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        dialog.setContentView(scroll)
        dialog.show()

        val maxHeight = (activity.resources.displayMetrics.heightPixels * 0.72f).toInt()
        val comfortableHeight = minOf(maxHeight, dp(390))
        dialog.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        )?.let { sheet ->
            sheet.layoutParams = sheet.layoutParams.apply {
                height = comfortableHeight
            }
            sheet.requestLayout()
        }
        dialog.behavior.apply {
            isFitToContents = true
            skipCollapsed = true
            state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun buildSettingsContent(onItemClick: (Int) -> Unit): LinearLayout {
        val disabledSubs =
            player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val items = listOf(
            "Качество" to qualityButton.text.toString(),
            "Скорость воспроизведения" to if (speed == 1f) "Обычная" else "${speed}x",
            "Субтитры" to if (disabledSubs) "Выкл" else "Авто",
            "Таймер сна" to sleepLabel(),
            "Автовоспроизведение" to if (settings.autoplay) "Вкл" else "Выкл"
        )

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(14))
            background = roundedInt(palette.surface, 22)

            addView(TextView(activity).apply {
                text = "Настройки видео"
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.text)
                setPadding(dp(3), dp(3), dp(3), dp(9))
            })

            items.forEachIndexed { index, pair ->
                addView(
                    sheetRow(pair.first, pair.second) {
                        onItemClick(index)
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(50)
                    ).apply {
                        bottomMargin = dp(4)
                    }
                )
            }
        }
    }

    private fun handleSettingsAction(index: Int) {
        when (index) {
            0 -> showQualityPicker()
            1 -> showSpeedPicker()
            2 -> toggleSubtitles()
            3 -> showSleepPicker()
            4 -> {
                settings.autoplay = !settings.autoplay
                showSettingsSheet()
            }
        }
    }

    private fun showFullscreenSettingsPanel() {
        val host = fullscreenHost ?: return
        dismissFullscreenSettings(animated = false)

        val scrim = FrameLayout(activity).apply {
            setBackgroundColor(Color.parseColor("#99000000"))
            isClickable = true
            isFocusable = true
            elevation = dp(140).toFloat()
        }

        val screenWidth =
            host.width.takeIf { it > 0 }
                ?: activity.resources.displayMetrics.widthPixels
        val panelWidth = minOf(
            dp(420),
            (screenWidth * 0.50f).toInt()
        ).coerceAtLeast(dp(300))
            .coerceAtMost((screenWidth - dp(24)).coerceAtLeast(dp(280)))

        val panel = FrameLayout(activity).apply {
            background = roundedInt(palette.surface, 24)
            isClickable = true
            elevation = dp(18).toFloat()
        }

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                buildSettingsContent { index ->
                    dismissFullscreenSettings {
                        handleSettingsAction(index)
                    }
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        panel.addView(
            scroll,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        scrim.addView(
            panel,
            FrameLayout.LayoutParams(
                panelWidth,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END
            ).apply {
                topMargin = dp(10)
                bottomMargin = dp(10)
                marginEnd = dp(10)
            }
        )

        scrim.setOnClickListener {
            dismissFullscreenSettings()
        }
        panel.setOnClickListener { /* consume clicks inside panel */ }

        host.addView(
            scrim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        settingsOverlay = scrim
        settingsPanel = panel

        if (settings.animations) {
            scrim.alpha = 0f
            panel.translationX = panelWidth.toFloat() + dp(24)
            scrim.animate()
                .alpha(1f)
                .setDuration(160L)
                .start()
            panel.animate()
                .translationX(0f)
                .setDuration(220L)
                .setInterpolator(
                    android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
                )
                .start()
        }
    }

    fun dismissFullscreenSettingsIfOpen(): Boolean {
        if (settingsOverlay == null) return false
        dismissFullscreenSettings()
        return true
    }

    private fun dismissFullscreenSettings(
        animated: Boolean = true,
        after: (() -> Unit)? = null
    ) {
        val scrim = settingsOverlay
        val panel = settingsPanel

        if (scrim == null) {
            after?.invoke()
            return
        }

        settingsOverlay = null
        settingsPanel = null

        fun removeNow() {
            runCatching { (scrim.parent as? ViewGroup)?.removeView(scrim) }
            if (fullscreen && player.isPlaying) {
                handler.removeCallbacks(autoHideControls)
                handler.postDelayed(autoHideControls, 3_000L)
            }
            after?.invoke()
        }

        if (
            animated &&
            settings.animations &&
            panel != null &&
            panel.width > 0
        ) {
            scrim.animate()
                .alpha(0f)
                .setDuration(140L)
                .start()
            panel.animate()
                .translationX(panel.width.toFloat() + dp(24))
                .setDuration(170L)
                .setInterpolator(
                    android.view.animation.PathInterpolator(0.4f, 0f, 1f, 1f)
                )
                .withEndAction { removeNow() }
                .start()
        } else {
            removeNow()
        }
    }

    private fun sheetRow(title: String, value: String, click: () -> Unit): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(8), 0)
            background = roundedInt(palette.surfaceAlt, 18)
            isClickable = true
            isFocusable = true

            addView(
                TextView(activity).apply {
                    text = title
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(palette.text)
                },
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            addView(TextView(activity).apply {
                text = value
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.accent)
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = roundedInt(palette.surface, 11)
            })

            addView(
                ImageView(activity).apply {
                    setImageResource(R.drawable.ic_chevron_right)
                    imageTintList =
                        android.content.res.ColorStateList.valueOf(palette.muted)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                },
                LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                    marginStart = dp(5)
                }
            )

            setOnClickListener {
                pulse(this)
                click()
            }
        }

    private fun toggleSubtitles() {
        val params=player.trackSelectionParameters
        val disabled=params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        player.trackSelectionParameters=params.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,!disabled).build()
        showSettingsSheet()
    }

    private fun sleepLabel():String = when(sleepSelection) { 1->"5 мин";2->"10 мин";3->"15 мин";4->"30 мин";5->"45 мин";6->"60 мин";7->"До конца видео";else->"Выкл" }


    private fun showSpeedPicker() {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        val labels = speeds.map { if (it == 1f) "Обычная • 1×" else "$it×" }.toMutableList().apply { add("Точная настройка…") }
        val selected = speeds.indexOfFirst { kotlin.math.abs(it - speed) < 0.001f }
            .let { if (it >= 0) it else 3 }
        ModernDialogs.showChoices(activity, palette, "Скорость воспроизведения", labels, selected) { which ->
            if (which == speeds.size) showPreciseSpeedSheet() else {
                speed=speeds[which]; settings.playbackSpeed=speed; player.setPlaybackSpeed(speed)
                speedActionButton?.text=if(speed==1f) "1×  Скорость" else speed.toString()+"×  Скорость"
            }
        }
    }

    private fun showPreciseSpeedSheet() {
        val dialog=BottomSheetDialog(activity)
        val box=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(18),dp(20),dp(26)); background=roundedInt(palette.surface,24) }
        val value=TextView(activity).apply { text=String.format("%.2fx",speed); textSize=20f; gravity=Gravity.CENTER; setTypeface(typeface,Typeface.BOLD); setTextColor(palette.text) }
        val slider=SeekBar(activity).apply {
            max=175; progress=((speed-.25f)*100).toInt().coerceIn(0,175)
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b:SeekBar?,p:Int,user:Boolean) { if(user){ speed=(.25f+p/100f).coerceIn(.25f,2f); value.text=String.format("%.2fx",speed); player.setPlaybackSpeed(speed); settings.playbackSpeed=speed } }
                override fun onStartTrackingTouch(b:SeekBar?)=Unit
                override fun onStopTrackingTouch(b:SeekBar?)=Unit
            })
        }
        box.addView(TextView(activity).apply { text="Точная скорость"; textSize=17f; setTypeface(typeface,Typeface.BOLD); setTextColor(palette.text) }); box.addView(value); box.addView(slider)
        dialog.setContentView(box); dialog.show()
    }

    private fun showSleepPicker() {
        val labels=listOf("Выкл","5 минут","10 минут","15 минут","30 минут","45 минут","60 минут","До конца видео")
        ModernDialogs.showChoices(activity,palette,"Таймер сна",labels,sleepSelection) { which ->
            sleepRunnable?.let { handler.removeCallbacks(it) }; sleepRunnable=null; sleepSelection=which
            if(which in 1..6) { val minutes=listOf(0,5,10,15,30,45,60)[which]; val r=Runnable { player.pause(); showOverlay() }; sleepRunnable=r; handler.postDelayed(r,minutes*60_000L) }
        }
    }

    private fun showStats() {
        val format = player.videoFormat
        val res = if (format?.height != null && format.height > 0) "${format.width}×${format.height}" else "Оригинал"
        val bitrate = if (format?.bitrate != null && format.bitrate > 0) "%.1f Мбит/с".format(format.bitrate / 1_000_000.0) else "неизвестно"
        ModernDialogs.showChoices(
            activity,
            palette,
            "Статистика видео",
            listOf(
                "Разрешение: $res",
                "Битрейт: $bitrate",
                "Позиция: ${formatMs(player.currentPosition)} / ${formatMs(player.duration)}",
                "Размер: ${buildSize()}"
            ),
            0
        ) { }
    }

    private fun buildMiniPlayer(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(4), dp(8), dp(4))
        background = roundedInt(palette.surface, 18)
        visibility = View.GONE
        elevation = dp(12).toFloat()

        miniVideoHost = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
        }
        addView(miniVideoHost, LinearLayout.LayoutParams(dp(112), dp(63)))

        addView(
            TextView(activity).apply {
                text = cleanTitle(item.title)
                textSize = 13f
                maxLines = 2
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.text)
                setPadding(dp(10), 0, dp(8), 0)
                setOnClickListener { exitMiniPlayer() }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val miniPlayPause = ImageButton(activity).apply {
            setImageResource(if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            imageTintList =
                android.content.res.ColorStateList.valueOf(palette.text)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = roundedInt(palette.surfaceAlt, 18)
            contentDescription = "Пауза или воспроизведение"
            setOnClickListener {
                if (player.isPlaying) player.pause() else player.play()
                setImageResource(if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
                pulse(this)
            }
        }
        addView(miniPlayPause, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
            marginEnd = dp(6)
        })

        val close = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList =
                android.content.res.ColorStateList.valueOf(palette.muted)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = roundedInt(palette.surfaceAlt, 18)
            contentDescription = "Закрыть мини-плеер"
            setOnClickListener {
                pulse(this)
                player.pause()
                onBack()
            }
        }
        addView(close, LinearLayout.LayoutParams(dp(42), dp(42)))

        setOnClickListener { exitMiniPlayer() }
        setOnTouchListener(object : View.OnTouchListener {
            var x = 0f

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        x = e.x
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        v.translationX = e.x - x
                        v.alpha =
                            (1f - kotlin.math.abs(v.translationX) / v.width)
                                .coerceIn(.25f, 1f)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (kotlin.math.abs(v.translationX) > v.width * .35f) {
                            player.pause()
                            onBack()
                        } else {
                            v.animate()
                                .translationX(0f)
                                .alpha(1f)
                                .setDuration(220L)
                                .start()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun enterMiniPlayer() {
        if(miniMode||fullscreen)return; miniMode=true; persistPlaybackPosition(true)
        header.visibility=View.GONE; details.visibility=View.GONE; socialActionsRow.visibility=View.GONE; actionsRow.visibility=View.GONE; nextVideosBlock.visibility=View.GONE; overlay.visibility=View.GONE
        (playerView.parent as? ViewGroup)?.removeView(playerView); miniVideoHost.removeAllViews(); miniVideoHost.addView(playerView,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)); playerCard.visibility=View.GONE
        root.gravity=Gravity.BOTTOM; miniBar.alpha=0f; miniBar.translationY=dp(76).toFloat(); miniBar.visibility=View.VISIBLE; miniBar.animate().alpha(1f).translationY(0f).setDuration(260L).start()
    }

    private fun exitMiniPlayer() {
        if(!miniMode)return; miniMode=false; (playerView.parent as? ViewGroup)?.removeView(playerView); playerCard.addView(playerView,0,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)); miniBar.visibility=View.GONE; root.gravity=Gravity.TOP; playerCard.visibility=View.VISIBLE
        header.visibility=View.VISIBLE; details.visibility=View.VISIBLE; socialActionsRow.visibility=View.VISIBLE; actionsRow.visibility=View.VISIBLE; nextVideosBlock.visibility=View.VISIBLE; showOverlay()
    }

    private fun showTransientIndicator(value:String) {
        seekFeedback.animate().cancel(); seekFeedback.text=value; seekFeedback.translationX=0f; seekFeedback.alpha=0f; seekFeedback.visibility=View.VISIBLE
        seekFeedback.animate().alpha(1f).setDuration(180L).withEndAction { seekFeedback.animate().alpha(0f).setStartDelay(450L).setDuration(220L).withEndAction { seekFeedback.visibility=View.GONE }.start() }.start()
    }

    fun animateEntranceFrom(sourceBounds: Rect?) {
        if (!settings.animations || sourceBounds == null || sourceBounds.width() <= 0 || sourceBounds.height() <= 0) return
        playerCard.post {
            if (destroyed || pipMode || fullscreen) return@post
            val location = IntArray(2)
            playerCard.getLocationOnScreen(location)
            val targetWidth = playerCard.width.coerceAtLeast(1)
            val targetHeight = playerCard.height.coerceAtLeast(1)
            val targetCenterX = location[0] + targetWidth / 2f
            val targetCenterY = location[1] + targetHeight / 2f
            val sourceCenterX = sourceBounds.exactCenterX()
            val sourceCenterY = sourceBounds.exactCenterY()

            playerCard.pivotX = targetWidth / 2f
            playerCard.pivotY = targetHeight / 2f
            playerCard.scaleX = (sourceBounds.width().toFloat() / targetWidth).coerceIn(0.35f, 1f)
            playerCard.scaleY = (sourceBounds.height().toFloat() / targetHeight).coerceIn(0.35f, 1f)
            playerCard.translationX = sourceCenterX - targetCenterX
            playerCard.translationY = sourceCenterY - targetCenterY
            playerCard.alpha = 0.72f
            playerCard.animate().cancel()
            playerCard.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .translationY(0f)
                .alpha(1f)
                .setDuration(320L)
                .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                .start()
        }
    }

    fun prepareForPictureInPicture(): Boolean {
        if (destroyed) return false
        if (pipMode) return true
        if (miniMode) exitMiniPlayer()
        if (fullscreen || fullscreenHost != null) setFullscreenMode(false)

        val parent = playerCard.parent as? ViewGroup ?: return false
        pipOriginalParent = parent
        pipOriginalIndex = parent.indexOfChild(playerCard)
        pipOriginalLayoutParams = playerCard.layoutParams

        parent.removeView(playerCard)

        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val host = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            clipChildren = true
            clipToPadding = true
        }
        content.addView(
            host,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        host.addView(
            playerCard,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )

        pipHost = host
        pipMode = true
        playerCard.clipToOutline = false
        playerCard.background = rounded("#000000", 0)
        overlay.visibility = View.GONE
        previewBubble.visibility = View.GONE
        endOverlay.visibility = View.GONE
        return true
    }

    fun restoreFromPictureInPicture() {
        if (!pipMode) return
        val host = pipHost
        runCatching { host?.removeView(playerCard) }
        runCatching { (host?.parent as? ViewGroup)?.removeView(host) }

        val parent = pipOriginalParent
        if (parent != null && playerCard.parent == null) {
            val params = pipOriginalLayoutParams
                ?: LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (activity.resources.displayMetrics.widthPixels * 9f / 16f).toInt()
                )
            val index = pipOriginalIndex.coerceIn(0, parent.childCount)
            parent.addView(playerCard, index, params)
        }

        pipHost = null
        pipOriginalParent = null
        pipOriginalIndex = -1
        pipOriginalLayoutParams = null
        pipMode = false
        playerCard.clipToOutline = true
        playerCard.background = rounded("#000000", 18)
        showOverlay()
    }

    private fun updateAmbientFromDrawable(drawable: Drawable) {
        runCatching {
            val bitmap = drawable.toBitmap(32, 18, Bitmap.Config.ARGB_8888)
            var red = 0L
            var green = 0L
            var blue = 0L
            var count = 0L
            for (y in 0 until bitmap.height step 2) {
                for (x in 0 until bitmap.width step 2) {
                    val color = bitmap.getPixel(x, y)
                    red += Color.red(color)
                    green += Color.green(color)
                    blue += Color.blue(color)
                    count++
                }
            }
            if (count > 0L) {
                val sampled = Color.rgb(
                    (red / count).toInt(),
                    (green / count).toInt(),
                    (blue / count).toInt()
                )
                applyAmbientColor(sampled)
            }
        }
    }

    private fun applyAmbientColor(target: Int) {
        ambientAnimator?.cancel()
        if (!settings.animations) {
            ambientColor = target
            root.background = ambientGradient(target)
            return
        }
        val from = ambientColor
        ambientAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 360L
            interpolator = android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
            addUpdateListener { animator ->
                val f = animator.animatedFraction
                ambientColor = Color.rgb(
                    (Color.red(from) + (Color.red(target) - Color.red(from)) * f).toInt(),
                    (Color.green(from) + (Color.green(target) - Color.green(from)) * f).toInt(),
                    (Color.blue(from) + (Color.blue(target) - Color.blue(from)) * f).toInt()
                )
                root.background = ambientGradient(ambientColor)
            }
            start()
        }
    }

    private fun ambientGradient(color: Int): GradientDrawable {
        fun mix(base: Int, accent: Int, amount: Float): Int {
            val a = amount.coerceIn(0f, 1f)
            return Color.rgb(
                (Color.red(base) * (1f - a) + Color.red(accent) * a).toInt(),
                (Color.green(base) * (1f - a) + Color.green(accent) * a).toInt(),
                (Color.blue(base) * (1f - a) + Color.blue(accent) * a).toInt()
            )
        }
        val glow = mix(palette.background, color, if (palette === AppThemes.Light) 0.10f else 0.18f)
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(glow, palette.background, palette.background)
        )
    }

    val isFullscreen: Boolean
        get() = fullscreen

    fun exitFullscreen() {
        setFullscreenMode(false)
    }

    fun setFullscreenMode(enabled: Boolean) {
        if (fullscreenTransition) return

        fullscreenTransition = true
        try {
            if (enabled) {
                if (fullscreen) {
                    showOverlay()
                    return
                }
                if (miniMode) exitMiniPlayer()

                fullscreenOriginalIndex = root.indexOfChild(playerCard).takeIf { it >= 0 } ?: 1
                fullscreenOriginalLayoutParams =
                    (playerCard.layoutParams as? LinearLayout.LayoutParams)?.let {
                        LinearLayout.LayoutParams(it)
                    }

                (playerCard.parent as? ViewGroup)?.removeView(playerCard)

                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                val host = FrameLayout(activity).apply {
                    setBackgroundColor(Color.BLACK)
                    isClickable = true
                    isFocusable = true
                    elevation = dp(100).toFloat()
                }

                content.addView(
                    host,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )

                host.addView(
                    playerCard,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )

                fullscreenHost = host
                fullscreen = true
                playerCard.clipToOutline = false
                playerCard.background = rounded("#000000", 0)
                playerCard.requestLayout()

                if (settings.animations) {
                    host.alpha = 0f
                    playerCard.alpha = 0.78f
                    playerCard.scaleX = 0.94f
                    playerCard.scaleY = 0.94f
                    host.animate()
                        .alpha(1f)
                        .setDuration(SohrMotion.HERO)
                        .setInterpolator(SohrMotion.smooth())
                        .start()
                    playerCard.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(SohrMotion.HERO)
                        .setInterpolator(SohrMotion.smooth())
                        .start()
                } else {
                    host.alpha = 1f
                    playerCard.alpha = 1f
                    playerCard.scaleX = 1f
                    playerCard.scaleY = 1f
                }
                showOverlay()
            } else {
                dismissFullscreenSettings(animated = false)
                val host = fullscreenHost

                if (host != null) {
                    runCatching { host.removeView(playerCard) }
                    runCatching { (host.parent as? ViewGroup)?.removeView(host) }
                } else {
                    runCatching { (playerCard.parent as? ViewGroup)?.removeView(playerCard) }
                }

                if (playerCard.parent == null) {
                    val width = activity.resources.displayMetrics.widthPixels
                    val params = fullscreenOriginalLayoutParams
                        ?: LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (width * 9f / 16f).toInt()
                        ).apply {
                            marginStart = dp(12)
                            marginEnd = dp(12)
                        }

                    val index = fullscreenOriginalIndex
                        .coerceIn(0, root.childCount)

                    root.addView(playerCard, index, params)
                }

                fullscreenHost = null
                fullscreenOriginalIndex = -1
                fullscreenOriginalLayoutParams = null
                fullscreen = false

                playerCard.clipToOutline = true
                playerCard.background = rounded("#000000", 18)
                playerCard.requestLayout()
                root.requestLayout()

                if (settings.animations) {
                    playerCard.animate().cancel()
                    playerCard.alpha = 0.72f
                    playerCard.scaleX = 1.035f
                    playerCard.scaleY = 1.035f
                    playerCard.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(SohrMotion.HERO)
                        .setInterpolator(SohrMotion.smooth())
                        .start()
                } else {
                    playerCard.alpha = 1f
                    playerCard.scaleX = 1f
                    playerCard.scaleY = 1f
                }
                showOverlay()
            }
        } catch (_: Throwable) {
            // Always restore the player to the normal screen instead of
            // leaving a detached PlayerView or swallowing future clicks.
            runCatching { fullscreenHost?.removeView(playerCard) }
            runCatching { (fullscreenHost?.parent as? ViewGroup)?.removeView(fullscreenHost) }
            fullscreenHost = null

            if (playerCard.parent == null) {
                val width = activity.resources.displayMetrics.widthPixels
                root.addView(
                    playerCard,
                    fullscreenOriginalIndex.coerceIn(0, root.childCount),
                    fullscreenOriginalLayoutParams
                        ?: LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (width * 9f / 16f).toInt()
                        ).apply {
                            marginStart = dp(12)
                            marginEnd = dp(12)
                        }
                )
            }

            fullscreen = false
            fullscreenOriginalIndex = -1
            fullscreenOriginalLayoutParams = null
            playerCard.clipToOutline = true
            playerCard.background = rounded("#000000", 18)
            playerCard.requestLayout()
        } finally {
            fullscreenTransition = false
        }
    }

    fun destroy() {
        destroyed = true
        ambientAnimator?.cancel()
        dismissFullscreenSettings(animated = false)
        runCatching { if (pipMode || pipHost != null) restoreFromPictureInPicture() }
        runCatching { if (fullscreen || fullscreenHost != null) setFullscreenMode(false) }
        persistPlaybackPosition(force = true)
        root.keepScreenOn = false
        sleepRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacks(showBufferingRunnable)
        handler.removeCallbacksAndMessages(null)
        previewJob?.cancel()
        previewScope.cancel()
        previewImage.setImageDrawable(null)
        previewCache.values.toSet().forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
        previewCache.clear()
        previewBitmap = null
        runCatching { previewRetriever?.release() }
        runCatching { previewDataSource?.close() }
        previewRetriever = null
        previewDataSource = null
        runCatching { mediaSessionBridge.release() }
        player.release()
    }

    private data class QualityOption(
        val label: String,
        val group: TrackGroup,
        val trackIndex: Int
    )

    private fun availableQualityOptions(): List<QualityOption> {
        val result = mutableListOf<QualityOption>()
        for (group in player.currentTracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO || !group.isSupported) continue
            for (index in 0 until group.length) {
                if (!group.isTrackSupported(index)) continue
                val format = group.getTrackFormat(index)
                val height = format.height
                val bitrate = format.bitrate
                val resolution = if (height > 0) "${height}p" else "Оригинал"
                val bitrateText = if (bitrate > 0) " • %.1f Мбит/с".format(bitrate / 1_000_000.0) else ""
                result.add(QualityOption(resolution + bitrateText, group.mediaTrackGroup, index))
            }
        }
        return result.distinctBy { it.label }
    }

    private fun showQualityPicker() {
        val options = availableQualityOptions()
        if (options.isEmpty()) {
            ModernDialogs.showChoices(activity, palette, "Качество видео", listOf("Оригинал • лучшее доступное"), 0) { }
            return
        }

        val labels = mutableListOf("Авто • лучшее доступное")
        labels.addAll(options.map { it.label })

        ModernDialogs.showChoices(activity, palette, "Качество видео", labels, qualitySelection) { which ->
            qualitySelection = which
            if (which == 0) {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .build()
            } else {
                val option = options[which - 1]
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex))
                    .build()
            }
            updateQualityLabel()
        }
    }

    private fun updateQualityLabel() {
        if (!::qualityButton.isInitialized) return
        val height = player.videoFormat?.height ?: 0
        qualityButton.text = if (height > 0) "${height}p" else "Оригинал"
    }

    private fun updatePlayIcon() {
        playPause.setImageResource(if (player.playWhenReady) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun scheduleProgress() {
        handler.postDelayed({
            updateProgress()
            scheduleProgress()
        }, if (player.isPlaying) 250L else 850L)
    }

    private fun updateProgress() {
        if (!dragging) {
            val duration = resolvedDurationMs()
            if (duration > 0) {
                seekBar.setProgress(player.currentPosition, duration, player.bufferedPosition)
                totalTime.text = formatMs(duration)
            }
        }
        currentTime.text = formatMs(player.currentPosition)
        persistPlaybackPosition()
        updatePlayIcon()
    }

    private fun resolvedDurationMs(): Long {
        val playerDuration = player.duration
        if (playerDuration != C.TIME_UNSET && playerDuration > 0L) return playerDuration
        return item.durationSeconds.coerceAtLeast(0) * 1000L
    }

    private fun initialDurationLabel(): String = formatMs(item.durationSeconds.coerceAtLeast(0) * 1000L)

    private fun updateDurationLabel() {
        if (!::totalTime.isInitialized) return
        totalTime.text = formatMs(resolvedDurationMs())
    }

    fun flushPlaybackPosition() {
        persistPlaybackPosition(force = true)
    }

    private fun persistPlaybackPosition(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastProgressPersistAt < 2_000L) return
        lastProgressPersistAt = now

        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = resolvedDurationMs()
        settings.savePlaybackPosition(item.messageId, position, duration)
    }

    private fun toggleOverlay() {
        if (overlay.alpha > 0.1f) hideOverlay() else showOverlay()
    }

    private fun showOverlay() {
        handler.removeCallbacks(autoHideControls); overlay.visibility=View.VISIBLE; overlay.animate().cancel(); overlay.animate().alpha(1f).setDuration(if(settings.animations)180 else 0).start()
        if(player.isPlaying) handler.postDelayed(autoHideControls,3_000L)
    }

    private fun hideOverlay() {
        handler.removeCallbacks(autoHideControls); overlay.animate().cancel(); overlay.animate()
            .alpha(0f)
            .setDuration(if (settings.animations) 180 else 0)
            .withEndAction { overlay.visibility = View.GONE }
            .start()
    }

    private fun pulse(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (!settings.animations) return
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

    private fun iconButton(resId: Int, backgroundColor: String, size: Int = 48): ImageButton =
        ImageButton(activity).apply {
            runCatching { setImageResource(resId) }
                .onFailure { setImageDrawable(null) }
            setBackgroundColor(Color.TRANSPARENT)
            background = rounded(backgroundColor, size / 2)
            val inset = when {
                size <= 36 -> 8
                size <= 40 -> 9
                else -> 12
            }
            setPadding(dp(inset), dp(inset), dp(inset), dp(inset))
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        }

    private fun textCircle(label: String, color: String): TextView =
        TextView(activity).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(color))
            background = rounded("#990B0911", 28)
        }

    private fun timeLabel(value: String): TextView =
        TextView(activity).apply {
            text = value
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }

    private fun cleanTitle(raw: String): String {
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
        val firstLine = normalized
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        if (item.source == "twitch") {
            val lowered = firstLine.lowercase(Locale.getDefault())
            val hasTelegramPromo =
                lowered.contains("t.me/t2xtwitch") ||
                    lowered.contains("tg:") ||
                    lowered.contains("тг:")
            val readable = firstLine
                .substringBefore(" | ")
                .substringBefore("|")
                .replace(Regex("""https?://\S+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""t\.me/\S+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
            val basicLetters = readable.count {
                (it in 'A'..'Z') || (it in 'a'..'z') || (it in 'А'..'я') || it == 'Ё' || it == 'ё' || it.isDigit()
            }
            val noisy = readable.length > 0 && basicLetters < (readable.length * 0.42f)
            if (hasTelegramPromo && (readable.isBlank() || noisy)) return "Запись стрима T2x2"
            if (readable.isNotBlank() && !noisy) return readable.take(80)
            return "Запись стрима T2x2"
        }

        val fileName = firstLine.matches(
            Regex(
                """\d{4}-\d{2}-\d{2}[_-].*\.(mp4|mkv|mov|webm)""",
                RegexOption.IGNORE_CASE
            )
        )
        return when {
            fileName -> "Запись стрима"
            firstLine.isNotBlank() -> firstLine
            else -> "Видео"
        }
    }

    private fun cleanDescription(raw: String): String {
        val lines = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
        val firstContent = lines.indexOfFirst { it.isNotBlank() }
        if (firstContent < 0 || firstContent >= lines.lastIndex) return ""
        return lines
            .drop(firstContent + 1)
            .map { it.trim() }
            .filterNot {
                val lower = it.lowercase(Locale.getDefault())
                lower.contains("t.me/t2xtwitch") || lower.startsWith("tg:") || lower.startsWith("тг:")
            }
            .joinToString("\n")
            .trim()
            .replace(Regex("\n{3,}"), "\n\n")
    }

    private fun buildMeta(): String =
        if (item.source == "twitch") {
            listOf(formatMs(item.durationSeconds * 1000L), "Twitch", "@t2x2").joinToString(" • ")
        } else {
            listOf(formatMs(item.durationSeconds * 1000L), buildSize(), "@t2x2_video").joinToString(" • ")
        }

    private fun buildSize(): String {
        val mb = item.fileSize / 1048576.0
        return if (mb >= 1024) "%.1f ГБ".format(mb / 1024.0) else "%.0f МБ".format(mb)
    }

    private fun formatMs(ms: Long): String {
        if (ms < 0) return "0:00"
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun rounded(color: String, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun roundedInt(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
