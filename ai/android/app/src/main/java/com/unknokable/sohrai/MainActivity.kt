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
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var uiPrefs: SharedPreferences
    private lateinit var keyStore: SecureKeyStore
    private lateinit var chatStore: ChatStore

    private val api = SmartAiClient()
    private val messages = mutableListOf<ChatMessage>()

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var input: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var statusText: TextView
    private var thinkingCard: LinearLayout? = null
    private var thinkingLabel: TextView? = null
    private var generationJob: Job? = null
    private var currentAssistantIndex: Int? = null

    private var busy = false
    private var lightTheme = false
    private var animations = true

    private val palette: ThemePalette
        get() = if (lightTheme) AppThemes.Light else AppThemes.Dark

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        uiPrefs = getSharedPreferences("sohr_ai_ui_v4", MODE_PRIVATE)
        lightTheme = uiPrefs.getBoolean("light_theme", false)
        animations = uiPrefs.getBoolean("animations", true)

        keyStore = SecureKeyStore(this)
        chatStore = ChatStore(this)
        messages += chatStore.load()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        root = FrameLayout(this).apply {
            setBackgroundColor(palette.background)
            clipToPadding = false
        }

        setContentView(root)
        installSafeInsets()
        applySystemTheme()
        showSplash()

        root.postDelayed({
            if (keysReady()) showChatScreen() else showConnectScreen()
        }, 620L)
    }

    override fun onDestroy() {
        api.cancel()
        generationJob?.cancel()
        super.onDestroy()
    }

    private fun keysReady(): Boolean =
        keyStore.has("gemini") && keyStore.has("groq")

    private fun installSafeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = maxOf(bars.bottom, ime.bottom)

            view.setPadding(0, bars.top, 0, bottom)

            if (::recycler.isInitialized && ime.bottom > 0) {
                recycler.post { scrollToBottom() }
            }

            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun applySystemTheme() {
        root.setBackgroundColor(palette.background)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = lightTheme
            isAppearanceLightNavigationBars = lightTheme
        }
    }

    private fun showSplash() {
        val page = FrameLayout(this).apply {
            setBackgroundColor(palette.background)
        }

        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val logo = TextView(this).apply {
            text = "SOHR AI"
            textSize = 31f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            alpha = if (animations) 0f else 1f
            scaleX = if (animations) 0.94f else 1f
            scaleY = if (animations) 0.94f else 1f
        }

        val loader = LoadingWaveView(this, palette.accent).apply {
            alpha = if (animations) 0f else 1f
        }

        val subtitle = TextView(this).apply {
            text = "Smart"
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTextColor(palette.muted)
            setPadding(0, dp(9), 0, 0)
            alpha = if (animations) 0f else 1f
        }

        center.addView(logo)
        center.addView(
            loader,
            LinearLayout.LayoutParams(dp(50), dp(50)).apply {
                topMargin = dp(15)
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )
        center.addView(subtitle)

        page.addView(
            center,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        root.removeAllViews()
        root.addView(page)

        if (animations) {
            logo.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(280L)
                .setInterpolator(PathInterpolator(0.22f, 1f, 0.36f, 1f))
                .start()

            loader.animate()
                .alpha(1f)
                .setStartDelay(90L)
                .setDuration(170L)
                .start()

            subtitle.animate()
                .alpha(1f)
                .setStartDelay(140L)
                .setDuration(170L)
                .start()
        }
    }

    private fun showConnectScreen() {
        applySystemTheme()

        val page = FrameLayout(this).apply {
            setBackgroundColor(palette.background)
        }

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }

        content.addView(TextView(this).apply {
            text = "SOHR AI"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })

        content.addView(TextView(this).apply {
            text = "Один чат · три модели · бесплатные API"
            textSize = 13f
            setTextColor(palette.muted)
            setPadding(0, dp(4), 0, dp(14))
        })

        content.addView(TextView(this).apply {
            text = "Подключение"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })

        content.addView(TextView(this).apply {
            text = "Два бесплатных ключа нужны один раз. Потом приложение само выбирает модель и режим."
            textSize = 12.5f
            setTextColor(palette.muted)
            setPadding(0, dp(4), 0, dp(10))
        })

        val geminiInput = providerKeyCard(
            title = "Google Gemini",
            description = "Gemini 3.8 Flash · основной чат + веб-проверка",
            button = "Получить Gemini API key",
            url = "https://aistudio.google.com/apikey",
            hint = "AIza…"
        )

        val groqInput = providerKeyCard(
            title = "Groq",
            description = "GPT-OSS 120B + Qwen 3.8 · проверка и резерв",
            button = "Получить Groq API key",
            url = "https://console.groq.com/keys",
            hint = "gsk_…"
        )

        content.addView(
            geminiInput.first,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(9) }
        )

        content.addView(
            groqInput.first,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val inlineStatus = TextView(this).apply {
            textSize = 12f
            setTextColor(palette.muted)
            visibility = View.GONE
            setPadding(dp(2), dp(10), dp(2), 0)
        }
        content.addView(inlineStatus)

        val connect = actionButton(
            "Проверить ключи и войти",
            palette.accent,
            Color.WHITE
        )

        connect.setOnClickListener {
            animateTap(connect)

            val gemini = geminiInput.second.text?.toString()?.trim().orEmpty()
            val groq = groqInput.second.text?.toString()?.trim().orEmpty()

            if (gemini.isBlank() || groq.isBlank()) {
                inlineStatus.text = "Вставь оба ключа."
                inlineStatus.setTextColor(Color.parseColor("#FF7A9A"))
                inlineStatus.visibility = View.VISIBLE
                return@setOnClickListener
            }

            connect.isEnabled = false
            connect.alpha = 0.62f
            connect.text = "Проверяю…"

            inlineStatus.text = "Проверяем Google AI…"
            inlineStatus.setTextColor(palette.muted)
            inlineStatus.visibility = View.VISIBLE

            lifecycleScope.launch {
                val geminiCheck = api.verifyGemini(gemini)

                if (geminiCheck.isFailure) {
                    inlineStatus.text = friendlyError(
                        geminiCheck.exceptionOrNull()?.message,
                        "Gemini"
                    )
                    inlineStatus.setTextColor(Color.parseColor("#FF7A9A"))
                    connect.isEnabled = true
                    connect.alpha = 1f
                    connect.text = "Проверить ключи и войти"
                    return@launch
                }

                inlineStatus.text = "Проверяем Groq…"

                val groqCheck = api.verifyGroq(groq)

                if (groqCheck.isFailure) {
                    inlineStatus.text = friendlyError(
                        groqCheck.exceptionOrNull()?.message,
                        "Groq"
                    )
                    inlineStatus.setTextColor(Color.parseColor("#FF7A9A"))
                    connect.isEnabled = true
                    connect.alpha = 1f
                    connect.text = "Проверить ключи и войти"
                    return@launch
                }

                keyStore.save("gemini", gemini)
                keyStore.save("groq", groq)

                inlineStatus.text = "Готово."
                inlineStatus.setTextColor(palette.accent)

                root.postDelayed({
                    showChatScreen()
                }, if (animations) 140L else 0L)
            }
        }

        content.addView(
            connect,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply { topMargin = dp(12) }
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(palette.accentSoft, 17)
        }

        info.addView(TextView(this).apply {
            text = "Как работает Smart"
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })

        info.addView(TextView(this).apply {
            text = "Обычный вопрос — один быстрый ответ Gemini. Сложный вопрос — Gemini, GPT-OSS и Qwen сверяют решение, затем получается один итог. Свежая информация — отдельный веб-режим."
            textSize = 11.7f
            setTextColor(palette.muted)
            setPadding(0, dp(4), 0, 0)
            setLineSpacing(dp(2).toFloat(), 1f)
        })

        content.addView(
            info,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        )

        scroll.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        page.addView(
            scroll,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        replaceRoot(page, true)
    }

    private fun providerKeyCard(
        title: String,
        description: String,
        button: String,
        url: String,
        hint: String
    ): Pair<View, EditText> {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(14))
            background = rounded(palette.surface, 18)
        }

        card.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })

        card.addView(TextView(this).apply {
            text = description
            textSize = 11.5f
            setTextColor(palette.muted)
            setPadding(0, dp(2), 0, dp(9))
        })

        val open = actionButton(button, palette.surfaceAlt, palette.text)
        open.setOnClickListener {
            animateTap(open)
            openUrl(url)
        }

        card.addView(
            open,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(45)
            )
        )

        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(2), dp(3), dp(2))
            background = roundedWithStroke(
                palette.surfaceAlt,
                15,
                palette.stroke
            )
        }

        val field = EditText(this).apply {
            this.hint = hint
            setHintTextColor(palette.muted)
            setTextColor(palette.text)
            textSize = 14f
            isSingleLine = true
            maxLines = 1
            background = null
            inputType =
                InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            setPadding(0, dp(8), dp(5), dp(8))
        }

        val eye = ImageButton(this).apply {
            setImageResource(R.drawable.ic_visibility)
            imageTintList = ColorStateList.valueOf(palette.muted)
            background = rounded(palette.surfaceAlt, 17)
            setPadding(dp(9), dp(9), dp(9), dp(9))

            var visible = false

            setOnClickListener {
                visible = !visible
                setImageResource(
                    if (visible) R.drawable.ic_visibility_off
                    else R.drawable.ic_visibility
                )

                field.transformationMethod =
                    if (visible) HideReturnsTransformationMethod.getInstance()
                    else PasswordTransformationMethod.getInstance()

                field.setSelection(field.text.length)
                animateTap(this)
            }
        }

        shell.addView(
            field,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        shell.addView(
            eye,
            LinearLayout.LayoutParams(dp(40), dp(40))
        )

        card.addView(
            shell,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )

        return card to field
    }

    private fun showChatScreen() {
        applySystemTheme()

        if (messages.isEmpty()) {
            messages += ChatMessage(
                "assistant",
                "Привет. Это **SOHR AI**. Пиши как в обычный ChatGPT — приложение само решит, когда нужен один быстрый ответ, когда перекрёстная проверка, а когда веб."
            )
            chatStore.save(messages)
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(6), dp(10), dp(4))
        }

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        titles.addView(TextView(this).apply {
            text = "SOHR AI"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })

        statusText = TextView(this).apply {
            text = "Smart · готов"
            textSize = 10.8f
            setTextColor(palette.muted)
            setPadding(0, dp(1), 0, 0)
        }

        titles.addView(statusText)

        header.addView(
            titles,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val settings = ImageButton(this).apply {
            setImageResource(R.drawable.ic_settings)
            imageTintList = ColorStateList.valueOf(palette.text)
            background = rounded(palette.surfaceAlt, 19)
            setPadding(dp(9), dp(9), dp(9), dp(9))

            setOnClickListener {
                animateTap(this)
                showSettingsDialog()
            }
        }

        header.addView(
            settings,
            LinearLayout.LayoutParams(dp(40), dp(40))
        )

        page.addView(header)

        thinkingCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), dp(5), dp(9), dp(5))
            background = rounded(palette.surface, 14)
            visibility = View.GONE
        }

        thinkingCard?.addView(
            LoadingWaveView(this, palette.accent),
            LinearLayout.LayoutParams(dp(26), dp(26))
        )

        thinkingLabel = TextView(this).apply {
            text = "Думаю…"
            textSize = 11.5f
            setTextColor(palette.muted)
            setPadding(dp(7), 0, 0, 0)
        }

        thinkingCard?.addView(
            thinkingLabel,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        page.addView(
            thinkingCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(14)
                marginEnd = dp(14)
                bottomMargin = dp(3)
            }
        )

        recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, dp(2), 0, dp(5))
            setBackgroundColor(Color.TRANSPARENT)
        }

        adapter = ChatAdapter(messages, palette)
        recycler.adapter = adapter

        page.addView(
            recycler,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(dp(10), dp(4), dp(10), dp(7))
        }

        val inputShell = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(2), dp(4), dp(2))
            background = roundedWithStroke(
                palette.surface,
                22,
                palette.stroke
            )
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
            setPadding(dp(9), dp(9), dp(7), dp(9))

            inputType =
                InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE

            imeOptions = EditorInfo.IME_ACTION_SEND

            setOnEditorActionListener { _, actionId, event ->
                val submit =
                    actionId == EditorInfo.IME_ACTION_SEND ||
                    (
                        event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                            event.action == KeyEvent.ACTION_DOWN &&
                            !event.isShiftPressed
                        )

                if (submit) {
                    sendMessage()
                    true
                } else {
                    false
                }
            }
        }

        sendButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_send)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = rounded(palette.accent, 20)
            setPadding(dp(10), dp(10), dp(10), dp(10))

            setOnClickListener {
                animateTap(this)

                if (busy) {
                    stopGeneration()
                } else {
                    sendMessage()
                }
            }
        }

        inputShell.addView(
            input,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        inputShell.addView(
            sendButton,
            LinearLayout.LayoutParams(dp(42), dp(42))
        )

        composer.addView(
            inputShell,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        page.addView(composer)

        replaceRoot(page, true)
        recycler.post { scrollToBottom() }
    }

    private fun sendMessage() {
        if (busy) return

        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return

        val geminiKey = keyStore.load("gemini")
        val groqKey = keyStore.load("groq")

        if (geminiKey.isNullOrBlank() || groqKey.isNullOrBlank()) {
            showConnectScreen()
            return
        }

        input.setText("")

        messages += ChatMessage("user", text)

        val assistantIndex = messages.size
        messages += ChatMessage("assistant", "")
        currentAssistantIndex = assistantIndex

        adapter.notifyItemRangeInserted(
            assistantIndex - 1,
            2
        )

        scrollToBottom()
        chatStore.save(messages)
        setBusy(true)

        val requestHistory = messages.dropLast(1).map { it.copy() }

        generationJob = lifecycleScope.launch {
            try {
                val result = api.reply(
                    geminiKey = geminiKey,
                    groqKey = groqKey,
                    messages = requestHistory,
                    onStage = { stage ->
                        runOnUiThread {
                            if (!busy) return@runOnUiThread
                            statusText.text = stage
                            thinkingLabel?.text = stage
                        }
                    },
                    onDelta = { delta ->
                        runOnUiThread {
                            if (!busy) return@runOnUiThread

                            val index = currentAssistantIndex
                                ?: return@runOnUiThread

                            if (index !in messages.indices) {
                                return@runOnUiThread
                            }

                            messages[index].text += delta
                            adapter.notifyItemChanged(index)
                            scrollToBottom()
                        }
                    }
                )

                val index = currentAssistantIndex

                if (index != null && index in messages.indices) {
                    messages[index].text = result.text
                    adapter.notifyItemChanged(index)
                }

                statusText.text = result.mode
                chatStore.save(messages)
                scrollToBottom()
            } catch (error: Throwable) {
                if (!busy) return@launch

                val index = currentAssistantIndex

                if (index != null && index in messages.indices) {
                    if (messages[index].text.isBlank()) {
                        messages[index].text =
                            "**Не удалось получить ответ.**\\n\\n" +
                                friendlyError(
                                    error.message,
                                    "AI"
                                )
                    } else {
                        messages[index].text +=
                            "\\n\\n_Соединение прервалось до полного завершения._"
                    }

                    adapter.notifyItemChanged(index)
                }

                statusText.text = "Ошибка"
                chatStore.save(messages)
            } finally {
                if (busy) {
                    setBusy(false)
                }
            }
        }
    }

    private fun stopGeneration() {
        api.cancel()
        generationJob?.cancel()
        generationJob = null

        val index = currentAssistantIndex

        if (
            index != null &&
            index in messages.indices &&
            messages[index].text.isBlank()
        ) {
            messages[index].text = "_Остановлено._"
            adapter.notifyItemChanged(index)
        }

        chatStore.save(messages)
        statusText.text = "Остановлено"
        setBusy(false)
    }

    private fun setBusy(value: Boolean) {
        busy = value
        thinkingCard?.visibility =
            if (value) View.VISIBLE else View.GONE

        if (::sendButton.isInitialized) {
            sendButton.setImageResource(
                if (value) R.drawable.ic_stop
                else R.drawable.ic_send
            )
            sendButton.alpha = 1f
            sendButton.isEnabled = true
        }

        if (!value) {
            currentAssistantIndex = null
            if (::statusText.isInitialized &&
                (
                    statusText.text.toString().contains("Думаю", true) ||
                    statusText.text.toString().contains("Сверяю", true) ||
                    statusText.text.toString().contains("Собираю", true) ||
                    statusText.text.toString().contains("Проверяю", true)
                    )
            ) {
                statusText.text = "Smart · готов"
            }
        }
    }

    private fun showSettingsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(18))
            background = rounded(palette.surface, 24)
        }

        box.addView(TextView(this).apply {
            text = "Настройки"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })

        box.addView(TextView(this).apply {
            text = "SOHR AI Smart 0.4"
            textSize = 11.5f
            setTextColor(palette.muted)
            setPadding(0, dp(2), 0, dp(12))
        })

        box.addView(TextView(this).apply {
            text = "Тема"
            textSize = 12.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            setPadding(dp(2), 0, 0, dp(6))
        })

        box.addView(
            themeSelector(dialog),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
            )
        )

        box.addView(
            settingActionRow(
                title = "Анимации",
                description = if (animations) "Включены" else "Выключены",
                action = if (animations) "ON" else "OFF"
            ) {
                animations = !animations
                uiPrefs.edit()
                    .putBoolean("animations", animations)
                    .apply()

                dialog.dismiss()
                showSettingsDialog()
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )

        box.addView(
            settingActionRow(
                title = "AI-подключения",
                description = "Gemini + Groq",
                action = "Сменить"
            ) {
                keyStore.clearAll()
                dialog.dismiss()
                showConnectScreen()
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(7) }
        )

        box.addView(
            settingActionRow(
                title = "История",
                description = "Очистить локальный чат",
                action = "Очистить"
            ) {
                messages.clear()
                chatStore.save(messages)
                dialog.dismiss()
                showChatScreen()
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(7) }
        )

        box.addView(TextView(this).apply {
            text = "Smart автоматически экономит лимиты: три модели запускаются только когда запрос реально сложный."
            textSize = 11f
            setTextColor(palette.muted)
            setPadding(dp(3), dp(11), dp(3), 0)
        })

        dialog.setContentView(box)

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

            attributes = attributes.apply {
                dimAmount = 0.55f
                gravity = Gravity.BOTTOM
            }
        }

        dialog.show()

        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun themeSelector(dialog: Dialog): View {
        val selector = FrameLayout(this).apply {
            background = rounded(palette.surfaceAlt, 15)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            clipChildren = true
        }

        val indicator = View(this).apply {
            background = rounded(palette.accent, 12)
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val dark = TextView(this).apply {
            text = "Тёмная"
            gravity = Gravity.CENTER
            textSize = 12.5f
            setTypeface(typeface, Typeface.BOLD)
        }

        val light = TextView(this).apply {
            text = "Светлая"
            gravity = Gravity.CENTER
            textSize = 12.5f
            setTypeface(typeface, Typeface.BOLD)
        }

        row.addView(
            dark,
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )

        row.addView(
            light,
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )

        selector.addView(
            indicator,
            FrameLayout.LayoutParams(
                0,
                dp(42),
                Gravity.START or Gravity.CENTER_VERTICAL
            )
        )

        selector.addView(
            row,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
            )
        )

        fun position(animated: Boolean) {
            val slot =
                (
                    (selector.width -
                        selector.paddingLeft -
                        selector.paddingRight) / 2f
                    ).coerceAtLeast(0f)

            if (slot <= 0f) return

            val params =
                indicator.layoutParams as FrameLayout.LayoutParams

            params.width = slot.toInt()
            indicator.layoutParams = params

            val target = if (lightTheme) slot else 0f

            indicator.animate().cancel()

            if (animated && animations) {
                indicator.animate()
                    .translationX(target)
                    .setDuration(180L)
                    .setInterpolator(
                        PathInterpolator(
                            0.22f,
                            1f,
                            0.36f,
                            1f
                        )
                    )
                    .start()
            } else {
                indicator.translationX = target
            }

            dark.setTextColor(
                if (!lightTheme) Color.WHITE else palette.muted
            )

            light.setTextColor(
                if (lightTheme) Color.WHITE else palette.muted
            )
        }

        dark.setOnClickListener {
            if (!lightTheme) return@setOnClickListener

            lightTheme = false
            uiPrefs.edit()
                .putBoolean("light_theme", false)
                .apply()

            position(true)

            selector.postDelayed({
                dialog.dismiss()
                showChatScreen()
            }, if (animations) 180L else 0L)
        }

        light.setOnClickListener {
            if (lightTheme) return@setOnClickListener

            lightTheme = true
            uiPrefs.edit()
                .putBoolean("light_theme", true)
                .apply()

            position(true)

            selector.postDelayed({
                dialog.dismiss()
                showChatScreen()
            }, if (animations) 180L else 0L)
        }

        selector.post {
            position(false)
        }

        return selector
    }

    private fun settingActionRow(
        title: String,
        description: String,
        action: String,
        onClick: () -> Unit
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(13), dp(11), dp(9), dp(11))
        background = rounded(palette.surfaceAlt, 15)

        val labels = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
        }

        labels.addView(TextView(this@MainActivity).apply {
            text = title
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })

        labels.addView(TextView(this@MainActivity).apply {
            text = description
            textSize = 11f
            setTextColor(palette.muted)
            setPadding(0, dp(2), 0, 0)
        })

        addView(
            labels,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        addView(TextView(this@MainActivity).apply {
            text = action
            textSize = 11.5f
            gravity = Gravity.CENTER
            setTextColor(palette.accent)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(9), dp(7), dp(9), dp(7))
            background = rounded(palette.accentSoft, 11)
        })

        setOnClickListener {
            animateTap(this)
            onClick()
        }
    }

    private fun actionButton(
        label: String,
        backgroundColor: Int,
        foregroundColor: Int
    ): TextView = TextView(this).apply {
        text = label
        textSize = 13.5f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(foregroundColor)
        background = rounded(backgroundColor, 15)
        isClickable = true
        isFocusable = true
    }

    private fun replaceRoot(
        view: View,
        animate: Boolean
    ) {
        val old = root.getChildAt(0)

        if (!animate || !animations || old == null) {
            root.removeAllViews()

            root.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            return
        }

        view.alpha = 0f
        view.translationY = dp(5).toFloat()

        root.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200L)
            .setInterpolator(
                PathInterpolator(
                    0.22f,
                    1f,
                    0.36f,
                    1f
                )
            )
            .withEndAction {
                if (old.parent === root) {
                    root.removeView(old)
                }
            }
            .start()

        old.animate()
            .alpha(0f)
            .setDuration(110L)
            .start()
    }

    private fun animateTap(view: View) {
        if (!animations) return

        view.animate().cancel()

        view.animate()
            .scaleX(0.97f)
            .scaleY(0.97f)
            .setDuration(65L)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150L)
                    .setInterpolator(
                        PathInterpolator(
                            0.22f,
                            1f,
                            0.36f,
                            1f
                        )
                    )
                    .start()
            }
            .start()
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                )
            )
        }
    }

    private fun friendlyError(
        raw: String?,
        provider: String
    ): String {
        val text = raw.orEmpty()

        return when {
            text.contains("401", true) ||
                text.contains("unauthorized", true) ||
                text.contains("api key", true) ||
                text.contains("invalid", true) ->
                provider + ": ключ не подошёл. Создай новый ключ и вставь его ещё раз."

            text.contains("429", true) ||
                text.contains("rate", true) ||
                text.contains("quota", true) ->
                provider + ": бесплатный лимит временно достигнут. Приложение попробует резервную модель там, где это возможно."

            text.isBlank() ->
                provider + ": неизвестная ошибка соединения."

            else -> text.take(650)
        }
    }

    private fun scrollToBottom() {
        if (::recycler.isInitialized && messages.isNotEmpty()) {
            recycler.scrollToPosition(messages.lastIndex)
        }
    }

    private fun rounded(
        color: Int,
        radiusDp: Int
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun roundedWithStroke(
        color: Int,
        radiusDp: Int,
        stroke: Int
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
