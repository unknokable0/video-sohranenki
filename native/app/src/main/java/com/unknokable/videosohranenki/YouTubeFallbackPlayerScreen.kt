package com.unknokable.videosohranenki

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Official YouTube embedded-player fallback used only when the native stream
 * resolver cannot obtain a direct media URL. The YouTube player itself remains
 * untouched so YouTube can enforce its own playback, account and age rules.
 */
class YouTubeFallbackPlayerScreen(
    private val activity: Activity,
    private val video: YouTubeFeedVideo,
    private val settings: AppSettings,
    private val onBack: () -> Unit
) {
    val root: FrameLayout = FrameLayout(activity)

    private val palette get() = settings.palette()
    private var webView: WebView? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var playerHost: FrameLayout? = null
    private var chromeHost: LinearLayout? = null

    init {
        build()
    }

    @Suppress("SetJavaScriptEnabled")
    private fun build() {
        root.setBackgroundColor(palette.background)

        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(18))
        }
        chromeHost = page

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_back)
            imageTintList = android.content.res.ColorStateList.valueOf(palette.text)
            background = rounded(palette.surfaceAlt, 18)
            setPadding(dp(11), dp(11), dp(11), dp(11))
            contentDescription = "Назад"
            setOnClickListener { onBack() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))

        header.addView(
            TextView(activity).apply {
                text = video.title
                textSize = 16.5f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                includeFontPadding = false
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(palette.text)
                setPadding(dp(12), 0, dp(4), 0)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        page.addView(header)

        val host = FrameLayout(activity).apply {
            background = rounded(Color.BLACK, 20)
            clipToOutline = true
        }
        playerHost = host

        val browser = WebView(activity).apply {
            setBackgroundColor(Color.BLACK)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean = false

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame != true) return
                    showLocalError()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(
                    view: View?,
                    callback: CustomViewCallback?
                ) {
                    if (view == null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    if (customView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }

                    customView = view
                    customViewCallback = callback
                    chromeHost?.visibility = View.GONE
                    root.addView(
                        view,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    activity.window.decorView.systemUiVisibility =
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                }

                override fun onHideCustomView() {
                    exitCustomView()
                }
            }
        }
        webView = browser
        host.addView(
            browser,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val availableWidth =
            (activity.resources.displayMetrics.widthPixels - dp(28)).coerceAtLeast(dp(240))
        val playerHeight = (availableWidth * 9f / 16f).toInt()
        page.addView(
            host,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                playerHeight
            ).apply { topMargin = dp(14) }
        )

        page.addView(
            TextView(activity).apply {
                text = video.channelTitle
                textSize = 12.5f
                setTextColor(palette.muted)
                includeFontPadding = false
                setPadding(dp(4), dp(10), dp(4), 0)
            }
        )

        root.addView(
            page,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val embedUrl =
            "https://www.youtube.com/embed/${video.videoId}" +
                "?autoplay=1&playsinline=1&controls=1&fs=1&rel=0"

        browser.loadUrl(
            embedUrl,
            mapOf(
                "Referer" to "https://unknokable0.github.io/video-sohranenki/"
            )
        )
    }

    /**
     * @return true when Back was consumed by fullscreen/history; false when the
     * Activity should close the player screen itself.
     */
    fun handleBack(): Boolean {
        if (customView != null) {
            exitCustomView()
            return true
        }
        val browser = webView
        if (browser?.canGoBack() == true) {
            browser.goBack()
            return true
        }
        return false
    }

    fun destroy() {
        exitCustomView()
        val browser = webView
        webView = null
        runCatching {
            browser?.stopLoading()
            browser?.loadUrl("about:blank")
            browser?.clearHistory()
            browser?.removeAllViews()
            browser?.destroy()
        }
        playerHost = null
        chromeHost = null
    }

    private fun exitCustomView() {
        val view = customView ?: return
        runCatching { root.removeView(view) }
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        chromeHost?.visibility = View.VISIBLE
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun showLocalError() {
        val host = playerHost ?: return
        host.removeAllViews()
        host.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(22), dp(20), dp(22), dp(20))
                addView(TextView(activity).apply {
                    text = "YouTube не смог воспроизвести видео"
                    textSize = 17f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(Color.WHITE)
                })
                addView(TextView(activity).apply {
                    text = "Если сам YouTube ограничил встроенное воспроизведение, SOHR не обходит это ограничение."
                    textSize = 12.5f
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#B7B5C4"))
                    setPadding(0, dp(8), 0, 0)
                })
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * activity.resources.displayMetrics.density
            setColor(color)
        }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
