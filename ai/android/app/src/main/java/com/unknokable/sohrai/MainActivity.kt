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
    private lateinit var prefs: SharedPreferences
    private lateinit var keyStore: SecureKeyStore
    private lateinit var chatStore: ChatStore

    private val api = OpenAiClient()
    private val messages = mutableListOf<ChatMessage>()

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var input: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var modelTitle: TextView
    private var thinkingCard: LinearLayout? = null
    private var thinkingLabel: TextView? = null
    private var generationJob: Job? = null
    private var currentAssistantIndex: Int? = null

    private var busy = false
    private var lightTheme = false
    private var animations = true

    private val palette: ThemePalette
        get() = if (lightTheme) AppThemes.Light else AppThemes.Dark

    private val modelId: String
        get() = prefs.getString(
            "model_id",
            "gpt-5.6-sol"
        ) ?: "gpt-5.6-sol"

    private val previousResponseId: String?
        get() = prefs.getString(
            "previous_response_id",
            null
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(
            "sohr_ai_ui_v6",
            MODE_PRIVATE
        )

        lightTheme = prefs.getBoolean(
            "light_theme",
            false
        )

        animations = prefs.getBoolean(
            "animations",
            true
        )

        keyStore = SecureKeyStore(this)
        chatStore = ChatStore(this)
        messages += chatStore.load()

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        root = FrameLayout(this).apply {
            setBackgroundColor(palette.background)
            clipToPadding = false
        }

        setContentView(root)

        installSafeInsets()
        applySystemTheme()
        showSplash()

        root.postDelayed({
            if (keyStore.has("openai")) {
                showChatScreen()
            } else {
                showConnectScreen()
            }
        }, 520L)
    }

    override fun onDestroy() {
        api.cancel()
        generationJob?.cancel()
        super.onDestroy()
    }

    private fun installSafeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(
            root
        ) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            val ime = insets.getInsets(
                WindowInsetsCompat.Type.ime()
            )

            view.setPadding(
                0,
                bars.top,
                0,
                maxOf(
                    bars.bottom,
                    ime.bottom
                )
            )

            if (
                ::recycler.isInitialized &&
                ime.bottom > 0
            ) {
                recycler.post {
                    scrollToBottom()
                }
            }

            insets
        }

        ViewCompat.requestApplyInsets(root)
    }

    private fun applySystemTheme() {
        root.setBackgroundColor(palette.background)

        window.statusBarColor =
            Color.TRANSPARENT

        window.navigationBarColor =
            Color.TRANSPARENT

        WindowInsetsControllerCompat(
            window,
            root
        ).apply {
            isAppearanceLightStatusBars =
                lightTheme

            isAppearanceLightNavigationBars =
                lightTheme
        }
    }

    private fun showSplash() {
        val page = FrameLayout(this).apply {
            setBackgroundColor(
                palette.background
            )
        }

        val center =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                gravity = Gravity.CENTER
            }

        val logo = TextView(this).apply {
            text = "SOHR AI"
            textSize = 30f
            gravity = Gravity.CENTER

            setTypeface(
                typeface,
                Typeface.BOLD
            )

            setTextColor(palette.text)

            alpha =
                if (animations) 0f else 1f

            scaleX =
                if (animations) 0.96f else 1f

            scaleY =
                if (animations) 0.96f else 1f
        }

        val loader =
            LoadingWaveView(
                this,
                if (lightTheme)
                    Color.BLACK
                else
                    Color.WHITE
            ).apply {
                alpha =
                    if (animations) 0f else 1f
            }

        center.addView(logo)

        center.addView(
            loader,
            LinearLayout.LayoutParams(
                dp(44),
                dp(44)
            ).apply {
                topMargin = dp(14)
                gravity =
                    Gravity.CENTER_HORIZONTAL
            }
        )

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
                .setDuration(230L)
                .setInterpolator(
                    PathInterpolator(
                        0.22f,
                        1f,
                        0.36f,
                        1f
                    )
                )
                .start()

            loader.animate()
                .alpha(1f)
                .setStartDelay(70L)
                .setDuration(150L)
                .start()
        }
    }

    private fun showConnectScreen() {
        applySystemTheme()

        val page = FrameLayout(this).apply {
            setBackgroundColor(
                palette.background
            )
        }

        val scroll =
            ScrollView(this).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode =
                    View.OVER_SCROLL_NEVER
                isFillViewport = true
            }

        val content =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER_HORIZONTAL

                setPadding(
                    dp(18),
                    dp(30),
                    dp(18),
                    dp(26)
                )
            }

        content.addView(
            TextView(this).apply {
                text = "SOHR AI"
                textSize = 30f
                gravity = Gravity.CENTER

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                setTextColor(palette.text)
            }
        )

        content.addView(
            TextView(this).apply {
                text = "Подключение OpenAI"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(palette.muted)

                setPadding(
                    0,
                    dp(6),
                    0,
                    dp(24)
                )
            }
        )

        val card =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(16),
                    dp(16),
                    dp(16),
                    dp(16)
                )

                background =
                    rounded(
                        palette.surface,
                        22
                    )
            }

        card.addView(
            TextView(this).apply {
                text = "OpenAI API key"
                textSize = 17f

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                setTextColor(palette.text)
            }
        )

        card.addView(
            TextView(this).apply {
                text =
                    "Ключ сохраняется зашифрованно на этом устройстве и не вшивается в APK."

                textSize = 12.5f
                setTextColor(palette.muted)

                setPadding(
                    0,
                    dp(5),
                    0,
                    dp(12)
                )
            }
        )

        val openKeys =
            actionButton(
                "Открыть OpenAI API keys",
                palette.surfaceAlt,
                palette.text
            )

        openKeys.setOnClickListener {
            animateTap(openKeys)

            openUrl(
                "https://platform.openai.com/api-keys"
            )
        }

        card.addView(
            openKeys,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        val keyShell =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(11),
                    dp(2),
                    dp(4),
                    dp(2)
                )

                background =
                    roundedWithStroke(
                        palette.background,
                        17,
                        palette.stroke
                    )
            }

        val keyField =
            EditText(this).apply {
                hint = "sk-…"
                setHintTextColor(palette.muted)
                setTextColor(palette.text)
                textSize = 14.5f
                maxLines = 1
                isSingleLine = true
                background = null

                inputType =
                    InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_PASSWORD

                transformationMethod =
                    PasswordTransformationMethod
                        .getInstance()

                setPadding(
                    0,
                    dp(9),
                    dp(6),
                    dp(9)
                )
            }

        val eye =
            ImageButton(this).apply {
                setImageResource(
                    R.drawable.ic_visibility
                )

                imageTintList =
                    ColorStateList.valueOf(
                        palette.muted
                    )

                background =
                    rounded(
                        palette.background,
                        18
                    )

                setPadding(
                    dp(10),
                    dp(10),
                    dp(10),
                    dp(10)
                )

                var visible = false

                setOnClickListener {
                    visible = !visible

                    setImageResource(
                        if (visible)
                            R.drawable.ic_visibility_off
                        else
                            R.drawable.ic_visibility
                    )

                    keyField.transformationMethod =
                        if (visible)
                            HideReturnsTransformationMethod
                                .getInstance()
                        else
                            PasswordTransformationMethod
                                .getInstance()

                    keyField.setSelection(
                        keyField.text.length
                    )

                    animateTap(this)
                }
            }

        keyShell.addView(
            keyField,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        keyShell.addView(
            eye,
            LinearLayout.LayoutParams(
                dp(44),
                dp(44)
            )
        )

        card.addView(
            keyShell,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        )

        val inlineStatus =
            TextView(this).apply {
                textSize = 12f
                setTextColor(palette.muted)
                visibility = View.GONE

                setPadding(
                    dp(2),
                    dp(9),
                    dp(2),
                    0
                )
            }

        card.addView(inlineStatus)

        val connect =
            actionButton(
                "Подключить",
                palette.accent,
                if (lightTheme)
                    Color.WHITE
                else
                    Color.BLACK
            )

        connect.setOnClickListener {
            animateTap(connect)

            val key =
                keyField.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()

            if (key.isBlank()) {
                inlineStatus.text =
                    "Вставь OpenAI API key."

                inlineStatus.setTextColor(
                    Color.parseColor("#EF6A6A")
                )

                inlineStatus.visibility =
                    View.VISIBLE

                return@setOnClickListener
            }

            connect.isEnabled = false
            connect.alpha = 0.55f
            connect.text = "Проверяю…"

            inlineStatus.text =
                "Проверяю доступ к GPT‑5.6 Sol…"

            inlineStatus.setTextColor(
                palette.muted
            )

            inlineStatus.visibility =
                View.VISIBLE

            lifecycleScope.launch {
                val check =
                    api.verifyKey(key)

                if (check.isSuccess) {
                    keyStore.save(
                        "openai",
                        key
                    )

                    inlineStatus.text =
                        "Готово."

                    inlineStatus.setTextColor(
                        if (lightTheme)
                            Color.parseColor("#0D7A45")
                        else
                            Color.parseColor("#7EE2A8")
                    )

                    root.postDelayed({
                        showChatScreen()
                    }, if (animations) 130L else 0L)
                } else {
                    inlineStatus.text =
                        friendlyError(
                            check.exceptionOrNull()
                                ?.message
                        )

                    inlineStatus.setTextColor(
                        Color.parseColor("#EF6A6A")
                    )

                    connect.isEnabled = true
                    connect.alpha = 1f
                    connect.text = "Подключить"
                }
            }
        }

        card.addView(
            connect,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                topMargin = dp(12)
            }
        )

        card.addView(
            TextView(this).apply {
                text =
                    "ChatGPT‑подписка и OpenAI API оплачиваются отдельно. Приложение использует баланс API твоего аккаунта."

                textSize = 10.8f
                setTextColor(palette.muted)

                setPadding(
                    dp(2),
                    dp(10),
                    dp(2),
                    0
                )
            }
        )

        content.addView(
            card,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
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

        replaceRoot(
            page,
            true
        )
    }

    private fun showChatScreen() {
        applySystemTheme()

        if (messages.isEmpty()) {
            messages += ChatMessage(
                "assistant",
                "Чем могу помочь?"
            )

            chatStore.save(messages)
        }

        val page =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setBackgroundColor(
                    palette.background
                )
            }

        val header =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(10),
                    dp(5),
                    dp(10),
                    dp(4)
                )
            }

        val newChat =
            ImageButton(this).apply {
                setImageResource(
                    R.drawable.ic_new_chat
                )

                imageTintList =
                    ColorStateList.valueOf(
                        palette.text
                    )

                background =
                    transparentSelectable()

                setPadding(
                    dp(10),
                    dp(10),
                    dp(10),
                    dp(10)
                )

                setOnClickListener {
                    animateTap(this)
                    newConversation()
                }
            }

        header.addView(
            newChat,
            LinearLayout.LayoutParams(
                dp(44),
                dp(44)
            )
        )

        modelTitle =
            TextView(this).apply {
                text =
                    modelDisplayName(
                        modelId
                    ) + " ⌄"

                textSize = 15.5f
                gravity = Gravity.CENTER

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                setTextColor(palette.text)

                setOnClickListener {
                    animateTap(this)
                    showModelDialog()
                }
            }

        header.addView(
            modelTitle,
            LinearLayout.LayoutParams(
                0,
                dp(44),
                1f
            )
        )

        val settings =
            ImageButton(this).apply {
                setImageResource(
                    R.drawable.ic_settings
                )

                imageTintList =
                    ColorStateList.valueOf(
                        palette.text
                    )

                background =
                    transparentSelectable()

                setPadding(
                    dp(10),
                    dp(10),
                    dp(10),
                    dp(10)
                )

                setOnClickListener {
                    animateTap(this)
                    showSettingsDialog()
                }
            }

        header.addView(
            settings,
            LinearLayout.LayoutParams(
                dp(44),
                dp(44)
            )
        )

        page.addView(header)

        thinkingCard =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(12),
                    dp(5),
                    dp(12),
                    dp(5)
                )

                visibility = View.GONE
            }

        thinkingCard?.addView(
            LoadingWaveView(
                this,
                if (lightTheme)
                    Color.BLACK
                else
                    Color.WHITE
            ),
            LinearLayout.LayoutParams(
                dp(24),
                dp(24)
            )
        )

        thinkingLabel =
            TextView(this).apply {
                text = "Думаю…"
                textSize = 11.8f
                setTextColor(palette.muted)

                setPadding(
                    dp(7),
                    0,
                    0,
                    0
                )
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
            }
        )

        recycler =
            RecyclerView(this).apply {
                layoutManager =
                    LinearLayoutManager(
                        this@MainActivity
                    ).apply {
                        stackFromEnd = true
                    }

                overScrollMode =
                    View.OVER_SCROLL_NEVER

                clipToPadding = false

                setPadding(
                    0,
                    dp(3),
                    0,
                    dp(8)
                )

                setBackgroundColor(
                    Color.TRANSPARENT
                )
            }

        adapter =
            ChatAdapter(
                messages,
                palette
            )

        recycler.adapter = adapter

        page.addView(
            recycler,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val composer =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(10),
                    dp(5),
                    dp(10),
                    dp(9)
                )
            }

        val shell =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(7),
                    dp(3),
                    dp(5),
                    dp(3)
                )

                background =
                    roundedWithStroke(
                        palette.surface,
                        25,
                        palette.stroke
                    )
            }

        input =
            EditText(this).apply {
                hint = "Сообщение"
                setHintTextColor(palette.muted)
                setTextColor(palette.text)
                textSize = 15.5f
                minLines = 1
                maxLines = 6
                isSingleLine = false
                background = null

                setPadding(
                    dp(9),
                    dp(9),
                    dp(8),
                    dp(9)
                )

                inputType =
                    InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE

                imeOptions =
                    EditorInfo.IME_ACTION_SEND

                setOnEditorActionListener {
                        _,
                        actionId,
                        event ->

                    val submit =
                        actionId ==
                            EditorInfo.IME_ACTION_SEND ||
                            (
                                event?.keyCode ==
                                    KeyEvent.KEYCODE_ENTER &&
                                    event.action ==
                                    KeyEvent.ACTION_DOWN &&
                                    !event.isShiftPressed
                                )

                    if (submit) {
                        if (busy) {
                            stopGeneration()
                        } else {
                            sendMessage()
                        }

                        true
                    } else {
                        false
                    }
                }
            }

        sendButton =
            ImageButton(this).apply {
                setImageResource(
                    R.drawable.ic_send
                )

                imageTintList =
                    ColorStateList.valueOf(
                        sendIconColor()
                    )

                background =
                    rounded(
                        palette.accent,
                        21
                    )

                setPadding(
                    dp(10),
                    dp(10),
                    dp(10),
                    dp(10)
                )

                setOnClickListener {
                    animateTap(this)

                    if (busy) {
                        stopGeneration()
                    } else {
                        sendMessage()
                    }
                }
            }

        shell.addView(
            input,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        shell.addView(
            sendButton,
            LinearLayout.LayoutParams(
                dp(42),
                dp(42)
            )
        )

        composer.addView(
            shell,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        page.addView(composer)

        replaceRoot(
            page,
            true
        )

        recycler.post {
            scrollToBottom()
        }
    }

    private fun sendMessage() {
        if (busy) return

        val text =
            input.text
                ?.toString()
                ?.trim()
                .orEmpty()

        if (text.isBlank()) return

        val key =
            keyStore.load("openai")

        if (key.isNullOrBlank()) {
            showConnectScreen()
            return
        }

        input.setText("")

        messages += ChatMessage(
            "user",
            text
        )

        val assistantIndex =
            messages.size

        messages += ChatMessage(
            "assistant",
            ""
        )

        currentAssistantIndex =
            assistantIndex

        adapter.notifyItemRangeInserted(
            assistantIndex - 1,
            2
        )

        chatStore.save(messages)
        scrollToBottom()
        setBusy(true)

        val history =
            messages
                .dropLast(1)
                .map { it.copy() }

        generationJob =
            lifecycleScope.launch {
                try {
                    val result =
                        api.streamResponse(
                            key = key,
                            model = modelId,
                            previousResponseId =
                                previousResponseId,
                            history = history,
                            userText = text,
                            onStage = { stage ->
                                runOnUiThread {
                                    if (!busy) {
                                        return@runOnUiThread
                                    }

                                    thinkingLabel?.text =
                                        stage
                                }
                            },
                            onDelta = { delta ->
                                runOnUiThread {
                                    if (!busy) {
                                        return@runOnUiThread
                                    }

                                    val index =
                                        currentAssistantIndex
                                            ?: return@runOnUiThread

                                    if (
                                        index !in
                                        messages.indices
                                    ) {
                                        return@runOnUiThread
                                    }

                                    messages[index].text +=
                                        delta

                                    adapter.notifyItemChanged(
                                        index
                                    )

                                    scrollToBottom()
                                }
                            }
                        )

                    val index =
                        currentAssistantIndex

                    if (
                        index != null &&
                        index in messages.indices
                    ) {
                        messages[index].text =
                            renderResult(result)

                        adapter.notifyItemChanged(
                            index
                        )
                    }

                    result.responseId
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?.let {
                            prefs.edit()
                                .putString(
                                    "previous_response_id",
                                    it
                                )
                                .apply()
                        }

                    chatStore.save(messages)
                    scrollToBottom()
                } catch (error: Throwable) {
                    if (!busy) {
                        return@launch
                    }

                    val index =
                        currentAssistantIndex

                    if (
                        index != null &&
                        index in messages.indices
                    ) {
                        if (
                            messages[index].text
                                .isBlank()
                        ) {
                            messages[index].text =
                                "**Не удалось получить ответ.**\n\n" +
                                    friendlyError(
                                        error.message
                                    )
                        } else {
                            messages[index].text +=
                                "\n\n_Соединение прервалось. Уже полученный текст сохранён._"
                        }

                        adapter.notifyItemChanged(
                            index
                        )
                    }

                    chatStore.save(messages)
                } finally {
                    if (busy) {
                        setBusy(false)
                    }
                }
            }
    }

    private fun renderResult(
        result: OpenAiResult
    ): String {
        if (result.sources.isEmpty()) {
            return result.text
        }

        return buildString {
            append(
                result.text.trim()
            )

            append("\n\n**Источники**")

            result.sources
                .distinctBy { it.second }
                .take(8)
                .forEach { source ->
                    append("\n- [")
                    append(
                        source.first
                            .replace("[", "")
                            .replace("]", "")
                    )
                    append("](")
                    append(source.second)
                    append(")")
                }
        }
    }

    private fun stopGeneration() {
        api.cancel()

        generationJob?.cancel()
        generationJob = null

        val index =
            currentAssistantIndex

        if (
            index != null &&
            index in messages.indices &&
            messages[index].text.isBlank()
        ) {
            messages[index].text =
                "_Остановлено._"

            adapter.notifyItemChanged(index)
        }

        chatStore.save(messages)

        setBusy(false)
    }

    private fun setBusy(value: Boolean) {
        busy = value

        thinkingCard?.visibility =
            if (value)
                View.VISIBLE
            else
                View.GONE

        if (::sendButton.isInitialized) {
            sendButton.setImageResource(
                if (value)
                    R.drawable.ic_stop
                else
                    R.drawable.ic_send
            )

            sendButton.imageTintList =
                ColorStateList.valueOf(
                    sendIconColor()
                )
        }

        if (!value) {
            currentAssistantIndex = null
        }
    }

    private fun newConversation() {
        if (busy) {
            stopGeneration()
        }

        messages.clear()
        chatStore.clear()

        prefs.edit()
            .remove(
                "previous_response_id"
            )
            .apply()

        showChatScreen()
    }

    private fun showModelDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(
            Window.FEATURE_NO_TITLE
        )

        val box =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(16),
                    dp(16),
                    dp(16),
                    dp(18)
                )

                background =
                    rounded(
                        palette.surface,
                        24
                    )
            }

        box.addView(
            TextView(this).apply {
                text = "Модель"
                textSize = 20f

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                setTextColor(palette.text)
            }
        )

        box.addView(
            TextView(this).apply {
                text =
                    "GPT‑5.6 Sol — баланс качества и цены. GPT‑6 Astra Max — максимум качества, но заметно дороже."

                textSize = 11.5f
                setTextColor(palette.muted)

                setPadding(
                    0,
                    dp(4),
                    0,
                    dp(12)
                )
            }
        )

        box.addView(
            modelRow(
                title = "GPT‑5.6 Sol",
                description =
                    "По умолчанию · сильный чат",
                selected =
                    modelId ==
                        "gpt-5.6-sol"
            ) {
                chooseModel(
                    dialog,
                    "gpt-5.6-sol"
                )
            }
        )

        box.addView(
            modelRow(
                title = "GPT‑6 Astra Max",
                description =
                    "Самая мощная модель OpenAI · дороже",
                selected =
                    modelId ==
                        "gpt-6-astra"
            ) {
                chooseModel(
                    dialog,
                    "gpt-6-astra"
                )
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        )

        dialog.setContentView(box)

        prepareBottomDialog(dialog)
    }

    private fun chooseModel(
        dialog: Dialog,
        id: String
    ) {
        prefs.edit()
            .putString(
                "model_id",
                id
            )
            .remove(
                "previous_response_id"
            )
            .apply()

        dialog.dismiss()

        if (::modelTitle.isInitialized) {
            modelTitle.text =
                modelDisplayName(id) +
                    " ⌄"
        }
    }

    private fun modelRow(
        title: String,
        description: String,
        selected: Boolean,
        onClick: () -> Unit
    ): View =
        LinearLayout(this).apply {
            orientation =
                LinearLayout.HORIZONTAL

            gravity =
                Gravity.CENTER_VERTICAL

            setPadding(
                dp(14),
                dp(12),
                dp(12),
                dp(12)
            )

            background =
                rounded(
                    if (selected)
                        palette.accentSoft
                    else
                        palette.background,
                    17
                )

            val labels =
                LinearLayout(
                    this@MainActivity
                ).apply {
                    orientation =
                        LinearLayout.VERTICAL
                }

            labels.addView(
                TextView(
                    this@MainActivity
                ).apply {
                    text = title
                    textSize = 14f

                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )

                    setTextColor(
                        palette.text
                    )
                }
            )

            labels.addView(
                TextView(
                    this@MainActivity
                ).apply {
                    text = description
                    textSize = 11f
                    setTextColor(
                        palette.muted
                    )

                    setPadding(
                        0,
                        dp(2),
                        0,
                        0
                    )
                }
            )

            addView(
                labels,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            addView(
                TextView(
                    this@MainActivity
                ).apply {
                    text =
                        if (selected)
                            "✓"
                        else
                            ""

                    textSize = 18f
                    gravity = Gravity.CENTER
                    setTextColor(
                        palette.text
                    )
                },
                LinearLayout.LayoutParams(
                    dp(34),
                    dp(34)
                )
            )

            setOnClickListener {
                animateTap(this)
                onClick()
            }
        }

    private fun showSettingsDialog() {
        val dialog = Dialog(this)

        dialog.requestWindowFeature(
            Window.FEATURE_NO_TITLE
        )

        val box =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(16),
                    dp(16),
                    dp(16),
                    dp(18)
                )

                background =
                    rounded(
                        palette.surface,
                        24
                    )
            }

        box.addView(
            TextView(this).apply {
                text = "Настройки"
                textSize = 20f

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                setTextColor(palette.text)
            }
        )

        box.addView(
            TextView(this).apply {
                text = "SOHR AI · OpenAI v0.6"
                textSize = 11.5f
                setTextColor(palette.muted)

                setPadding(
                    0,
                    dp(2),
                    0,
                    dp(12)
                )
            }
        )

        box.addView(
            settingRow(
                "Тема",
                if (lightTheme)
                    "Светлая"
                else
                    "Тёмная",
                "Сменить"
            ) {
                lightTheme =
                    !lightTheme

                prefs.edit()
                    .putBoolean(
                        "light_theme",
                        lightTheme
                    )
                    .apply()

                dialog.dismiss()
                showChatScreen()
            }
        )

        box.addView(
            settingRow(
                "Анимации",
                if (animations)
                    "Включены"
                else
                    "Выключены",
                if (animations)
                    "ON"
                else
                    "OFF"
            ) {
                animations =
                    !animations

                prefs.edit()
                    .putBoolean(
                        "animations",
                        animations
                    )
                    .apply()

                dialog.dismiss()
                showSettingsDialog()
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        )

        box.addView(
            settingRow(
                "OpenAI API key",
                "Зашифрован на устройстве",
                "Сменить"
            ) {
                keyStore.clear("openai")

                prefs.edit()
                    .remove(
                        "previous_response_id"
                    )
                    .apply()

                dialog.dismiss()
                showConnectScreen()
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        )

        box.addView(
            settingRow(
                "Новая беседа",
                "Очистить локальную историю",
                "Очистить"
            ) {
                dialog.dismiss()
                newConversation()
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        )

        box.addView(
            TextView(this).apply {
                text =
                    "Веб‑поиск включён как инструмент: модель сама использует его, когда информация может быть свежей."

                textSize = 10.8f
                setTextColor(palette.muted)

                setPadding(
                    dp(3),
                    dp(11),
                    dp(3),
                    0
                )
            }
        )

        dialog.setContentView(box)

        prepareBottomDialog(dialog)
    }

    private fun settingRow(
        title: String,
        description: String,
        action: String,
        onClick: () -> Unit
    ): View =
        LinearLayout(this).apply {
            orientation =
                LinearLayout.HORIZONTAL

            gravity =
                Gravity.CENTER_VERTICAL

            setPadding(
                dp(13),
                dp(11),
                dp(9),
                dp(11)
            )

            background =
                rounded(
                    palette.background,
                    16
                )

            val labels =
                LinearLayout(
                    this@MainActivity
                ).apply {
                    orientation =
                        LinearLayout.VERTICAL
                }

            labels.addView(
                TextView(
                    this@MainActivity
                ).apply {
                    text = title
                    textSize = 13.5f

                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )

                    setTextColor(
                        palette.text
                    )
                }
            )

            labels.addView(
                TextView(
                    this@MainActivity
                ).apply {
                    text = description
                    textSize = 11f
                    setTextColor(
                        palette.muted
                    )

                    setPadding(
                        0,
                        dp(2),
                        0,
                        0
                    )
                }
            )

            addView(
                labels,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            addView(
                TextView(
                    this@MainActivity
                ).apply {
                    text = action
                    textSize = 11.5f
                    gravity = Gravity.CENTER

                    setTextColor(
                        palette.text
                    )

                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )

                    setPadding(
                        dp(9),
                        dp(7),
                        dp(9),
                        dp(7)
                    )

                    background =
                        rounded(
                            palette.accentSoft,
                            12
                        )
                }
            )

            setOnClickListener {
                animateTap(this)
                onClick()
            }
        }

    private fun prepareBottomDialog(
        dialog: Dialog
    ) {
        dialog.window?.apply {
            setBackgroundDrawableResource(
                android.R.color.transparent
            )

            addFlags(
                WindowManager.LayoutParams
                    .FLAG_DIM_BEHIND
            )

            attributes =
                attributes.apply {
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

    private fun modelDisplayName(
        id: String
    ): String =
        when (id) {
            "gpt-6-astra" ->
                "GPT‑6 Astra Max"

            else ->
                "GPT‑5.6 Sol"
        }

    private fun friendlyError(
        raw: String?
    ): String {
        val text =
            raw.orEmpty()

        return when {
            text.contains(
                "401",
                true
            ) ||
                text.contains(
                    "invalid api key",
                    true
                ) ->
                "OpenAI API key не подошёл. Проверь ключ и вставь его ещё раз."

            text.contains(
                "insufficient_quota",
                true
            ) ||
                text.contains(
                    "billing",
                    true
                ) ||
                text.contains(
                    "quota",
                    true
                ) ->
                "На OpenAI API нет доступного баланса или достигнут лимит аккаунта."

            text.contains(
                "429",
                true
            ) ||
                text.contains(
                    "rate limit",
                    true
                ) ->
                "OpenAI временно ограничил частоту запросов. Повтори чуть позже."

            text.contains(
                "model",
                true
            ) &&
                (
                    text.contains(
                        "access",
                        true
                    ) ||
                        text.contains(
                            "not found",
                            true
                        )
                    ) ->
                "У этого API‑ключа нет доступа к выбранной модели. Выбери GPT‑5.6 Sol."

            text.contains(
                "timeout",
                true
            ) ->
                "Ответ занял слишком долго и был остановлен."

            text.isBlank() ->
                "Неизвестная ошибка OpenAI API."

            else ->
                text.take(650)
        }
    }

    private fun actionButton(
        label: String,
        backgroundColor: Int,
        foregroundColor: Int
    ): TextView =
        TextView(this).apply {
            text = label
            textSize = 13.5f
            gravity = Gravity.CENTER

            setTypeface(
                typeface,
                Typeface.BOLD
            )

            setTextColor(
                foregroundColor
            )

            background =
                rounded(
                    backgroundColor,
                    16
                )

            isClickable = true
            isFocusable = true
        }

    private fun replaceRoot(
        view: View,
        animate: Boolean
    ) {
        val old =
            root.getChildAt(0)

        if (
            !animate ||
            !animations ||
            old == null
        ) {
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
        view.translationY =
            dp(4).toFloat()

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
            .setDuration(180L)
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
            .setDuration(100L)
            .start()
    }

    private fun animateTap(
        view: View
    ) {
        if (!animations) return

        view.animate().cancel()

        view.animate()
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(55L)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(135L)
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

    private fun openUrl(
        url: String
    ) {
        runCatching {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                )
            )
        }
    }

    private fun scrollToBottom() {
        if (
            ::recycler.isInitialized &&
            messages.isNotEmpty()
        ) {
            recycler.scrollToPosition(
                messages.lastIndex
            )
        }
    }

    private fun sendIconColor(): Int =
        if (lightTheme)
            Color.WHITE
        else
            Color.BLACK

    private fun transparentSelectable(): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = dp(18).toFloat()
        }

    private fun rounded(
        color: Int,
        radiusDp: Int
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius =
                dp(radiusDp).toFloat()
        }

    private fun roundedWithStroke(
        color: Int,
        radiusDp: Int,
        stroke: Int
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius =
                dp(radiusDp).toFloat()

            setStroke(
                dp(1),
                stroke
            )
        }

    private fun dp(value: Int): Int =
        (
            value *
                resources
                    .displayMetrics
                    .density
            ).toInt()
}
