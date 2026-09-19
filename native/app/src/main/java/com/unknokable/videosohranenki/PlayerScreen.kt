package com.unknokable.videosohranenki

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
    private var fullscreen = false
    private var dragging = false
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

        val width = activity.resources.displayMetrics.widthPixels
        root.addView(
            playerCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (width * 9f / 16f).toInt()
            ).apply {
                marginStart = dp(10)
                marginEnd = dp(10)
            }
        )

        details = buildDetails()
        root.addView(details)

        player = ExoPlayer.Builder(activity)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        playerView.player = player
        player.setMediaItem(MediaItem.fromUri(mediaUrl))
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
                }
            }
        })

        playerCard.setOnClickListener { toggleOverlay() }
        scheduleProgress()
    }

    private fun buildHeader(): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
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
            maxLines = 1
            setPadding(dp(12), 0, dp(8), 0)
        }

        val spacer = View(activity)

        row.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(spacer, LinearLayout.LayoutParams(dp(48), dp(48)))
        return row
    }

    private fun buildOverlay(): FrameLayout {
        val frame = FrameLayout(activity).apply {
            setBackgroundColor(Color.parseColor("#38000000"))
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
            setPadding(dp(14), dp(6), dp(14), dp(12))
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
        totalTime = timeLabel("0:00").apply { gravity = Gravity.END }
        val spacer = View(activity)

        val fullscreenButton = iconButton(R.drawable.ic_fullscreen, "#66181322", 40).apply {
            setOnClickListener {
                onFullscreen(!fullscreen)
                pulse(this)
            }
        }

        times.addView(currentTime)
        times.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        times.addView(totalTime)
        times.addView(fullscreenButton, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginStart = dp(8) })

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
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        val title = TextView(activity).apply {
            text = cleanTitle(item.title)
            textSize = 20f
            setTextColor(Color.parseColor("#F7F5FF"))
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

    fun setFullscreenMode(enabled: Boolean) {
        fullscreen = enabled
        header.visibility = if (enabled) View.GONE else View.VISIBLE
        details.visibility = if (enabled) View.GONE else View.VISIBLE

        val params = playerCard.layoutParams as LinearLayout.LayoutParams
        if (enabled) {
            params.height = 0
            params.weight = 1f
            params.marginStart = 0
            params.marginEnd = 0
        } else {
            val width = activity.resources.displayMetrics.widthPixels
            params.height = (width * 9f / 16f).toInt()
            params.weight = 0f
            params.marginStart = dp(10)
            params.marginEnd = dp(10)
        }
        playerCard.layoutParams = params
        playerCard.requestLayout()
        showOverlay()
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        player.release()
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

    private fun buildMeta(): String {
        val mb = item.fileSize / 1048576.0
        val size = if (mb >= 1024) "%.1f ГБ".format(mb / 1024.0) else "%.0f МБ".format(mb)
        return listOf(formatMs(item.durationSeconds * 1000L), size, "@t2x2_video").joinToString(" • ")
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

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
