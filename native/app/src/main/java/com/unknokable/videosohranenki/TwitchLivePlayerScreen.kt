package com.unknokable.videosohranenki

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
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
import androidx.media3.common.MediaItem
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

@androidx.annotation.OptIn(UnstableApi::class)
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

    private val playerCard = FrameLayout(activity)
    private val playerView = PlayerView(activity)
    private val loader = LoadingWaveView(activity, Color.parseColor("#FF5F7E"))
    private val playPause = ImageButton(activity)
    private val muteButton = ImageButton(activity)
    private val fullscreenButton = ImageButton(activity)

    private val chatStatus = TextView(activity)
    private val chatScroll = ScrollView(activity)
    private val chatList = LinearLayout(activity)
    private val chatRows = mutableListOf<View>()

    private var player: ExoPlayer? = null
    private var muted = false
    private var fullscreen = false
    private var destroyed = false

    val isFullscreen: Boolean
        get() = fullscreen

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(palette.background)

        root.addView(buildHeader())

        playerCard.apply {
            setBackgroundColor(Color.BLACK)
            background = rounded(Color.BLACK, 18)
            clipToOutline = true
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

        loader.alpha = 0.92f
        playerCard.addView(
            loader,
            FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER)
        )

        playerCard.addView(
            buildPlayerOverlay(),
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

        root.addView(buildStreamInfo())
        root.addView(
            buildChat(),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
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

        val back = iconButton(R.drawable.ic_back, palette.surfaceAlt, 42).apply {
            contentDescription = "Назад"
            setOnClickListener { onBack() }
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
            textSize = 16f
            maxLines = 1
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })
        titles.addView(TextView(activity).apply {
            text = "@" + live.login + " • LIVE"
            textSize = 11.5f
            setTextColor(Color.parseColor("#FF5F7E"))
            setPadding(0, dp(2), 0, 0)
        })

        row.addView(back, LinearLayout.LayoutParams(dp(42), dp(42)))
        row.addView(
            pulse,
            LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                marginStart = dp(10)
            }
        )
        row.addView(
            titles,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        return row
    }

    private fun buildPlayerOverlay(): View {
        val overlay = FrameLayout(activity).apply {
            setBackgroundColor(Color.parseColor("#14000000"))
        }

        playPause.apply {
            setImageResource(R.drawable.ic_pause)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = rounded(Color.parseColor("#840A0A0E"), 27)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            contentDescription = "Пауза"
            setOnClickListener {
                val p = player ?: return@setOnClickListener
                if (p.isPlaying) p.pause() else p.play()
            }
        }
        overlay.addView(
            playPause,
            FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER)
        )

        val liveBadge = TextView(activity).apply {
            text = "LIVE"
            textSize = 10.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(9), 0, dp(9), 0)
            background = rounded(Color.parseColor("#E84F70"), 11)
        }
        overlay.addView(
            liveBadge,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(28),
                Gravity.BOTTOM or Gravity.START
            ).apply {
                leftMargin = dp(10)
                bottomMargin = dp(10)
            }
        )

        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        muteButton.apply {
            setImageResource(R.drawable.ic_volume_on)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = rounded(Color.parseColor("#840A0A0E"), 20)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            contentDescription = "Выключить звук"
            setOnClickListener { toggleMute() }
        }

        fullscreenButton.apply {
            setImageResource(R.drawable.ic_fullscreen)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = rounded(Color.parseColor("#840A0A0E"), 20)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            contentDescription = "Полный экран"
            setOnClickListener { setFullscreenMode(!fullscreen) }
        }

        actions.addView(muteButton, LinearLayout.LayoutParams(dp(42), dp(42)))
        actions.addView(
            fullscreenButton,
            LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                marginStart = dp(8)
            }
        )

        overlay.addView(
            actions,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(44),
                Gravity.BOTTOM or Gravity.END
            ).apply {
                rightMargin = dp(10)
                bottomMargin = dp(10)
            }
        )
        return overlay
    }

    private fun buildStreamInfo(): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(10))

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
                ).joinToString(" • ")
                textSize = 12f
                setTextColor(palette.muted)
                setPadding(0, dp(5), 0, 0)
            })
        }
    }

    private fun buildChat(): View {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(12))
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(8))
        }

        header.addView(
            TextView(activity).apply {
                text = "Чат"
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.text)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        chatStatus.apply {
            text = "Подключаем…"
            textSize = 10.5f
            setTextColor(palette.muted)
            setOnClickListener {
                if (text.toString().contains("переподключ", ignoreCase = true)) {
                    onChatScopeMissing()
                }
            }
        }
        header.addView(chatStatus)
        box.addView(header)

        chatList.orientation = LinearLayout.VERTICAL
        chatList.setPadding(dp(8), dp(8), dp(8), dp(8))

        chatScroll.apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            background = rounded(palette.surface, 18)
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
                    .setBufferDurationsMs(7_000, 30_000, 500, 1_000)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()

                val exo = ExoPlayer.Builder(activity)
                    .setLoadControl(loadControl)
                    .build()
                player = exo
                playerView.player = exo
                exo.volume = 1f
                exo.playWhenReady = true
                exo.setMediaItem(MediaItem.fromUri(hlsUrl))
                exo.prepare()
                exo.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
                        playPause.contentDescription = if (isPlaying) "Пауза" else "Продолжить"
                    }

                    override fun onRenderedFirstFrame() {
                        loader.visibility = View.GONE
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
            chatStatus.text = "Переподключи Twitch для чата"
            return
        }

        chatClient.connect(
            accessToken = accessToken,
            accountLogin = accountLogin,
            channelLogin = live.login,
            onStatus = { status ->
                activity.runOnUiThread {
                    if (!destroyed) {
                        chatStatus.text = status
                        chatStatus.setTextColor(
                            if (status == "Чат подключён") Color.parseColor("#72D8A4")
                            else palette.muted
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

    private fun appendChat(message: TwitchChatMessage) {
        val text = SpannableStringBuilder()
            .append(message.displayName)
            .append(": ")
            .append(message.text)

        val nameEnd = message.displayName.length.coerceAtMost(text.length)
        if (nameEnd > 0) {
            text.setSpan(
                ForegroundColorSpan(message.color),
                0,
                nameEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            text.setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                nameEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val row = TextView(activity).apply {
            this.text = text
            textSize = 13f
            setTextColor(palette.text)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(palette.surfaceAlt, 12)
            maxLines = 5
        }

        chatList.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(5)
            }
        )
        chatRows.add(row)

        while (chatRows.size > 70) {
            val old = chatRows.removeAt(0)
            chatList.removeView(old)
        }

        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun showPlayerError(message: String) {
        val error = TextView(activity).apply {
            text = message
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(18), dp(11), dp(18), dp(11))
            background = rounded(Color.parseColor("#C018111E"), 16)
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

    private fun toggleMute() {
        muted = !muted
        player?.volume = if (muted) 0f else 1f
        muteButton.setImageResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on)
        muteButton.contentDescription = if (muted) "Включить звук" else "Выключить звук"
    }

    fun setFullscreenMode(enabled: Boolean) {
        if (fullscreen == enabled) return
        fullscreen = enabled
        onFullscreen(enabled)

        val params = playerCard.layoutParams as LinearLayout.LayoutParams
        if (enabled) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                child.visibility = if (child === playerCard) View.VISIBLE else View.GONE
            }
            params.height = 0
            params.weight = 1f
            params.marginStart = 0
            params.marginEnd = 0
        } else {
            for (i in 0 until root.childCount) {
                root.getChildAt(i).visibility = View.VISIBLE
            }
            val width = activity.resources.displayMetrics.widthPixels
            params.height = (width * 9f / 16f).toInt()
            params.weight = 0f
            params.marginStart = dp(12)
            params.marginEnd = dp(12)
        }
        playerCard.layoutParams = params
    }

    fun exitFullscreen() {
        setFullscreenMode(false)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        chatClient.close()
        scope.cancel()
        playerView.player = null
        player?.release()
        player = null
    }

    private fun iconButton(resId: Int, backgroundColor: Int, size: Int): ImageButton =
        ImageButton(activity).apply {
            setImageResource(resId)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = rounded(backgroundColor, size / 2)
            setPadding(dp(11), dp(11), dp(11), dp(11))
        }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
