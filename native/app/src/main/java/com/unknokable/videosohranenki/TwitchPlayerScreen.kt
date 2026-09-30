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
import android.view.MotionEvent
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
import kotlin.math.abs
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
    private lateinit var remainingTime: TextView
    private lateinit var qualityButton: TextView
    private lateinit var fullscreenButton: ImageButton

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreen = false
    private var pipMode = false
    private var pipHost: FrameLayout? = null
    private var pipOriginalParent: ViewGroup? = null
    private var pipOriginalIndex = -1
    private var pipOriginalLayoutParams: ViewGroup.LayoutParams? = null
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
    private var gestureDownX = 0f
    private var gestureDownY = 0f
    private var gestureConsumed = false

    val isFullscreen: Boolean
        get() = fullscreen || customView != null

    private val progressPoll = object : Runnable {
        override fun run() {
            if (destroyed) return
            pollPlayerState()
            handler.postDelayed(this, if (paused) 1_000L else 500L)
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
            playerCard.translationY = dp(6).toFloat()
            controlsCard.alpha = 0f
            controlsCard.translationY = dp(6).toFloat()
            playerCard.post {
                val ease = android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
                playerCard.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(190L)
                    .setInterpolator(ease)
                    .start()
                controlsCard.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(35L)
                    .setDuration(190L)
                    .setInterpolator(ease)
                    .start()
            }
        }
    }

    private fun buildHeader(): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(7))
            setBackgroundColor(palette.background)
        }

        val back = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_back)
            imageTintList = ColorStateList.valueOf(palette.text)
            background = rounded(palette.surfaceAlt, 19)
            setPadding(dp(9), dp(9), dp(9), dp(9))
            contentDescription = "Назад"
            setOnClickListener {
                pulse(this)
                onBack()
            }
        }

        val titleBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(6), 0)
        }
        titleBox.addView(TextView(activity).apply {
            text = "Запись стрима"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })
        titleBox.addView(TextView(activity).apply {
            text = "Twitch • @t2x2"
            textSize = 11f
            setTextColor(palette.muted)
            setPadding(0, dp(2), 0, 0)
        })

        row.addView(back, LinearLayout.LayoutParams(dp(38), dp(38)))
        row.addView(titleBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(View(activity), LinearLayout.LayoutParams(dp(38), dp(38)))
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
        webView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gestureDownX = event.x
                    gestureDownY = event.y
                    gestureConsumed = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - gestureDownX
                    val dy = event.y - gestureDownY
                    val threshold = dp(26).toFloat()
                    if (
                        abs(dx) > threshold && abs(dx) > abs(dy) * 1.25f ||
                        abs(dy) > threshold && abs(dy) > abs(dx) * 1.25f
                    ) {
                        gestureConsumed = true
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - gestureDownX
                    val dy = event.y - gestureDownY
                    val verticalThreshold = dp(72).toFloat()
                    val horizontalThreshold = dp(58).toFloat()

                    if (abs(dx) > horizontalThreshold && abs(dx) > abs(dy) * 1.25f) {
                        seekBy(if (dx > 0f) 10_000L else -10_000L)
                        return@setOnTouchListener true
                    }

                    if (abs(dy) > verticalThreshold && abs(dy) > abs(dx) * 1.25f) {
                        // Vertical gestures only navigate fullscreen. They never
                        // change system/media volume.
                        if (dy < 0f && !fullscreen) {
                            onFullscreen(true)
                            return@setOnTouchListener true
                        }
                        if (dy > 0f && fullscreen) {
                            onFullscreen(false)
                            return@setOnTouchListener true
                        }
                    }

                    if (gestureConsumed) return@setOnTouchListener true
                }
                MotionEvent.ACTION_CANCEL -> gestureConsumed = false
            }
            false
        }
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
            setPadding(dp(10), dp(6), dp(10), dp(8))
            background = rounded(Color.parseColor("#14111B"), 16)
        }

        seekBar = SohrTimeBar(activity, palette.accent).apply {
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
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24))
        )

        val times = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        currentTime = timeLabel(formatMs(currentMs)).apply {
            minWidth = dp(40)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        totalTime = timeLabel(formatMs(durationMs)).apply {
            minWidth = dp(44)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        remainingTime = TextView(activity).apply {
            text = remainingTimeLabel(currentMs, durationMs)
            textSize = 9.8f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(Color.parseColor("#55221A30"), 12)
            setPadding(dp(7), 0, dp(7), 0)
        }

        qualityButton = TextView(activity).apply {
            text = "Авто"
            textSize = 10f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(7), 0, dp(7), 0)
            background = rounded(Color.parseColor("#55221A30"), 13)
            setOnClickListener {
                pulse(this)
                showQualityPicker()
            }
        }

        val settingsButton = iconButton(R.drawable.ic_player_settings, Color.parseColor("#55221A30"), 34).apply {
            contentDescription = "Настройки плеера"
            setOnClickListener {
                pulse(this)
                showPlayerSettings()
            }
        }

        fullscreenButton = iconButton(R.drawable.ic_fullscreen, Color.parseColor("#55221A30"), 34).apply {
            setOnClickListener {
                pulse(this)
                onFullscreen(!fullscreen)
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
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30))
        )
        actionGroup.addView(
            settingsButton,
            LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginStart = dp(2) }
        )
        actionGroup.addView(
            fullscreenButton,
            LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginStart = dp(2) }
        )

        times.addView(currentTime, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)))
        times.addView(totalTime, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)).apply {
            marginStart = dp(4)
        })
        times.addView(
            remainingTime,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(25)).apply {
                marginStart = dp(5)
            }
        )
        times.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
        times.addView(actionGroup, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)))
        box.addView(times)

        val transport = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }

        val rewind = iconButton(R.drawable.ic_replay_10, Color.parseColor("#4D181322"), 40).apply {
            contentDescription = "Назад на 10 секунд"
            setOnClickListener {
                pulse(this)
                seekBy(-10_000L)
            }
        }
        playPause = iconButton(R.drawable.ic_play, Color.parseColor("#8B5CF6"), 48).apply {
            setOnClickListener {
                pulse(this)
                if (paused) runJs("window.sohr&&window.sohr.play()")
                else runJs("window.sohr&&window.sohr.pause()")
            }
        }
        val forward = iconButton(R.drawable.ic_forward_10, Color.parseColor("#4D181322"), 40).apply {
            contentDescription = "Вперёд на 10 секунд"
            setOnClickListener {
                pulse(this)
                seekBy(10_000L)
            }
        }

        transport.addView(rewind, LinearLayout.LayoutParams(dp(42), dp(40)).apply { marginEnd = dp(12) })
        transport.addView(playPause, LinearLayout.LayoutParams(dp(50), dp(50)))
        transport.addView(forward, LinearLayout.LayoutParams(dp(42), dp(40)).apply { marginStart = dp(12) })
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
        remainingTime.text = remainingTimeLabel(currentMs, durationMs)
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

    private fun showPlayerSettings() {
        val quality = qualityLabel(currentQuality.ifBlank { "auto" })
        ModernDialogs.showChoices(
            activity,
            palette,
            "Настройки видео",
            listOf(
                "Качество • $quality",
                "Смотреть сначала"
            ),
            -1
        ) { which ->
            when (which) {
                0 -> showQualityPicker()
                1 -> {
                    seekTo(0L)
                    currentMs = 0L
                    currentTime.text = formatMs(0L)
                    remainingTime.text = remainingTimeLabel(0L, durationMs)
                    seekBar.setProgress(0L, durationMs, bufferedMs)
                }
            }
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
        remainingTime.text = remainingTimeLabel(target, durationMs)
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
        if (activity.resources.configuration.smallestScreenWidthDp < 600) {
            activity.requestedOrientation =
                if (enabled) {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
        }
        if (::fullscreenButton.isInitialized) {
            fullscreenButton.setImageResource(
                if (enabled) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen
            )
        }
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

    fun isPlayingForPictureInPicture(): Boolean =
        !destroyed && ready && !paused

    fun prepareForPictureInPicture(): Boolean {
        if (destroyed) return false
        if (pipMode) return true

        if (customView != null || fullscreen) {
            exitFullscreen()
        }

        val parent = playerCard.parent as? ViewGroup ?: return false
        pipOriginalParent = parent
        pipOriginalIndex = parent.indexOfChild(playerCard)
        pipOriginalLayoutParams = playerCard.layoutParams

        parent.removeView(playerCard)

        val activityContent = activity.findViewById<ViewGroup>(android.R.id.content)
        val host = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            clipChildren = true
            clipToPadding = true
            elevation = dp(220).toFloat()
        }
        activityContent.addView(
            host,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        root.visibility = View.INVISIBLE
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
        playerCard.background = rounded(Color.BLACK, 0)
        controlsCard.visibility = View.GONE
        return true
    }

    fun restoreFromPictureInPicture() {
        if (!pipMode) return

        val host = pipHost
        runCatching { host?.removeView(playerCard) }
        runCatching { (host?.parent as? ViewGroup)?.removeView(host) }

        val parent = pipOriginalParent
        if (parent != null && playerCard.parent == null) {
            val params = pipOriginalLayoutParams ?: normalPlayerLayoutParams()
            val index = pipOriginalIndex.coerceIn(0, parent.childCount)
            parent.addView(playerCard, index, params)
        }

        pipHost = null
        pipOriginalParent = null
        pipOriginalIndex = -1
        pipOriginalLayoutParams = null
        pipMode = false

        playerCard.background = rounded(Color.BLACK, 18)
        playerCard.clipToOutline = true
        controlsCard.visibility = View.VISIBLE
        root.visibility = View.VISIBLE
        root.requestLayout()
        playerCard.requestLayout()
    }

    fun destroy() {
        if (destroyed) return
        runCatching { if (pipMode || pipHost != null) restoreFromPictureInPicture() }
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

    private fun normalPlayerLayoutParams(): LinearLayout.LayoutParams {
        val horizontalMargins = dp(20)
        val availableWidth =
            (activity.resources.displayMetrics.widthPixels - horizontalMargins).coerceAtLeast(dp(240))
        val height = (availableWidth * 9f / 16f).toInt()
        return LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height).apply {
            marginStart = dp(10)
            marginEnd = dp(10)
            topMargin = 0
        }
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
            val inset = when {
                size <= 36 -> 8
                size <= 42 -> 9
                else -> 11
            }
            setPadding(dp(inset), dp(inset), dp(inset), dp(inset))
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

    private fun remainingTimeLabel(positionMs: Long, durationMs: Long): String =
        "До конца " + formatMs((durationMs - positionMs).coerceAtLeast(0L))

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
