package com.unknokable.videosohranenki

import android.app.Activity
import android.content.Context
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
import android.widget.Scroller
import android.widget.TextView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager

class SohrOnboarding(
    private val activity: Activity,
    private val settings: AppSettings,
    private val onFinished: () -> Unit
) {
    private class SohrIntroPager(context: Context) : ViewPager(context) {
        init {
            // ViewPager owns the only transition timing. The five distinct
            // illustrations crossfade during a swipe; no path morph is used.
            runCatching {
                val field = ViewPager::class.java.getDeclaredField("mScroller")
                field.isAccessible = true
                field.set(
                    this,
                    object : Scroller(
                        context,
                        PathInterpolator(0.22f, 1f, 0.36f, 1f)
                    ) {
                        override fun startScroll(
                            startX: Int,
                            startY: Int,
                            dx: Int,
                            dy: Int,
                            duration: Int
                        ) {
                            super.startScroll(
                                startX,
                                startY,
                                dx,
                                dy,
                                PAGE_FADE_DURATION_MS
                            )
                        }
                    }
                )
            }
        }

        private companion object {
            const val PAGE_FADE_DURATION_MS = 292
        }
    }

    private data class Page(
        val title: String,
        val description: String
    )

    val root = FrameLayout(activity)

    private val pages = listOf(
        Page(
            "Сохранение видео и эфиров",
            "Любимые видео и эфиры — в одном месте."
        ),
        Page(
            "Streak и прогресс",
            "Следите за сериями и активностью по дням."
        ),
        Page(
            "Удобный просмотр",
            "Видео и эфиры — в удобном встроенном плеере."
        ),
        Page(
            "Загрузки и коллекции",
            "Сохраняйте видео и собирайте коллекции."
        ),
        Page(
            "Настройка под себя",
            "Выбирайте тему, цвета и удобные параметры."
        )
    )

    private lateinit var pager: ViewPager
    private lateinit var morphView: SohrIntroMorphView
    private lateinit var dots: SohrIntroDotsView
    private lateinit var nextButton: TextView
    private lateinit var skipButton: TextView

    private var currentPage = 0
    private var currentProgress = 0f

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
                includeFontPadding = false
                gravity = Gravity.CENTER_VERTICAL
                setTypeface(typeface, Typeface.BOLD)
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
            ).apply {
                marginEnd = dp(8)
            }
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
                val progress = currentProgress

                SohrThemeTransition.reveal(
                    host = root,
                    anchor = this,
                    enabled = settings.animations
                ) {
                    settings.lightTheme = !settings.lightTheme
                    build()
                    pager.setCurrentItem(page, false)
                    currentPage = page
                    currentProgress = progress
                    morphView.setPageProgress(progress)
                    dots.setPageProgress(progress)
                    updateChrome()
                }
            }
        }
        top.addView(
            themeButton,
            LinearLayout.LayoutParams(dp(40), dp(40))
        )
        column.addView(top)

        morphView = SohrIntroMorphView(activity).apply {
            setPalette(palette())
            setAnimationsEnabled(settings.animations)
            setPageProgress(currentProgress)
        }
        // Keep the artwork and primary action visible on shorter devices.
        val screenHeightDp =
            activity.resources.displayMetrics.heightPixels /
                activity.resources.displayMetrics.density
        val heroHeightDp = (screenHeightDp - 422f).toInt().coerceIn(166, 238)
        column.addView(
            morphView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(heroHeightDp)
            ).apply {
                topMargin = dp(12)
            }
        )

        pager = SohrIntroPager(activity).apply {
            id = View.generateViewId()
            offscreenPageLimit = 1
            adapter = IntroPagerAdapter()

            setPageTransformer(false) { page, position ->
                val distance =
                    kotlin.math.abs(position).coerceIn(0f, 1f)

                page.alpha = 1f - distance * 0.32f
                page.translationX =
                    -position * dp(14).toFloat()

                val scale =
                    1f - distance * 0.012f
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
                        val progress =
                            (position + positionOffset)
                                .coerceIn(
                                    0f,
                                    (pages.size - 1).toFloat()
                                )

                        currentProgress = progress
                        morphView.setPageProgress(progress)
                        dots.setPageProgress(progress)
                    }

                    override fun onPageSelected(position: Int) {
                        currentPage = position
                        updateChrome()
                    }

                    override fun onPageScrollStateChanged(
                        state: Int
                    ) = Unit
                }
            )
        }
        column.addView(
            pager,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(176)
            )
        )

        dots = SohrIntroDotsView(activity).apply {
            setCount(pages.size)
            setPalette(
                active = palette().accent,
                inactive = palette().muted
            )
            setPageProgress(currentProgress)
        }
        column.addView(
            dots,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(28)
            ).apply {
                topMargin = dp(2)
                bottomMargin = dp(8)
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
                    pager.setCurrentItem(
                        currentPage + 1,
                        true
                    )
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

        pager.setCurrentItem(currentPage, false)
        morphView.setPageProgress(currentProgress)
        updateChrome()

        // Keep the icon canvas at full opacity; only its five drawings
        // crossfade internally on each ViewPager scroll event.
        morphView.animate().cancel()
        morphView.alpha = 1f
        morphView.scaleX = 1f
        morphView.scaleY = 1f

        if (settings.animations) {
            pager.alpha = 0f
            pager.translationY = dp(7).toFloat()
            dots.alpha = 0f
            nextButton.alpha = 0f
            nextButton.translationY = dp(7).toFloat()

            morphView.post {
                pager.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(65L)
                    .setDuration(280L)
                    .setInterpolator(ease)
                    .start()

                dots.animate()
                    .alpha(1f)
                    .setStartDelay(110L)
                    .setDuration(220L)
                    .start()

                nextButton.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(150L)
                    .setDuration(270L)
                    .setInterpolator(ease)
                    .start()
            }
        }
    }

    private inner class IntroPagerAdapter : PagerAdapter() {
        override fun getCount(): Int = pages.size

        override fun isViewFromObject(
            view: View,
            obj: Any
        ): Boolean = view === obj

        override fun instantiateItem(
            container: ViewGroup,
            position: Int
        ): Any {
            val page = pages[position]

            val content = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity =
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(2), dp(4), 0)
            }

            val title = TextView(activity).apply {
                text = page.title
                textSize = 25f
                gravity = Gravity.CENTER
                maxLines = 2
                includeFontPadding = false
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette().text)
            }
            content.addView(
                title,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(74)
                )
            )

            val description = TextView(activity).apply {
                text = page.description
                textSize = 14f
                gravity = Gravity.CENTER
                maxLines = 2
                includeFontPadding = false
                setLineSpacing(dp(2).toFloat(), 1.04f)
                setTextColor(palette().muted)
                setPadding(dp(14), dp(6), dp(14), 0)
            }
            content.addView(
                description,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(74)
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

    private fun updateChrome() {
        if (::nextButton.isInitialized) {
            nextButton.text =
                if (currentPage == pages.lastIndex) {
                    "Начать"
                } else {
                    "Дальше"
                }
        }

        if (::skipButton.isInitialized) {
            skipButton.visibility =
                if (currentPage == pages.lastIndex) {
                    View.INVISIBLE
                } else {
                    View.VISIBLE
                }
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
            shape =
                android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
        }

    private fun circleDrawable(
        color: Int
    ): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            shape =
                android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
        }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
