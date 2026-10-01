package com.unknokable.videosohranenki

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONTokener
import kotlin.math.abs

class YouTubePlayerScreen(
    private val activity: Activity,
    private val video: YouTubeFeedVideo,
    private val palette: ThemePalette,
    private val animationsEnabled: Boolean,
    private val onBack: () -> Unit,
    private val onFullscreen: (Boolean) -> Unit
) {
    val root = FrameLayout(activity)

    private val handler = Handler(Looper.getMainLooper())
    private val page = LinearLayout(activity)
    private val playerCard = FrameLayout(activity)
    private val webView = WebView(activity)
    private val overlay = FrameLayout(activity)
    private val gestureSurface = View(activity)
    private lateinit var playPause: ImageButton
    private lateinit var seekBar: SohrTimeBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var remainingTime: TextView
    private lateinit var fullscreenButton: ImageButton
    private lateinit var seekFeedback: TextView
    private lateinit var fineLabel: TextView

    private var destroyed = false
    private var fullscreen = false
    private var fullscreenHost: FrameLayout? = null
    private var playerCardOriginalIndex = -1
    private var playerCardOriginalParams: LinearLayout.LayoutParams? = null
    private var controlsVisible = true
    private var dragging = false
    private var ready = false
    private var playing = false
    private var currentMs = 0L
    private var durationMs = 0L
    private var bufferedMs = 0L

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownAt = 0L
    private var touchMoved = false
    private var lastTapAt = 0L
    private var lastTapX = 0f
    private var longPressActive = false

    private val autoHide = Runnable {
        if (playing && !dragging) hideControls()
    }

    private val progressPoll = object : Runnable {
        override fun run() {
            if (destroyed) return
            pollYouTubeState()
            handler.postDelayed(this, 350L)
        }
    }

    private val longPressRunnable = Runnable {
        if (touchMoved || destroyed) return@Runnable
        longPressActive = true
        gestureSurface.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        setPlaybackRate(2.0)
        showSeekFeedback("2×")
    }

    val isFullscreen: Boolean
        get() = fullscreen

    init {
        root.setBackgroundColor(palette.background)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(palette.background)

        page.addView(buildHeader())

        playerCard.setBackgroundColor(Color.BLACK)
        playerCard.background = rounded(Color.BLACK, 18)
        playerCard.clipToOutline = true

        configureWebView()
        playerCard.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        playerCard.addView(
            gestureSurface,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        installGestures()

        buildOverlay()
        playerCard.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        seekFeedback = TextView(activity).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = rounded(Color.parseColor("#D91A1720"), 14)
            visibility = View.GONE
            alpha = 0f
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

        val screenWidth = activity.resources.displayMetrics.widthPixels
        val side = dp(16)
        val playerWidth = (screenWidth - side * 2).coerceAtLeast(dp(240))
        page.addView(
            playerCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (playerWidth * 9f / 16f).toInt()
            ).apply {
                marginStart = side
                marginEnd = side
                topMargin = dp(2)
            }
        )

        page.addView(buildDetails())

        root.addView(
            page,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        webView.loadDataWithBaseURL(
            "https://www.youtube.com/",
            playerHtml(video.videoId),
            "text/html",
            "UTF-8",
            null
        )

        handler.post(progressPoll)
        showControls()
    }

    private fun configureWebView() {
        webView.setBackgroundColor(Color.BLACK)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            builtInZoomControls = false
            displayZoomControls = false
        }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
    }

    private fun playerHtml(videoId: String): String = """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
          <style>
            html,body,#player{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden}
            iframe{width:100%!important;height:100%!important;border:0!important}
          </style>
        </head>
        <body>
          <div id="player"></div>
          <script src="https://www.youtube.com/iframe_api"></script>
          <script>
            var player = null;
            function onYouTubeIframeAPIReady(){
              player = new YT.Player('player',{
                videoId:'$videoId',
                width:'100%',
                height:'100%',
                playerVars:{
                  autoplay:1,
                  controls:0,
                  disablekb:1,
                  fs:0,
                  playsinline:1,
                  rel:0,
                  iv_load_policy:3,
                  modestbranding:1
                },
                events:{
                  onReady:function(e){ try{e.target.playVideo();}catch(_){} }
                }
              });
            }
            function sohrPlay(){ try{player.playVideo();}catch(_){} }
            function sohrPause(){ try{player.pauseVideo();}catch(_){} }
            function sohrSeek(ms){ try{player.seekTo(Math.max(0,ms/1000),true);}catch(_){} }
            function sohrRate(rate){ try{player.setPlaybackRate(rate);}catch(_){} }
            function sohrSnapshot(){
              try{
                if(!player || !player.getDuration) return '';
                var d = Number(player.getDuration()||0);
                var c = Number(player.getCurrentTime()||0);
                var s = Number(player.getPlayerState()||-1);
                var b = Number(player.getVideoLoadedFraction()||0);
                return [c,d,s,b].join('|');
              }catch(_){ return ''; }
            }
          </script>
        </body>
        </html>
    """.trimIndent()

    private fun buildHeader(): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(5), dp(14), dp(5))
            setBackgroundColor(palette.background)

            addView(iconButton(R.drawable.ic_back, 36).apply {
                setOnClickListener {
                    SohrMotion.press(this, animationsEnabled)
                    onBack()
                }
            }, LinearLayout.LayoutParams(dp(44), dp(44)))

            addView(TextView(activity).apply {
                text = video.title
                textSize = 13.5f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                includeFontPadding = false
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.text)
                setPadding(dp(10), 0, dp(10), 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            addView(View(activity), LinearLayout.LayoutParams(dp(44), dp(44)))
        }

    private fun buildOverlay() {
        overlay.setBackgroundColor(Color.parseColor("#12000000"))

        val center = LinearLayout(activity).apply {
            gravity = Gravity.CENTER
        }
        playPause = iconButton(R.drawable.ic_play, 44).apply {
            background = rounded(Color.parseColor("#96100E16"), 22)
            setOnClickListener {
                showControls()
                if (playing) pause() else play()
                SohrMotion.press(this, animationsEnabled)
            }
        }
        center.addView(playPause, LinearLayout.LayoutParams(dp(50), dp(50)))
        overlay.addView(
            center,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        fineLabel = TextView(activity).apply {
            text = "Точная перемотка • по секундам"
            textSize = 10f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(withAlpha(palette.accent, 190), 12)
            setPadding(dp(10), 0, dp(10), 0)
            visibility = View.GONE
            alpha = 0f
        }
        overlay.addView(
            fineLabel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(27),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = dp(54)
            }
        )

        val bottom = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
            background = rounded(Color.parseColor("#260A0810"), 8)
        }

        seekBar = SohrTimeBar(activity, palette.accent).apply {
            listener = object : SohrTimeBar.Listener {
                override fun onScrubStart(positionMs: Long) {
                    dragging = true
                    showControls()
                    currentTime.text = formatMs(positionMs)
                }

                override fun onScrubMove(positionMs: Long, fraction: Float) {
                    currentTime.text = formatMs(positionMs)
                    remainingTime.text = remainingLabel(positionMs, durationMs)
                }

                override fun onFineScrubMode(enabled: Boolean, positionMs: Long, fraction: Float) {
                    if (enabled) showFineLabel() else hideFineLabel()
                    currentTime.text = formatMs(positionMs)
                    remainingTime.text = remainingLabel(positionMs, durationMs)
                }

                override fun onScrubStop(positionMs: Long, canceled: Boolean) {
                    dragging = false
                    hideFineLabel()
                    if (!canceled) seekTo(positionMs)
                    scheduleAutoHide()
                }
            }
        }

        val times = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        currentTime = timeLabel("0:00").apply {
            minWidth = dp(34)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        totalTime = timeLabel("0:00").apply {
            minWidth = dp(38)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(5), 0)
        }
        remainingTime = TextView(activity).apply {
            text = "Осталось 0:00"
            textSize = 9.6f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(withAlpha(palette.accent, 48), 10)
            setPadding(dp(8), 0, dp(8), 0)
        }

        fullscreenButton = iconButton(R.drawable.ic_fullscreen, 28).apply {
            contentDescription = "Полный экран"
            background = rounded(Color.parseColor("#44221A30"), 10)
            setOnClickListener {
                SohrMotion.press(this, animationsEnabled)
                setFullscreenMode(!fullscreen)
            }
        }

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
        times.addView(fullscreenButton, LinearLayout.LayoutParams(dp(36), dp(32)))

        bottom.addView(seekBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)))
        bottom.addView(times)

        overlay.addView(
            bottom,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                leftMargin = dp(7)
                rightMargin = dp(7)
                bottomMargin = dp(4)
            }
        )
    }

    private fun buildDetails(): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(18))

            addView(TextView(activity).apply {
                text = video.title
                textSize = 18f
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.text)
                includeFontPadding = false
            })

            addView(TextView(activity).apply {
                text = video.channelTitle + " • " + video.handle
                textSize = 12f
                setTextColor(palette.muted)
                includeFontPadding = false
                setPadding(0, dp(7), 0, 0)
            })

            addView(TextView(activity).apply {
                text = "SOHR Player"
                textSize = 10.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.accent)
                includeFontPadding = false
                setPadding(0, dp(9), 0, 0)
            })
        }

    private fun installGestures() {
        gestureSurface.isClickable = true
        gestureSurface.isFocusable = true
        gestureSurface.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x
                    touchDownY = event.y
                    touchDownAt = SystemClock.uptimeMillis()
                    touchMoved = false
                    longPressActive = false
                    v.postDelayed(longPressRunnable, 430L)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - touchDownX
                    val dy = event.y - touchDownY
                    if (abs(dx) > dp(18) || abs(dy) > dp(18)) {
                        touchMoved = true
                        if (!longPressActive) v.removeCallbacks(longPressRunnable)
                    }
                    // Horizontal drag over the video never seeks.
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(longPressRunnable)

                    if (longPressActive) {
                        longPressActive = false
                        setPlaybackRate(1.0)
                        scheduleAutoHide()
                        return@setOnTouchListener true
                    }

                    if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        return@setOnTouchListener true
                    }

                    val dx = event.x - touchDownX
                    val dy = event.y - touchDownY
                    if (abs(dy) > dp(86) && abs(dy) > abs(dx) * 1.25f) {
                        if (dy < 0 && !fullscreen) setFullscreenMode(true)
                        else if (dy > 0 && fullscreen) setFullscreenMode(false)
                        return@setOnTouchListener true
                    }

                    if (!touchMoved && SystemClock.uptimeMillis() - touchDownAt < 430L) {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastTapAt <= 320L && abs(event.x - lastTapX) < playerCard.width * 0.35f) {
                            lastTapAt = 0L
                            val forward = event.x >= playerCard.width / 2f
                            seekBy(if (forward) 10_000L else -10_000L)
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showSeekFeedback(if (forward) "+10 сек" else "−10 сек")
                        } else {
                            lastTapAt = now
                            lastTapX = event.x
                            handler.postDelayed({
                                if (lastTapAt == now && !destroyed) {
                                    lastTapAt = 0L
                                    if (controlsVisible) hideControls() else showControls()
                                }
                            }, 320L)
                        }
                    }
                    true
                }

                else -> true
            }
        }
    }

    private fun pollYouTubeState() {
        webView.evaluateJavascript("window.sohrSnapshot ? window.sohrSnapshot() : ''") { raw ->
            if (destroyed) return@evaluateJavascript
            val decoded = runCatching { JSONTokener(raw).nextValue() as? String }
                .getOrNull()
                .orEmpty()
            val parts = decoded.split('|')
            if (parts.size < 4) return@evaluateJavascript

            val currentSeconds = parts[0].toDoubleOrNull() ?: return@evaluateJavascript
            val durationSeconds = parts[1].toDoubleOrNull() ?: 0.0
            val state = parts[2].toIntOrNull() ?: -1
            val loadedFraction = parts[3].toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.0

            currentMs = (currentSeconds * 1000.0).toLong().coerceAtLeast(0L)
            durationMs = (durationSeconds * 1000.0).toLong().coerceAtLeast(0L)
            bufferedMs = (durationMs * loadedFraction).toLong().coerceIn(0L, durationMs.coerceAtLeast(0L))
            ready = durationMs > 0L
            playing = state == 1

            root.keepScreenOn = playing
            playPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)

            if (!dragging && ready) {
                seekBar.setProgress(currentMs, durationMs, bufferedMs)
                currentTime.text = formatMs(currentMs)
                totalTime.text = formatMs(durationMs)
                remainingTime.text = remainingLabel(currentMs, durationMs)
            }
            if (playing) scheduleAutoHide() else handler.removeCallbacks(autoHide)
        }
    }

    private fun play() {
        webView.evaluateJavascript("if(window.sohrPlay){sohrPlay();}", null)
        showControls()
    }

    private fun pause() {
        webView.evaluateJavascript("if(window.sohrPause){sohrPause();}", null)
        showControls()
    }

    private fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        currentMs = target
        webView.evaluateJavascript("if(window.sohrSeek){sohrSeek($target);}", null)
        seekBar.setProgress(target, durationMs, bufferedMs)
        currentTime.text = formatMs(target)
        remainingTime.text = remainingLabel(target, durationMs)
        showControls()
    }

    private fun seekBy(deltaMs: Long) {
        if (!ready) return
        seekTo((currentMs + deltaMs).coerceIn(0L, durationMs))
    }

    private fun setPlaybackRate(rate: Double) {
        webView.evaluateJavascript("if(window.sohrRate){sohrRate($rate);}", null)
    }

    private fun showControls() {
        handler.removeCallbacks(autoHide)
        controlsVisible = true
        overlay.visibility = View.VISIBLE
        overlay.animate().cancel()
        if (animationsEnabled) {
            overlay.alpha = overlay.alpha.coerceAtLeast(0f)
            overlay.animate()
                .alpha(1f)
                .setDuration(130L)
                .setInterpolator(SohrMotion.smooth())
                .start()
        } else {
            overlay.alpha = 1f
        }
        scheduleAutoHide()
    }

    private fun hideControls() {
        if (dragging || !playing) return
        handler.removeCallbacks(autoHide)
        controlsVisible = false
        overlay.animate().cancel()
        if (animationsEnabled) {
            overlay.animate()
                .alpha(0f)
                .setDuration(150L)
                .setInterpolator(SohrMotion.smooth())
                .withEndAction {
                    if (!controlsVisible) overlay.visibility = View.GONE
                }
                .start()
        } else {
            overlay.alpha = 0f
            overlay.visibility = View.GONE
        }
    }

    private fun scheduleAutoHide() {
        handler.removeCallbacks(autoHide)
        if (playing && !dragging) handler.postDelayed(autoHide, 2_200L)
    }

    private fun showFineLabel() {
        fineLabel.visibility = View.VISIBLE
        fineLabel.animate().cancel()
        fineLabel.alpha = 0f
        fineLabel.translationY = dp(4).toFloat()
        fineLabel.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(if (animationsEnabled) 120L else 0L)
            .start()
    }

    private fun hideFineLabel() {
        if (fineLabel.visibility != View.VISIBLE) return
        fineLabel.animate().cancel()
        fineLabel.animate()
            .alpha(0f)
            .translationY(dp(3).toFloat())
            .setDuration(if (animationsEnabled) 90L else 0L)
            .withEndAction { fineLabel.visibility = View.GONE }
            .start()
    }

    private fun showSeekFeedback(text: String) {
        seekFeedback.text = text
        seekFeedback.visibility = View.VISIBLE
        seekFeedback.animate().cancel()
        seekFeedback.alpha = 0f
        seekFeedback.scaleX = 0.94f
        seekFeedback.scaleY = 0.94f
        seekFeedback.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(if (animationsEnabled) 110L else 0L)
            .withEndAction {
                handler.postDelayed({
                    if (destroyed) return@postDelayed
                    seekFeedback.animate()
                        .alpha(0f)
                        .setDuration(if (animationsEnabled) 130L else 0L)
                        .withEndAction { seekFeedback.visibility = View.GONE }
                        .start()
                }, 420L)
            }
            .start()
    }

    fun exitFullscreen() {
        setFullscreenMode(false)
    }

    private fun setFullscreenMode(enabled: Boolean) {
        if (enabled == fullscreen || destroyed) return

        if (activity.resources.configuration.smallestScreenWidthDp < 600) {
            activity.requestedOrientation =
                if (enabled) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        if (enabled) {
            playerCardOriginalIndex = page.indexOfChild(playerCard).takeIf { it >= 0 } ?: 1
            playerCardOriginalParams =
                (playerCard.layoutParams as? LinearLayout.LayoutParams)?.let { LinearLayout.LayoutParams(it) }

            (playerCard.parent as? ViewGroup)?.removeView(playerCard)
            val host = FrameLayout(activity).apply {
                setBackgroundColor(Color.BLACK)
                elevation = dp(100).toFloat()
            }
            val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
            contentRoot.addView(
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
            fullscreenButton.setImageResource(R.drawable.ic_fullscreen_exit)
            fullscreenButton.contentDescription = "Выйти из полного экрана"
            playerCard.clipToOutline = false
            playerCard.background = rounded(Color.BLACK, 0)
            onFullscreen(true)
            showControls()
        } else {
            val host = fullscreenHost
            runCatching { host?.removeView(playerCard) }
            runCatching { (host?.parent as? ViewGroup)?.removeView(host) }

            if (playerCard.parent == null) {
                val width = activity.resources.displayMetrics.widthPixels
                val params = playerCardOriginalParams
                    ?: LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ((width - dp(32)) * 9f / 16f).toInt()
                    ).apply {
                        marginStart = dp(16)
                        marginEnd = dp(16)
                    }
                page.addView(
                    playerCard,
                    playerCardOriginalIndex.coerceIn(0, page.childCount),
                    params
                )
            }

            fullscreenHost = null
            playerCardOriginalIndex = -1
            playerCardOriginalParams = null
            fullscreen = false
            fullscreenButton.setImageResource(R.drawable.ic_fullscreen)
            fullscreenButton.contentDescription = "Полный экран"
            playerCard.clipToOutline = true
            playerCard.background = rounded(Color.BLACK, 18)
            onFullscreen(false)
            showControls()
        }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        root.keepScreenOn = false
        runCatching { if (fullscreen || fullscreenHost != null) setFullscreenMode(false) }
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.destroy()
    }

    private fun iconButton(icon: Int, sizeDp: Int): ImageButton =
        ImageButton(activity).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp((sizeDp * 0.24f).toInt()), dp((sizeDp * 0.24f).toInt()), dp((sizeDp * 0.24f).toInt()), dp((sizeDp * 0.24f).toInt()))
            background = rounded(palette.surfaceAlt, sizeDp / 2)
        }

    private fun timeLabel(value: String): TextView =
        TextView(activity).apply {
            text = value
            textSize = 10.5f
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }

    private fun remainingLabel(positionMs: Long, totalMs: Long): String =
        "Осталось " + formatMs((totalMs - positionMs).coerceAtLeast(0L))

    private fun formatMs(value: Long): String {
        val total = (value.coerceAtLeast(0L) / 1000L)
        val hours = total / 3600L
        val minutes = (total % 3600L) / 60L
        val seconds = total % 60L
        return if (hours > 0L) {
            "$hours:%02d:%02d".format(minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
        }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
