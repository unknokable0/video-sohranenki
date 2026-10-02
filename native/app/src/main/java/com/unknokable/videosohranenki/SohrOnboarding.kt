package com.unknokable.videosohranenki

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager

class SohrOnboarding(
    private val activity: Activity,
    private val settings: AppSettings,
    private val onFinished: () -> Unit
) {
    private data class Page(
        val title: String,
        val description: String
    )

    val root = FrameLayout(activity)

    private val pages = listOf(
        Page(
            "Добро пожаловать в SOHR",
            "Видео, источники и прогресс — в одном спокойном интерфейсе."
        ),
        Page(
            "Лента без лишнего",
            "Чёткие превью, понятные даты и быстрый доступ к выбранным каналам."
        ),
        Page(
            "Удобный просмотр",
            "Telegram и Twitch — внутри SOHR. YouTube открывается сразу на нужном ролике."
        ),
        Page(
            "Streak и прогресс",
            "Просмотр по дням и аккуратный прогресс без перегруженных экранов."
        ),
        Page(
            "Персонализация SOHR",
            "Темы, акцент, уведомления и обновления настраиваются в одном месте."
        )
    )

    private lateinit var pager: ViewPager
    private lateinit var artwork: SohrIntroArtworkView
    private lateinit var dots: SohrIntroDotsView
    private lateinit var nextButton: TextView
    private lateinit var skipButton: TextView
    private lateinit var themeButton: ImageView

    private var currentPage = 0
    private var currentScroll = 0f

    private val ease = PathInterpolator(0.22f, 1f, 0.36f, 1f)

    init {
        build()
    }

    private fun palette(): ThemePalette = settings.palette()

    private fun build() {
        root.removeAllViews()
        root.setBackgroundColor(palette().background)

        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(18))
        }
        root.addView(
            column,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        top.addView(
            View(activity),
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )

        skipButton = TextView(activity).apply {
            text = "Пропустить"
            textSize = 13f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(palette().muted)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener {
                press(this)
                onFinished()
            }
        }
        top.addView(
            skipButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(40)
            ).apply { marginEnd = dp(8) }
        )

        themeButton = ImageView(activity).apply {
            setImageResource(
                if (settings.lightTheme) R.drawable.ic_theme_moon
                else R.drawable.ic_theme_sun
            )
            imageTintList = android.content.res.ColorStateList.valueOf(palette().text)
            background = circleDrawable(palette().surfaceAlt)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            contentDescription = "Сменить тему"
            setOnClickListener {
                press(this)
                val page = currentPage
                val scroll = currentScroll
                SohrThemeTransition.reveal(
                    host = root,
                    anchor = this,
                    enabled = settings.animations
                ) {
                    settings.lightTheme = !settings.lightTheme
                    build()
                    pager.setCurrentItem(page, false)
                    artwork.setPageProgress(scroll)
                    dots.setPageProgress(scroll)
                }
            }
        }
        top.addView(themeButton, LinearLayout.LayoutParams(dp(40), dp(40)))
        column.addView(top)

        artwork = SohrIntroArtworkView(activity).apply {
            setPalette(palette())
            setPageProgress(0f)
        }
        column.addView(
            artwork,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(228)
            ).apply {
                topMargin = dp(14)
            }
        )

        pager = ViewPager(activity).apply {
            id = View.generateViewId()
            offscreenPageLimit = 1
            pageMargin = 0
            adapter = TextPagerAdapter()
            setPageTransformer(false) { page, position ->
                val abs = kotlin.math.abs(position).coerceIn(0f, 1f)
                page.alpha = 1f - abs * 0.22f
                page.translationX = -position * dp(10)
                page.scaleX = 1f - abs * 0.018f
                page.scaleY = 1f - abs * 0.018f
            }
            addOnPageChangeListener(
                object : ViewPager.OnPageChangeListener {
                    override fun onPageScrolled(
                        position: Int,
                        positionOffset: Float,
                        positionOffsetPixels: Int
                    ) {
                        val progress =
                            (position + positionOffset)
                                .coerceIn(0f, (pages.size - 1).toFloat())
                        currentScroll = progress
                        artwork.setPageProgress(progress)
                        dots.setPageProgress(progress)
                    }

                    override fun onPageSelected(position: Int) {
                        currentPage = position
                        updateBottomChrome(position)
                    }

                    override fun onPageScrollStateChanged(state: Int) = Unit
                }
            )
        }
        column.addView(
            pager,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(166)
            )
        )

        dots = SohrIntroDotsView(activity).apply {
            setPalette(
                active = palette().accent,
                inactive = palette().muted
            )
            setCount(pages.size)
            setPageProgress(0f)
        }
        column.addView(
            dots,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(26)
            ).apply {
                topMargin = dp(2)
            }
        )

        column.addView(
            View(activity),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        nextButton = TextView(activity).apply {
            text = "Дальше"
            textSize = 15f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = roundedDrawable(palette().accent, 18)
            setOnClickListener {
                press(this)
                if (currentPage == pages.lastIndex) {
                    onFinished()
                } else {
                    pager.setCurrentItem(currentPage + 1, true)
                }
            }
        }
        column.addView(
            nextButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
            )
        )

        updateBottomChrome(0)

        if (settings.animations) {
            artwork.alpha = 0f
            artwork.scaleX = 0.94f
            artwork.scaleY = 0.94f
            pager.alpha = 0f
            pager.translationY = dp(8).toFloat()
            dots.alpha = 0f
            nextButton.alpha = 0f
            nextButton.translationY = dp(8).toFloat()

            artwork.post {
                artwork.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(360L)
                    .setInterpolator(ease)
                    .start()
                pager.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(95L)
                    .setDuration(280L)
                    .setInterpolator(ease)
                    .start()
                dots.animate()
                    .alpha(1f)
                    .setStartDelay(145L)
                    .setDuration(220L)
                    .start()
                nextButton.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(185L)
                    .setDuration(280L)
                    .setInterpolator(ease)
                    .start()
            }
        }
    }

    private inner class TextPagerAdapter : PagerAdapter() {
        override fun getCount(): Int = pages.size

        override fun isViewFromObject(view: View, obj: Any): Boolean =
            view === obj

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val page = pages[position]

            val content = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setPadding(dp(12), dp(4), dp(12), 0)
            }

            val title = TextView(activity).apply {
                text = page.title
                textSize = 27f
                gravity = Gravity.CENTER
                maxLines = 2
                includeFontPadding = false
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette().text)
            }

            val description = TextView(activity).apply {
                text = page.description
                textSize = 14.2f
                gravity = Gravity.CENTER
                maxLines = 3
                includeFontPadding = false
                setLineSpacing(dp(2).toFloat(), 1.04f)
                setTextColor(palette().muted)
                setPadding(dp(8), dp(12), dp(8), 0)
            }

            content.addView(
                title,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(72)
                )
            )
            content.addView(
                description,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(86)
                )
            )

            container.addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            return content
        }

        override fun destroyItem(
            container: ViewGroup,
            position: Int,
            obj: Any
        ) {
            container.removeView(obj as View)
        }
    }

    private fun updateBottomChrome(position: Int) {
        if (::nextButton.isInitialized) {
            nextButton.text =
                if (position == pages.lastIndex) "Начать" else "Дальше"
        }
        if (::skipButton.isInitialized) {
            skipButton.visibility =
                if (position == pages.lastIndex) View.INVISIBLE
                else View.VISIBLE
        }
    }

    private fun press(view: View) {
        if (!settings.animations) return
        view.animate().cancel()
        view.animate()
            .scaleX(0.965f)
            .scaleY(0.965f)
            .setDuration(55L)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(145L)
                    .setInterpolator(ease)
                    .start()
            }
            .start()
    }

    private fun roundedDrawable(
        color: Int,
        radiusDp: Int
    ): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
        }

    private fun circleDrawable(
        color: Int
    ): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
        }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
