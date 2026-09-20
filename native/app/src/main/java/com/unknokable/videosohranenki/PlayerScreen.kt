package com.unknokable.videosohranenki

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.SeekBar
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
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

@OptIn(UnstableApi::class)
class PlayerScreen(
    private val activity: Activity,
    private val item: VideoItem,
    private val mediaUrl: String,
    private val previewDataSourceFactory: () -> MediaDataSource,
    private val settings: AppSettings,
    private val startPositionMs: Long = 0L,
    private val nextItem: VideoItem? = null,
    private val onPlayNext: ((VideoItem) -> Unit)? = null,
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
    private var dragging = false
    private var speed = settings.playbackSpeed
    private var sleepRunnable: Runnable? = null
    private var sleepSelection = 0
    private var qualitySelection = 0
    private var playbackCounted = false
    private var lastProgressPersistAt = 0L
    private var speedActionButton: TextView? = null
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
        root.setBackgroundColor(palette.background)
        root.keepScreenOn = false

        header = buildHeader()
        root.addView(header)

        playerCard = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            background = rounded("#000000", 18)
            clipToOutline = true
        }

        playerView = PlayerView(activity).apply {
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
            item.thumbnailPath?.takeIf { it.isNotBlank() }?.let { path ->
                runCatching { BitmapFactory.decodeFile(path) }.getOrNull()?.let { setImageBitmap(it) }
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
            FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER)
        )

        speedBadge = TextView(activity).apply {
            text = "2x  ▶▶"
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

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                25_000,
                120_000,
                650,
                1_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(activity)
            .forceEnableMediaCodecAsynchronousQueueing()

        player = ExoPlayer.Builder(activity, renderersFactory)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        playerView.player = player
        player.setMediaItem(MediaItem.fromUri(mediaUrl))
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
                if (isPlaying) { handler.removeCallbacks(autoHideControls); handler.postDelayed(autoHideControls, 3_000L) } else handler.removeCallbacks(autoHideControls)
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

    private fun buildHeader(): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(palette.background)
        }

        val back = iconButton(R.drawable.ic_back, "#181322", 42).apply {
            setOnClickListener { pulse(this); onBack() }
        }

        val title = TextView(activity).apply {
            text = cleanTitle(item.title)
            textSize = 15f
            setTextColor(palette.text)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(9), 0, dp(9), 0)
        }

        val spacer = View(activity)

        row.addView(back, LinearLayout.LayoutParams(dp(42), dp(42)))
        row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(spacer, LinearLayout.LayoutParams(dp(42), dp(42)))
        return row
    }

    private fun buildOverlay(): FrameLayout {
        val frame = FrameLayout(activity).apply {
            setBackgroundColor(Color.parseColor("#24000000"))
        }

        val center = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        playPause = iconButton(R.drawable.ic_play, "#8B5CF6", 54).apply {
            setOnClickListener {
                if (player.isPlaying) player.pause() else player.play()
                pulse(this)
            }
        }

        center.addView(playPause, LinearLayout.LayoutParams(dp(56), dp(56)))

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
            setPadding(dp(14), dp(6), dp(14), dp(10))
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
            minWidth = dp(42)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        totalTime = timeLabel(initialDurationLabel()).apply {
            minWidth = dp(48)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(8), 0)
        }
        val spacer = View(activity)

        qualityButton = TextView(activity).apply {
            text = "Авто"
            textSize = 10.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(10), 0, dp(10), 0)
            background = rounded("#66181322", 14)
            setOnClickListener { showQualityPicker() }
        }

        val settingsButton = TextView(activity).apply {
            text = "⚙"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded("#66181322", 20)
            setOnClickListener { pulse(this); showSettingsSheet() }
        }

        val fullscreenButton = iconButton(R.drawable.ic_fullscreen, "#66181322", 40).apply {
            setOnClickListener {
                onFullscreen(!fullscreen)
                pulse(this)
            }
        }

        times.addView(currentTime, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)))
        times.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        times.addView(totalTime, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)))
        times.addView(settingsButton, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginStart = dp(4) })
        times.addView(fullscreenButton, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginStart = dp(4) })

        bottom.addView(seekBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)))
        bottom.addView(times)

        frame.addView(
            bottom,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
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
            return
        }
        if (lastPreviewMs >= 0 && kotlin.math.abs(bucket - lastPreviewMs) < 1_500) return
        lastPreviewMs = bucket
        val requestId = ++previewRequestId

        previewJob?.cancel()
        previewJob = previewScope.launch {
            delay(20)
            val bitmap = runCatching {
                previewMutex.withLock {
                    ensurePreviewRetriever()
                    val raw = previewRetriever?.getFrameAtTime(
                        bucket * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    ) ?: return@withLock null

                    val width = 320
                    val height = (raw.height * (width.toFloat() / raw.width.toFloat()))
                        .toInt()
                        .coerceAtLeast(1)
                    val scaled = if (raw.width > width) {
                        Bitmap.createScaledBitmap(raw, width, height, true)
                    } else {
                        raw
                    }
                    if (scaled !== raw) raw.recycle()
                    scaled
                }
            }.getOrNull()

            if (bitmap != null) {
                withContext(Dispatchers.Main) {
                    if (!dragging || requestId != previewRequestId) {
                        bitmap.recycle()
                        return@withContext
                    }
                    previewCache[bucket] = bitmap
                    previewImage.setImageBitmap(bitmap)
                    previewBitmap = bitmap
                }
            }
        }
    }

    private fun ensurePreviewRetriever() {
        if (previewRetriever != null) return
        val dataSource = previewDataSourceFactory()
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
            setPadding(dp(16), dp(18), dp(16), dp(12))
        }

        val title = TextView(activity).apply {
            text = cleanTitle(item.title)
            textSize = 21f
            setTextColor(palette.text)
            setTypeface(typeface, Typeface.BOLD)
        }

        val meta = TextView(activity).apply {
            text = buildMeta()
            textSize = 13f
            setTextColor(palette.muted)
            setPadding(0, dp(7), 0, 0)
        }

        box.addView(title)
        box.addView(meta)
        return box
    }

    private fun buildSocialActions(): LinearLayout {
        val row=LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(2),dp(12),dp(10)) }
        var watched=isWatched
        lateinit var watchedButton:TextView
        lateinit var restoreButton:TextView
        fun syncWatchedActions() {
            watchedButton.alpha = if (watched) .72f else 1f
            restoreButton.visibility = if (watched) View.VISIBLE else View.GONE
            restoreButton.alpha = if (watched) 1f else 0f
        }
        watchedButton=actionPill("✓  Просмотрено") {
            if(watched) {
                Toast.makeText(activity,"Видео уже добавлено в просмотренное",Toast.LENGTH_SHORT).show()
                watchedButton.animate().cancel(); watchedButton.animate().scaleX(1.03f).scaleY(1.03f).setDuration(if(settings.animations)90L else 0L).withEndAction { watchedButton.animate().scaleX(1f).scaleY(1f).setDuration(if(settings.animations)110L else 0L).start() }.start()
                return@actionPill
            }
            ModernDialogs.showConfirm(activity,palette,"Отметить просмотренным?","Видео переместится в раздел «Просмотренное» и исчезнет из обычных сборников.","Да, просмотрено") {
                watched=true; onWatchedChange?.invoke(item,true); syncWatchedActions()
            }
        }
        restoreButton=actionPill("↩  Вернуть") {
            if(!watched) return@actionPill
            ModernDialogs.showConfirm(activity,palette,"Вернуть в сборники?","Видео исчезнет из «Просмотренного» и снова появится в обычном сборнике.","Вернуть") {
                watched=false; onWatchedChange?.invoke(item,false); syncWatchedActions()
                Toast.makeText(activity,"Видео возвращено в сборники",Toast.LENGTH_SHORT).show()
            }
        }
        syncWatchedActions()
        val shareButton=actionPill("↗  Поделиться") { val send=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_SUBJECT,cleanTitle(item.title));putExtra(Intent.EXTRA_TEXT,"${cleanTitle(item.title)} • SOHR")};runCatching{activity.startActivity(Intent.createChooser(send,"Поделиться видео"))} }
        val downloadButton=actionPill("↓  Скачать"){enqueueDownload()}
        row.addView(watchedButton,LinearLayout.LayoutParams(0,dp(42),1f).apply{marginEnd=dp(5)})
        row.addView(restoreButton,LinearLayout.LayoutParams(0,dp(44),1f).apply { marginStart=dp(8) });row.addView(shareButton,LinearLayout.LayoutParams(0,dp(42),1f).apply{marginEnd=dp(5)});row.addView(downloadButton,LinearLayout.LayoutParams(0,dp(42),1f))
        return row
    }

    private fun buildNextVideosBlock(): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(18))

            addView(TextView(activity).apply {
                text = "Следующие видео"
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.text)
                setPadding(dp(4), dp(4), dp(4), dp(10))
            })

            val next = nextItem
            if (next == null) {
                addView(TextView(activity).apply {
                    text = "Это последнее видео в сборнике"
                    textSize = 13f
                    setTextColor(palette.muted)
                    setPadding(dp(14), dp(14), dp(14), dp(14))
                    background = roundedInt(palette.surfaceAlt, 16)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            } else {
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = roundedInt(palette.surfaceAlt, 16)
                    isClickable = true
                    isFocusable = true

                    val thumb = ImageView(activity).apply {
                    scaleType=ImageView.ScaleType.CENTER_CROP
                    background=roundedInt(palette.surface,12)
                    clipToOutline=true
                    next.thumbnailPath?.takeIf{it.isNotBlank()}?.let{path->runCatching{BitmapFactory.decodeFile(path)}.getOrNull()?.let{setImageBitmap(it)}}
                }
                addView(thumb,LinearLayout.LayoutParams(dp(96),dp(58)).apply{marginEnd=dp(12)})

                    val textBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                    textBox.addView(TextView(activity).apply {
                        text = cleanTitle(next.title)
                        textSize = 14f
                        maxLines = 2
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(palette.text)
                    })
                    textBox.addView(TextView(activity).apply {
                        text = "${formatMs(next.durationSeconds * 1000L)}  •  Следующее"
                        textSize = 12f
                        setTextColor(palette.muted)
                        setPadding(0, dp(4), 0, 0)
                    })
                    addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(TextView(activity).apply {
                        text = "▶"
                        textSize = 17f
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        background = rounded("#8B5CF6", 20)
                    }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(10) })
                    setOnClickListener {
                        pulse(this)
                        onPlayNext?.invoke(next)
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
    }

    private fun enqueueDownload() {
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

        val speedLabel = if (speed == 1f) "1×  Скорость" else speed.toString() + "×  Скорость"
        val speedBtn = actionPill(speedLabel) { showSpeedPicker() }
        speedActionButton = speedBtn
        val sleepBtn = actionPill("◷  Таймер") { showSleepPicker() }

        row.addView(
            speedBtn,
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) }
        )
        row.addView(
            sleepBtn,
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )
        return row
    }

    private fun actionPill(label: String, onClick: (View) -> Unit): TextView =
        TextView(activity).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            background = roundedInt(palette.surfaceAlt, 14)
            setOnClickListener {
                pulse(this)
                onClick(this)
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
                    player.seekTo(positionMs)
                    showPreview()
                    updatePreviewUi(positionMs, (positionMs.toFloat()/resolvedDurationMs().coerceAtLeast(1L)).coerceIn(0f,1f))
                    requestPreview(positionMs)
                } else { player.setScrubbingModeEnabled(false); hidePreview() }
            },
            onBrightness = { showTransientIndicator("☀  $it%") },
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
        val disabledSubs = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val items = listOf(
            "Качество" to qualityButton.text.toString(),
            "Скорость воспроизведения" to if (speed == 1f) "Обычная" else "${speed}x",
            "Субтитры" to if (disabledSubs) "Выкл" else "Авто",
            "Таймер сна" to sleepLabel(),
            "Стабильная громкость" to if (settings.stableVolume) "Вкл" else "Выкл",
            "Автовоспроизведение" to if (settings.autoplay) "Вкл" else "Выкл"
        )
        val dialog = BottomSheetDialog(activity)
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18),dp(10),dp(18),dp(24))
            background = roundedInt(palette.surface,24)
        }
        box.addView(TextView(activity).apply {
            text="Настройки видео"; textSize=20f; setTypeface(typeface,Typeface.BOLD); setTextColor(palette.text); setPadding(dp(6),dp(10),dp(6),dp(12))
        })
        items.forEachIndexed { index,pair ->
            box.addView(sheetRow(pair.first,pair.second) {
                dialog.dismiss()
                when(index) {
                    0 -> showQualityPicker()
                    1 -> showSpeedPicker()
                    2 -> toggleSubtitles()
                    3 -> showSleepPicker()
                    4 -> { settings.stableVolume=!settings.stableVolume; showSettingsSheet() }
                    5 -> { settings.autoplay=!settings.autoplay; showSettingsSheet() }
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)).apply { bottomMargin=dp(5) })
        }
        dialog.setContentView(box); dialog.show()
    }

    private fun sheetRow(title:String,value:String,click:()->Unit): View = LinearLayout(activity).apply {
        orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(14),0,dp(12),0); background=roundedInt(palette.surfaceAlt,16)
        addView(TextView(activity).apply { text=title; textSize=14f; setTypeface(typeface,Typeface.BOLD); setTextColor(palette.text) },LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        addView(TextView(activity).apply { text="$value   ›"; textSize=13f; setTextColor(palette.muted) })
        setOnClickListener { pulse(this); click() }
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
        orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(8),dp(4),dp(8),dp(4)); background=roundedInt(palette.surface,18); visibility=View.GONE; elevation=dp(12).toFloat()
        miniVideoHost=FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }; addView(miniVideoHost,LinearLayout.LayoutParams(dp(112),dp(63)))
        addView(TextView(activity).apply { text=cleanTitle(item.title); textSize=13f; maxLines=2; setTypeface(typeface,Typeface.BOLD); setTextColor(palette.text); setPadding(dp(10),0,dp(8),0); setOnClickListener { exitMiniPlayer() } },LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        addView(TextView(activity).apply { text="Ⅱ"; textSize=18f; gravity=Gravity.CENTER; setTextColor(palette.text); setOnClickListener { if(player.isPlaying) player.pause() else player.play(); text=if(player.isPlaying) "Ⅱ" else "▶" } },LinearLayout.LayoutParams(dp(42),dp(56)))
        addView(TextView(activity).apply { text="×"; textSize=24f; gravity=Gravity.CENTER; setTextColor(palette.muted); setOnClickListener { player.pause(); onBack() } },LinearLayout.LayoutParams(dp(42),dp(56)))
        setOnClickListener { exitMiniPlayer() }
        setOnTouchListener(object:View.OnTouchListener { var x=0f; override fun onTouch(v:View,e:MotionEvent):Boolean { when(e.actionMasked){ MotionEvent.ACTION_DOWN->{x=e.x;return true}; MotionEvent.ACTION_MOVE->{v.translationX=e.x-x;v.alpha=(1f-kotlin.math.abs(v.translationX)/v.width).coerceIn(.25f,1f);return true}; MotionEvent.ACTION_UP->{if(kotlin.math.abs(v.translationX)>v.width*.35f){player.pause();onBack()}else v.animate().translationX(0f).alpha(1f).setDuration(220L).start();return true} };return false } })
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

    fun setFullscreenMode(enabled: Boolean) {
        if (enabled) exitMiniPlayer()
        fullscreen = enabled
        header.visibility = if (enabled) View.GONE else View.VISIBLE
        details.visibility = if (enabled) View.GONE else View.VISIBLE
        actionsRow.visibility = if (enabled) View.GONE else View.VISIBLE
        socialActionsRow.visibility = if (enabled) View.GONE else View.VISIBLE
        nextVideosBlock.visibility = if (enabled) View.GONE else View.VISIBLE

        val params = playerCard.layoutParams as LinearLayout.LayoutParams
        if (enabled) {
            params.height = 0
            params.weight = 1f
            params.marginStart = 0
            params.marginEnd = 0
            playerCard.background = rounded("#000000", 0)
        } else {
            val width = activity.resources.displayMetrics.widthPixels
            params.height = (width * 9f / 16f).toInt()
            params.weight = 0f
            params.marginStart = dp(12)
            params.marginEnd = dp(12)
            playerCard.background = rounded("#000000", 18)
        }
        playerCard.layoutParams = params
        playerCard.requestLayout()
        showOverlay()
    }

    fun destroy() {
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
        handler.removeCallbacks(autoHideControls); overlay.visibility=View.VISIBLE; overlay.animate().cancel(); overlay.animate().alpha(1f).setDuration(if(settings.animations)240 else 0).start()
        if(player.isPlaying) handler.postDelayed(autoHideControls,3_000L)
    }

    private fun hideOverlay() {
        handler.removeCallbacks(autoHideControls); overlay.animate().cancel(); overlay.animate()
            .alpha(0f)
            .setDuration(if (settings.animations) 240 else 0)
            .withEndAction { overlay.visibility = View.GONE }
            .start()
    }

    private fun pulse(view: View) {
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
            setImageResource(resId)
            setBackgroundColor(Color.TRANSPARENT)
            background = rounded(backgroundColor, size / 2)
            setPadding(dp(12), dp(12), dp(12), dp(12))
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
        val fileName = raw.matches(Regex("""\d{4}-\d{2}-\d{2}[_-].*\.(mp4|mkv|mov|webm)""", RegexOption.IGNORE_CASE))
        return if (fileName) "Запись стрима" else raw
    }

    private fun buildMeta(): String =
        listOf(formatMs(item.durationSeconds * 1000L), buildSize(), "@t2x2_video").joinToString(" • ")

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
