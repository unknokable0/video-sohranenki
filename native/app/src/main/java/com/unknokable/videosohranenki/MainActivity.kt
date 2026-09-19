package com.unknokable.videosohranenki

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.tdlibandroid.ktx.TdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var client: TdClient
    private lateinit var root: FrameLayout
    private var streamServer: TelegramStreamServer? = null
    private var playerScreen: PlayerScreen? = null
    private lateinit var settings: AppSettings
    private var currentVideos: List<VideoItem> = emptyList()
    private var currentDay: DayCollection? = null
    private var isPlayerScreen = false
    private var isSettingsScreen = false
    private var fullScreen = false
    private var channelChatId: Long = 0L
    private var pendingCodeState: TdApi.AuthorizationStateWaitCode? = null
    private var authSubmitButton: Button? = null
    private var authErrorView: TextView? = null
    private var requestedPhoneNumber: String? = null

    private val palette get() = settings.palette()
    private val bg get() = palette.background
    private val panel get() = palette.surface
    private val purple get() = palette.accent
    private val text get() = palette.text
    private val muted get() = palette.muted

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settings = AppSettings(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        root = FrameLayout(this).apply { setBackgroundColor(bg) }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            if (!fullScreen) {
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(0, bars.top, 0, bars.bottom)
            } else {
                view.setPadding(0, 0, 0, 0)
            }
            insets
        }
        applySystemTheme()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullScreen) {
                    setFullscreen(false)
                    return
                }
                if (isPlayerScreen) {
                    playerScreen?.destroy()
                    playerScreen = null
                    isPlayerScreen = false
                    currentDay?.let { showDayCollection(it) } ?: showFeed(currentVideos)
                    return
                }
                if (isSettingsScreen) {
                    isSettingsScreen = false
                    showFeed(currentVideos)
                    return
                }
                if (currentDay != null) {
                    currentDay = null
                    showFeed(currentVideos)
                    return
                }
                finish()
            }
        })

        if (BuildConfig.TELEGRAM_API_ID == 0 || BuildConfig.TELEGRAM_API_HASH.isBlank()) {
            showMessage(
                "Не подключены Telegram API ключи",
                "Проверь TELEGRAM_API_ID и TELEGRAM_API_HASH в GitHub Actions Secrets."
            )
            return
        }

        try {
            System.loadLibrary("tdjni")
        } catch (e: Throwable) {
            showMessage("Ошибка TDLib", e.message ?: "Не удалось загрузить tdjni")
            return
        }

        client = TdClient(
            filesDir = filesDir.absolutePath + "/tdlib",
            verbosityLevel = 1,
            apiId = BuildConfig.TELEGRAM_API_ID,
            apiHash = BuildConfig.TELEGRAM_API_HASH
        )

        lifecycleScope.launch {
            client.updates.collect { update ->
                when (update) {
                    is TdApi.UpdateAuthorizationState -> handleAuthState(update.authorizationState)
                    is TdApi.UpdateNewMessage -> {
                        if (channelChatId != 0L && update.message.chatId == channelChatId) {
                            loadVideos()
                        }
                    }
                }
            }
        }

        showLoading("Подключаем Telegram…")
        client.init()
    }

    private fun handleAuthState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> runOnUiThread {
                pendingCodeState = null
                showPhoneLogin()
            }
            is TdApi.AuthorizationStateWaitCode -> runOnUiThread {
                pendingCodeState = state
                showCodeLogin(state)
            }
            is TdApi.AuthorizationStateWaitPassword -> runOnUiThread { showPasswordLogin(state.passwordHint ?: "") }
            is TdApi.AuthorizationStateReady -> {
                ensureStreamServer()
                loadVideos()
            }
            is TdApi.AuthorizationStateLoggingOut -> runOnUiThread { showLoading("Выходим…") }
            is TdApi.AuthorizationStateClosing -> runOnUiThread { showLoading("Закрываем соединение…") }
            is TdApi.AuthorizationStateClosed -> Unit
        }
    }

    private fun showPhoneLogin() {
        showAuthForm(
            title = "Вход",
            subtitle = "Введи номер телефона, который привязан к Telegram.",
            hint = "+48 123 456 789",
            inputType = InputType.TYPE_CLASS_PHONE,
            button = "Продолжить",
            footer = "Как войти:\n1. Введи номер обязательно с + и кодом страны.\n2. Нажми «Продолжить».\n3. Telegram отправит код — обычно в приложение Telegram, иногда по SMS или другим доступным способом.\n4. Введи полученный код на следующем экране.",
            showBack = false
        ) { value ->
            val normalized = value.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
            if (!normalized.startsWith("+") || normalized.drop(1).any { !it.isDigit() } || normalized.length < 9) {
                throw IllegalArgumentException("Введи номер в формате +48123456789")
            }
            requestedPhoneNumber = normalized
            client.send(TdApi.SetAuthenticationPhoneNumber(normalized, null))
        }
    }

    private fun showCodeLogin(state: TdApi.AuthorizationStateWaitCode) {
        val info = state.codeInfo
        val expected = requestedPhoneNumber
        val actual = normalizePhone(info.phoneNumber)
        if (!expected.isNullOrBlank() && actual.isNotBlank() && normalizePhone(expected) != actual) {
            authErrorView?.apply {
                text = "Telegram ждёт код для другого номера. Авторизация будет сброшена."
                visibility = View.VISIBLE
            }
            resetTelegramAuthorization("Номер авторизации не совпал с введённым")
            return
        }
        val delivery = authCodeDeliveryLabel(info.type?.javaClass?.simpleName.orEmpty())
        val nextDelivery = authCodeDeliveryLabel(info.nextType?.javaClass?.simpleName.orEmpty())
        val timeout = info.timeout.coerceAtLeast(0)

        val extra = buildString {
            append("Код отправлен: ")
            append(delivery)
            if (info.phoneNumber.isNotBlank()) append("\nНомер: ${info.phoneNumber}")
            if (timeout > 0 && nextDelivery.isNotBlank()) {
                append("\nПовторная отправка через $timeout сек. Следующий способ: $nextDelivery.")
            } else if (nextDelivery.isNotBlank()) {
                append("\nМожно запросить код ещё раз: $nextDelivery.")
            }
        }

        showAuthForm(
            title = "Код подтверждения",
            subtitle = extra,
            hint = "Код",
            inputType = InputType.TYPE_CLASS_NUMBER,
            button = "Продолжить",
            footer = "Если код не появился, сначала проверь официальный Telegram на других устройствах. Затем попробуй «Отправить код ещё раз».",
            showBack = true,
            secondaryButton = "Отправить код ещё раз",
            onBack = { resetTelegramAuthorization("Смена номера") },
            onSecondary = {
                launchRequest {
                    client.send(TdApi.ResendAuthenticationCode(null))
                }
            }
        ) { value ->
            val code = value.trim()
            if (code.length < 3 || code.any { !it.isDigit() }) {
                throw IllegalArgumentException("Проверь код подтверждения")
            }
            client.send(TdApi.CheckAuthenticationCode(code))
        }
    }

    private fun showPasswordLogin(hint: String) {
        showAuthForm(
            title = "Облачный пароль",
            subtitle = if (hint.isBlank()) "На аккаунте включена двухэтапная проверка." else "Подсказка: $hint",
            hint = "Пароль",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            button = "Войти",
            footer = "Это пароль двухэтапной защиты Telegram. Он не сохраняется в приложении.",
            showBack = true,
            onBack = { resetTelegramAuthorization("Смена номера") }
        ) { value ->
            if (value.isBlank()) throw IllegalArgumentException("Введи пароль")
            client.send(TdApi.CheckAuthenticationPassword(value))
        }
    }

    private fun normalizePhone(value: String): String =
        value.filter { it.isDigit() }

    private fun resetTelegramAuthorization(reason: String) {
        lifecycleScope.launch {
            try {
                showLoading("Сбрасываем вход…")
                runCatching { client.send(TdApi.Close()) }
                kotlinx.coroutines.delay(500)
            } finally {
                runCatching {
                    java.io.File(filesDir, "tdlib").deleteRecursively()
                }
                requestedPhoneNumber = null
                recreate()
            }
        }
    }

    private fun authCodeDeliveryLabel(className: String): String {
        val name = className.lowercase()
        return when {
            "telegrammessage" in name -> "в Telegram"
            "sms" in name -> "по SMS"
            "call" in name -> "телефонным звонком"
            "fragment" in name -> "через Fragment"
            "email" in name -> "на email"
            name.isBlank() -> "Telegram"
            else -> "Telegram ($className)"
        }
    }

    private fun showAuthForm(
        title: String,
        subtitle: String,
        hint: String,
        inputType: Int,
        button: String,
        footer: String? = null,
        showBack: Boolean = false,
        secondaryButton: String? = null,
        onBack: (() -> Unit)? = null,
        onSecondary: (() -> Unit)? = null,
        onSubmit: suspend (String) -> Unit
    ) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(24))
            setBackgroundColor(bg)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = roundedBg(panel, 24)
        }

        val brand = TextView(this).apply {
            text = "VS"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedBg(purple, 16)
        }
        if (showBack) {
            val back = TextView(this).apply {
                text = "‹  Назад"
                textSize = 14f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(this@MainActivity.text)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = roundedBg(palette.surfaceAlt, 14)
                setOnClickListener {
                    animatePress(this)
                    onBack?.invoke()
                }
            }
            card.addView(back, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(42)
            ).apply { bottomMargin = dp(4) })
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 29f
            setTextColor(this@MainActivity.text)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(16), 0, 0)
        }
        val subtitleView = TextView(this).apply {
            text = subtitle
            textSize = 15f
            setTextColor(muted)
            setPadding(0, dp(8), 0, dp(24))
        }
        val input = EditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            setTextColor(this@MainActivity.text)
            setHintTextColor(muted)
            setSingleLine(true)
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = roundedBg(palette.surfaceAlt, 16)
        }
        val errorView = TextView(this).apply {
            textSize = 12.5f
            setTextColor(Color.parseColor("#FF6B81"))
            visibility = View.GONE
            setPadding(dp(2), dp(10), dp(2), 0)
        }
        authErrorView = errorView

        val submit = Button(this).apply {
            text = button
            setTextColor(Color.WHITE)
            background = roundedBg(purple, 16)
            setOnClickListener {
                animatePress(this)
                val value = input.text.toString()
                if (value.isBlank()) {
                    errorView.text = "Заполни поле"
                    errorView.visibility = View.VISIBLE
                    return@setOnClickListener
                }

                errorView.visibility = View.GONE
                isEnabled = false
                alpha = 0.72f
                lifecycleScope.launch {
                    try {
                        onSubmit(value)
                    } catch (e: Exception) {
                        val message = friendlyAuthError(e.message)
                        errorView.text = message
                        errorView.visibility = View.VISIBLE
                        this@apply.isEnabled = true
                        this@apply.alpha = 1f
                    }
                }
            }
        }
        authSubmitButton = submit

        card.addView(brand, LinearLayout.LayoutParams(dp(52), dp(52)))
        card.addView(titleView)
        card.addView(subtitleView)
        card.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(52)
        ))
        card.addView(submit, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(52)
        ).apply { topMargin = dp(12) })
        card.addView(errorView)

        secondaryButton?.let { secondaryLabel ->
            val secondary = TextView(this).apply {
                text = secondaryLabel
                textSize = 14f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(purple)
                background = roundedBg(palette.surfaceAlt, 16)
                setOnClickListener {
                    animatePress(this)
                    onSecondary?.invoke()
                }
            }
            card.addView(secondary, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply { topMargin = dp(10) })
        }

        footer?.let { helpText ->
            val help = TextView(this).apply {
                text = helpText
                textSize = 12.5f
                setTextColor(muted)
                setPadding(dp(2), dp(14), dp(2), 0)
                setLineSpacing(0f, 1.15f)
            }
            card.addView(help)
        }

        container.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        replaceRoot(container)
        input.requestFocus()
    }

    private fun animatePress(view: View) {
        if (!settings.animations) return
        view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(65).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(105).start()
        }.start()
    }

    private fun friendlyAuthError(raw: String?): String {
        val text = raw.orEmpty()
        return when {
            "PHONE_NUMBER_INVALID" in text -> "Номер телефона неверный. Введи его с + и кодом страны."
            "PHONE_NUMBER_FLOOD" in text -> "Слишком много попыток. Telegram временно ограничил отправку кода."
            "PHONE_CODE_INVALID" in text -> "Код неверный. Проверь цифры и попробуй ещё раз."
            "PHONE_CODE_EXPIRED" in text -> "Код уже истёк. Нажми «Отправить код ещё раз»."
            "PASSWORD_HASH_INVALID" in text -> "Неверный облачный пароль."
            "API_ID" in text -> "Ошибка Telegram API. Нужно проверить API ID/API Hash приложения."
            text.isBlank() -> "Telegram не принял запрос. Попробуй ещё раз."
            else -> text
        }
    }

    private fun launchRequest(block: suspend () -> Unit) {
        lifecycleScope.launch {
            try {
                block()
            } catch (e: Exception) {
                val message = friendlyAuthError(e.message)
                authErrorView?.let {
                    it.text = message
                    it.visibility = View.VISIBLE
                } ?: Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                authSubmitButton?.apply {
                    isEnabled = true
                    alpha = 1f
                }
            }
        }
    }

    private fun ensureStreamServer() {
        if (streamServer != null) return
        try {
            streamServer = TelegramStreamServer(client).also {
                it.start(10_000, false)
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Не удалось запустить видеопоток: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadVideos() {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) { showLoading("Собираем записи за неделю…") }
            try {
                val chat = client.send(TdApi.SearchPublicChat("t2x2_video"))
                channelChatId = chat.id

                val zone = ZoneId.systemDefault()
                val cutoffDate = LocalDate.now(zone).minusDays(6)
                val cutoffEpoch = cutoffDate.atStartOfDay(zone).toEpochSecond()

                val collected = linkedMapOf<Long, VideoItem>()
                var fromMessageId = 0L
                var page = 0
                var reachedOldMessages = false

                while (page < 20 && !reachedOldMessages) {
                    val history = client.send(
                        TdApi.GetChatHistory(chat.id, fromMessageId, 0, 100, false)
                    )
                    if (history.messages.isEmpty()) break

                    for (message in history.messages) {
                        if (message.date.toLong() < cutoffEpoch) {
                            reachedOldMessages = true
                            continue
                        }
                        messageToVideo(message)?.let { collected[it.messageId] = it }
                    }

                    val last = history.messages.lastOrNull() ?: break
                    if (last.id == fromMessageId) break
                    fromMessageId = last.id
                    page++
                }

                val videos = collected.values
                    .filter { it.date.toLong() >= cutoffEpoch }
                    .sortedByDescending { it.date }

                val preparedVideos = if (settings.previews) {
                    videos.map { item -> attachThumbnail(item) }
                } else {
                    videos.map { it.copy(thumbnailPath = null) }
                }

                withContext(Dispatchers.Main) {
                    currentVideos = preparedVideos
                    currentDay = null
                    showFeed(preparedVideos)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showMessage(
                        "Не удалось загрузить записи",
                        e.message ?: "Неизвестная ошибка Telegram"
                    )
                }
            }
        }
    }

    private fun messageToVideo(message: TdApi.Message): VideoItem? {
        return when (val content = message.content) {
            is TdApi.MessageVideo -> {
                val file = content.video.video
                VideoItem(
                    messageId = message.id,
                    title = content.caption.text.trim().ifBlank {
                        content.video.fileName.ifBlank { "Запись стрима" }
                    },
                    date = message.date,
                    durationSeconds = content.video.duration,
                    fileId = file.id,
                    fileSize = if (file.size > 0) file.size else file.expectedSize,
                    mimeType = content.video.mimeType.ifBlank { "video/mp4" },
                    thumbnailFileId = content.video.thumbnail?.file?.id
                )
            }
            is TdApi.MessageDocument -> {
                val doc = content.document
                val mime = doc.mimeType.ifBlank { "application/octet-stream" }
                val name = doc.fileName
                val isVideo = mime.startsWith("video/") ||
                    name.endsWith(".mp4", true) ||
                    name.endsWith(".mkv", true) ||
                    name.endsWith(".mov", true) ||
                    name.endsWith(".webm", true)
                if (!isVideo) return null
                val file = doc.document
                VideoItem(
                    messageId = message.id,
                    title = content.caption.text.trim().ifBlank {
                        name.ifBlank { "Запись стрима" }
                    },
                    date = message.date,
                    durationSeconds = 0,
                    fileId = file.id,
                    fileSize = if (file.size > 0) file.size else file.expectedSize,
                    mimeType = mime,
                    thumbnailFileId = doc.thumbnail?.file?.id
                )
            }
            else -> null
        }
    }

    private suspend fun attachThumbnail(item: VideoItem): VideoItem {
        val thumbId = item.thumbnailFileId ?: return item
        return try {
            val file = client.send(TdApi.DownloadFile(thumbId, 1, 0, 0, true))
            val path = file.local.path.takeIf { it.isNotBlank() && file.local.isDownloadingCompleted }
            item.copy(thumbnailPath = path)
        } catch (_: Exception) {
            item
        }
    }

    private fun showFeed(videos: List<VideoItem>) {
        isPlayerScreen = false
        isSettingsScreen = false
        currentDay = null
        setFullscreen(false)
        applySystemTheme()

        val zone = ZoneId.systemDefault()
        val baseGroups = videos
            .groupBy { Instant.ofEpochSecond(it.date.toLong()).atZone(zone).toLocalDate() }
            .map { (date, dayVideos) ->
                DayCollection(date, dayVideos.sortedByDescending { it.date })
            }

        val groups = when (settings.collectionSort) {
            CollectionSort.NEWEST -> baseGroups.sortedByDescending { it.date }
            CollectionSort.OLDEST -> baseGroups.sortedBy { it.date }
            CollectionSort.MOST_VIDEOS -> baseGroups.sortedByDescending { it.videos.size }
            CollectionSort.LONGEST -> baseGroups.sortedByDescending { it.totalDurationSeconds }
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(10))
            setBackgroundColor(bg)
        }

        val titleView = TextView(this).apply {
            text = "ВИДЕО СОХРАНЕНКИ"
            textSize = 24f
            gravity = Gravity.START
            maxLines = 1
            setTextColor(this@MainActivity.text)
            setTypeface(typeface, Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "Последние 7 дней • ${groups.size} сборников • ${videos.size} видео"
            textSize = 12f
            gravity = Gravity.START
            maxLines = 1
            setTextColor(muted)
            setPadding(0, dp(5), 0, dp(12))
        }

        val controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val sectionTitle = TextView(this).apply {
            text = "Сборники по дням"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(muted)
        }

        val refresh = ImageButton(this).apply {
            setImageResource(R.drawable.ic_refresh)
            background = roundedBg(palette.surfaceAlt, 16)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener {
                if (!isEnabled) return@setOnClickListener
                isEnabled = false
                loadVideos()
                postDelayed({ isEnabled = true }, 800)
            }
        }

        val settingsButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_settings)
            background = roundedBg(palette.surfaceAlt, 16)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener {
                if (!isEnabled) return@setOnClickListener
                isEnabled = false
                showSettings()
                postDelayed({ isEnabled = true }, 500)
            }
        }

        controlRow.addView(sectionTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controlRow.addView(refresh, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(8) })
        controlRow.addView(settingsButton, LinearLayout.LayoutParams(dp(48), dp(48)))

        header.addView(titleView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        header.addView(subtitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        header.addView(controlRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        page.addView(header)

        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), dp(10))
        }

        val sort = TextView(this).apply {
            text = "⇅  ${settings.collectionSort.label}"
            textSize = 12f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(purple)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedBg(palette.surface, 16)
            setOnClickListener { showCollectionSortDialog() }
        }

        tools.addView(sort)
        page.addView(tools)

        if (groups.isEmpty()) {
            val empty = TextView(this).apply {
                text = "За последние 7 дней видео не найдено."
                setTextColor(muted)
                textSize = 16f
                gravity = Gravity.CENTER
            }
            page.addView(empty, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            val list = RecyclerView(this).apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = DayCollectionAdapter(groups, palette, settings.animations) { showDayCollection(it) }
                setBackgroundColor(bg)
                setHasFixedSize(true)
                itemAnimator = if (settings.animations) itemAnimator else null
            }
            page.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        replaceRoot(page)
    }

    private fun showDayCollection(collection: DayCollection) {
        currentDay = collection
        isPlayerScreen = false
        isSettingsScreen = false
        setFullscreen(false)
        applySystemTheme()

        val sortedVideos = when (settings.videoSort) {
            VideoSort.NEWEST -> collection.videos.sortedByDescending { it.date }
            VideoSort.OLDEST -> collection.videos.sortedBy { it.date }
            VideoSort.LONGEST -> collection.videos.sortedByDescending { it.durationSeconds }
            VideoSort.SHORTEST -> collection.videos.sortedBy { it.durationSeconds }
            VideoSort.LARGEST -> collection.videos.sortedByDescending { it.fileSize }
            VideoSort.SMALLEST -> collection.videos.sortedBy { it.fileSize }
            VideoSort.TITLE -> collection.videos.sortedBy { it.title.lowercase(Locale("ru")) }
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
            setBackgroundColor(bg)
        }

        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            background = roundedBg(palette.surfaceAlt, 24)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val titleView = TextView(this).apply {
            text = dayTitle(collection.date)
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(this@MainActivity.text)
            setTypeface(typeface, Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "${collection.videos.size} видео • @t2x2_video"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(0, dp(3), 0, 0)
        }

        titles.addView(titleView)
        titles.addView(subtitle)

        val spacer = View(this)
        header.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(spacer, LinearLayout.LayoutParams(dp(48), dp(48)))
        page.addView(header)

        val sortRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(2), dp(16), dp(8))
        }

        val countLabel = TextView(this).apply {
            text = "Видео в сборнике"
            textSize = 13f
            setTextColor(muted)
        }

        val sort = TextView(this).apply {
            text = "⇅  ${settings.videoSort.label}"
            textSize = 12f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(purple)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = roundedBg(palette.surface, 14)
            setOnClickListener { showVideoSortDialog(collection) }
        }

        sortRow.addView(countLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sortRow.addView(sort)
        page.addView(sortRow)

        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = VideoAdapter(sortedVideos, palette, settings.animations) { openPlayer(it) }
            setBackgroundColor(bg)
            setHasFixedSize(true)
            itemAnimator = if (settings.animations) itemAnimator else null
        }

        page.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        replaceRoot(page)
    }

    private fun dayTitle(date: LocalDate): String {
        val today = LocalDate.now()
        val formatted = date.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))
        return when (date) {
            today -> "Сегодня • $formatted"
            today.minusDays(1) -> "Вчера • $formatted"
            else -> formatted.replaceFirstChar { it.titlecase(Locale("ru")) }
        }
    }

    private fun openPlayer(item: VideoItem) {
        val server = streamServer
        if (server == null) {
            Toast.makeText(this, "Видеопоток ещё не готов", Toast.LENGTH_SHORT).show()
            return
        }

        playerScreen?.destroy()
        isSettingsScreen = false
        isPlayerScreen = true

        playerScreen = PlayerScreen(
            activity = this,
            item = item,
            mediaUrl = server.url(item),
            settings = settings,
            onBack = { onBackPressedDispatcher.onBackPressed() },
            onFullscreen = { setFullscreen(it) }
        )

        replaceRoot(playerScreen!!.root)
    }

    private fun showSettings() {
        isSettingsScreen = true
        isPlayerScreen = false
        applySystemTheme()
        val screen = SettingsScreen(
            this,
            settings,
            onBack = { needsReload ->
                isSettingsScreen = false
                if (needsReload) loadVideos() else showFeed(currentVideos)
            },
            onThemeChanged = {
                applySystemTheme()
                showSettings()
            },
            onLogout = { confirmLogout() }
        )
        replaceRoot(screen.build())
    }

    private fun showCollectionSortDialog() {
        val values = CollectionSort.values()
        ModernDialogs.showChoices(
            context = this,
            palette = palette,
            title = "Сортировка сборников",
            options = values.map { it.label },
            selected = settings.collectionSort.ordinal
        ) { which ->
            settings.collectionSort = values[which]
            showFeed(currentVideos)
        }
    }

    private fun showVideoSortDialog(collection: DayCollection) {
        val values = VideoSort.values()
        ModernDialogs.showChoices(
            context = this,
            palette = palette,
            title = "Сортировка видео",
            options = values.map { it.label },
            selected = settings.videoSort.ordinal
        ) { which ->
            settings.videoSort = values[which]
            showDayCollection(collection)
        }
    }

    private fun confirmLogout() {
        ModernDialogs.showConfirm(
            context = this,
            palette = palette,
            title = "Выйти из аккаунта?",
            message = "Сессия будет удалена с этого телефона. При следующем входе снова понадобится номер и код.",
            confirm = "Выйти",
            destructive = true
        ) {
            lifecycleScope.launch {
                try {
                    showLoading("Выходим из аккаунта…")
                    client.send(TdApi.LogOut())
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, e.message ?: "Не удалось выйти", Toast.LENGTH_LONG).show()
                    showSettings()
                }
            }
        }
    }

    private fun setFullscreen(enabled: Boolean) {
        fullScreen = enabled
        playerScreen?.setFullscreenMode(enabled)
        ViewCompat.requestApplyInsets(root)
        if (enabled) {
            if (settings.autoRotateFullscreen) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.let {
                    it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            } else {
                @Suppress("DEPRECATION")
                run { window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE }
            }
        }
    }

    private fun showLoading(message: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(bg)
        }

        val spinner = LoadingWaveView(this, purple)
        val label = TextView(this).apply {
            text = message
            setTextColor(muted)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        }

        box.addView(spinner, LinearLayout.LayoutParams(dp(72), dp(72)))
        box.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        replaceRoot(box)
    }

    private fun showMessage(title: String, description: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(bg)
        }
        val titleView = TextView(this).apply {
            text = title
            textSize = 23f
            setTextColor(this@MainActivity.text)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val descriptionView = TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        box.addView(titleView)
        box.addView(descriptionView)
        replaceRoot(box)
    }

    private fun replaceRoot(view: View) {
        root.removeAllViews()
        if (settings.animations) {
            view.alpha = 0f
            view.translationY = dp(6).toFloat()
        }
        root.addView(view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        if (settings.animations) {
            view.animate().alpha(1f).translationY(0f).setDuration(180).start()
        }
    }

    private fun roundedBg(color: Int, radiusDp: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun applySystemTheme() {
        root.setBackgroundColor(bg)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            val appearance = if (settings.lightTheme) mask else 0
            window.insetsController?.setSystemBarsAppearance(appearance, mask)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (settings.lightTheme) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        playerScreen?.destroy()
        playerScreen = null
        streamServer?.stop()
        if (::client.isInitialized) client.close()
        super.onDestroy()
    }
}
