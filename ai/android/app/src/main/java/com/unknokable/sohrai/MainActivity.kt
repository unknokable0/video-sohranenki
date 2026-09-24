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
    private var busy = false
    private var lightTheme = false
    private var animations = true
    private var stageText: String? = null

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
            if (keyStore.load().isNullOrBlank()) showConnectScreen() else showChatScreen()
        }, 680L)
    }

    private fun installSafeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = maxOf(bars.bottom, ime.bottom)
            view.setPadding(0, bars.top, 0, bottom)
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
        val page = FrameLayout(this).apply { setBackgroundColor(palette.background) }
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val logo = TextView(this).apply {
            text = "SOHR AI"
            textSize = 32f
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

        val status = TextView(this).apply {
            text = "Free Fusion"
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTextColor(palette.muted)
            setPadding(0, dp(10), 0, 0)
            alpha = if (animations) 0f else 1f
        }

        center.addView(logo)
        center.addView(loader, LinearLayout.LayoutParams(dp(52), dp(52)).apply {
            topMargin = dp(16)
            gravity = Gravity.CENTER_HORIZONTAL
        })
        center.addView(status)

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
                .setDuration(300L)
                .setInterpolator(PathInterpolator(0.22f, 1f, 0.36f, 1f))
                .start()
            loader.animate().alpha(1f).setStartDelay(100L).setDuration(180L).start()
            status.animate().alpha(1f).setStartDelay(160L).setDuration(180L).start()
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
            setPadding(dp(16), dp(14), dp(16), dp(24))
        }

        content.addView(TextView(this).apply {
            text = "SOHR AI"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            gravity = Gravity.CENTER
        })

        content.addView(TextView(this).apply {
            text = "Free Fusion · 0 zł за ответы"
            textSize = 13.5f
            setTextColor(palette.accent)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(14))
        })

        val modelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf("Nemotron", "Laguna", "Ling").forEachIndexed { index, label ->
            modelRow.addView(
                providerBadge(label),
                LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                    if (index > 0) marginStart = dp(5)
                }
            )
        }
        content.addView(modelRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(palette.surface, 20)
        }
        content.addView(
            card,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        )

        card.addView(TextView(this).apply {
            text = "Бесплатное подключение"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })

        card.addView(TextView(this).apply {
            text = "Нужен только бесплатный ключ OpenRouter. Карта не нужна. Приложение использует только модели с ценой 0."
            textSize = 12.5f
            setTextColor(palette.muted)
            setPadding(0, dp(5), 0, dp(12))
            setLineSpacing(dp(2).toFloat(), 1f)
        })

        card.addView(
            actionButton("Получить бесплатный ключ", palette.accentSoft, palette.text).apply {
                setOnClickListener {
                    animateTap(this)
                    openUrl("https://openrouter.ai/settings/keys")
                }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
        )

        card.addView(TextView(this).apply {
            text = "Войди или зарегистрируйся → Create API Key → скопируй ключ → вернись сюда."
            textSize = 11.5f
            setTextColor(palette.muted)
            setPadding(dp(2), dp(9), dp(2), dp(12))
        })

        val keyShell = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), dp(2), dp(4), dp(2))
            background = roundedWithStroke(palette.surfaceAlt, 16, palette.stroke)
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
                keyInput.transformationMethod =
                    if (visible) HideReturnsTransformationMethod.getInstance()
                    else PasswordTransformationMethod.getInstance()
                keyInput.setSelection(keyInput.text.length)
                animateTap(this)
            }
        }

        keyShell.addView(keyInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        keyShell.addView(eye, LinearLayout.LayoutParams(dp(44), dp(44)))
        card.addView(keyShell)

        val inlineStatus = TextView(this).apply {
            textSize = 11.5f
            setTextColor(palette.muted)
            visibility = View.GONE
            setPadding(dp(2), dp(9), dp(2), 0)
        }
        card.addView(inlineStatus)

        card.addView(
            actionButton("Подключить Free Fusion", palette.accent, Color.WHITE).apply {
                setOnClickListener {
                    animateTap(this)
                    val key = keyInput.text.toString().trim()
                    if (key.isBlank()) {
                        inlineStatus.text = "Вставь ключ OpenRouter."
                        inlineStatus.setTextColor(Color.parseColor("#FF7A9A"))
                        inlineStatus.visibility = View.VISIBLE
                        return@setOnClickListener
                    }

                    isEnabled = false
                    alpha = 0.65f
                    text = "Проверяю…"
                    inlineStatus.text = "Проверяем бесплатный ключ…"
                    inlineStatus.setTextColor(palette.muted)
                    inlineStatus.visibility = View.VISIBLE

                    lifecycleScope.launch {
                        val result = api.verifyKey(key)
                        if (result.isSuccess) {
                            keyStore.save(key)
                            inlineStatus.text = "Готово."
                            inlineStatus.setTextColor(palette.accent)
                            postTransition { showChatScreen() }
                        } else {
                            inlineStatus.text = friendlyError(result.exceptionOrNull()?.message)
                            inlineStatus.setTextColor(Color.parseColor("#FF7A9A"))
                            isEnabled = true
                            alpha = 1f
                            text = "Подключить Free Fusion"
                        }
                    }
                }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
                topMargin = dp(12)
            }
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(palette.accentSoft, 17)
        }
        info.addView(TextView(this).apply {
            text = "Что внутри"
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        })
        info.addView(TextView(this).apply {
            text = "Nemotron 3 Ultra + Laguna S 2.1 + Ling 3.0 Flash дают независимые ответы. Бесплатный финальный проход сверяет их и делает один ответ. Никаких платных моделей и автоматических списаний."
            textSize = 11.8f
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

        content.addView(TextView(this).apply {
            text = "На бесплатном OpenRouter есть дневной лимит запросов. Это ограничение сервиса, но платить не нужно."
            textSize = 10.8f
            setTextColor(palette.muted)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), 0)
        })

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

    private fun showChatScreen() {
        applySystemTheme()

        if (messages.isEmpty()) {
            messages += ChatMessage(
                "assistant",
                "Привет. Это **SOHR AI Free Fusion**. Один запрос сверяют несколько бесплатных моделей, а ты получаешь один аккуратный итог."
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
            setPadding(dp(14), dp(7), dp(10), dp(5))
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
            text = "Free Fusion · 3 модели · 0 zł"
            textSize = 11f
            setTextColor(palette.muted)
            setPadding(0, dp(1), 0, 0)
        }
        titles.addView(statusText)

        header.addView(
            titles,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val settingsButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_settings)
            imageTintList = ColorStateList.valueOf(palette.text)
            background = rounded(palette.surfaceAlt, 20)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener {
                animateTap(this)
                showSettingsDialog()
            }
        }
        header.addView(settingsButton, LinearLayout.LayoutParams(dp(42), dp(42)))
        page.addView(header)

        val badges = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), 0, dp(14), dp(5))
        }
        listOf("Nemotron", "Laguna", "Ling").forEachIndexed { index, label ->
            badges.addView(
                providerBadge(label),
                LinearLayout.LayoutParams(0, dp(28), 1f).apply {
                    if (index > 0) marginStart = dp(4)
                }
            )
        }
        page.addView(badges)

        thinkingCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = rounded(palette.surface, 15)
            visibility = View.GONE
        }
        thinkingCard?.addView(
            LoadingWaveView(this, palette.accent),
            LinearLayout.LayoutParams(dp(28), dp(28))
        )
        thinkingCard?.addView(
            TextView(this).apply {
                tag = "thinking_text"
                text = "Free Fusion думает…"
                textSize = 11.8f
                setTextColor(palette.muted)
                setPadding(dp(7), 0, 0, 0)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
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
            setPadding(0, dp(2), 0, dp(6))
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
            setPadding(dp(10), dp(5), dp(10), dp(8))
        }

        val inputShell = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(2), dp(4), dp(2))
            background = roundedWithStroke(palette.surface, 22, palette.stroke)
        }

        input = EditText(this).apply {
            hint = "Сообщение…"
            setHintTextColor(palette.muted)
            setTextColor(palette.text)
            textSize = 15.5f
            minLines = 1
            maxLines = 5
            isSingleLine = false
            background = null
            setPadding(dp(9), dp(9), dp(8), dp(9))
            inputType =
                InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, actionId, event ->
                val sendAction =
                    actionId == EditorInfo.IME_ACTION_SEND ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                        event.action == KeyEvent.ACTION_DOWN &&
                        !event.isShiftPressed)
                if (sendAction) {
                    sendMessage()
                    true
                } else false
            }
        }

        sendButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_send)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = rounded(palette.accent, 21)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener {
                animateTap(this)
                sendMessage()
            }
        }

        inputShell.addView(
            input,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        inputShell.addView(sendButton, LinearLayout.LayoutParams(dp(42), dp(42)))
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
                val result = api.fusion(key, history) { stage ->
                    stageText = stage
                    thinkingCard?.findViewWithTag<TextView>("thinking_text")?.text = stage
                    statusText.text = stage
                }
                messages[assistantIndex].text = result.text
                adapter.notifyItemChanged(assistantIndex)
                statusText.text = result.concreteModel ?: "Free Fusion · готово"
                store.save(messages)
                scrollToBottom()
            } catch (error: Throwable) {
                messages[assistantIndex].text =
                    "**Не удалось получить ответ.**\n\n" + friendlyError(error.message)
                adapter.notifyItemChanged(assistantIndex)
                statusText.text = "Free Fusion · ошибка"
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
            sendButton.alpha = if (value) 0.42f else 1f
        }
        if (::input.isInitialized) input.isEnabled = !value

        thinkingCard?.visibility = if (value) View.VISIBLE else View.GONE

        if (value) {
            stageText = "Запускаю бесплатные модели…"
            thinkingCard?.findViewWithTag<TextView>("thinking_text")?.text = stageText
            if (::statusText.isInitialized) statusText.text = "Free Fusion думает…"
        } else {
            stageText = null
            if (::statusText.isInitialized && statusText.text.contains("думает", true)) {
                statusText.text = "Free Fusion · 3 модели · 0 zł"
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
            text = "SOHR AI Free Fusion 0.3"
            textSize = 11.5f
            setTextColor(palette.muted)
            setPadding(0, dp(2), 0, dp(13))
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
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
        )

        box.addView(
            settingActionRow(
                title = "Анимации",
                description = if (animations) "Включены" else "Выключены",
                action = if (animations) "ON" else "OFF"
            ) {
                animations = !animations
                uiPrefs.edit().putBoolean("animations", animations).apply()
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
                title = "OpenRouter Free",
                description = "Ключ зашифрован на этом устройстве",
                action = "Сменить"
            ) {
                keyStore.clear()
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
                title = "История чата",
                description = "Удалить локальную историю",
                action = "Очистить"
            ) {
                messages.clear()
                store.save(messages)
                dialog.dismiss()
                showChatScreen()
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(7) }
        )

        box.addView(
            actionButton("Открыть OpenRouter", palette.surfaceAlt, palette.text).apply {
                setOnClickListener {
                    animateTap(this)
                    openUrl("https://openrouter.ai/settings/keys")
                }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                topMargin = dp(10)
            }
        )

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

        row.addView(dark, LinearLayout.LayoutParams(0, dp(42), 1f))
        row.addView(light, LinearLayout.LayoutParams(0, dp(42), 1f))
        selector.addView(
            indicator,
            FrameLayout.LayoutParams(0, dp(42), Gravity.START or Gravity.CENTER_VERTICAL)
        )
        selector.addView(
            row,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42))
        )

        fun position(animated: Boolean) {
            val slot =
                ((selector.width - selector.paddingLeft - selector.paddingRight) / 2f)
                    .coerceAtLeast(0f)
            if (slot <= 0f) return

            val params = indicator.layoutParams as FrameLayout.LayoutParams
            params.width = slot.toInt()
            indicator.layoutParams = params

            val target = if (lightTheme) slot else 0f
            indicator.animate().cancel()

            if (animated && animations) {
                indicator.animate()
                    .translationX(target)
                    .setDuration(190L)
                    .setInterpolator(PathInterpolator(0.22f, 1f, 0.36f, 1f))
                    .start()
            } else {
                indicator.translationX = target
            }

            dark.setTextColor(if (!lightTheme) Color.WHITE else palette.muted)
            light.setTextColor(if (lightTheme) Color.WHITE else palette.muted)
        }

        dark.setOnClickListener {
            if (!lightTheme) return@setOnClickListener
            lightTheme = false
            uiPrefs.edit().putBoolean("light_theme", false).apply()
            position(true)
            selector.postDelayed(
                { dialog.dismiss(); showChatScreen() },
                if (animations) 190L else 0L
            )
        }

        light.setOnClickListener {
            if (lightTheme) return@setOnClickListener
            lightTheme = true
            uiPrefs.edit().putBoolean("light_theme", true).apply()
            position(true)
            selector.postDelayed(
                { dialog.dismiss(); showChatScreen() },
                if (animations) 190L else 0L
            )
        }

        selector.post { position(false) }
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
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
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

    private fun providerBadge(label: String): View =
        TextView(this).apply {
            text = label
            textSize = 10.8f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            background = rounded(palette.surfaceAlt, 12)
        }

    private fun actionButton(label: String, bg: Int, fg: Int): TextView =
        TextView(this).apply {
            text = label
            textSize = 13.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(fg)
            background = rounded(bg, 16)
            isClickable = true
            isFocusable = true
        }

    private fun replaceRoot(view: View, animate: Boolean) {
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
            .setDuration(210L)
            .setInterpolator(PathInterpolator(0.22f, 1f, 0.36f, 1f))
            .withEndAction {
                if (old.parent === root) root.removeView(old)
            }
            .start()

        old.animate().alpha(0f).setDuration(120L).start()
    }

    private fun postTransition(block: () -> Unit) {
        root.postDelayed(block, if (animations) 140L else 0L)
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
                    .setDuration(155L)
                    .setInterpolator(PathInterpolator(0.22f, 1f, 0.36f, 1f))
                    .start()
            }
            .start()
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun friendlyError(raw: String?): String {
        val text = raw.orEmpty()
        return when {
            text.contains("401", true) ||
                text.contains("unauthorized", true) ||
                (text.contains("key", true) && text.contains("invalid", true)) ->
                "Ключ не подошёл. Создай новый бесплатный ключ OpenRouter."

            text.contains("402", true) ||
                text.contains("credit", true) ||
                text.contains("balance", true) ->
                "Запрос случайно попал на платный endpoint. В этой версии платные модели не используются — повтори запрос."

            text.contains("429", true) ||
                text.contains("rate", true) ->
                "Дневной или минутный бесплатный лимит OpenRouter закончился. Оплата не требуется — лимит восстановится по правилам OpenRouter."

            text.isBlank() ->
                "Неизвестная ошибка соединения."

            else -> text.take(650)
        }
    }

    private fun scrollToBottom() {
        if (::recycler.isInitialized && messages.isNotEmpty()) {
            recycler.scrollToPosition(messages.lastIndex)
        }
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
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
