package com.unknokable.videosohranenki

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.math.roundToLong

class TwitchPlayerScreen(
    private val activity: Activity,
    private val item: VideoItem,
    private val videoId: String,
    private val palette: ThemePalette,
    private val animationsEnabled: Boolean,
    private val settings: AppSettings,
    private val startPositionMs: Long = 0L,
    private val onBack: () -> Unit,
    private val onFullscreen: (Boolean) -> Unit
) {
    val root = FrameLayout(activity)

    private val handler = Handler(Looper.getMainLooper())
    private val content = LinearLayout(activity)
    private val webView = WebView(activity)

    private lateinit var header: LinearLayout
    private lateinit var playerCard: FrameLayout
    private lateinit var controlsCard: LinearLayout
    private lateinit var details: LinearLayout
    private lateinit var playPause: ImageButton
    private lateinit var seekBar: SohrTimeBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var qualityButton: TextView
    private lateinit var fullscreenButton: ImageButton

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreen = false
    private var destroyed = false
    private var ready = false
    private var paused = true
    private var dragging = false
    private var currentMs = startPositionMs.coerceAtLeast(0L)
    private var durationMs = item.durationSeconds.coerceAtLeast(0) * 1000L
    private var bufferedMs = currentMs
    private var qualities: List<String> = emptyList()
    private var currentQuality = ""
    private var lastPersistAt = 0L

    val isFullscreen: Boolean
        get() = fullscreen || customView != null

    private val progressPoll = object : Runnable {
        override fun run() {
            if (destroyed) return
            pollPlayerState()
            handler.postDelayed(this, if (paused) 850L else 350L)
        }
    }

    init {
        root.setBackgroundColor(Color.BLACK)

        content.orientation = LinearLayout.VERTICAL
        content.setBackgroundColor(palette.background)

        header = buildHeader()
        content.addView(header)

        playerCard = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            background = rounded(Color.BLACK, 18)
            clipToOutline = true
        }

        setupWebView()
        playerCard.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        content.addView(playerCard, normalPlayerLayoutParams())

        controlsCard = buildControls()
        content.addView(
            controlsCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
                topMargin = dp(8)
            }
        )

        details = buildDetails()
        content.addView(
            details,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
                topMargin = dp(12)
                bottomMargin = dp(18)
            }
        )

        root.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        loadVideo()
        handler.post(progressPoll)

        if (animationsEnabled) {
            playerCard.alpha = 0f
            playerCard.translationY = dp(8).toFloat()
            controlsCard.alpha = 0f
            controlsCard.translationY = dp(8).toFloat()
            playerCard.post {
                val ease = android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
                playerCard.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(240L)
                    .setInterpolator(ease)
                    .start()
                controlsCard.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(55L)
                    .setDuration(240L)
                    .setInterpolator(ease)
                    .start()
            }
        }
    }

    private fun buildHeader(): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
            setBackgroundColor(palette.background)
        }

        val back = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_back)
            imageTintList = ColorStateList.valueOf(palette.text)
            background = rounded(palette.surfaceAlt, 22)
            setPadding(dp(11), dp(11), dp(11), dp(11))
            contentDescription = "Назад"
            setOnClickListener {
                pulse(this)
                onBack()
            }
        }

        val titleBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        titleBox.addView(TextView(activity).apply {
            text = "Запись стрима"
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })
        titleBox.addView(TextView(activity).apply {
            text = "Twitch • @t2x2"
            textSize = 11.5f
            setTextColor(palette.muted)
            setPadding(0, dp(2), 0, 0)
        })

        row.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        row.addView(titleBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(View(activity), LinearLayout.LayoutParams(dp(44), dp(44)))
        return row
    }

    private fun setupWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.setBackgroundColor(Color.BLACK)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                content.visibility = View.GONE
                root.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                onFullscreen(true)
            }

            override fun onHideCustomView() {
                val view = customView ?: return
                root.removeView(view)
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                content.visibility = View.VISIBLE
                if (fullscreen) setFullscreenMode(false)
                onFullscreen(false)
            }
        }
    }

    private fun loadVideo() {
        val url = Uri.parse("https://unknokable0.github.io/video-sohranenki/twitch-player/")
            .buildUpon()
            .appendQueryParameter("video", videoId)
            .appendQueryParameter("start", (startPositionMs / 1000L).coerceAtLeast(0L).toString())
            .build()
            .toString()
        webView.loadUrl(url)
    }

    private fun buildControls(): LinearLayout {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(9), dp(14), dp(12))
            background = rounded(Color.parseColor("#14111B"), 18)
        }

        seekBar = SohrTimeBar(activity).apply {
            listener = object : SohrTimeBar.Listener {
                override fun onScrubStart(positionMs: Long) {
                    dragging = true
                    currentTime.text = formatMs(positionMs)
                }

                override fun onScrubMove(positionMs: Long, fraction: Float) {
                    currentTime.text = formatMs(positionMs)
                }

                override fun onScrubStop(positionMs: Long, canceled: Boolean) {
                    if (!canceled) {
                        currentMs = positionMs
                        currentTime.text = formatMs(positionMs)
                        seekTo(positionMs)
                    }
                    dragging = false
                }
            }
        }
        seekBar.setProgress(currentMs, durationMs, bufferedMs)
        box.addView(
            seekBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30))
        )

        val times = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        currentTime = timeLabel(formatMs(currentMs)).apply {
            minWidth = dp(46)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        totalTime = timeLabel(formatMs(durationMs)).apply {
            minWidth = dp(50)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

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

        fullscreenButton = iconButton(R.drawable.ic_fullscreen, Color.parseColor("#66181322"), 40).apply {
            setOnClickListener {
                pulse(this)
                onFullscreen(!fullscreen)
            }
        }

        times.addView(currentTime, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)))
        times.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
        times.addView(qualityButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)))
        times.addView(totalTime, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
            marginStart = dp(8)
        })
        times.addView(fullscreenButton, LinearLayout.LayoutParams(dp(38), dp(38)).apply {
            marginStart = dp(5)
        })
        box.addView(times)

        val transport = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }

        val rewind = transportButton("−10").apply {
            setOnClickListener {
                pulse(this)
                seekBy(-10_000L)
            }
        }
        playPause = iconButton(R.drawable.ic_play, Color.parseColor("#8B5CF6"), 54).apply {
            setOnClickListener {
                pulse(this)
                if (paused) runJs("window.sohr&&window.sohr.play()")
                else runJs("window.sohr&&window.sohr.pause()")
            }
        }
        val forward = transportButton("+10").apply {
            setOnClickListener {
                pulse(this)
                seekBy(10_000L)
            }
        }

        transport.addView(rewind, LinearLayout.LayoutParams(dp(54), dp(46)).apply { marginEnd = dp(18) })
        transport.addView(playPause, LinearLayout.LayoutParams(dp(56), dp(56)))
        transport.addView(forward, LinearLayout.LayoutParams(dp(54), dp(46)).apply { marginStart = dp(18) })
        box.addView(transport)
        return box
    }

    private fun buildDetails(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(activity).apply {
            text = item.title.ifBlank { "Запись стрима" }
            textSize = 18f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })
        addView(TextView(activity).apply {
            text = "${formatMs(durationMs)} • Twitch • @t2x2"
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(0, dp(5), 0, 0)
        })
    }

    private fun pollPlayerState() {
        if (destroyed) return
        webView.evaluateJavascript(
            "(function(){try{return window.sohr?JSON.stringify(window.sohr.status()):null}catch(e){return null}})()"
        ) { raw ->
            if (destroyed || raw.isNullOrBlank() || raw == "null") return@evaluateJavascript
            runCatching {
                val decoded = JSONTokener(raw).nextValue()
                val jsonText = if (decoded is String) decoded else raw
                val obj = JSONObject(jsonText)
                val wasReady = ready
                ready = obj.optBoolean("ready", false)
                paused = obj.optBoolean("paused", true)

                val reportedDuration = (obj.optDouble("duration", 0.0) * 1000.0).roundToLong()
                if (reportedDuration > 0L) durationMs = reportedDuration
                val reportedCurrent = (obj.optDouble("current", 0.0) * 1000.0).roundToLong().coerceAtLeast(0L)
                if (!dragging) currentMs = reportedCurrent.coerceAtMost(durationMs.coerceAtLeast(reportedCurrent))

                val bufferSeconds = obj.optDouble("buffer", 0.0).coerceAtLeast(0.0)
                bufferedMs = (currentMs + (bufferSeconds * 1000.0).roundToLong())
                    .coerceAtMost(durationMs.coerceAtLeast(currentMs))

                currentQuality = obj.optString("quality", "")
                val array = obj.optJSONArray("qualities")
                if (array != null) {
                    qualities = buildList {
                        for (i in 0 until array.length()) {
                            val value = array.optString(i)
                            if (value.isNotBlank()) add(value)
                        }
                    }.distinct()
                }

                updateUi()
                persistPosition()

                if (!wasReady && ready && startPositionMs > 0L) {
                    seekTo(startPositionMs)
                }
            }
        }
    }

    private fun updateUi() {
        playPause.setImageResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause)
        if (!dragging) {
            seekBar.setProgress(currentMs, durationMs, bufferedMs)
            currentTime.text = formatMs(currentMs)
        }
        totalTime.text = formatMs(durationMs)
        qualityButton.text = qualityLabel(currentQuality.ifBlank { "auto" })
        activity.window.decorView.keepScreenOn = !paused
    }

    private fun showQualityPicker() {
        if (!ready || qualities.isEmpty()) {
            ModernDialogs.showChoices(
                activity,
                palette,
                "Качество видео",
                listOf("Авто • Twitch выберет качество"),
                0
            ) { }
            return
        }

        val available = qualities
        val labels = available.map(::qualityLabel)
        val selected = available.indexOf(currentQuality).coerceAtLeast(0)

        ModernDialogs.showChoices(activity, palette, "Качество видео", labels, selected) { which ->
            val quality = available.getOrNull(which) ?: return@showChoices
            runJs("window.sohr&&window.sohr.setQuality(${JSONObject.quote(quality)})")
            currentQuality = quality
            qualityButton.text = qualityLabel(quality)
        }
    }

    private fun qualityLabel(raw: String): String = when (raw.lowercase()) {
        "auto" -> "Авто"
        "chunked" -> "Оригинал"
        else -> raw
    }

    private fun seekBy(deltaMs: Long) {
        val target = (currentMs + deltaMs).coerceIn(0L, durationMs.coerceAtLeast(0L))
        currentMs = target
        currentTime.text = formatMs(target)
        seekBar.setProgress(target, durationMs, bufferedMs)
        seekTo(target)
    }

    private fun seekTo(positionMs: Long) {
        val seconds = positionMs.coerceAtLeast(0L) / 1000.0
        runJs("window.sohr&&window.sohr.seek($seconds)")
    }

    private fun runJs(js: String) {
        if (!destroyed) webView.evaluateJavascript(js, null)
    }

    fun setFullscreenMode(enabled: Boolean) {
        fullscreen = enabled
        if (customView != null) return

        header.visibility = if (enabled) View.GONE else View.VISIBLE
        details.visibility = if (enabled) View.GONE else View.VISIBLE

        val params = playerCard.layoutParams as LinearLayout.LayoutParams
        if (enabled) {
            params.height = 0
            params.weight = 1f
            params.marginStart = 0
            params.marginEnd = 0
            params.topMargin = 0
        } else {
            val normal = normalPlayerLayoutParams()
            params.height = normal.height
            params.weight = 0f
            params.marginStart = normal.marginStart
            params.marginEnd = normal.marginEnd
            params.topMargin = normal.topMargin
        }
        playerCard.layoutParams = params

        val controlParams = controlsCard.layoutParams as LinearLayout.LayoutParams
        controlParams.marginStart = if (enabled) 0 else dp(12)
        controlParams.marginEnd = if (enabled) 0 else dp(12)
        controlParams.topMargin = if (enabled) 0 else dp(8)
        controlsCard.layoutParams = controlParams
    }

    fun exitFullscreen() {
        val view = customView
        if (view != null) {
            root.removeView(view)
            customView = null
            customViewCallback?.onCustomViewHidden()
            customViewCallback = null
            content.visibility = View.VISIBLE
        }
        if (fullscreen || view != null) onFullscreen(false)
    }

    fun destroy() {
        if (destroyed) return
        persistPosition(force = true)
        destroyed = true
        handler.removeCallbacks(progressPoll)
        activity.window.decorView.keepScreenOn = false
        customView?.let { root.removeView(it) }
        customView = null
        customViewCallback = null
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        webView.removeAllViews()
        webView.destroy()
    }

    private fun persistPosition(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPersistAt < 2_000L) return
        lastPersistAt = now
        settings.savePlaybackPosition(item.messageId, currentMs, durationMs)
    }

    private fun normalPlayerLayoutParams() =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)).apply {
            marginStart = dp(12)
            marginEnd = dp(12)
            topMargin = dp(4)
        }

    private fun transportButton(label: String): TextView =
        TextView(activity).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(Color.parseColor("#66181322"), 23)
        }

    private fun iconButton(resId: Int, backgroundColor: Int, size: Int): ImageButton =
        ImageButton(activity).apply {
            setImageResource(resId)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = rounded(backgroundColor, size / 2)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        }

    private fun timeLabel(value: String): TextView =
        TextView(activity).apply {
            text = value
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }

    private fun pulse(view: View) {
        if (!animationsEnabled) return
        view.animate().cancel()
        view.animate()
            .scaleX(0.94f)
            .scaleY(0.94f)
            .setDuration(55L)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(125L)
                    .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                    .start()
            }
            .start()
    }

    private fun formatMs(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val total = safe / 1000L
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
