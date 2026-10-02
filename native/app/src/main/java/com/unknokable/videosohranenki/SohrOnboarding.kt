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
    data class Page(
        val icon: Int,
        val title: String,
        val description: String,
        val accentLabel: String
    )

    val root = FrameLayout(activity)

    private val pages = listOf(
        Page(
            R.drawable.sohr_brand_logo,
            "Добро пожаловать в SOHR",
            "Одна аккуратная медиатека для видео, ленты, Twitch и твоего прогресса.",
            "ТВОЙ SOHR"
        ),
        Page(
            R.drawable.ic_nav_video,
            "Лента без лишнего шума",
            "Выбранные каналы, чёткие превью, дата публикации и быстрый переход к нужному видео.",
            "ЛЕНТА"
        ),
        Page(
            R.drawable.ic_play,
            "Смотреть удобно",
            "Telegram и Twitch открываются внутри SOHR. YouTube — одной кнопкой в YouTube без лишних ошибок.",
            "ПРОСМОТР"
        ),
        Page(
            R.drawable.ic_streak_shield,
            "Сохраняй Streak",
            "Дни просмотра, прогресс и небольшие анимации помогают видеть свою активность.",
            "STREAK"
        ),
        Page(
            R.drawable.ic_setting_appearance,
            "Сделай SOHR своим",
            "Темы, акцент, уведомления и обновления собраны в одном спокойном интерфейсе.",
            "ГОТОВО"
        )
    )

    private var index = 0
    private lateinit var contentHost: FrameLayout
    private lateinit var dots: LinearLayout
    private lateinit var nextButton: TextView
    private lateinit var themeButton: ImageView
    private val ease = PathInterpolator(0.22f, 1f, 0.36f, 1f)

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
            setPadding(dp(22), dp(18), dp(22), dp(20))
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
        top.addView(TextView(activity).apply {
            text = "SOHR"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette().text)
            includeFontPadding = false
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            gravity = Gravity.CENTER_VERTICAL
        })

        themeButton = ImageView(activity).apply {
            setImageResource(
                if (settings.lightTheme) R.drawable.ic_theme_moon
                else R.drawable.ic_theme_sun
            )
            imageTintList = ColorStateList.valueOf(palette().text)
            background = rounded(palette().surfaceAlt, 18)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(11), dp(11), dp(11), dp(11))
            contentDescription = "Сменить тему"
            setOnClickListener {
                press(this)
                settings.lightTheme = !settings.lightTheme
                buildShell()
                showPage(index, direction = 0, animate = true)
            }
        }
        top.addView(themeButton, LinearLayout.LayoutParams(dp(44), dp(44)))
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
                    if (kotlin.math.abs(dx) < dp(52) || kotlin.math.abs(velocityX) < 240f) {
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
                dp(30)
            ).apply {
                bottomMargin = dp(12)
            }
        )

        nextButton = TextView(activity).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(palette().accent, 19)
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
                dp(56)
            )
        )

        updateDots()
        updateButton()
    }

    private fun showPage(target: Int, direction: Int, animate: Boolean = true) {
        val safe = target.coerceIn(0, pages.lastIndex)
        val old = contentHost.getChildAt(0)
        index = safe
        val pageView = buildPage(pages[safe])

        if (old == null || !animate || !settings.animations) {
            contentHost.removeAllViews()
            contentHost.addView(pageView)
            updateDots()
            updateButton()
            return
        }

        val enterX = dp(34).toFloat() * if (direction >= 0) 1f else -1f
        val exitX = -enterX * 0.65f

        pageView.alpha = 0f
        pageView.translationX = enterX
        pageView.scaleX = 0.975f
        pageView.scaleY = 0.975f
        contentHost.addView(pageView)

        old.animate()
            .alpha(0f)
            .translationX(exitX)
            .scaleX(0.985f)
            .scaleY(0.985f)
            .setDuration(170L)
            .setInterpolator(ease)
            .withEndAction {
                if (old.parent === contentHost) contentHost.removeView(old)
            }
            .start()

        pageView.animate()
            .alpha(1f)
            .translationX(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(35L)
            .setDuration(330L)
            .setInterpolator(ease)
            .start()

        updateDots()
        updateButton()
    }

    private fun buildPage(page: Page): View {
        val wrap = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        val illustration = FrameLayout(activity).apply {
            background = rounded(palette().surface, 32)
            elevation = dp(3).toFloat()
        }

        val halo = View(activity).apply {
            background = oval(palette().accentSoft)
            alpha = 0.82f
        }
        illustration.addView(
            halo,
            FrameLayout.LayoutParams(dp(176), dp(176), Gravity.CENTER)
        )

        val icon = ImageView(activity).apply {
            setImageResource(page.icon)
            if (page.icon != R.drawable.sohr_brand_logo) {
                imageTintList = ColorStateList.valueOf(palette().accent)
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(24), dp(24), dp(24), dp(24))
            background = rounded(palette().surfaceAlt, 30)
        }
        illustration.addView(
            icon,
            FrameLayout.LayoutParams(dp(116), dp(116), Gravity.CENTER)
        )

        val chipLeft = miniChip(page.accentLabel.take(5))
        illustration.addView(
            chipLeft,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(34),
                Gravity.START or Gravity.BOTTOM
            ).apply {
                marginStart = dp(18)
                bottomMargin = dp(24)
            }
        )

        val chipRight = ImageView(activity).apply {
            setImageResource(
                when (index) {
                    1 -> R.drawable.ic_search
                    2 -> R.drawable.ic_action_open
                    3 -> R.drawable.ic_stat_trophy
                    4 -> R.drawable.ic_setting_update
                    else -> R.drawable.ic_check
                }
            )
            imageTintList = ColorStateList.valueOf(palette().accent)
            background = rounded(palette().surfaceAlt, 17)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(9), dp(9), dp(9), dp(9))
        }
        illustration.addView(
            chipRight,
            FrameLayout.LayoutParams(dp(42), dp(42), Gravity.END or Gravity.TOP).apply {
                marginEnd = dp(18)
                topMargin = dp(24)
            }
        )

        wrap.addView(
            illustration,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(270)
            ).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
                bottomMargin = dp(30)
            }
        )

        val title = TextView(activity).apply {
            text = page.title
            textSize = 27f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette().text)
            includeFontPadding = false
        }
        wrap.addView(title)

        val description = TextView(activity).apply {
            text = page.description
            textSize = 14.2f
            gravity = Gravity.CENTER
            setTextColor(palette().muted)
            setLineSpacing(dp(2).toFloat(), 1.04f)
            setPadding(dp(12), dp(12), dp(12), 0)
            includeFontPadding = false
        }
        wrap.addView(description)

        if (settings.animations) {
            icon.scaleX = 0.82f
            icon.scaleY = 0.82f
            icon.alpha = 0f
            halo.scaleX = 0.86f
            halo.scaleY = 0.86f
            chipLeft.alpha = 0f
            chipRight.alpha = 0f

            icon.post {
                halo.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(420L)
                    .setInterpolator(ease)
                    .start()
                icon.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .rotationBy(if (index % 2 == 0) 2.5f else -2.5f)
                    .setDuration(430L)
                    .setInterpolator(ease)
                    .start()
                chipLeft.animate()
                    .alpha(1f)
                    .translationY(-dp(4).toFloat())
                    .setStartDelay(120L)
                    .setDuration(340L)
                    .setInterpolator(ease)
                    .start()
                chipRight.animate()
                    .alpha(1f)
                    .translationY(dp(3).toFloat())
                    .setStartDelay(165L)
                    .setDuration(340L)
                    .setInterpolator(ease)
                    .start()
            }
        }

        return wrap
    }

    private fun miniChip(label: String): TextView =
        TextView(activity).apply {
            text = label
            textSize = 10.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette().text)
            background = rounded(palette().surfaceAlt, 15)
            setPadding(dp(13), 0, dp(13), 0)
            includeFontPadding = false
        }

    private fun next() {
        if (index < pages.lastIndex) showPage(index + 1, direction = 1)
    }

    private fun previous() {
        if (index > 0) showPage(index - 1, direction = -1)
    }

    private fun updateButton() {
        if (::nextButton.isInitialized) {
            nextButton.text = if (index == pages.lastIndex) "Начать" else "Дальше"
        }
    }

    private fun updateDots() {
        if (!::dots.isInitialized) return
        dots.removeAllViews()
        pages.indices.forEach { dotIndex ->
            val selected = dotIndex == index
            val dot = View(activity).apply {
                background = rounded(
                    if (selected) palette().accent else palette().surfaceAlt,
                    5
                )
                isClickable = true
                setOnClickListener {
                    if (dotIndex != index) {
                        showPage(dotIndex, if (dotIndex > index) 1 else -1)
                    }
                }
            }
            dots.addView(
                dot,
                LinearLayout.LayoutParams(
                    dp(if (selected) 24 else 8),
                    dp(8)
                ).apply {
                    marginStart = dp(4)
                    marginEnd = dp(4)
                }
            )
        }
    }

    private fun press(view: View) {
        if (!settings.animations) return
        view.animate().cancel()
        view.animate()
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(55L)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150L)
                    .setInterpolator(ease)
                    .start()
            }
            .start()
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
        }

    private fun oval(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
