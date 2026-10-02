package com.unknokable.videosohranenki

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class SohrOnboarding(
    private val activity: Activity,
    private val settings: AppSettings,
    private val onFinished: () -> Unit
) {
    private data class Page(
        val icon: Int,
        val kicker: String,
        val title: String,
        val description: String
    )

    val root = FrameLayout(activity)

    private val pages = listOf(
        Page(
            R.drawable.ic_onboarding_cloud,
            "МЕДИАТЕКА",
            "Добро пожаловать в SOHR",
            "Видео, источники и прогресс — в одном спокойном интерфейсе."
        ),
        Page(
            R.drawable.ic_onboarding_feed,
            "ЛЕНТА",
            "Лента без лишнего",
            "Чёткие превью, понятные даты и быстрый доступ к выбранным каналам."
        ),
        Page(
            R.drawable.ic_onboarding_watch,
            "ПРОСМОТР",
            "Просмотр без лишних экранов",
            "Telegram и Twitch — внутри SOHR. YouTube открывается сразу на нужном ролике."
        ),
        Page(
            R.drawable.ic_onboarding_streak,
            "STREAK",
            "Streak и прогресс",
            "Аккуратный прогресс по дням помогает видеть активность без перегруженных экранов."
        ),
        Page(
            R.drawable.ic_onboarding_customize,
            "ОФОРМЛЕНИЕ",
            "Персонализация SOHR",
            "Темы, акцент, уведомления и обновления настраиваются в одном месте."
        )
    )

    private var index = 0
    private lateinit var contentHost: FrameLayout
    private lateinit var dots: LinearLayout
    private lateinit var nextButton: TextView
    private lateinit var skipButton: TextView
    private lateinit var themeButton: ImageView

    private val ease = PathInterpolator(0.22f, 1f, 0.36f, 1f)
    private val quickEase = PathInterpolator(0.2f, 0f, 0f, 1f)

    init {
        buildShell()
        showPage(0, direction = 0, animate = false)
    }

    private fun palette(): ThemePalette = settings.palette()

    private fun buildShell() {
        root.removeAllViews()
        root.setBackgroundColor(palette().background)

        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(14), dp(24), dp(18))
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
                textSize = 17.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette().text)
                includeFontPadding = false
                gravity = Gravity.CENTER_VERTICAL
            },
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )

        skipButton = TextView(activity).apply {
            text = "Пропустить"
            textSize = 12.5f
            setTextColor(palette().muted)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(10), 0, dp(10), 0)
            isClickable = true
            isFocusable = true
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

        themeButton = ImageView(activity).apply {
            setImageResource(
                if (settings.lightTheme) R.drawable.ic_theme_moon
                else R.drawable.ic_theme_sun
            )
            imageTintList = ColorStateList.valueOf(palette().text)
            background = circle(palette().surfaceAlt)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            contentDescription = "Сменить тему"
            setOnClickListener {
                press(this)
                val saved = index
                settings.lightTheme = !settings.lightTheme
                buildShell()
                showPage(saved, direction = 0, animate = false)
            }
        }
        top.addView(themeButton, LinearLayout.LayoutParams(dp(40), dp(40)))
        column.addView(top)

        contentHost = FrameLayout(activity).apply {
            isClickable = true
        }
        column.addView(
            contentHost,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(8)
            }
        )

        val gestures = GestureDetector(
            activity,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    val start = e1 ?: return false
                    val dx = e2.x - start.x
                    if (kotlin.math.abs(dx) < dp(48) || kotlin.math.abs(velocityX) < 220f) {
                        return false
                    }
                    if (dx < 0f) next() else previous()
                    return true
                }
            }
        )
        contentHost.setOnTouchListener { _, event ->
            gestures.onTouchEvent(event)
        }

        dots = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
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
            textSize = 15f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(palette().accent, 18)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                press(this)
                if (index == pages.lastIndex) onFinished() else next()
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
    }

    private fun showPage(target: Int, direction: Int, animate: Boolean = true) {
        val safe = target.coerceIn(0, pages.lastIndex)
        val old = contentHost.getChildAt(0)
        index = safe
        val next = buildPage(pages[safe])

        if (old == null || !animate || !settings.animations) {
            contentHost.removeAllViews()
            contentHost.addView(next)
            updateChrome()
            return
        }

        old.animate().cancel()
        next.animate().cancel()

        val enterX = dp(28).toFloat() * if (direction >= 0) 1f else -1f
        val exitX = -enterX * 0.65f

        next.alpha = 0f
        next.translationX = enterX
        next.scaleX = 0.985f
        next.scaleY = 0.985f
        contentHost.addView(next)

        old.animate()
            .alpha(0f)
            .translationX(exitX)
            .scaleX(0.992f)
            .scaleY(0.992f)
            .setDuration(165L)
            .setInterpolator(quickEase)
            .withEndAction {
                if (old.parent === contentHost) contentHost.removeView(old)
            }
            .start()

        next.animate()
            .alpha(1f)
            .translationX(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(28L)
            .setDuration(300L)
            .setInterpolator(ease)
            .start()

        updateChrome()
    }

    private fun buildPage(page: Page): View {
        val wrap = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }

        val hero = FrameLayout(activity)

        val outerRing = View(activity).apply {
            background = ring(
                strokeColor = alpha(palette().accent, 0.16f),
                strokeWidthDp = 1
            )
        }
        hero.addView(
            outerRing,
            FrameLayout.LayoutParams(dp(224), dp(224), Gravity.CENTER)
        )

        val middleGlow = View(activity).apply {
            background = circle(alpha(palette().accent, 0.10f))
        }
        hero.addView(
            middleGlow,
            FrameLayout.LayoutParams(dp(176), dp(176), Gravity.CENTER)
        )

        val orbitA = View(activity).apply {
            background = circle(alpha(palette().accent, 0.48f))
        }
        hero.addView(
            orbitA,
            FrameLayout.LayoutParams(dp(10), dp(10), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(45)
                marginEnd = dp(40)
            }
        )

        val orbitB = View(activity).apply {
            background = circle(alpha(palette().accent, 0.26f))
        }
        hero.addView(
            orbitB,
            FrameLayout.LayoutParams(dp(7), dp(7), Gravity.BOTTOM or Gravity.START).apply {
                bottomMargin = dp(50)
                marginStart = dp(43)
            }
        )

        val iconPlate = FrameLayout(activity).apply {
            background = circle(palette().surfaceAlt)
        }
        hero.addView(
            iconPlate,
            FrameLayout.LayoutParams(dp(118), dp(118), Gravity.CENTER)
        )

        val icon = ImageView(activity).apply {
            setImageResource(page.icon)
            imageTintList = ColorStateList.valueOf(palette().accent)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(27), dp(27), dp(27), dp(27))
            contentDescription = page.title
        }
        iconPlate.addView(
            icon,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        wrap.addView(
            hero,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(250)
            ).apply {
                bottomMargin = dp(22)
            }
        )

        val kicker = TextView(activity).apply {
            text = page.kicker
            textSize = 10.5f
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette().accent)
            includeFontPadding = false
        }
        wrap.addView(kicker)

        val title = TextView(activity).apply {
            text = page.title
            textSize = 27f
            gravity = Gravity.CENTER
            maxLines = 2
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette().text)
            includeFontPadding = false
            setPadding(dp(8), dp(10), dp(8), 0)
        }
        wrap.addView(title)

        val description = TextView(activity).apply {
            text = page.description
            textSize = 14.2f
            gravity = Gravity.CENTER
            maxLines = 3
            setTextColor(palette().muted)
            setLineSpacing(dp(2).toFloat(), 1.04f)
            setPadding(dp(20), dp(12), dp(20), 0)
            includeFontPadding = false
        }
        wrap.addView(description)

        if (settings.animations) {
            outerRing.alpha = 0f
            outerRing.scaleX = 0.82f
            outerRing.scaleY = 0.82f
            middleGlow.alpha = 0f
            middleGlow.scaleX = 0.76f
            middleGlow.scaleY = 0.76f
            iconPlate.alpha = 0f
            iconPlate.scaleX = 0.84f
            iconPlate.scaleY = 0.84f
            orbitA.alpha = 0f
            orbitB.alpha = 0f
            kicker.alpha = 0f
            title.alpha = 0f
            description.alpha = 0f

            iconPlate.post {
                outerRing.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(420L)
                    .setInterpolator(ease)
                    .start()

                middleGlow.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(35L)
                    .setDuration(390L)
                    .setInterpolator(ease)
                    .start()

                iconPlate.animate()
                    .alpha(1f)
                    .scaleX(1.035f)
                    .scaleY(1.035f)
                    .setStartDelay(55L)
                    .setDuration(260L)
                    .setInterpolator(ease)
                    .withEndAction {
                        iconPlate.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(145L)
                            .setInterpolator(quickEase)
                            .start()
                    }
                    .start()

                orbitA.translationX = -dp(8).toFloat()
                orbitA.translationY = dp(8).toFloat()
                orbitB.translationX = dp(8).toFloat()
                orbitB.translationY = -dp(8).toFloat()

                orbitA.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .translationY(0f)
                    .setStartDelay(150L)
                    .setDuration(300L)
                    .setInterpolator(ease)
                    .start()

                orbitB.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .translationY(0f)
                    .setStartDelay(195L)
                    .setDuration(300L)
                    .setInterpolator(ease)
                    .start()

                kicker.animate()
                    .alpha(1f)
                    .setStartDelay(110L)
                    .setDuration(210L)
                    .start()
                title.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(145L)
                    .setDuration(250L)
                    .setInterpolator(ease)
                    .start()
                description.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(185L)
                    .setDuration(260L)
                    .setInterpolator(ease)
                    .start()
            }
        }

        return wrap
    }

    private fun next() {
        if (index < pages.lastIndex) showPage(index + 1, direction = 1)
    }

    private fun previous() {
        if (index > 0) showPage(index - 1, direction = -1)
    }

    private fun updateChrome() {
        updateDots()
        if (::nextButton.isInitialized) {
            nextButton.text = if (index == pages.lastIndex) "Начать" else "Дальше"
        }
        if (::skipButton.isInitialized) {
            skipButton.visibility =
                if (index == pages.lastIndex) View.INVISIBLE else View.VISIBLE
        }
    }

    private fun updateDots() {
        if (!::dots.isInitialized) return
        dots.removeAllViews()

        pages.indices.forEach { dotIndex ->
            val selected = dotIndex == index
            val dot = View(activity).apply {
                background = rounded(
                    if (selected) palette().accent
                    else alpha(palette().muted, 0.22f),
                    4
                )
                isClickable = true
                setOnClickListener {
                    if (dotIndex != index) {
                        showPage(
                            dotIndex,
                            direction = if (dotIndex > index) 1 else -1
                        )
                    }
                }
            }

            dots.addView(
                dot,
                LinearLayout.LayoutParams(
                    dp(if (selected) 22 else 6),
                    dp(6)
                ).apply {
                    marginStart = dp(4)
                    marginEnd = dp(4)
                }
            )

            if (settings.animations && selected) {
                dot.scaleX = 0.7f
                dot.alpha = 0f
                dot.post {
                    dot.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .setDuration(220L)
                        .setInterpolator(ease)
                        .start()
                }
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

    private fun ring(strokeColor: Int, strokeWidthDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(dp(strokeWidthDp), strokeColor)
        }

    private fun circle(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
        }

    private fun alpha(color: Int, fraction: Float): Int =
        Color.argb(
            (255f * fraction.coerceIn(0f, 1f)).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
