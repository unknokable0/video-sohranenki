package com.unknokable.videosohranenki

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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

class TwitchPlayerScreen(
    private val activity: Activity,
    private val item: VideoItem,
    private val videoId: String,
    private val palette: ThemePalette,
    private val animationsEnabled: Boolean,
    private val onBack: () -> Unit,
    private val onFullscreen: (Boolean) -> Unit
) {
    val root = FrameLayout(activity)
    private val content = LinearLayout(activity)
    private val webView = WebView(activity)
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreen = false

    val isFullscreen: Boolean get() = fullscreen

    init {
        root.setBackgroundColor(Color.BLACK)

        content.orientation = LinearLayout.VERTICAL
        content.setBackgroundColor(palette.background)

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(9))
            setBackgroundColor(palette.background)
        }

        val back = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_back)
            imageTintList = ColorStateList.valueOf(palette.text)
            background = rounded(palette.surfaceAlt, 22)
            setPadding(dp(11), dp(11), dp(11), dp(11))
            contentDescription = "Назад"
            setOnClickListener {
                if (animationsEnabled) {
                    animate().cancel()
                    animate().scaleX(0.94f).scaleY(0.94f).setDuration(55L).withEndAction {
                        animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
                    }.start()
                }
                onBack()
            }
        }

        val titles = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        titles.addView(TextView(activity).apply {
            text = "Twitch • @t2x2"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })
        titles.addView(TextView(activity).apply {
            text = item.title
            textSize = 11.5f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(palette.muted)
            setPadding(0, dp(2), 0, 0)
        })

        header.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(header)

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
                fullscreen = true
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
                exitFullscreen()
            }
        }

        content.addView(
            webView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        root.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val url = Uri.parse("https://unknokable0.github.io/video-sohranenki/twitch-player/")
            .buildUpon()
            .appendQueryParameter("video", videoId)
            .build()
            .toString()
        webView.loadUrl(url)

        if (animationsEnabled) {
            webView.alpha = 0f
            webView.post {
                webView.animate()
                    .alpha(1f)
                    .setDuration(220L)
                    .start()
            }
        }
    }

    fun exitFullscreen() {
        val view = customView
        if (view != null) {
            root.removeView(view)
            customView = null
            content.visibility = View.VISIBLE
            customViewCallback?.onCustomViewHidden()
            customViewCallback = null
        }
        if (fullscreen) {
            fullscreen = false
            onFullscreen(false)
        }
    }

    fun destroy() {
        exitFullscreen()
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        webView.removeAllViews()
        webView.destroy()
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
