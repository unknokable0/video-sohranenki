package com.unknokable.videosohranenki

import android.app.Activity
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
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * SOHR-native YouTube viewing screen.
 *
 * YouTube is used only as the playback engine. Its controls are disabled and
 * all visible playback UI belongs to SOHR.
 */
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
    private val content = LinearLayout(activity)
    private val playerCard = FrameLayout(activity)
    private val webView = WebView(activity)
    private val controlsOverlay = FrameLayout(activity)
    private val gestureLayer = View(activity)

    private lateinit var playPause: ImageButton
    private lateinit var seekBar: SohrTimeBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var remainingTime: TextView
    private lateinit var fullscreenButton: ImageButton
    private lateinit var feedback: TextView
    private lateinit var fineScrubHint: TextView

    private var fullscreen = false
    private var destroyed = false
    private var ready = false
    private var playing = false
    private var durationMs = 0L
    private var currentMs = 0L
    private var bufferedMs = 0L
    private var dragging = false
    private var controlsVisible = true
    private var temporarySpeed = false

    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var moved = false
    private var lastTapAt = 0L
    private var lastTapX = 0f

    private val autoHide = Runnable {
        if (playing && !dragging) setControlsVisible(false)
    }

    private val longPress = Runnable {
        if (destroyed || moved || !playing) return@Runnable
        temporarySpeed = true
        gestureLayer.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        js("window.sohrSetRate && window.sohrSetRate(2.0)")
        showFeedback("2×")
    }

    val isFullscreen: Boolean
        get() = fullscreen

    init {
        root.setBackgroundColor(palette.background)
        content.orientation = LinearLayout.VERTICAL
        content.setBackgroundColor(palette.background)

        content.addView(buildHeader())
        configurePlayerCard()
        content.addView(playerCard, normalPlayerLayoutParams())
        content.addView(buildDetails())

        root.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        loadYouTubeEngine()
    }

    private fun configurePlayerCard() {
        playerCard.setBackgroundColor(Color.BLACK)
        playerCard.background = rounded(Color.BLACK, 18)
        playerCard.clipToOutline = true

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
        }
        webView.addJavascriptInterface(YouTubeBridge(), "SOHR")
        webView.webViewClient = object : WebViewClient() {}

        playerCard.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        gestureLayer.setBackgroundColor(Color.TRANSPARENT)
        gestureLayer.isClickable = true
        gestureLayer.isFocusable = true
        gestureLayer.setOnTouchListener { v, event -> handlePlayerGesture(v, event) }
        playerCard.addView(
            gestureLayer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        controlsOverlay.setBackgroundColor(Color.parseColor("#18000000"))
        playerCard.addView(
            controlsOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        buildControlsInto(controlsOverlay)
    }

    private fun buildControlsInto(frame: FrameLayout) {
        playPause = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_play)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(13), dp(13), dp(13), dp(13))
            background = rounded(Color.parseColor("#A3121019"), 25)
            contentDescription = "Воспроизведение"
            setOnClickListener {
                if (!ready) return@setOnClickListener
                SohrMotion.press(this, animationsEnabled)
                if (playing) pause() else play()
                showControls()
            }
        }
        frame.addView(
            playPause,
            FrameLayout.LayoutParams(dp(50), dp(50), Gravity.CENTER)
        )

        feedback = TextView(activity).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(Color.parseColor("#C616131E"), 16)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            visibility = View.GONE
            alpha = 0f
        }
        frame.addView(
            feedback,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                topMargin = dp(78)
            }
        )

        fineScrubHint = TextView(activity).apply {
            text = "Точная перемотка"
            textSize = 10.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(withAlpha(palette.accent, 190), 12)
            setPadding(dp(9), dp(5), dp(9), dp(5))
            visibility = View.GONE
        }
        frame.addView(
            fineScrubHint,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = dp(10)
            }
        )

        val bottom = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(2), dp(5), dp(3))
            background = rounded(Color.parseColor("#52100D17"), 10)
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
                    remainingTime.text = remainingLabel(positionMs)
                    fineScrubHint.text = "Точная перемотка • " + formatMs(positionMs)
                }

                override fun onFineScrubMode(enabled: Boolean, positionMs: Long, fraction: Float) {
                    fineScrubHint.visibility = if (enabled) View.VISIBLE else View.GONE
                    if (enabled) fineScrubHint.text = "Точная перемотка • " + formatMs(positionMs)
                }

                override fun onScrubStop(positionMs: Long, canceled: Boolean) {
                    dragging = false
                    fineScrubHint.visibility = View.GONE
                    if (!canceled) seekTo(positionMs)
                    scheduleAutoHide()
                }
            }
        }

        val times = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        currentTime = timeLabel("0:00")
        totalTime = timeLabel("0:00")
        remainingTime = TextView(activity).apply {
            text = "Осталось 0:00"
            textSize = 9.6f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(withAlpha(palette.accent, 58), 10)
            setPadding(dp(8), 0, dp(8), 0)
        }

        fullscreenButton = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_fullscreen)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = rounded(Color.parseColor("#44221A30"), 10)
            contentDescription = "Полный экран"
            setOnClickListener {
                SohrMotion.press(this, animationsEnabled)
                if (fullscreen) exitFullscreen() else enterFullscreen()
            }
        }

        times.addView(currentTime, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(31)))
        times.addView(TextView(activity).apply {
            text = "/"
            textSize = 10f
            setTextColor(Color.parseColor("#B7B6C0"))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(12), dp(31)))
        times.addView(totalTime, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(31)))
        times.addView(
            remainingTime,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(24)).apply {
                marginStart = dp(7)
            }
        )
        times.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
        times.addView(fullscreenButton, LinearLayout.LayoutParams(dp(34), dp(31)))

        bottom.addView(seekBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)))
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
    }

    private fun loadYouTubeEngine() {
        val safeVideoId = video.videoId.replace(Regex("[^A-Za-z0-9_-]"), "")
        val html = """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
              <style>
                html,body,#player{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden}
                iframe{width:100%!important;height:100%!important;border:0!important;pointer-events:none!important}
              </style>
            </head>
            <body>
              <div id="player"></div>
              <script src="https://www.youtube.com/iframe_api"></script>
              <script>
                var p=null;
                var poll=null;
                function emit(){
                  if(!p || !p.getDuration) return;
                  try{
                    SOHR.onProgress(
                      p.getCurrentTime()||0,
                      p.getDuration()||0,
                      p.getVideoLoadedFraction()||0,
                      p.getPlayerState()||0,
                      p.getPlaybackRate()||1
                    );
                  }catch(e){}
                }
                function onYouTubeIframeAPIReady(){
                  p=new YT.Player('player',{
                    videoId:'$safeVideoId',
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
                      onReady:function(){ SOHR.onReady(p.getDuration()||0); p.playVideo(); emit(); },
                      onStateChange:function(){ emit(); },
                      onError:function(e){ SOHR.onError(e.data||0); }
                    }
                  });
                  poll=setInterval(emit,250);
                }
                window.sohrPlay=function(){ if(p) p.playVideo(); }
                window.sohrPause=function(){ if(p) p.pauseVideo(); }
                window.sohrSeek=function(ms){ if(p) p.seekTo(Math.max(0,ms/1000),true); }
                window.sohrSetRate=function(rate){ if(p) p.setPlaybackRate(rate); }
              </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(
            "https://www.youtube.com",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private inner class YouTubeBridge {
        @JavascriptInterface
        fun onReady(durationSeconds: Double) {
            activity.runOnUiThread {
                if (destroyed) return@runOnUiThread
                ready = true
                durationMs = (durationSeconds * 1000.0).roundToLong().coerceAtLeast(0L)
                totalTime.text = formatMs(durationMs)
                remainingTime.text = remainingLabel(currentMs)
                seekBar.setProgress(currentMs, durationMs, bufferedMs)
                showControls()
            }
        }

        @JavascriptInterface
        fun onProgress(
            currentSeconds: Double,
            durationSeconds: Double,
            loadedFraction: Double,
            state: Int,
            playbackRate: Double
        ) {
            activity.runOnUiThread {
                if (destroyed) return@runOnUiThread
                currentMs = (currentSeconds * 1000.0).roundToLong().coerceAtLeast(0L)
                durationMs = (durationSeconds * 1000.0).roundToLong().coerceAtLeast(durationMs)
                bufferedMs = (durationMs * loadedFraction.coerceIn(0.0, 1.0)).roundToLong()
                playing = state == 1

                if (!dragging) {
                    currentTime.text = formatMs(currentMs)
                    remainingTime.text = remainingLabel(currentMs)
                    seekBar.setProgress(currentMs, durationMs, bufferedMs)
                }
                totalTime.text = formatMs(durationMs)
                updatePlayIcon()
                if (playing && controlsVisible) scheduleAutoHide()
            }
        }

        @JavascriptInterface
        fun onError(code: Int) {
            activity.runOnUiThread {
                if (!destroyed) showFeedback("Не удалось запустить видео")
            }
        }
    }

    private fun handlePlayerGesture(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downAt = SystemClock.uptimeMillis()
                moved = false
                v.postDelayed(longPress, 420L)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) > dp(18).toFloat() || abs(dy) > dp(18).toFloat()) {
                    moved = true
                    v.removeCallbacks(longPress)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.removeCallbacks(longPress)
                if (temporarySpeed) {
                    temporarySpeed = false
                    js("window.sohrSetRate && window.sohrSetRate(1.0)")
                    feedback.visibility = View.GONE
                }
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) return true

                val dx = event.x - downX
                val dy = event.y - downY

                if (abs(dy) > dp(86) && abs(dy) > abs(dx) * 1.25f) {
                    if (dy < 0f && !fullscreen) enterFullscreen()
                    else if (dy > 0f && fullscreen) exitFullscreen()
                    return true
                }

                if (!moved && SystemClock.uptimeMillis() - downAt < 420L) {
                    val now = SystemClock.uptimeMillis()
                    if (
                        now - lastTapAt <= 320L &&
                        abs(event.x - lastTapX) < playerCard.width * 0.35f
                    ) {
                        lastTapAt = 0L
                        val forward = event.x >= playerCard.width / 2f
                        val target = (currentMs + if (forward) 10_000L else -10_000L)
                            .coerceIn(0L, durationMs.coerceAtLeast(1L))
                        seekTo(target)
                        gestureLayer.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        showFeedback(if (forward) "+10 сек" else "−10 сек")
                    } else {
                        lastTapAt = now
                        lastTapX = event.x
                        handler.postDelayed({
                            if (lastTapAt == now) {
                                lastTapAt = 0L
                                if (controlsVisible) setControlsVisible(false) else showControls()
                            }
                        }, 320L)
                    }
                }
                return true
            }
        }
        return true
    }

    private fun buildHeader(): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))

            addView(ImageButton(activity).apply {
                setImageResource(R.drawable.ic_back)
                imageTintList = ColorStateList.valueOf(palette.text)
                background = rounded(palette.surfaceAlt, 22)
                setPadding(dp(11), dp(11), dp(11), dp(11))
                contentDescription = "Назад"
                setOnClickListener {
                    SohrMotion.press(this, animationsEnabled)
                    onBack()
                }
            }, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                marginEnd = dp(10)
            })

            val titleBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            titleBox.addView(TextView(activity).apply {
                text = video.channelTitle
                textSize = 15.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.text)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                includeFontPadding = false
            })
            titleBox.addView(TextView(activity).apply {
                text = video.handle
                textSize = 11f
                setTextColor(palette.muted)
                includeFontPadding = false
                setPadding(0, dp(2), 0, 0)
            })
            addView(titleBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
        }

    fun enterFullscreen() {
        if (fullscreen || destroyed) return
        fullscreen = true
        content.removeView(playerCard)
        content.visibility = View.GONE
        root.addView(
            playerCard,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        playerCard.clipToOutline = false
        fullscreenButton.setImageResource(R.drawable.ic_fullscreen_exit)
        onFullscreen(true)
        showControls()
    }

    fun exitFullscreen() {
        if (!fullscreen || destroyed) return
        root.removeView(playerCard)
        content.visibility = View.VISIBLE
        content.addView(playerCard, 1, normalPlayerLayoutParams())
        playerCard.clipToOutline = true
        fullscreen = false
        fullscreenButton.setImageResource(R.drawable.ic_fullscreen)
        onFullscreen(false)
        showControls()
    }

    private fun normalPlayerLayoutParams(): LinearLayout.LayoutParams {
        val width = activity.resources.displayMetrics.widthPixels
        val side = dp(16)
        val playerWidth = (width - side * 2).coerceAtLeast(dp(240))
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (playerWidth * 9f / 16f).toInt()
        ).apply {
            marginStart = side
            marginEnd = side
            topMargin = dp(4)
        }
    }

    private fun play() {
        js("window.sohrPlay && window.sohrPlay()")
    }

    private fun pause() {
        js("window.sohrPause && window.sohrPause()")
    }

    private fun seekTo(positionMs: Long) {
        val safe = positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L))
        currentMs = safe
        currentTime.text = formatMs(safe)
        remainingTime.text = remainingLabel(safe)
        seekBar.setProgress(safe, durationMs, bufferedMs)
        js("window.sohrSeek && window.sohrSeek($safe)")
        showControls()
    }

    private fun js(script: String) {
        if (destroyed) return
        webView.evaluateJavascript(script, null)
    }

    private fun updatePlayIcon() {
        playPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun showControls() {
        setControlsVisible(true)
        scheduleAutoHide()
    }

    private fun setControlsVisible(visible: Boolean) {
        controlsVisible = visible
        handler.removeCallbacks(autoHide)
        controlsOverlay.animate().cancel()
        if (animationsEnabled) {
            controlsOverlay.animate()
                .alpha(if (visible) 1f else 0f)
                .setDuration(if (visible) 130L else 170L)
                .withStartAction {
                    if (visible) controlsOverlay.visibility = View.VISIBLE
                }
                .withEndAction {
                    if (!visible) controlsOverlay.visibility = View.INVISIBLE
                }
                .start()
        } else {
            controlsOverlay.alpha = if (visible) 1f else 0f
            controlsOverlay.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        }
    }

    private fun scheduleAutoHide() {
        handler.removeCallbacks(autoHide)
        if (playing && !dragging) handler.postDelayed(autoHide, 2600L)
    }

    private fun showFeedback(text: String) {
        feedback.animate().cancel()
        feedback.text = text
        feedback.visibility = View.VISIBLE
        feedback.alpha = 1f
        handler.postDelayed({
            if (!destroyed) {
                feedback.animate()
                    .alpha(0f)
                    .setDuration(130L)
                    .withEndAction { feedback.visibility = View.GONE }
                    .start()
            }
        }, 700L)
    }

    private fun timeLabel(value: String): TextView =
        TextView(activity).apply {
            text = value
            textSize = 10.5f
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }

    private fun remainingLabel(position: Long): String {
        val remaining = (durationMs - position).coerceAtLeast(0L)
        return "Осталось " + formatMs(remaining)
    }

    private fun formatMs(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) / 1000L)
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (h > 0L) "$h:%02d:%02d".format(m, s) else "$m:%02d".format(s)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        if (fullscreen) {
            runCatching {
                root.removeView(playerCard)
                content.visibility = View.VISIBLE
                content.addView(playerCard, 1, normalPlayerLayoutParams())
            }
            fullscreen = false
            onFullscreen(false)
        }
        webView.removeJavascriptInterface("SOHR")
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.webViewClient = WebViewClient()
        webView.destroy()
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
        }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
