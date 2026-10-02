package com.unknokable.videosohranenki

import android.app.Activity
import android.content.res.ColorStateList
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
        val icon: Int,
        val title: String,
        val description: String
    )

    val root = FrameLayout(activity)

    private val pages = listOf(
        Page(
            R.drawable.ic_intro_library_clean,
            "Всё важное в SOHR",
            "Видео, источники и прогресс собраны в одном месте."
        ),
        Page(
            R.drawable.ic_intro_feed_clean,
            "Лента без лишнего",
            "Новые видео, понятные даты и быстрый доступ к выбранным каналам."
        ),
        Page(
            R.drawable.ic_intro_watch_clean,
            "Удобный просмотр",
            "Telegram и Twitch — внутри SOHR. YouTube открывается на нужном ролике."
        ),
        Page(
            R.drawable.ic_intro_streak_clean,
            "Streak и прогресс",
            "Просмотр по дням помогает видеть привычку без лишних экранов."
        ),
        Page(
            R.drawable.ic_intro_tune_clean,
            "Настройте под себя",
            "Тема, акцент, уведомления и обновления — в одном разделе."
        )
    )

    private lateinit var pager: ViewPager
    private lateinit var dots: SohrIntroDotsView
    private lateinit var nextButton: TextView
    private lateinit var skipButton: TextView

    private var currentPage = 0

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
            setPadding(dp(24), dp(12), dp(24), dp(18))
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
            TextView(activity).apply {
                text = "SOHR"
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                includeFontPadding = false
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(palette().text)
            },
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )

        skipButton = TextView(activity).apply {
            text = "Пропустить"
            textSize = 13f
            includeFontPadding = false
            gravity = Gravity.CENTER
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

        val themeButton = ImageView(activity).apply {
            setImageResource(
                if (settings.lightTheme) R.drawable.ic_theme_moon
                else R.drawable.ic_theme_sun
            )
            imageTintList = ColorStateList.valueOf(palette().text)
            background = circleDrawable(palette().surfaceAlt)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            contentDescription = "Сменить тему"
            setOnClickListener {
                press(this)
                val page = currentPage
                SohrThemeTransition.reveal(
                    host = root,
                    anchor = this,
                    enabled = settings.animations
                ) {
                    settings.lightTheme = !settings.lightTheme
                    build()
                    pager.setCurrentItem(page, false)
                    dots.setPageProgress(page.toFloat())
                }
            }
        }
        top.addView(themeButton, LinearLayout.LayoutParams(dp(40), dp(40)))
        column.addView(top)

        pager = ViewPager(activity).apply {
            id = View.generateViewId()
            offscreenPageLimit = 1
            adapter = IntroPagerAdapter()
            setPageTransformer(false) { page, position ->
                val distance = kotlin.math.abs(position).coerceIn(0f, 1f)
                page.alpha = 1f - distance * 0.38f
                page.translationX = -position * dp(22).toFloat()
                val scale = 1f - distance * 0.025f
                page.scaleX = scale
                page.scaleY = scale
            }
            addOnPageChangeListener(
                object : ViewPager.OnPageChangeListener {
                    override fun onPageScrolled(
                        position: Int,
                        positionOffset: Float,
                        positionOffsetPixels: Int
                    ) {
                        dots.setPageProgress(
                            (position + positionOffset)
                                .coerceIn(0f, (pages.size - 1).toFloat())
                        )
                    }

                    override fun onPageSelected(position: Int) {
                        currentPage = position
                        updateChrome()
                        animateSelectedHero()
                    }

                    override fun onPageScrollStateChanged(state: Int) = Unit
                }
            )
        }
        column.addView(
            pager,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(6)
            }
        )

        dots = SohrIntroDotsView(activity).apply {
            setCount(pages.size)
            setPalette(
                active = palette().accent,
                inactive = palette().muted
            )
            setPageProgress(0f)
        }
        column.addView(
            dots,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(28)
            ).apply {
                bottomMargin = dp(10)
            }
        )

        nextButton = TextView(activity).apply {
            text = "Дальше"
            textSize = 15f
            includeFontPadding = false
            gravity = Gravity.CENTER
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

        updateChrome()

        if (settings.animations) {
            pager.alpha = 0f
            pager.translationY = dp(8).toFloat()
            dots.alpha = 0f
            nextButton.alpha = 0f
            nextButton.translationY = dp(8).toFloat()

            pager.post {
                pager.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300L)
                    .setInterpolator(ease)
                    .start()

                dots.animate()
                    .alpha(1f)
                    .setStartDelay(80L)
                    .setDuration(220L)
                    .start()

                nextButton.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(130L)
                    .setDuration(270L)
                    .setInterpolator(ease)
                    .start()
            }
        }
    }

    private inner class IntroPagerAdapter : PagerAdapter() {
        override fun getCount(): Int = pages.size

        override fun isViewFromObject(view: View, obj: Any): Boolean =
            view === obj

        override fun instantiateItem(
            container: ViewGroup,
            position: Int
        ): Any {
            val page = pages[position]

            val root = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(14), dp(18), dp(14), 0)
            }

            val hero = FrameLayout(activity).apply {
                tag = "intro_hero"
            }

            val glow = View(activity).apply {
                background = circleDrawable(palette().accentSoft)
            }
            hero.addView(
                glow,
                FrameLayout.LayoutParams(
                    dp(168),
                    dp(168),
                    Gravity.CENTER
                )
            )

            val icon = ImageView(activity).apply {
                setImageResource(page.icon)
                imageTintList = ColorStateList.valueOf(palette().accent)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = page.title
            }
            hero.addView(
                icon,
                FrameLayout.LayoutParams(
                    dp(92),
                    dp(92),
                    Gravity.CENTER
                )
            )

            root.addView(
                hero,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(236)
                )
            )

            val title = TextView(activity).apply {
                text = page.title
                textSize = 28f
                gravity = Gravity.CENTER
                maxLines = 2
                includeFontPadding = false
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette().text)
            }
            root.addView(
                title,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(72)
                )
            )

            val description = TextView(activity).apply {
                text = page.description
                textSize = 14f
                gravity = Gravity.CENTER
                maxLines = 3
                includeFontPadding = false
                setLineSpacing(dp(2).toFloat(), 1.04f)
                setTextColor(palette().muted)
                setPadding(dp(8), dp(8), dp(8), 0)
            }
            root.addView(
                description,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(84)
                )
            )

            container.addView(
                root,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            if (settings.animations) {
                hero.scaleX = 0.92f
                hero.scaleY = 0.92f
                hero.alpha = 0f
                hero.post {
                    hero.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(330L)
                        .setInterpolator(ease)
                        .start()
                }
            }

            return root
        }

        override fun destroyItem(
            container: ViewGroup,
            position: Int,
            obj: Any
        ) {
            container.removeView(obj as View)
        }
    }

    private fun animateSelectedHero() {
        if (!settings.animations) return
        val page = pager.findViewWithTag<View>("intro_hero") ?: return
        page.animate().cancel()
        page.scaleX = 0.97f
        page.scaleY = 0.97f
        page.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220L)
            .setInterpolator(ease)
            .start()
    }

    private fun updateChrome() {
        if (::nextButton.isInitialized) {
            nextButton.text =
                if (currentPage == pages.lastIndex) "Начать" else "Дальше"
        }
        if (::skipButton.isInitialized) {
            skipButton.visibility =
                if (currentPage == pages.lastIndex) View.INVISIBLE
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
