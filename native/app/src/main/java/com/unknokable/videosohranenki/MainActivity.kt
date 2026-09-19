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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
    private var player: ExoPlayer? = null
    private var currentVideos: List<VideoItem> = emptyList()
    private var currentDay: DayCollection? = null
    private var isPlayerScreen = false
    private var fullScreen = false
    private var channelChatId: Long = 0L

    private val bg = Color.parseColor("#0B0911")
    private val panel = Color.parseColor("#151120")
    private val purple = Color.parseColor("#8B5CF6")
    private val text = Color.parseColor("#F7F5FF")
    private val muted = Color.parseColor("#9E96AD")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = FrameLayout(this).apply { setBackgroundColor(bg) }
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullScreen) {
                    setFullscreen(false)
                    return
                }
                if (isPlayerScreen) {
                    player?.release()
                    player = null
                    isPlayerScreen = false
                    currentDay?.let { showDayCollection(it) } ?: showFeed(currentVideos)
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
            is TdApi.AuthorizationStateWaitPhoneNumber -> runOnUiThread { showPhoneLogin() }
            is TdApi.AuthorizationStateWaitCode -> runOnUiThread { showCodeLogin() }
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
            title = "Вход в Telegram",
            subtitle = "Это нужно один раз. Введи номер аккаунта Telegram в международном формате.",
            hint = "+48…",
            inputType = InputType.TYPE_CLASS_PHONE,
            button = "Получить код"
        ) { value ->
            launchRequest {
                client.send(TdApi.SetAuthenticationPhoneNumber(value.trim(), null))
            }
        }
    }

    private fun showCodeLogin() {
        showAuthForm(
            title = "Код из Telegram",
            subtitle = "Код придёт в приложение Telegram. Введи его здесь.",
            hint = "Код",
            inputType = InputType.TYPE_CLASS_NUMBER,
            button = "Продолжить"
        ) { value ->
            launchRequest {
                client.send(TdApi.CheckAuthenticationCode(value.trim()))
            }
        }
    }

    private fun showPasswordLogin(hint: String) {
        showAuthForm(
            title = "Облачный пароль",
            subtitle = if (hint.isBlank()) "На аккаунте включена двухэтапная проверка." else "Подсказка: $hint",
            hint = "Пароль",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            button = "Войти"
        ) { value ->
            launchRequest {
                client.send(TdApi.CheckAuthenticationPassword(value))
            }
        }
    }

    private fun showAuthForm(
        title: String,
        subtitle: String,
        hint: String,
        inputType: Int,
        button: String,
        onSubmit: (String) -> Unit
    ) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(56), dp(24), dp(24))
            setBackgroundColor(bg)
        }

        val brand = TextView(this).apply {
            text = "ВИДЕО СОХРАНЕНКИ"
            textSize = 14f
            setTextColor(Color.parseColor("#A78BFA"))
            setTypeface(typeface, Typeface.BOLD)
        }
        val titleView = TextView(this).apply {
            text = title
            textSize = 29f
            setTextColor(this@MainActivity.text)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(20), 0, 0)
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
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(panel)
        }
        val submit = Button(this).apply {
            text = button
            setTextColor(Color.WHITE)
            setBackgroundColor(purple)
            setOnClickListener {
                val value = input.text.toString()
                if (value.isBlank()) {
                    Toast.makeText(this@MainActivity, "Заполни поле", Toast.LENGTH_SHORT).show()
                } else {
                    isEnabled = false
                    onSubmit(value)
                }
            }
        }

        container.addView(brand)
        container.addView(titleView)
        container.addView(subtitleView)
        container.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        container.addView(submit, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(52)
        ).apply { topMargin = dp(14) })

        replaceRoot(container)
        input.requestFocus()
    }

    private fun launchRequest(block: suspend () -> Unit) {
        lifecycleScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    e.message ?: "Ошибка Telegram",
                    Toast.LENGTH_LONG
                ).show()
                when {
                    root.childCount == 0 -> showPhoneLogin()
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

                withContext(Dispatchers.Main) {
                    currentVideos = videos
                    currentDay = null
                    showFeed(videos)
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
                    mimeType = content.video.mimeType.ifBlank { "video/mp4" }
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
                    mimeType = mime
                )
            }
            else -> null
        }
    }

    private fun showFeed(videos: List<VideoItem>) {
        isPlayerScreen = false
        currentDay = null
        setFullscreen(false)

        val zone = ZoneId.systemDefault()
        val groups = videos
            .groupBy { Instant.ofEpochSecond(it.date.toLong()).atZone(zone).toLocalDate() }
            .map { (date, dayVideos) ->
                DayCollection(date, dayVideos.sortedByDescending { it.date })
            }
            .sortedByDescending { it.date }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(16), dp(10), dp(12))
            setBackgroundColor(bg)
        }

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val titleView = TextView(this).apply {
            text = "ВИДЕО СОХРАНЕНКИ"
            textSize = 22f
            setTextColor(this@MainActivity.text)
            setTypeface(typeface, Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "Последние 7 дней • ${groups.size} сборников • ${videos.size} видео"
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(4), 0, 0)
        }

        titles.addView(titleView)
        titles.addView(subtitle)

        val refresh = Button(this).apply {
            text = "↻"
            textSize = 22f
            setTextColor(Color.WHITE)
            setBackgroundColor(panel)
            setOnClickListener { loadVideos() }
        }

        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(refresh, LinearLayout.LayoutParams(dp(52), dp(46)))
        page.addView(header)

        val weekHint = TextView(this).apply {
            text = "Записи автоматически собраны по дням"
            textSize = 13f
            setTextColor(muted)
            setPadding(dp(18), dp(2), dp(18), dp(6))
        }
        page.addView(weekHint)

        if (groups.isEmpty()) {
            val empty = TextView(this).apply {
                text = "За последние 7 дней видео не найдено."
                setTextColor(muted)
                textSize = 16f
                gravity = Gravity.CENTER
            }
            page.addView(
                empty,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        } else {
            val list = RecyclerView(this).apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = DayCollectionAdapter(groups) { showDayCollection(it) }
                setBackgroundColor(bg)
            }
            page.addView(
                list,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        }

        replaceRoot(page)
    }

    private fun showDayCollection(collection: DayCollection) {
        currentDay = collection
        isPlayerScreen = false
        setFullscreen(false)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(12), dp(12), dp(10))
            setBackgroundColor(bg)
        }

        val back = Button(this).apply {
            text = "←"
            textSize = 21f
            setTextColor(Color.WHITE)
            setBackgroundColor(panel)
            setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }

        val titleView = TextView(this).apply {
            text = dayTitle(collection.date)
            textSize = 20f
            setTextColor(this@MainActivity.text)
            setTypeface(typeface, Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "${collection.videos.size} видео • @t2x2_video"
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(3), 0, 0)
        }

        titles.addView(titleView)
        titles.addView(subtitle)

        header.addView(back, LinearLayout.LayoutParams(dp(50), dp(46)))
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        page.addView(header)

        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = VideoAdapter(collection.videos) { openPlayer(it) }
            setBackgroundColor(bg)
        }

        page.addView(
            list,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

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

        isPlayerScreen = true
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(bg)
        }

        val back = Button(this).apply {
            text = "←"
            textSize = 21f
            setTextColor(Color.WHITE)
            setBackgroundColor(panel)
            setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }

        val titleView = TextView(this).apply {
            text = item.title
            setTextColor(this@MainActivity.text)
            textSize = 14f
            maxLines = 2
            setPadding(dp(10), 0, dp(10), 0)
        }

        val fullscreen = Button(this).apply {
            text = "⛶"
            textSize = 20f
            setTextColor(Color.WHITE)
            setBackgroundColor(panel)
            setOnClickListener { setFullscreen(!fullScreen) }
        }

        controls.addView(back, LinearLayout.LayoutParams(dp(50), dp(46)))
        controls.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(fullscreen, LinearLayout.LayoutParams(dp(50), dp(46)))

        val playerView = PlayerView(this).apply {
            useController = true
            setBackgroundColor(Color.BLACK)
        }

        page.addView(controls)
        page.addView(playerView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        replaceRoot(page)

        player?.release()
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(server.url(item)))
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    private fun setFullscreen(enabled: Boolean) {
        fullScreen = enabled
        if (enabled) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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
            setBackgroundColor(bg)
        }
        val progress = ProgressBar(this)
        val label = TextView(this).apply {
            text = message
            setTextColor(muted)
            textSize = 15f
            setPadding(0, dp(14), 0, 0)
        }
        box.addView(progress)
        box.addView(label)
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
        root.addView(view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        player?.release()
        player = null
        streamServer?.stop()
        if (::client.isInitialized) client.close()
        super.onDestroy()
    }
}
