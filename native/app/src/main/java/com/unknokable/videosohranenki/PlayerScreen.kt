package com.unknokable.videosohranenki

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
class PlayerScreen(
    private val activity: Activity,
    private val item: VideoItem,
    private val mediaUrl: String,
    private val settings: AppSettings,
    private val onBack: () -> Unit,
    private val onFullscreen: (Boolean) -> Unit
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
    private lateinit var actionsRow: LinearLayout
    private lateinit var aiPanel: LinearLayout

    private var fullscreen = false
    private var dragging = false
    private var loopEnabled = false
    private var speed = 1f
    private var sleepRunnable: Runnable? = null
    private val palette get() = settings.palette()

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(palette.background)

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
        aiPanel = buildAiPanel()
        root.addView(aiPanel)

        player = ExoPlayer.Builder(activity)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        playerView.player = player
        player.setMediaItem(MediaItem.fromUri(mediaUrl))
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.prepare()
        player.playWhenReady = settings.autoplay

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayIcon()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateProgress()
                if (playbackState == Player.STATE_READY) {
                    totalTime.text = formatMs(player.duration)
                    updateQualityLabel()
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

        val back = iconButton(R.drawable.ic_back, "#181322").apply {
            setOnClickListener { onBack() }
        }

        val title = TextView(activity).apply {
            text = cleanTitle(item.title)
            textSize = 15f
            setTextColor(palette.text)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(10), 0, dp(10), 0)
        }

        val menu = textCircle("⋮", "#B89AFF").apply {
            textSize = 22f
            setOnClickListener { showPlayerMenu() }
        }

        row.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(menu, LinearLayout.LayoutParams(dp(48), dp(48)))
        return row
    }

    private fun buildOverlay(): FrameLayout {
        val frame = FrameLayout(activity).apply {
            setBackgroundColor(Color.parseColor("#30000000"))
        }

        val center = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val rewind = textCircle("−10", "#7CFFB2").apply {
            setOnClickListener {
                player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                pulse(this)
            }
        }

        playPause = iconButton(R.drawable.ic_play, "#8B5CF6", 64).apply {
            setOnClickListener {
                if (player.isPlaying) player.pause() else player.play()
                pulse(this)
            }
        }

        val forward = textCircle("+10", "#7CFFB2").apply {
            setOnClickListener {
                val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                player.seekTo((player.currentPosition + 10_000).coerceAtMost(duration))
                pulse(this)
            }
        }

        center.addView(rewind, LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(24) })
        center.addView(playPause, LinearLayout.LayoutParams(dp(68), dp(68)))
        center.addView(forward, LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginStart = dp(24) })

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
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = Unit
                override fun onStartTrackingTouch(seekBar: SeekBar?) { dragging = true }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val duration = player.duration
                    if (duration > 0) player.seekTo(duration * (seekBar?.progress ?: 0) / 1000L)
                    dragging = false
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
            textSize = 11f
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
        times.addView(qualityButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply { marginStart = dp(8) })
        times.addView(fullscreenButton, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginStart = dp(6) })

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

    private fun buildAiPanel(): LinearLayout {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = roundedInt(palette.surface, 20)
        }

        val head = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = TextView(activity).apply {
            text = "AI"
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = roundedInt(palette.accent, 14)
        }

        val labels = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }

        val title = TextView(activity).apply {
            text = "Умные моменты стрима"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        }

        val subtitle = TextView(activity).apply {
            text = if (settings.aiAnalysis)
                "Интерфейс готов. Для реального распознавания игр, реакций и глав нужен AI-сервер."
            else
                "AI-анализ выключен в настройках"
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(0, dp(3), 0, 0)
        }

        labels.addView(title)
        labels.addView(subtitle)

        head.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)))
        head.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(head)

        if (settings.aiAnalysis) {
            val state = TextView(activity).apply {
                text = "AI-бэкенд не подключён"
                gravity = Gravity.CENTER
                textSize = 12.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.accent)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = roundedInt(palette.surfaceAlt, 14)
            }
            box.addView(state, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) })
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(14))
            addView(box)
        }
    }

    private fun buildActions(): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), dp(14))
        }

        val speedBtn = actionPill("1× Скорость") { showSpeedPicker() }
        val loopBtn = actionPill("↻ Цикл") {
            loopEnabled = !loopEnabled
            player.repeatMode = if (loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            it.alpha = if (loopEnabled) 1f else 0.72f
            Toast.makeText(activity, if (loopEnabled) "Цикл включён" else "Цикл выключен", Toast.LENGTH_SHORT).show()
        }
        val sleepBtn = actionPill("◷ Таймер") { showSleepPicker() }

        row.addView(speedBtn, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        row.addView(loopBtn, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        row.addView(sleepBtn, LinearLayout.LayoutParams(0, dp(44), 1f))
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
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleOverlay()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val half = playerCard.width / 2f
                if (e.x < half) {
                    player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                    showGestureHint("−10 сек")
                } else {
                    val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                    player.seekTo((player.currentPosition + 10_000).coerceAtMost(duration))
                    showGestureHint("+10 сек")
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                player.playbackParameters = PlaybackParameters(2f)
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

        playerCard.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                if (speedBadge.alpha > 0f) {
                    player.playbackParameters = PlaybackParameters(speed)
                    speedBadge.animate().alpha(0f).setDuration(120).start()
                }
            }
            true
        }
    }

    private fun showGestureHint(text: String) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
    }

    private fun showPlayerMenu() {
        val labels = listOf(
            "Качество",
            "Скорость воспроизведения",
            if (loopEnabled) "Выключить цикл" else "Зациклить видео",
            "Таймер сна",
            "Статистика видео",
            "AI-моменты"
        )
        ModernDialogs.showChoices(activity, palette, "Настройки видео", labels, 0) { which ->
            when (which) {
                0 -> showQualityPicker()
                1 -> showSpeedPicker()
                2 -> {
                    loopEnabled = !loopEnabled
                    player.repeatMode = if (loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                }
                3 -> showSleepPicker()
                4 -> showStats()
                5 -> Toast.makeText(activity, "Для реального AI-анализа нужно подключить AI-бэкенд", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showSpeedPicker() {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        val labels = speeds.map { if (it == 1f) "Обычная • 1×" else "$it×" }
        val selected = speeds.indexOfFirst { it == speed }.coerceAtLeast(3)
        ModernDialogs.showChoices(activity, palette, "Скорость воспроизведения", labels, selected) { which ->
            speed = speeds[which]
            player.playbackParameters = PlaybackParameters(speed)
            Toast.makeText(activity, "Скорость ${labels[which]}", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(activity, "Таймер сна завершён", Toast.LENGTH_SHORT).show()
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
        aiPanel.visibility = if (enabled) View.GONE else View.VISIBLE

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
        sleepRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
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
        playPause.setImageResource(if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
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
        updatePlayIcon()
    }

    private fun toggleOverlay() {
        if (overlay.alpha > 0.1f) hideOverlay() else showOverlay()
    }

    private fun showOverlay() {
        overlay.visibility = View.VISIBLE
        overlay.animate().alpha(1f).setDuration(if (settings.animations) 160 else 0).start()
    }

    private fun hideOverlay() {
        overlay.animate()
            .alpha(0f)
            .setDuration(if (settings.animations) 160 else 0)
            .withEndAction { overlay.visibility = View.GONE }
            .start()
    }

    private fun pulse(view: View) {
        if (!settings.animations) return
        view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(70).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
        }.start()
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
