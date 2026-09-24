package com.unknokable.sohrai

import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var uiPrefs: SharedPreferences
    private lateinit var keyStore: SecureKeyStore
    private lateinit var store: ChatStore
    private val api = FusionApiClient()
    private val messages = mutableListOf<ChatMessage>()

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var input: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var statusText: TextView
    private var thinkingCard: LinearLayout? = null
    private var thinkingLoader: LoadingWaveView? = null
    private var busy = false
    private var lightTheme = false
    private var animations = true
    private var statusCycleToken = 0

    private val palette: ThemePalette
        get() = if (lightTheme) AppThemes.Light else AppThemes.Dark

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiPrefs = getSharedPreferences("sohr_ai_ui", MODE_PRIVATE)
        lightTheme = uiPrefs.getBoolean("light_theme", false)
        animations = uiPrefs.getBoolean("animations", true)
        keyStore = SecureKeyStore(this)
        store = ChatStore(this)
        messages += store.load()

        root = FrameLayout(this)
        setContentView(root)
        applySystemTheme()
        showSplash()

        root.postDelayed({
            if (keyStore.load().isNullOrBlank()) showConnectScreen() else showChatScreen()
        }, 720L)
    }

    private fun applySystemTheme() {
        window.statusBarColor = palette.background
        window.navigationBarColor = palette.background
        window.decorView.systemUiVisibility = if (lightTheme) {
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else 0
    }

    private fun showSplash() {
        val page = FrameLayout(this).apply { setBackgroundColor(palette.background) }
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val logo = TextView(this).apply {
            text = "SOHR AI"
            textSize = 34f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            alpha = 0f
            scaleX = 0.92f
            scaleY = 0.92f
        }
        val loader = LoadingWaveView(this, palette.accent).apply { alpha = 0f }
        val status = TextView(this).apply {
            text = "Собираем Fusion…"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(palette.muted)
            setPadding(0, dp(12), 0, 0)
            alpha = 0f
        }
        center.addView(logo)
        center.addView(loader, LinearLayout.LayoutParams(dp(56), dp(56)).apply {
            topMargin = dp(18)
            gravity = Gravity.CENTER_HORIZONTAL
        })
        center.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        page.addView(center, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.removeAllViews()
        root.addView(page)

        if (animations) {
            logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320L)
                .setInterpolator(PathInterpolator(0.22f, 1f, 0.36f, 1f)).start()
            loader.animate().alpha(1f).setStartDelay(120L).setDuration(220L).start()
            status.animate().alpha(1f).setStartDelay(200L).setDuration(220L).start()
        } else {
            logo.alpha = 1f
            logo.scaleX = 1f
            logo.scaleY = 1f
            loader.alpha = 1f
            status.alpha = 1f
        }
    }

    private fun showConnectScreen() {
        applySystemTheme()
        val page = FrameLayout(this).apply { setBackgroundColor(palette.background) }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(24), dp(18), dp(30))
        }

        val logo = TextView(this).apply {
            text = "SOHR AI"
            textSize = 27f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            gravity = Gravity.CENTER
        }
        content.addView(logo)

        content.addView(TextView(this).apply {
            text = "Один чат. Несколько топовых ИИ."
            textSize = 14f
            setTextColor(palette.muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, dp(20))
        })

        val modelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf("GPT", "Claude", "Gemini", "Grok").forEachIndexed { index, label ->
            modelRow.addView(providerBadge(label), LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                if (index > 0) marginStart = dp(6)
            })
        }
        content.addView(modelRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(palette.surface, 22)
        }
        val cardParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
        }
        content.addView(card, cardParams)

        card.addView(TextView(this).apply {
            text = "Быстрое подключение"
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })
        card.addView(TextView(this).apply {
            text = "Нужен один ключ OpenRouter. Через него Fusion получает доступ к GPT, Claude, Gemini и Grok."
            textSize = 13f
            setTextColor(palette.muted)
            setPadding(0, dp(6), 0, dp(14))
            setLineSpacing(dp(2).toFloat(), 1f)
        })

        val openRouterButton = actionButton("Войти / зарегистрироваться и получить ключ", palette.accentSoft, palette.text).apply {
            setOnClickListener {
                animateTap(this)
                openUrl("https://openrouter.ai/settings/keys")
            }
        }
        card.addView(openRouterButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))

        card.addView(TextView(this).apply {
            text = "1. Открой OpenRouter  •  2. Войди или создай аккаунт  •  3. Create API Key  •  4. Скопируй ключ сюда"
            textSize = 11.5f
            setTextColor(palette.muted)
            setPadding(dp(2), dp(10), dp(2), dp(14))
            setLineSpacing(dp(2).toFloat(), 1f)
        })

        val keyShell = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(3), dp(4), dp(3))
            background = roundedWithStroke(palette.surfaceAlt, 17, palette.stroke)
        }
        val keyInput = EditText(this).apply {
            hint = "sk-or-v1-…"
            setHintTextColor(palette.muted)
            setTextColor(palette.text)
            textSize = 14.5f
            maxLines = 1
            isSingleLine = true
            background = null
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            setPadding(0, dp(8), dp(6), dp(8))
        }
        val eye = ImageButton(this).apply {
            setImageResource(R.drawable.ic_visibility)
            imageTintList = ColorStateList.valueOf(palette.muted)
            background = rounded(palette.surfaceAlt, 18)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            var visible = false
            setOnClickListener {
                visible = !visible
                setImageResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility)
                keyInput.transformationMethod = if (visible) HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
                keyInput.setSelection(keyInput.text.length)
                animateTap(this)
            }
        }
        keyShell.addView(keyInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        keyShell.addView(eye, LinearLayout.LayoutParams(dp(44), dp(44)))
        card.addView(keyShell)

        val inlineStatus = TextView(this).apply {
            textSize = 12f
            setTextColor(palette.muted)
            visibility = View.GONE
            setPadding(dp(2), dp(10), dp(2), 0)
        }
        card.addView(inlineStatus)

        val connect = actionButton("Подключить Fusion", palette.accent, Color.WHITE).apply {
            setOnClickListener {
                animateTap(this)
                val key = keyInput.text.toString().trim()
                if (key.isBlank()) {
                    inlineStatus.text = "Вставь API-ключ OpenRouter."
                    inlineStatus.setTextColor(Color.parseColor("#FF7A9A"))
                    inlineStatus.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                isEnabled = false
                alpha = 0.65f
                text = "Проверяю ключ…"
                inlineStatus.text = "Соединяемся с OpenRouter…"
                inlineStatus.setTextColor(palette.muted)
                inlineStatus.visibility = View.VISIBLE
                lifecycleScope.launch {
                    val result = api.verifyKey(key)
                    if (result.isSuccess) {
                        keyStore.save(key)
                        inlineStatus.text = "Готово. Fusion подключён."
                        inlineStatus.setTextColor(palette.accent)
                        postTransition { showChatScreen() }
                    } else {
                        inlineStatus.text = friendlyError(result.exceptionOrNull()?.message)
                        inlineStatus.setTextColor(Color.parseColor("#FF7A9A"))
                        isEnabled = true
                        alpha = 1f
                        text = "Подключить Fusion"
                    }
                }
            }
        }
        card.addView(connect, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply {
            topMargin = dp(14)
        })

        val costCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = rounded(palette.accentSoft, 18)
        }
        costCard.addView(TextView(this).apply {
            text = "Как это работает"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })
        costCard.addView(TextView(this).apply {
            text = "Каждый запрос проходит через GPT + Claude + Gemini + Grok, затем отдельный анализатор сверяет ответы и выдаёт один результат. Fusion использует несколько платных вызовов, поэтому расход выше обычного чата."
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(0, dp(5), 0, 0)
            setLineSpacing(dp(2).toFloat(), 1f)
        })
        content.addView(costCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })

        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        page.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        replaceRoot(page, true)
    }

    private fun showChatScreen() {
        applySystemTheme()
        if (messages.isEmpty()) {
            messages += ChatMessage(
                "assistant",
                "Привет. Это **SOHR AI Fusion**. Ты пишешь один раз — внутри ответ сверяют GPT, Claude, Gemini и Grok, а наружу выходит один итог."
            )
            store.save(messages)
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(11), dp(12), dp(8))
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(this).apply {
            text = "SOHR AI"
            textSize = 21f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })
        statusText = TextView(this).apply {
            text = "Fusion · 4 модели · Web"
            textSize = 11.5f
            setTextColor(palette.muted)
            setPadding(0, dp(2), 0, 0)
        }
        titles.addView(statusText)
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val settingsButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_settings)
            imageTintList = ColorStateList.valueOf(palette.text)
            background = rounded(palette.surfaceAlt, 22)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener {
                animateTap(this)
                showSettingsDialog()
            }
        }
        header.addView(settingsButton, LinearLayout.LayoutParams(dp(46), dp(46)))
        page.addView(header)

        val badges = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), 0, dp(14), dp(6))
        }
        listOf("GPT", "Claude", "Gemini", "Grok").forEachIndexed { index, label ->
            badges.addView(providerBadge(label), LinearLayout.LayoutParams(0, dp(31), 1f).apply {
                if (index > 0) marginStart = dp(5)
            })
        }
        page.addView(badges)

        thinkingCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(palette.surface, 16)
            visibility = View.GONE
        }
        thinkingLoader = LoadingWaveView(this, palette.accent)
        thinkingCard?.addView(thinkingLoader, LinearLayout.LayoutParams(dp(30), dp(30)))
        thinkingCard?.addView(TextView(this).apply {
            id = View.generateViewId()
            tag = "thinking_text"
            text = "Fusion работает…"
            textSize = 12.5f
            setTextColor(palette.muted)
            setPadding(dp(8), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        page.addView(thinkingCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(14); marginEnd = dp(14); bottomMargin = dp(4)
        })

        recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply { stackFromEnd = true }
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, dp(3), 0, dp(8))
            setBackgroundColor(Color.TRANSPARENT)
        }
        adapter = ChatAdapter(messages, palette)
        recycler.adapter = adapter
        page.addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(dp(12), dp(7), dp(12), dp(12))
        }
        val inputShell = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(3), dp(5), dp(3))
            background = roundedWithStroke(palette.surface, 24, palette.stroke)
        }
        input = EditText(this).apply {
            hint = "Сообщение…"
            setHintTextColor(palette.muted)
            setTextColor(palette.text)
            textSize = 15.5f
            minLines = 1
            maxLines = 6
            isSingleLine = false
            background = null
            setPadding(dp(10), dp(8), dp(8), dp(8))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        sendButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_send)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = rounded(palette.accent, 22)
            setPadding(dp(11), dp(11), dp(11), dp(11))
            setOnClickListener {
                animateTap(this)
                sendMessage()
            }
        }
        inputShell.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        inputShell.addView(sendButton, LinearLayout.LayoutParams(dp(44), dp(44)))
        composer.addView(inputShell, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        page.addView(composer)

        replaceRoot(page, true)
        recycler.post { scrollToBottom() }
    }

    private fun sendMessage() {
        if (busy) return
        val text = input.text.toString().trim()
        if (text.isBlank()) return
        val key = keyStore.load()
        if (key.isNullOrBlank()) {
            showConnectScreen()
            return
        }

        input.setText("")
        messages += ChatMessage("user", text)
        val assistantIndex = messages.size
        messages += ChatMessage("assistant", "")
        adapter.notifyItemRangeInserted(assistantIndex - 1, 2)
        scrollToBottom()
        store.save(messages)

        setBusy(true)
        val history = messages.dropLast(1).map { it.copy() }

        lifecycleScope.launch {
            try {
                val result = api.fusion(key, history)
                messages[assistantIndex].text = result.text
                adapter.notifyItemChanged(assistantIndex)
                statusText.text = "Fusion · проверено несколькими моделями"
                store.save(messages)
                scrollToBottom()
            } catch (error: Throwable) {
                messages[assistantIndex].text = "**Не удалось получить ответ.**\n\n" + friendlyError(error.message)
                adapter.notifyItemChanged(assistantIndex)
                statusText.text = "Ошибка Fusion"
                store.save(messages)
            } finally {
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        if (::sendButton.isInitialized) {
            sendButton.isEnabled = !value
            sendButton.alpha = if (value) 0.45f else 1f
        }
        if (::input.isInitialized) input.isEnabled = !value
        thinkingCard?.visibility = if (value) View.VISIBLE else View.GONE
        statusCycleToken += 1
        val token = statusCycleToken
        if (value) {
            statusText.text = "Fusion думает…"
            cycleThinkingStatus(token, 0)
        } else if (::statusText.isInitialized && statusText.text == "Fusion думает…") {
            statusText.text = "Fusion · 4 модели · Web"
        }
    }

    private fun cycleThinkingStatus(token: Int, index: Int) {
        if (!busy || token != statusCycleToken) return
        val steps = listOf(
            "GPT · Claude · Gemini · Grok анализируют…",
            "Fusion сверяет ответы и источники…",
            "Проверяем противоречия…",
            "Собираем один итоговый ответ…"
        )
        val label = thinkingCard?.findViewWithTag<TextView>("thinking_text")
        label?.text = steps[index % steps.size]
        thinkingCard?.postDelayed({
            cycleThinkingStatus(token, index + 1)
        }, 1900L)
    }

    private fun showSettingsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(20))
            background = rounded(palette.surface, 26)
        }

        box.addView(TextView(this).apply {
            text = "Настройки"
            textSize = 21f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })
        box.addView(TextView(this).apply {
            text = "SOHR AI Fusion 0.2"
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(0, dp(3), 0, dp(15))
        })

        box.addView(TextView(this).apply {
            text = "Тема"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            setPadding(dp(2), 0, 0, dp(7))
        })
        box.addView(themeSelector(dialog), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))

        val animationRow = settingActionRow(
            title = "Анимации",
            description = if (animations) "Включены" else "Выключены",
            action = if (animations) "ON" else "OFF"
        ) {
            animations = !animations
            uiPrefs.edit().putBoolean("animations", animations).apply()
            dialog.dismiss()
            showSettingsDialog()
        }
        box.addView(animationRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        })

        box.addView(settingActionRow(
            title = "OpenRouter",
            description = "Ключ сохранён зашифрованно на устройстве",
            action = "Сменить"
        ) {
            keyStore.clear()
            dialog.dismiss()
            showConnectScreen()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })

        box.addView(settingActionRow(
            title = "История чата",
            description = "Удалить сообщения только с этого устройства",
            action = "Очистить"
        ) {
            messages.clear()
            store.save(messages)
            dialog.dismiss()
            showChatScreen()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })

        box.addView(actionButton("Открыть OpenRouter", palette.surfaceAlt, palette.text).apply {
            setOnClickListener {
                animateTap(this)
                openUrl("https://openrouter.ai/settings/keys")
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply {
            topMargin = dp(12)
        })

        dialog.setContentView(box)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply {
                dimAmount = 0.55f
                width = WindowManager.LayoutParams.MATCH_PARENT
                gravity = Gravity.BOTTOM
            }
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun themeSelector(dialog: Dialog): View {
        val selector = FrameLayout(this).apply {
            background = rounded(palette.surfaceAlt, 16)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            clipChildren = true
        }
        val indicator = View(this).apply { background = rounded(palette.accent, 13) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val dark = TextView(this).apply {
            text = "Тёмная"; gravity = Gravity.CENTER; textSize = 13f; setTypeface(typeface, Typeface.BOLD)
        }
        val light = TextView(this).apply {
            text = "Светлая"; gravity = Gravity.CENTER; textSize = 13f; setTypeface(typeface, Typeface.BOLD)
        }
        row.addView(dark, LinearLayout.LayoutParams(0, dp(44), 1f))
        row.addView(light, LinearLayout.LayoutParams(0, dp(44), 1f))
        selector.addView(indicator, FrameLayout.LayoutParams(0, dp(44), Gravity.START or Gravity.CENTER_VERTICAL))
        selector.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))

        fun position(animated: Boolean) {
            val slot = ((selector.width - selector.paddingLeft - selector.paddingRight) / 2f).coerceAtLeast(0f)
            if (slot <= 0f) return
            val lp = indicator.layoutParams as FrameLayout.LayoutParams
            lp.width = slot.toInt()
            indicator.layoutParams = lp
            val target = if (lightTheme) slot else 0f
            if (animated && animations) {
                indicator.animate().translationX(target).setDuration(190L)
                    .setInterpolator(PathInterpolator(0.22f, 1f, 0.36f, 1f)).start()
            } else indicator.translationX = target
            dark.setTextColor(if (!lightTheme) Color.WHITE else palette.muted)
            light.setTextColor(if (lightTheme) Color.WHITE else palette.muted)
        }

        dark.setOnClickListener {
            if (!lightTheme) return@setOnClickListener
            lightTheme = false
            uiPrefs.edit().putBoolean("light_theme", false).apply()
            position(true)
            selector.postDelayed({ dialog.dismiss(); showChatScreen() }, if (animations) 190L else 0L)
        }
        light.setOnClickListener {
            if (lightTheme) return@setOnClickListener
            lightTheme = true
            uiPrefs.edit().putBoolean("light_theme", true).apply()
            position(true)
            selector.postDelayed({ dialog.dismiss(); showChatScreen() }, if (animations) 190L else 0L)
        }
        selector.post { position(false) }
        return selector
    }

    private fun settingActionRow(title: String, description: String, action: String, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(10), dp(12))
            background = rounded(palette.surfaceAlt, 16)
            val labels = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(TextView(this@MainActivity).apply {
                text = title; textSize = 14f; setTypeface(typeface, Typeface.BOLD); setTextColor(palette.text)
            })
            labels.addView(TextView(this@MainActivity).apply {
                text = description; textSize = 11.5f; setTextColor(palette.muted); setPadding(0, dp(2), 0, 0)
            })
            addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@MainActivity).apply {
                text = action; textSize = 12f; gravity = Gravity.CENTER; setTextColor(palette.accent)
                setTypeface(typeface, Typeface.BOLD); setPadding(dp(10), dp(8), dp(10), dp(8))
                background = rounded(palette.accentSoft, 12)
            })
            setOnClickListener { animateTap(this); onClick() }
        }
    }

    private fun providerBadge(label: String): View =
        TextView(this).apply {
            text = label
            textSize = 11.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            background = rounded(palette.surfaceAlt, 13)
        }

    private fun actionButton(label: String, bg: Int, fg: Int): TextView =
        TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(fg)
            background = rounded(bg, 17)
            isClickable = true
            isFocusable = true
        }

    private fun replaceRoot(view: View, animate: Boolean) {
        val old = root.getChildAt(0)
        if (!animate || !animations || old == null) {
            root.removeAllViews()
            root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            return
        }
        view.alpha = 0f
        view.translationY = dp(7).toFloat()
        root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        view.animate().alpha(1f).translationY(0f).setDuration(220L)
            .setInterpolator(PathInterpolator(0.22f, 1f, 0.36f, 1f))
            .withEndAction {
                if (old.parent === root) root.removeView(old)
            }.start()
        old.animate().alpha(0f).setDuration(140L).start()
    }

    private fun postTransition(block: () -> Unit) {
        root.postDelayed(block, if (animations) 160L else 0L)
    }

    private fun animateTap(view: View) {
        if (!animations) return
        view.animate().cancel()
        view.animate().scaleX(0.965f).scaleY(0.965f).setDuration(70L)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(170L)
                    .setInterpolator(PathInterpolator(0.22f, 1f, 0.36f, 1f)).start()
            }.start()
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun friendlyError(raw: String?): String {
        val text = raw.orEmpty()
        return when {
            text.contains("401", true) || text.contains("unauthorized", true) || text.contains("key", true) && text.contains("invalid", true) ->
                "Ключ не подошёл. Создай новый ключ OpenRouter и вставь его ещё раз."
            text.contains("402", true) || text.contains("credit", true) || text.contains("balance", true) ->
                "На OpenRouter не хватает баланса для Fusion. Пополни баланс и повтори запрос."
            text.contains("429", true) || text.contains("rate", true) ->
                "Слишком много запросов. OpenRouter временно ограничил скорость."
            text.isBlank() -> "Неизвестная ошибка соединения."
            else -> text.take(700)
        }
    }

    private fun scrollToBottom() {
        if (::recycler.isInitialized && messages.isNotEmpty()) recycler.scrollToPosition(messages.lastIndex)
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun roundedWithStroke(color: Int, radiusDp: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
