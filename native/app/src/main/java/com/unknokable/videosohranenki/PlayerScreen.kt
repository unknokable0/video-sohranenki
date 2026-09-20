package com.unknokable.videosohranenki

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
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
    private val onBack: () -> Unit,
    private val onFullscreen: (Boolean) -> Unit,
    private val onPlaybackStarted: () -> Unit
) {
    val root = LinearLayout(activity)
    val player: ExoPlayer

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var playerCard: FrameLayout
    private lateinit var header: LinearLayout
    private lateinit var details: LinearLayout
    private lateinit var overlay: FrameLayout
    private lateinit var playPause: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var qualityButton: TextView
    private lateinit var speedBadge: TextView
    private lateinit var bufferingLoader: LoadingWaveView
    private lateinit var actionsRow: LinearLayout
    private lateinit var previewBubble: LinearLayout
    private lateinit var previewImage: ImageView
    private lateinit var previewTime: TextView
    private val previewScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val previewMutex = Mutex()
    private var previewRetriever: MediaMetadataRetriever? = null
    private var previewDataSource: MediaDataSource? = null
    private var previewJob: Job? = null
    private var previewBitmap: Bitmap? = null
    private var lastPreviewMs = -1L

    private var fullscreen = false
    private var dragging = false
    private var speed = settings.playbackSpeed
    private var sleepRunnable: Runnable? = null
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
        root.keepScreenOn = true

        header = buildHeader()
        root.addView(header)

        playerCard = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            background = rounded("#000000", 18)
            clipToOutline = true
        }

        val playerView = PlayerView(activity).apply {
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
        actionsRow = buildActions()
        root.addView(actionsRow)

        previewBubble = buildSeekPreview()
        playerCard.addView(
            previewBubble,
            FrameLayout.LayoutParams(dp(174), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(58)
            }
        )

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                18_000,
                75_000,
                1_200,
                4_000
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
        player.playWhenReady = settings.autoplay
        player.prepare()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayIcon()
                if (isPlaying && !playbackCounted) {
                    playbackCounted = true
                    onPlaybackStarted()
                }
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
                    totalTime.text = formatMs(player.duration)
                    updateQualityLabel()
                }

                if (playbackState == Player.STATE_ENDED) {
                    settings.clearPlaybackPosition(item.messageId)
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

        seekBar = SeekBar(activity).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#A78BFA"))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#66FFFFFF"))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val duration = player.duration
                    if (duration <= 0) return
                    val target = duration * progress / 1000L
                    previewTime.text = formatMs(target)
                    currentTime.text = formatMs(target)
                    showPreview()
                    requestPreview(target)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    dragging = true
                    showPreview()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val duration = player.duration
                    if (duration > 0) {
                        val target = duration * (seekBar?.progress ?: 0) / 1000L
                        player.seekTo(target)
                        currentTime.text = formatMs(target)
                    }
                    dragging = false
                    handler.postDelayed({ hidePreview() }, 120)
                }
            })
        }

        val times = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        currentTime = timeLabel("0:00")
        totalTime = timeLabel("0:00")
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

        val fullscreenButton = iconButton(R.drawable.ic_fullscreen, "#66181322", 40).apply {
            setOnClickListener {
                onFullscreen(!fullscreen)
                pulse(this)
            }
        }

        times.addView(currentTime)
        times.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        times.addView(totalTime)
        times.addView(qualityButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)).apply { marginStart = dp(7) })
        times.addView(fullscreenButton, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginStart = dp(5) })

        bottom.addView(seekBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)))
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

    private fun requestPreview(positionMs: Long) {
        if (positionMs < 0) return
        if (lastPreviewMs >= 0 && kotlin.math.abs(positionMs - lastPreviewMs) < 750) return
        lastPreviewMs = positionMs

        previewJob?.cancel()
        previewJob = previewScope.launch {
            delay(70)
            val bitmap = runCatching {
                previewMutex.withLock {
                    ensurePreviewRetriever()
                    val raw = previewRetriever?.getFrameAtTime(
                        positionMs * 1000L,
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
                    if (!dragging) {
                        bitmap.recycle()
                        return@withContext
                    }
                    previewImage.setImageBitmap(bitmap)
                    previewBitmap?.takeIf { it !== bitmap }?.recycle()
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
        val detector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleOverlay()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val half = playerCard.width / 2f
                if (e.x < half) {
                    player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                } else {
                    val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                    player.seekTo((player.currentPosition + 10_000).coerceAtMost(duration))
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                player.setPlaybackSpeed(2f)
                speedBadge.animate().alpha(1f).setDuration(120).start()
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val start = e1 ?: return false
                val dy = e2.y - start.y
                if (kotlin.math.abs(dy) > 120 && kotlin.math.abs(velocityY) > kotlin.math.abs(velocityX)) {
                    if (dy < 0) onFullscreen(true) else onBack()
                    return true
                }
                return false
            }
        })

        playerCard.setOnTouchListener { view, event ->
            val handled = detector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                if (speedBadge.alpha > 0f) {
                    player.setPlaybackSpeed(speed)
                    speedBadge.animate().alpha(0f).setDuration(120).start()
                }
            }
            if (!handled && event.actionMasked == MotionEvent.ACTION_UP) {
                view.performClick()
            }
            handled
        }
    }


    private fun showPlayerMenu() {
        val labels = listOf(
            "Качество видео",
            "Скорость воспроизведения",
            "Таймер сна",
            "Статистика видео"
        )
        ModernDialogs.showChoices(activity, palette, "Видео", labels, -1) { which ->
            when (which) {
                0 -> showQualityPicker()
                1 -> showSpeedPicker()
                2 -> showSleepPicker()
                3 -> showStats()
            }
        }
    }

    private fun showSpeedPicker() {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        val labels = speeds.map { if (it == 1f) "Обычная • 1×" else "$it×" }
        val selected = speeds.indexOfFirst { it == speed }.coerceAtLeast(3)
        ModernDialogs.showChoices(activity, palette, "Скорость воспроизведения", labels, selected) { which ->
            speed = speeds[which]
            settings.playbackSpeed = speed
            player.setPlaybackSpeed(speed)
            speedActionButton?.text =
                if (speed == 1f) "1×  Скорость" else speed.toString() + "×  Скорость"
        }
    }

    private fun showSleepPicker() {
        val labels = listOf("Выкл", "10 минут", "20 минут", "30 минут", "45 минут", "1 час", "В конце видео")
        ModernDialogs.showChoices(activity, palette, "Таймер сна", labels, 0) { which ->
            sleepRunnable?.let { handler.removeCallbacks(it) }
            sleepRunnable = null
            when (which) {
                1,2,3,4,5 -> {
                    val minutes = listOf(0,10,20,30,45,60)[which]
                    val r = Runnable {
                        player.pause()
                    }
                    sleepRunnable = r
                    handler.postDelayed(r, minutes * 60_000L)
                }
                6 -> {
                    player.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) player.pause()
                        }
                    })
                }
            }
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

    fun setFullscreenMode(enabled: Boolean) {
        fullscreen = enabled
        header.visibility = if (enabled) View.GONE else View.VISIBLE
        details.visibility = if (enabled) View.GONE else View.VISIBLE
        actionsRow.visibility = if (enabled) View.GONE else View.VISIBLE

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
        previewBitmap?.recycle()
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

        ModernDialogs.showChoices(activity, palette, "Качество видео", labels, 0) { which ->
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
        }, 350)
    }

    private fun updateProgress() {
        if (!dragging) {
            val duration = player.duration
            if (duration > 0) {
                seekBar.progress = ((player.currentPosition * 1000L) / duration).toInt().coerceIn(0, 1000)
                totalTime.text = formatMs(duration)
            }
        }
        currentTime.text = formatMs(player.currentPosition)
        persistPlaybackPosition()
        updatePlayIcon()
    }

    private fun persistPlaybackPosition(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastProgressPersistAt < 2_000L) return
        lastProgressPersistAt = now

        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration
        settings.savePlaybackPosition(item.messageId, position, duration)
    }

    private fun toggleOverlay() {
        if (overlay.alpha > 0.1f) hideOverlay() else showOverlay()
    }

    private fun showOverlay() {
        overlay.visibility = View.VISIBLE
        overlay.animate().alpha(1f).setDuration(if (settings.animations) 135 else 0).start()
    }

    private fun hideOverlay() {
        overlay.animate()
            .alpha(0f)
            .setDuration(if (settings.animations) 135 else 0)
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
