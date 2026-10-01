package com.unknokable.videosohranenki

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
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

class YouTubePlayerScreen(
    private val activity: Activity,
    private val video: YouTubeFeedVideo,
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
    private var destroyed = false

    val isFullscreen: Boolean
        get() = customView != null

    init {
        root.setBackgroundColor(palette.background)
        content.orientation = LinearLayout.VERTICAL
        content.setBackgroundColor(palette.background)

        content.addView(buildHeader())

        val playerCard = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            background = rounded(Color.BLACK, 18)
            clipToOutline = true
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
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
                exitFullscreen()
            }
        }

        playerCard.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val screenWidth = activity.resources.displayMetrics.widthPixels
        val side = dp(16)
        val playerWidth = (screenWidth - side * 2).coerceAtLeast(dp(240))
        content.addView(
            playerCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (playerWidth * 9f / 16f).toInt()
            ).apply {
                marginStart = side
                marginEnd = side
                topMargin = dp(4)
            }
        )

        content.addView(buildDetails())

        root.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val embedUrl = Uri.parse("https://www.youtube.com/embed/${video.videoId}")
            .buildUpon()
            .appendQueryParameter("autoplay", "1")
            .appendQueryParameter("playsinline", "1")
            .appendQueryParameter("rel", "0")
            .appendQueryParameter("enablejsapi", "1")
            .build()
            .toString()

        webView.loadUrl(embedUrl)
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

            val titleBox = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
            titleBox.addView(TextView(activity).apply {
                text = "YouTube"
                textSize = 15.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.text)
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

            addView(TextView(activity).apply {
                text = "Открыть"
                textSize = 11f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.accent)
                background = rounded(palette.surfaceAlt, 14)
                setPadding(dp(10), 0, dp(10), 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    SohrMotion.press(this, animationsEnabled)
                    runCatching {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.watchUrl)))
                    }
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)))
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
                text = "Видео воспроизводится через официальный YouTube-плеер внутри SOHR"
                textSize = 11f
                setTextColor(palette.muted)
                includeFontPadding = false
                setPadding(0, dp(10), 0, 0)
                alpha = 0.82f
            })
        }

    fun exitFullscreen() {
        val view = customView ?: return
        root.removeView(view)
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        content.visibility = View.VISIBLE
        onFullscreen(false)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        runCatching { if (customView != null) exitFullscreen() }
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.destroy()
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
        }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
