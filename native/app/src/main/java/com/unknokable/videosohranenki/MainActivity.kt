package com.unknokable.videosohranenki

import android.Manifest
import android.app.Dialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.telephony.TelephonyManager
import android.util.Rational
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewAnimationUtils
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.i18n.phonenumbers.PhoneNumberUtil
import coil.load
import coil.transform.CircleCropTransformation
import io.github.tdlibandroid.ktx.TdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
    private var twitchPlayerScreen: TwitchPlayerScreen? = null
    private var twitchLivePlayerScreen: TwitchLivePlayerScreen? = null
    private var currentStreamingItem: VideoItem? = null
    private lateinit var settings: AppSettings
    private lateinit var streakTracker: StreakTracker
    private lateinit var updateManager: SohrUpdateManager
    private lateinit var videoCache: SohrVideoCache
    private lateinit var experienceStore: SohrExperienceStore
    private lateinit var downloadStore: SohrDownloadStore
    private var smartDownloadJob: kotlinx.coroutines.Job? = null
    private var previousVisitAtMs = 0L
    private var telegramReady = false
    private var pendingUpdateApk: File? = null
    private var waitingForInstallPermission = false
    private var updateProgressLabel: TextView? = null
    private var currentVideos: List<VideoItem> = emptyList()
    private var telegramVideos: List<VideoItem> = emptyList()
    private var twitchVideos: List<VideoItem> = emptyList()
    private var twitchLoadJob: kotlinx.coroutines.Job? = null
    private var twitchAuthJob: kotlinx.coroutines.Job? = null
    private var twitchAuthDialog: Dialog? = null
    private var twitchLiveJob: kotlinx.coroutines.Job? = null
    private var t2x2WatchJob: kotlinx.coroutines.Job? = null
    private var t2x2LiveSlot: FrameLayout? = null
    private var lastT2x2Live: TwitchLiveStream? = null
    private var lastT2x2LiveCheckedAt = 0L
    private var lastT2x2LiveUnavailable = false
    private val t2x2LiveCacheMs = 20_000L
    private val temporaryLivePreviewCacheMs = 60_000L
    private val temporaryBlockedLiveLogins = linkedSetOf<String>()
    // LIVE preview experiment is finished. The production card tracks T2x2 only.
    private val temporaryLivePreviewEnabled = false
    private var pendingTwitchWelcome = false
    private var pendingTwitchLiveAfterAuth: TwitchLiveStream? = null
    private var currentDay: DayCollection? = null
    private var isPlayerScreen = false
    private var playerBackInProgress = false
    private var isSettingsScreen = false
    private var isAccountScreen = false
    private var isStreakScreen = false
    private var fullScreen = false
    private var channelChatId: Long = 0L
    private var pendingCodeState: TdApi.AuthorizationStateWaitCode? = null
    private var authSubmitButton: Button? = null
    private var authErrorView: TextView? = null
    private var requestedPhoneNumber: String? = null
    private var authResetInProgress = false
    private var loadJob: kotlinx.coroutines.Job? = null
    private var reloadRequested = false
    private var suppressNextRootAnimation = false
    private var suppressNextContentAnimation = false
    private var currentPrimaryTab = SohrTab.VIDEOS
    private var pendingRootSlide = 0
    private var primaryShell: LinearLayout? = null
    private var primaryContentHost: FrameLayout? = null
    private var primaryNav: SohrBottomNavView? = null
    private var primaryShellLightTheme: Boolean? = null
    private var primaryShellAccent: String? = null
    private var startupPhase = true
    private var startupStatusView: TextView? = null
    private var completedUpdateNotice: String? = null
    private var completedUpdateNotes: String? = null
    private var feedRefreshButton: LinearLayout? = null
    private var feedRefreshLabel: TextView? = null
    private var feedRefreshLoader: LoadingWaveView? = null
    private var feedRefreshCompletedFlash = false
    private var feedAutoRefreshJob: kotlinx.coroutines.Job? = null
    private var lastFeedAutoRefreshAt = 0L
    private val telegramAutoRefreshIntervalMs = 15_000L
    private val twitchAutoRefreshIntervalMs = 180_000L
    private var startupUpdateCheckDone = false
    private var updateAutoCheckJob: kotlinx.coroutines.Job? = null
    private val automaticUpdateCheckIntervalMs = 6L * 60L * 60L * 1000L
    private var onboardingActive = false
    private var videoSection = 1 // 1 home, 2 feed, 3 watched
    private var pendingVideoSectionCrossfade = false
    private var pendingVideoSectionDirection = 0
    private var videoSectionSwitchLocked = false
    private var settingsScrollY = 0
    private var todayLiveStatusView: TextView? = null
    private var todayLiveDot: LivePulseView? = null
    private var todaySummaryView: TextView? = null
    private var auxiliaryScreen: String? = null
    private val inlinePreviewHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var inlinePreviewPlayer: androidx.media3.exoplayer.ExoPlayer? = null
    private var inlinePreviewView: androidx.media3.ui.PlayerView? = null
    private var inlinePreviewHost: FrameLayout? = null
    private var inlinePreviewStop: Runnable? = null
    private var feedSearchOpen = false
    private var closeFeedSearch: (() -> Boolean)? = null
    private var searchSystemBackCallback: android.window.OnBackInvokedCallback? = null
    private var predictiveBackTarget: View? = null
    private var twitchNetworkCooldownUntilElapsed = 0L
    private val activeManualDownloads = linkedSetOf<Int>()
    private val activeTwitchDownloads = linkedSetOf<Long>()

    private val palette get() = settings.palette()
    private val bg get() = palette.background
    private val panel get() = palette.surface
    private val purple get() = palette.accent
    private val text get() = palette.text
    private val muted get() = palette.muted
    private fun t(key: String): String = AppLanguages.t(settings.languageCode, key)

    override fun onCreate(savedInstanceState: Bundle?) {
        val systemSplash = installSplashScreen()
        super.onCreate(savedInstanceState)

        systemSplash.setOnExitAnimationListener { provider ->
            val splashView = provider.view
            val iconView = provider.iconView
            splashView.animate().cancel()
            iconView.animate().cancel()
            if (savedInstanceState == null) {
                iconView.scaleX = 0.94f
                iconView.scaleY = 0.94f
                iconView.animate()
                    .scaleX(1.07f)
                    .scaleY(1.07f)
                    .alpha(0f)
                    .setDuration(SohrMotion.HERO)
                    .setInterpolator(SohrMotion.smooth())
                    .start()
                splashView.animate()
                    .alpha(0f)
                    .setDuration(SohrMotion.HERO)
                    .setInterpolator(SohrMotion.smooth())
                    .withEndAction { provider.remove() }
                    .start()
            } else {
                provider.remove()
            }
        }

        settings = AppSettings(this)
        streakTracker = StreakTracker(this)
        updateManager = SohrUpdateManager(this)
        videoCache = SohrVideoCache(this)
        experienceStore = SohrExperienceStore(this)
        downloadStore = SohrDownloadStore(this)

        val runtimePrefs = getSharedPreferences("sohr_runtime", MODE_PRIVATE)
        previousVisitAtMs = if (savedInstanceState == null) {
            experienceStore.beginVisit().also {
                runtimePrefs.edit().putLong("session_previous_visit_ms", it).apply()
            }
        } else {
            runtimePrefs.getLong("session_previous_visit_ms", 0L)
        }
        val previousVersionCode = runtimePrefs.getInt("last_version_code", 0)
        val pendingUpdateVersion = runtimePrefs.getString("pending_update_version_name", null)
        val pendingUpdateNotes = runtimePrefs.getString("pending_update_notes", null).orEmpty()

        if (previousVersionCode > 0 && previousVersionCode < BuildConfig.VERSION_CODE) {
            completedUpdateNotice = BuildConfig.VERSION_NAME
            completedUpdateNotes =
                pendingUpdateNotes
                    .takeIf { pendingUpdateVersion == BuildConfig.VERSION_NAME && it.isNotBlank() }
                    ?: fallbackReleaseNotes(BuildConfig.VERSION_NAME)
        }

        runtimePrefs.edit()
            .putInt("last_version_code", BuildConfig.VERSION_CODE)
            .apply()

        if (
            previousVersionCode > 0 &&
            previousVersionCode < BuildConfig.VERSION_CODE &&
            pendingUpdateVersion == BuildConfig.VERSION_NAME
        ) {
            runtimePrefs.edit()
                .remove("pending_update_version_name")
                .remove("pending_update_notes")
                .apply()
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        root = FrameLayout(this).apply { setBackgroundColor(bg) }
        setContentView(root)
        setupT2x2Notifications()
        if (!handleTwitchAuthIntent(intent, loadAfter = false)) handleSharedIntent(intent)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            if (!fullScreen) {
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                view.setPadding(0, bars.top, 0, maxOf(bars.bottom, ime.bottom))
            } else {
                view.setPadding(0, 0, 0, 0)
            }
            insets
        }
        applySystemTheme()
        showStartupSplash()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                predictiveBackTarget = root.getChildAt(root.childCount - 1)
                predictiveBackTarget?.animate()?.cancel()
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                val target = predictiveBackTarget ?: return
                val progress = backEvent.progress.coerceIn(0f, 1f)
                target.pivotX = if (backEvent.swipeEdge == BackEventCompat.EDGE_LEFT) 0f else target.width.toFloat()
                target.pivotY = target.height / 2f
                target.scaleX = 1f - 0.028f * progress
                target.scaleY = 1f - 0.028f * progress
                target.translationX =
                    (if (backEvent.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f) *
                        dp(18) * progress
                target.alpha = 1f - 0.08f * progress
            }

            override fun handleOnBackCancelled() {
                resetPredictiveBackSurface(animated = true)
            }

            override fun handleOnBackPressed() {
                resetPredictiveBackSurface(animated = false)
                if (
                    feedSearchOpen &&
                    !isPlayerScreen &&
                    !isSettingsScreen &&
                    !isAccountScreen &&
                    !isStreakScreen &&
                    currentDay == null &&
                    closeFeedSearch?.invoke() == true
                ) {
                    return
                }
                if (closeCurrentPlayerScreen()) return

                if (auxiliaryScreen != null) {
                    auxiliaryScreen = null
                    pendingRootSlide = -1
                    showSelectedVideoSource()
                    return
                }

                if (isSettingsScreen) {
                    isSettingsScreen = false
                    pendingRootSlide = -1
                    showSelectedVideoSource()
                    return
                }
                if (isAccountScreen) {
                    isAccountScreen = false
                    pendingRootSlide = -1
                    showSelectedVideoSource()
                    return
                }
                if (isStreakScreen) {
                    isStreakScreen = false
                    pendingRootSlide = -1
                    showSelectedVideoSource()
                    return
                }
                if (currentDay != null) {
                    currentDay = null
                    pendingRootSlide = -1
                    showSelectedVideoSource()
                    return
                }
                finish()
            }
        })

        if (settings.guestMode) {
            root.postDelayed({ enterGuestMode(showNotice = false) }, 360L)
            return
        }

        if (BuildConfig.TELEGRAM_API_ID == 0 || BuildConfig.TELEGRAM_API_HASH.isBlank()) {
            showMessage(
                "Не подключены Telegram API ключи",
                "Проверьте TELEGRAM_API_ID и TELEGRAM_API_HASH в GitHub Actions Secrets."
            )
            return
        }

        try {
            System.loadLibrary("tdjni")
        } catch (e: Throwable) {
            showMessage("Ошибка TDLib", e.message ?: "Не удалось загрузить tdjni")
            return
        }

        requestedPhoneNumber = settings.authPhone
        val tdlibDir = filesDir.absolutePath + "/tdlib_session_" + settings.authGeneration
        client = TdClient(
            filesDir = tdlibDir,
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
                            scheduleFeedAutoRefresh(delayMs = 900L, force = true)
                        }
                    }
                }
            }
        }

        root.post {
            showLoading("Подключаем Telegram…")
            client.init()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!handleTwitchAuthIntent(intent, loadAfter = true)) handleSharedIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        startT2x2LiveWatch()
        if (android.os.Build.VERSION.SDK_INT >= 26 && !isInPictureInPictureMode) {
            stopService(Intent(this, PlaybackKeepAliveService::class.java))
        }
        if (waitingForInstallPermission && updateManager.canRequestInstall()) {
            waitingForInstallPermission = false
            pendingUpdateApk?.takeIf { it.exists() }?.let { apk ->
                root.postDelayed({ launchUpdateInstaller(apk) }, 220L)
            }
        }
        if (!startupPhase && !isPlayerScreen && !isSettingsScreen && !isAccountScreen && !isStreakScreen) {
            scheduleFeedAutoRefresh(delayMs = 550L, force = false)
            scheduleAutomaticUpdateCheck(delayMs = 1_100L, force = false)
        }
    }

    private fun scheduleAutomaticUpdateCheck(delayMs: Long = 900L, force: Boolean = false) {
        if (updateAutoCheckJob?.isActive == true) return

        val runtimePrefs = getSharedPreferences("sohr_runtime", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastCheckAt = runtimePrefs.getLong("last_auto_update_check_at", 0L)
        if (!force && lastCheckAt > 0L && now - lastCheckAt < automaticUpdateCheckIntervalMs) return

        updateAutoCheckJob = lifecycleScope.launch {
            delay(delayMs)
            runtimePrefs.edit().putLong("last_auto_update_check_at", System.currentTimeMillis()).apply()
            checkForUpdates(manual = false)
            updateAutoCheckJob = null
        }
    }

    private fun scheduleFeedAutoRefresh(delayMs: Long = 900L, force: Boolean = false) {
        if (settings.guestMode) return
        if (settings.videoSource == "telegram" && !telegramReady) return

        if (feedAutoRefreshJob?.isActive == true && !force) return
        feedAutoRefreshJob?.cancel()

        feedAutoRefreshJob = lifecycleScope.launch {
            if (delayMs > 0L) delay(delayMs)
            var forceNext = force

            while (isActive) {
                if (
                    isPlayerScreen ||
                    isSettingsScreen ||
                    isAccountScreen ||
                    isStreakScreen
                ) {
                    delay(1_000L)
                    continue
                }

                if (settings.videoSource == "telegram" && !telegramReady) {
                    delay(1_000L)
                    continue
                }

                val minInterval =
                    if (settings.videoSource == "twitch") {
                        twitchAutoRefreshIntervalMs
                    } else {
                        telegramAutoRefreshIntervalMs
                    }

                val now = System.currentTimeMillis()
                val elapsed = now - lastFeedAutoRefreshAt
                if (!forceNext && lastFeedAutoRefreshAt > 0L && elapsed < minInterval) {
                    delay((minInterval - elapsed).coerceAtLeast(250L))
                    continue
                }

                forceNext = false
                lastFeedAutoRefreshAt = System.currentTimeMillis()

                if (settings.videoSource == "twitch") {
                    if (twitchLoadJob?.isActive != true) {
                        loadTwitchVideos(inPlace = true)
                    }
                    while (isActive && twitchLoadJob?.isActive == true) {
                        delay(250L)
                    }
                } else if (telegramReady) {
                    if (loadJob?.isActive != true) {
                        loadVideos(inPlace = true, quiet = true)
                    }
                    while (isActive && loadJob?.isActive == true) {
                        delay(250L)
                    }
                }

                delay(350L)
            }
        }
    }


    private fun closeCurrentPlayerScreen(): Boolean {
        val hasPlayer =
            isPlayerScreen ||
            playerScreen != null ||
            twitchPlayerScreen != null ||
            twitchLivePlayerScreen != null

        if (!hasPlayer) return false
        if (playerBackInProgress) return true

        val playerActuallyFullscreen =
            playerScreen?.isFullscreen == true ||
            twitchPlayerScreen?.isFullscreen == true ||
            twitchLivePlayerScreen?.isFullscreen == true

        if (playerActuallyFullscreen) {
            if (playerScreen?.dismissFullscreenSettingsIfOpen() == true) return true

            when {
                twitchLivePlayerScreen?.isFullscreen == true -> twitchLivePlayerScreen?.exitFullscreen()
                twitchPlayerScreen?.isFullscreen == true -> twitchPlayerScreen?.exitFullscreen()
                playerScreen?.isFullscreen == true -> playerScreen?.exitFullscreen()
            }
            return true
        }

        // OEM rotation/config changes can leave the Activity fullscreen flag stale
        // even though the player has already returned to inline mode.
        // Repair that state, but keep processing this same Back press.
        if (fullScreen) {
            setFullscreen(false)
        }

        playerBackInProgress = true

        val outgoingPlayer = playerScreen
        val outgoingTwitchPlayer = twitchPlayerScreen
        val outgoingTwitchLivePlayer = twitchLivePlayerScreen

        outgoingPlayer?.flushPlaybackPosition()
        outgoingTwitchLivePlayer?.prepareForExit()

        playerScreen = null
        twitchPlayerScreen = null
        twitchLivePlayerScreen = null
        isPlayerScreen = false
        pendingRootSlide = -1
        suppressNextContentAnimation = true

        val day = currentDay
        if (day != null && day.videos.isNotEmpty()) {
            showDayCollection(day)
        } else {
            currentDay = null
            showSelectedVideoSource()
        }

        root.postDelayed({
            try {
                outgoingPlayer?.destroy()
                outgoingTwitchPlayer?.destroy()
                outgoingTwitchLivePlayer?.destroy()
                currentStreamingItem?.let { streamed -> streamServer?.release(streamed) }
                currentStreamingItem = null
            } finally {
                playerBackInProgress = false
            }
        }, if (settings.animations) 230L else 0L)

        return true
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val screen = playerScreen ?: return
        val activePlayer = screen.player
        if (!isPlayerScreen || activePlayer.isPlaying != true) return
        if (!screen.prepareForPictureInPicture()) return

        val serviceIntent = Intent(this, PlaybackKeepAliveService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)

        if (android.os.Build.VERSION.SDK_INT >= 26 && !isInPictureInPictureMode) {
            val entered = runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                )
            }.getOrDefault(false)
            if (!entered) screen.restoreFromPictureInPicture()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!isInPictureInPictureMode) {
            playerScreen?.restoreFromPictureInPicture()
            if (playerScreen?.player?.isPlaying != true) {
                stopService(Intent(this, PlaybackKeepAliveService::class.java))
            }
        }
    }

    private fun registerSearchBackInterceptor() {
        if (Build.VERSION.SDK_INT < 33 || searchSystemBackCallback != null) return

        val callback = android.window.OnBackInvokedCallback {
            if (feedSearchOpen) {
                closeFeedSearch?.invoke()
            }
        }
        searchSystemBackCallback = callback
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            callback
        )
    }

    private fun unregisterSearchBackInterceptor() {
        if (Build.VERSION.SDK_INT < 33) return
        val callback = searchSystemBackCallback ?: return
        runCatching {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
        }
        searchSystemBackCallback = null
    }

    private fun resetPredictiveBackSurface(animated: Boolean) {
        val target = predictiveBackTarget
        predictiveBackTarget = null
        if (target == null) return
        target.animate().cancel()
        if (animated && settings.animations) {
            target.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .alpha(1f)
                .setDuration(170L)
                .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                .start()
        } else {
            target.scaleX = 1f
            target.scaleY = 1f
            target.translationX = 0f
            target.alpha = 1f
        }
    }

    private fun showStartupSplash() {
        startupStatusView = null

        val page = FrameLayout(this).apply {
            setBackgroundColor(bg)
        }
        val loader = LoadingWaveView(this, purple).apply {
            alpha = 0f
            scaleX = 0.94f
            scaleY = 0.94f
        }
        page.addView(
            loader,
            FrameLayout.LayoutParams(dp(76), dp(76), Gravity.CENTER)
        )

        root.removeAllViews()
        root.addView(
            page,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        loader.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(SohrMotion.NORMAL)
            .setInterpolator(SohrMotion.smooth())
            .start()
    }

    private fun handleAuthState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> runOnUiThread {
                pendingCodeState = null
                showPhoneLogin()
            }
            is TdApi.AuthorizationStateWaitCode -> runOnUiThread {
                pendingCodeState = state
                val saved = settings.authPhone
                val actual = normalizePhone(state.codeInfo.phoneNumber)
                if (saved.isNullOrBlank()) {
                    resetTelegramAuthorization("Незавершённый старый вход")
                } else if (actual.isNotBlank() && normalizePhone(saved) != actual) {
                    resetTelegramAuthorization("Номер старой сессии не совпал")
                } else {
                    requestedPhoneNumber = saved
                    showCodeLogin(state)
                }
            }
            is TdApi.AuthorizationStateWaitEmailAddress -> runOnUiThread { showEmailAddressLogin() }
            is TdApi.AuthorizationStateWaitEmailCode -> runOnUiThread { showEmailCodeLogin(state) }
            is TdApi.AuthorizationStateWaitPassword -> runOnUiThread { showPasswordLogin(state.passwordHint ?: "") }
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> runOnUiThread {
                resetTelegramAuthorization("Возврат к входу по номеру")
            }
            is TdApi.AuthorizationStateReady -> {
                settings.authPhone = null
                settings.guestMode = false
                telegramReady = true
                ensureStreamServer()
                cleanupStorage()

                settings.postLoginTourSeen = true
                onboardingActive = false

                val zone = ZoneId.systemDefault()
                val cutoff = LocalDate.now(zone).minusDays(6).atStartOfDay(zone).toEpochSecond()
                val cached = videoCache.load().filter { it.localPath != null || it.date.toLong() >= cutoff }
                    .sortedWith(compareBy<VideoItem> { it.date }.thenBy { it.messageId })
                telegramVideos = cached
                currentVideos = cached
                if (cached.isNotEmpty()) updateStatsSnapshot(cached)

                runOnUiThread {
                    suppressNextRootAnimation = true
                    if (settings.videoSource == "twitch") {
                        showSelectedVideoSource(forceRefresh = true)
                    } else {
                        showFeed(cached)
                    }
                }

                // Do not block the first usable screen on a fresh network sync.
                loadVideos(inPlace = true, quiet = true)
                scheduleFeedAutoRefresh(delayMs = 2_000L, force = false)
            }
            is TdApi.AuthorizationStateLoggingOut -> runOnUiThread { showLoading("Выходим…") }
            is TdApi.AuthorizationStateClosing -> runOnUiThread { showLoading("Закрываем соединение…") }
            is TdApi.AuthorizationStateClosed -> Unit
        }
    }


    private fun enterGuestMode(showNotice: Boolean = true) {
        settings.guestMode = true
        settings.authPhone = null
        telegramReady = false
        startupPhase = false
        startupStatusView = null

        val zone = ZoneId.systemDefault()
        val cutoff = LocalDate.now(zone).minusDays(6).atStartOfDay(zone).toEpochSecond()
        val cached = videoCache.load()
            .filter { it.localPath != null || it.date.toLong() >= cutoff }
            .sortedWith(compareBy<VideoItem> { it.date }.thenBy { it.messageId })

        telegramVideos = cached
        currentVideos = cached
        suppressNextRootAnimation = true
        showFeed(cached)

        if (showNotice) {
            root.postDelayed({
                ModernDialogs.showNotice(
                    context = this,
                    palette = palette,
                    title = "Гостевой режим",
                    message = "Можно смотреть уже сохранённые локальные видео, пользоваться Streak, настройками и обновлениями. Новые Telegram-видео доступны после входа.",
                    button = "Понятно"
                )
            }, 220L)
        }
    }

    private fun showPhoneLogin() {
        showLoading(t("loading_countries"))
        lifecycleScope.launch {
            val phoneUtil = PhoneNumberUtil.getInstance()
            val regions = runCatching {
                client.send(TdApi.GetCountries()).countries
                    .asSequence()
                    .filter { !it.isHidden && it.callingCodes.isNotEmpty() }
                    .mapNotNull { info ->
                        val code = info.callingCodes.firstOrNull()
                            ?.filter { ch -> ch.isDigit() }
                            ?.toIntOrNull()
                            ?: return@mapNotNull null
                        PhoneCountry(
                            region = info.countryCode.uppercase(Locale.ROOT),
                            name = info.name.ifBlank {
                                Locale("", info.countryCode).getDisplayCountry(Locale("ru"))
                            },
                            dialCode = code,
                            flag = countryFlag(info.countryCode)
                        )
                    }
                    .distinctBy { pair -> pair.region to pair.dialCode }
                    .sortedBy { country -> country.name.lowercase(Locale("ru")) }
                    .toList()
            }.getOrElse {
                phoneUtil.supportedRegions
                    .map { region ->
                        PhoneCountry(
                            region = region,
                            name = Locale("", region).getDisplayCountry(Locale("ru")).ifBlank { region },
                            dialCode = phoneUtil.getCountryCodeForRegion(region),
                            flag = countryFlag(region)
                        )
                    }
                    .sortedBy { country -> country.name }
            }

            withContext(Dispatchers.Main) {
                renderPhoneLogin(regions)
            }
        }
    }

    private fun renderPhoneLogin(regions: List<PhoneCountry>) {
        startupPhase = false
        startupStatusView = null
        val phoneUtil = PhoneNumberUtil.getInstance()
        // Start neutrally: do not expose/infer a country code (for example +48)
        // before the user explicitly chooses a country.
        var selected = PhoneCountry(region = "ZZ", name = "", dialCode = 0, flag = "")

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

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val languageButton = TextView(this).apply {
            text = AppLanguages.byCode(settings.languageCode).shortLabel
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            background = roundedBg(palette.surfaceAlt, 14)
            setOnClickListener {
                animatePress(this)
                ModernDialogs.showChoices(
                    context = this@MainActivity,
                    palette = palette,
                    title = t("choose_language"),
                    options = AppLanguages.all.map { it.label },
                    selected = AppLanguages.all.indexOfFirst { it.code == settings.languageCode }.coerceAtLeast(0)
                ) { which ->
                    settings.languageCode = AppLanguages.all[which].code
                    renderPhoneLogin(regions)
                }
            }
        }

        topRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        topRow.addView(languageButton, LinearLayout.LayoutParams(dp(58), dp(44)))

        val title = TextView(this).apply {
            text = t("login")
            textSize = 29f
            setTextColor(this@MainActivity.text)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(16), 0, 0)
        }

        val subtitle = TextView(this).apply {
            text = t("choose_country")
            textSize = 14f
            setTextColor(muted)
            setPadding(0, dp(7), 0, dp(16))
        }

        val country = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(dp(14), 0, dp(14), 0)
            background = roundedBg(palette.surfaceAlt, 16)
        }

        val phoneRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
            background = roundedBg(palette.surfaceAlt, 16)
        }

        var prefixUpdating = false
        val prefix = EditText(this).apply {
            hint = "+"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setHintTextColor(muted)
            setPadding(dp(4), 0, dp(8), 0)
            inputType = InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
            gravity = Gravity.CENTER
            background = null
        }

        val input = EditText(this).apply {
            hint = t("phone")
            inputType = InputType.TYPE_CLASS_PHONE
            setTextColor(this@MainActivity.text)
            setHintTextColor(muted)
            setSingleLine(true)
            background = null
        }

        var formatter = phoneUtil.getAsYouTypeFormatter("ZZ")
        var formatting = false

        fun applyCountry() {
            val hasCountry = selected.dialCode > 0 && selected.region != "ZZ"
            country.text = if (hasCountry) {
                "${selected.flag}  ${selected.name}   +${selected.dialCode}"
            } else {
                if (settings.languageCode == "ru") "Выбрать страну" else t("choose_country")
            }
            prefixUpdating = true
            val prefixValue = if (hasCountry) "+${selected.dialCode}" else "+"
            prefix.setText(prefixValue)
            prefix.setSelection(prefixValue.length)
            prefixUpdating = false
            formatter = phoneUtil.getAsYouTypeFormatter(if (hasCountry) selected.region else "ZZ")
            val digits = input.text.toString().filter { it.isDigit() }
            if (digits.isNotEmpty()) {
                formatting = true
                formatter.clear()
                var formatted = ""
                digits.forEach { ch -> formatted = formatter.inputDigit(ch) }
                input.setText(formatted)
                input.setSelection(formatted.length)
                formatting = false
            }
        }

        country.setOnClickListener {
            animatePress(country)
            showCountryPicker(
                regions = regions,
                selected = selected
            ) { picked ->
                selected = picked
                applyCountry()
            }
        }

        prefix.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (prefixUpdating) return
                val digits = s?.toString().orEmpty().filter { it.isDigit() }.take(4)
                val normalized = if (digits.isBlank()) "+" else "+$digits"
                if (normalized != s?.toString().orEmpty()) {
                    prefixUpdating = true
                    prefix.setText(normalized)
                    prefix.setSelection(normalized.length)
                    prefixUpdating = false
                }
                val code = digits.toIntOrNull() ?: 0
                val match = regions.firstOrNull { it.dialCode == code }
                selected = match ?: PhoneCountry(region = "ZZ", name = "", dialCode = code, flag = "")
                country.text = if (match != null) {
                    "${match.flag}  ${match.name}   +${match.dialCode}"
                } else {
                    if (settings.languageCode == "ru") "Код страны введён вручную" else t("choose_country")
                }
                formatter = phoneUtil.getAsYouTypeFormatter(match?.region ?: "ZZ")
            }
        })

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (formatting) return
                val digits = s?.toString().orEmpty().filter { it.isDigit() }
                formatter.clear()
                var formatted = ""
                digits.take(18).forEach { ch -> formatted = formatter.inputDigit(ch) }
                if (formatted != s?.toString().orEmpty()) {
                    formatting = true
                    input.setText(formatted)
                    input.setSelection(formatted.length)
                    formatting = false
                }
            }
        })

        val error = TextView(this).apply {
            textSize = 12.5f
            setTextColor(Color.parseColor("#FF6B81"))
            visibility = View.GONE
            setPadding(dp(2), dp(8), dp(2), 0)
        }
        authErrorView = error

        val submit = Button(this).apply {
            text = t("continue")
            setTextColor(Color.WHITE)
            background = roundedBg(purple, 16)
            setOnClickListener {
                animatePress(this)
                val national = input.text.toString().filter { it.isDigit() }
                val dialDigits = prefix.text.toString().filter { it.isDigit() }
                if (dialDigits.isBlank()) {
                    error.text = if (settings.languageCode == "ru") "Введите код страны, например +48" else t("choose_country")
                    error.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                if (national.isBlank()) {
                    error.text = t("phone_empty")
                    error.visibility = View.VISIBLE
                    return@setOnClickListener
                }

                val normalized = "+$dialDigits$national"
                error.visibility = View.GONE
                isEnabled = false
                alpha = 0.72f

                lifecycleScope.launch {
                    try {
                        val parsed = phoneUtil.parse(normalized, selected.region.takeIf { it != "ZZ" } ?: "ZZ")
                        if (!phoneUtil.isValidNumber(parsed)) {
                            throw IllegalArgumentException(t("phone_check"))
                        }

                        requestedPhoneNumber = normalized
                        settings.guestMode = false
                        settings.authPhone = normalized

                        val authSettings = TdApi.PhoneNumberAuthenticationSettings(
                            false,
                            true,
                            false,
                            true,
                            false,
                            null,
                            emptyArray()
                        )
                        client.send(TdApi.SetAuthenticationPhoneNumber(normalized, authSettings))
                    } catch (e: Exception) {
                        error.text = friendlyAuthError(e.message)
                        error.visibility = View.VISIBLE
                        isEnabled = true
                        alpha = 1f
                    }
                }
            }
        }
        authSubmitButton = submit

        val guest = TextView(this).apply {
            text = "Продолжить как гость"
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(purple)
            background = roundedBg(palette.surfaceAlt, 16)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                animatePress(this)
                enterGuestMode()
            }
        }

        val help = TextView(this).apply {
            text = if (settings.languageCode == "ru") {
                "Выберите страну или введите код страны вручную, затем номер и нажмите «Продолжить»."
            } else {
                t("choose_country")
            }
            textSize = 12f
            setTextColor(muted)
            setPadding(dp(2), dp(12), dp(2), 0)
            setLineSpacing(0f, 1.12f)
        }

        card.addView(topRow)
        card.addView(title)
        card.addView(subtitle)
        card.addView(country, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(52)
        ))
        card.addView(phoneRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(54)
        ).apply { topMargin = dp(10) })
        phoneRow.addView(prefix, LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.MATCH_PARENT))
        phoneRow.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        card.addView(submit, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(50)
        ).apply { topMargin = dp(10) })
        card.addView(guest, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(48)
        ).apply { topMargin = dp(8) })
        card.addView(error)
        card.addView(help)

        container.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        applyCountry()
        if (settings.animations) {
            card.alpha = 0f
            card.translationY = dp(14).toFloat()
        }

        replaceRoot(container)

        if (settings.animations) {
            card.post {
                card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(280L)
                    .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                    .start()
            }
        }

        input.requestFocus()
    }

    private data class PhoneCountry(
        val region: String,
        val name: String,
        val dialCode: Int,
        val flag: String
    )


    private fun showCountryPicker(
        regions: List<PhoneCountry>,
        selected: PhoneCountry,
        onSelected: (PhoneCountry) -> Unit
    ) {
        val dialog = Dialog(this)
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(18))
            background = roundedBg(panel, 24)
        }

        val header = FrameLayout(this).apply {
            setPadding(0, 0, 0, dp(12))
        }
        header.addView(
            TextView(this).apply {
                text = t("choose_country_title")
                textSize = 20f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(this@MainActivity.text)
                setPadding(dp(48), 0, dp(48), 0)
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44),
                Gravity.CENTER
            )
        )
        header.addView(
            ImageButton(this).apply {
                setImageResource(R.drawable.ic_close)
                imageTintList = ColorStateList.valueOf(muted)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(11), dp(11), dp(11), dp(11))
                background = roundedBg(palette.surfaceAlt, 18)
                setOnClickListener { dialog.dismiss() }
            },
            FrameLayout.LayoutParams(dp(40), dp(40), Gravity.END or Gravity.CENTER_VERTICAL)
        )

        val search = EditText(this).apply {
            hint = t("country_search")
            setSingleLine(true)
            textSize = 15f
            setTextColor(this@MainActivity.text)
            setHintTextColor(muted)
            setPadding(dp(14), 0, dp(14), 0)
            background = roundedBg(palette.surfaceAlt, 15)
        }

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            addView(list)
        }

        fun render(query: String) {
            list.removeAllViews()
            val q = query.trim().lowercase(Locale.getDefault())
            val filtered = if (q.isBlank()) {
                regions
            } else {
                regions.filter { region ->
                    region.name.lowercase(Locale.getDefault()).contains(q) ||
                        region.region.lowercase(Locale.ROOT).contains(q) ||
                        ("+" + region.dialCode).contains(q) ||
                        region.dialCode.toString().contains(q)
                }
            }

            filtered.forEach { region ->
                val row = TextView(this).apply {
                    text = "${region.flag}   ${region.name}   +${region.dialCode}"
                    textSize = 15f
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(this@MainActivity.text)
                    setTypeface(
                        typeface,
                        if (region.region == selected.region) Typeface.BOLD else Typeface.NORMAL
                    )
                    setPadding(dp(12), dp(11), dp(12), dp(11))
                    background = roundedBg(
                        if (region.region == selected.region) palette.surfaceAlt else Color.TRANSPARENT,
                        13
                    )
                    setOnClickListener {
                        animatePress(this)
                        onSelected(region)
                        dialog.dismiss()
                    }
                }
                list.addView(
                    row,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(4) }
                )
            }

            if (filtered.isEmpty()) {
                list.addView(TextView(this).apply {
                    text = t("nothing_found")
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(muted)
                    setPadding(0, dp(24), 0, dp(24))
                })
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                render(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        wrapper.addView(header)
        wrapper.addView(
            search,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
            )
        )
        wrapper.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(520)
            ).apply { topMargin = dp(10) }
        )

        dialog.setContentView(wrapper)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCanceledOnTouchOutside(false)
        render("")
        dialog.show()
        dialog.window?.apply {
            decorView.setPadding(0, 0, 0, 0)
            setDimAmount(0.64f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(
                (resources.displayMetrics.widthPixels * 0.90f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        wrapper.alpha = 0f
        wrapper.scaleX = 0.96f
        wrapper.scaleY = 0.96f
        wrapper.translationY = dp(16).toFloat()
        wrapper.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(240L)
            .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
            .start()
        search.requestFocus()
    }

    private fun detectCountry(countries: List<PhoneCountry>): PhoneCountry {
        val telephony = getSystemService(TELEPHONY_SERVICE) as? TelephonyManager
        val candidates = listOf(
            telephony?.simCountryIso,
            telephony?.networkCountryIso,
            Locale.getDefault().country
        ).mapNotNull { it?.uppercase(Locale.ROOT)?.takeIf { value -> value.length == 2 } }

        val region = candidates.firstOrNull { code -> countries.any { it.region == code } }
            ?: "PL"
        return countries.firstOrNull { it.region == region }
            ?: countries.first()
    }

    private fun countryFlag(region: String): String {
        if (region.length != 2) return ""
        val upper = region.uppercase(Locale.ROOT)
        val first = upper[0].code - 'A'.code + 0x1F1E6
        val second = upper[1].code - 'A'.code + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    private fun showCodeLogin(state: TdApi.AuthorizationStateWaitCode) {
        startupPhase = false
        startupStatusView = null

        val info = state.codeInfo
        val expected = requestedPhoneNumber
        val actual = normalizePhone(info.phoneNumber)
        if (!expected.isNullOrBlank() && actual.isNotBlank() && normalizePhone(expected) != actual) {
            resetTelegramAuthorization("Номер авторизации не совпал с введённым")
            return
        }

        val delivery = authCodeDeliveryLabel(info.type?.javaClass?.simpleName.orEmpty())
        val nextDelivery = authCodeDeliveryLabel(info.nextType?.javaClass?.simpleName.orEmpty())
        val timeout = info.timeout.coerceAtLeast(0)
        val codeLength = runCatching {
            info.type?.javaClass?.getField("length")?.getInt(info.type)
        }.getOrNull()?.coerceIn(4, 8) ?: 5

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
            setBackgroundColor(bg)
        }

        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            imageTintList = ColorStateList.valueOf(this@MainActivity.text)
            background = roundedBg(palette.surfaceAlt, 22)
            setPadding(dp(11), dp(11), dp(11), dp(11))
            contentDescription = "Назад"
            setOnClickListener {
                animatePress(this)
                resetTelegramAuthorization("Смена номера")
            }
        }

        val backRow = FrameLayout(this).apply {
            addView(
                back,
                FrameLayout.LayoutParams(dp(44), dp(44), Gravity.START)
            )
        }
        container.addView(
            backRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            )
        )

        val artwork = ImageView(this).apply {
            setImageResource(R.drawable.ic_auth_telegram_code)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            alpha = 0f
            scaleX = 0.9f
            scaleY = 0.9f
        }
        container.addView(
            artwork,
            LinearLayout.LayoutParams(dp(112), dp(112)).apply {
                topMargin = dp(8)
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )

        val title = TextView(this).apply {
            text = "Проверьте Telegram"
            textSize = 27f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(0, dp(16), 0, 0)
        }

        val subtitle = TextView(this).apply {
            text = buildString {
                append("Введите код, который пришёл ")
                append(delivery)
                if (info.phoneNumber.isNotBlank()) {
                    append("\n")
                    append(info.phoneNumber)
                }
            }
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setLineSpacing(0f, 1.12f)
            setPadding(dp(12), dp(8), dp(12), dp(22))
        }

        container.addView(title)
        container.addView(
            subtitle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val codeWrap = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            isCursorVisible = false
            setTextColor(Color.TRANSPARENT)
            setHintTextColor(Color.TRANSPARENT)
            background = null
            alpha = 0.02f
            filters = arrayOf(android.text.InputFilter.LengthFilter(codeLength))
            contentDescription = "Код подтверждения"
        }

        val cellsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
        }

        val cells = MutableList(codeLength) { index ->
            TextView(this).apply {
                textSize = 23f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(this@MainActivity.text)
                background = codeCellDrawable(active = index == 0, filled = false)
                scaleX = if (index == 0) 1f else 0.98f
                scaleY = if (index == 0) 1f else 0.98f
            }.also { cell ->
                cellsRow.addView(
                    cell,
                    LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                        if (index > 0) marginStart = dp(8)
                    }
                )
            }
        }

        fun focusKeyboard() {
            input.requestFocus()
            input.postDelayed({
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }, 80L)
        }

        cellsRow.setOnClickListener {
            animatePress(cellsRow)
            focusKeyboard()
        }

        codeWrap.addView(
            input,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58)
            )
        )
        codeWrap.addView(
            cellsRow,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58)
            )
        )

        container.addView(
            codeWrap,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58)
            ).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
            }
        )

        val error = TextView(this).apply {
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#FF6B81"))
            visibility = View.GONE
            setPadding(dp(8), dp(10), dp(8), 0)
        }
        authErrorView = error
        container.addView(
            error,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val autoHint = TextView(this).apply {
            text = "Код подтвердится автоматически"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(0, dp(12), 0, 0)
        }
        container.addView(autoHint)

        val help = TextView(this).apply {
            text = "Не пришёл код?"
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(purple)
            setPadding(dp(12), dp(18), dp(12), dp(12))
            setOnClickListener {
                animatePress(this)
                val helpText = buildString {
                    append("Сначала проверьте официальный Telegram на других устройствах и уведомления. ")
                    append("Текущий способ доставки: ")
                    append(delivery)
                    append(".")
                    if (timeout > 0) {
                        append("\n\nПовторный запрос обычно станет доступен примерно через ")
                        append(timeout)
                        append(" сек.")
                    }
                    if (nextDelivery.isNotBlank()) {
                        append("\nСледующий способ: ")
                        append(nextDelivery)
                        append(".")
                    }
                }
                ModernDialogs.showConfirm(
                    context = this@MainActivity,
                    palette = palette,
                    title = "Не пришёл код?",
                    message = helpText,
                    confirm = "Отправить ещё раз"
                ) {
                    launchRequest {
                        client.send(TdApi.ResendAuthenticationCode(null))
                    }
                }
            }
        }
        container.addView(help)

        var previousLength = 0
        var submitting = false

        fun renderCode(value: String, errorState: Boolean = false) {
            val digits = value.filter { it.isDigit() }.take(codeLength)
            cells.forEachIndexed { index, cell ->
                val filled = index < digits.length
                val active = index == digits.length.coerceAtMost(codeLength - 1) && digits.length < codeLength
                cell.text = if (filled) digits[index].toString() else ""
                cell.background = codeCellDrawable(active, filled, errorState)

                if (filled && index >= previousLength && settings.animations) {
                    cell.animate().cancel()
                    cell.scaleX = 0.84f
                    cell.scaleY = 0.84f
                    cell.alpha = 0.45f
                    cell.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(165L)
                        .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                        .start()
                } else {
                    cell.alpha = 1f
                    if (!filled && !active) {
                        cell.scaleX = 0.98f
                        cell.scaleY = 0.98f
                    } else {
                        cell.scaleX = 1f
                        cell.scaleY = 1f
                    }
                }
            }
            previousLength = digits.length
        }

        fun submitCode(code: String) {
            if (submitting || code.length != codeLength) return
            submitting = true
            error.visibility = View.GONE
            cells.forEach { it.alpha = 0.72f }

            lifecycleScope.launch {
                try {
                    client.send(TdApi.CheckAuthenticationCode(code))
                } catch (e: Exception) {
                    submitting = false
                    val message = friendlyAuthError(e.message)
                    error.text = message
                    error.visibility = View.VISIBLE
                    cells.forEach { it.alpha = 1f }
                    renderCode(code, errorState = true)

                    codeWrap.animate().cancel()
                    codeWrap.animate()
                        .translationX(dp(7).toFloat())
                        .setDuration(55L)
                        .withEndAction {
                            codeWrap.animate()
                                .translationX(-dp(7).toFloat())
                                .setDuration(70L)
                                .withEndAction {
                                    codeWrap.animate()
                                        .translationX(0f)
                                        .setDuration(70L)
                                        .start()
                                }
                                .start()
                        }
                        .start()
                }
            }
        }

        input.addTextChangedListener(object : TextWatcher {
            private var editing = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (editing) return
                val raw = s?.toString().orEmpty()
                val digits = raw.filter { it.isDigit() }.take(codeLength)
                if (digits != raw) {
                    editing = true
                    input.setText(digits)
                    input.setSelection(digits.length)
                    editing = false
                }

                error.visibility = View.GONE
                renderCode(digits)

                if (digits.length == codeLength) {
                    input.postDelayed({ submitCode(digits) }, 120L)
                }
            }
        })

        replaceRoot(container)

        if (settings.animations) {
            artwork.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300L)
                .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                .withEndAction {
                    val flight = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 1700L
                        repeatCount = android.animation.ValueAnimator.INFINITE
                        repeatMode = android.animation.ValueAnimator.REVERSE
                        interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                        addUpdateListener { animator ->
                            val value = animator.animatedValue as Float
                            artwork.translationY = -dp(4) * value
                            artwork.translationX = dp(2) * value
                            artwork.rotation = -1.8f + (3.6f * value)
                        }
                    }
                    artwork.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) = Unit
                        override fun onViewDetachedFromWindow(v: View) {
                            flight.cancel()
                            artwork.removeOnAttachStateChangeListener(this)
                        }
                    })
                    flight.start()
                }
                .start()

            title.alpha = 0f
            subtitle.alpha = 0f
            codeWrap.alpha = 0f
            codeWrap.translationY = dp(10).toFloat()

            title.animate().alpha(1f).setStartDelay(70L).setDuration(220L).start()
            subtitle.animate().alpha(1f).setStartDelay(110L).setDuration(220L).start()
            codeWrap.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(140L)
                .setDuration(260L)
                .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                .start()
        } else {
            artwork.alpha = 1f
            artwork.scaleX = 1f
            artwork.scaleY = 1f
        }

        focusKeyboard()
    }

    private fun codeCellDrawable(active: Boolean, filled: Boolean, error: Boolean = false): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(palette.surfaceAlt)
            cornerRadius = dp(14).toFloat()
            val strokeColor = when {
                error -> Color.parseColor("#FF6B81")
                active -> purple
                filled -> Color.argb(170, Color.red(purple), Color.green(purple), Color.blue(purple))
                else -> palette.stroke
            }
            setStroke(dp(if (active || error) 2 else 1), strokeColor)
        }

    private fun showEmailAddressLogin() {
        showAuthForm(
            title = "Email для входа",
            subtitle = "Telegram просит подтвердить вход через email.",
            hint = "name@example.com",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            button = "Отправить код",
            footer = "Введите email, который Telegram просит для этой авторизации. Код придёт на него.",
            showBack = true,
            onBack = { resetTelegramAuthorization("Смена способа входа") }
        ) { value ->
            val email = value.trim()
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                throw IllegalArgumentException("Проверьте адрес email")
            }
            client.send(TdApi.SetAuthenticationEmailAddress(email))
        }
    }

    private fun showEmailCodeLogin(state: TdApi.AuthorizationStateWaitEmailCode) {
        val pattern = state.codeInfo?.emailAddressPattern.orEmpty()
        showAuthForm(
            title = "Код из email",
            subtitle = if (pattern.isBlank()) {
                "Telegram отправил код на email."
            } else {
                "Telegram отправил код на $pattern"
            },
            hint = "Код",
            inputType = InputType.TYPE_CLASS_NUMBER,
            button = "Продолжить",
            footer = "Если письма нет, проверьте Спам/Промоакции и подождите немного.",
            showBack = true,
            onBack = { resetTelegramAuthorization("Смена способа входа") }
        ) { value ->
            val code = value.trim()
            if (code.length < 3) throw IllegalArgumentException("Проверьте код из email")
            client.send(
                TdApi.CheckAuthenticationEmailCode(
                    TdApi.EmailAddressAuthenticationCode(code)
                )
            )
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
            if (value.isBlank()) throw IllegalArgumentException("Введите пароль")
            client.send(TdApi.CheckAuthenticationPassword(value))
        }
    }

    private fun normalizePhone(value: String): String =
        value.filter { it.isDigit() }

    private fun resetTelegramAuthorization(reason: String) {
        if (authResetInProgress) return
        authResetInProgress = true
        lifecycleScope.launch {
            showLoading("Сбрасываем старый вход…")
            val oldGeneration = settings.authGeneration
            settings.authGeneration = oldGeneration + 1
            settings.authPhone = null
            requestedPhoneNumber = null

            runCatching { client.close() }
            kotlinx.coroutines.delay(300)

            runCatching {
                java.io.File(filesDir, "tdlib_session_" + oldGeneration).deleteRecursively()
            }
            recreate()
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
        startupPhase = false
        startupStatusView = null

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

        if (showBack) {
            val back = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(10), 0, dp(12), 0)
                background = roundedBg(palette.surfaceAlt, 14)
                isClickable = true
                isFocusable = true
            }
            back.addView(
                ImageView(this).apply {
                    setImageResource(R.drawable.ic_back)
                    imageTintList = ColorStateList.valueOf(this@MainActivity.text)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(dp(7), dp(7), dp(7), dp(7))
                },
                LinearLayout.LayoutParams(dp(30), dp(30))
            )
            back.addView(
                TextView(this).apply {
                    text = "Назад"
                    textSize = 14f
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(this@MainActivity.text)
                }
            )
            back.setOnClickListener {
                animatePress(back)
                onBack?.invoke()
            }
            card.addView(
                back,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(42)
                ).apply { bottomMargin = dp(4) }
            )
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
        val isPassword = (inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD) == InputType.TYPE_TEXT_VARIATION_PASSWORD
        val input = EditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            setTextColor(this@MainActivity.text)
            setHintTextColor(muted)
            setSingleLine(true)
            setPadding(dp(15), dp(14), if (isPassword) dp(52) else dp(15), dp(14))
            background = roundedBg(palette.surfaceAlt, 16)
            if (isPassword) transformationMethod = PasswordTransformationMethod.getInstance()
        }

        val inputContainer = FrameLayout(this).apply {
            background = roundedBg(palette.surfaceAlt, 16)
        }
        input.background = null
        inputContainer.addView(input, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        if (isPassword) {
            var visiblePassword = false
            val eye = ImageButton(this).apply {
                setImageResource(R.drawable.ic_visibility_off)
                imageTintList = ColorStateList.valueOf(purple)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = ColorDrawable(Color.TRANSPARENT)
                contentDescription = "Показать пароль"
                setOnClickListener {
                    visiblePassword = !visiblePassword
                    input.transformationMethod = if (visiblePassword) {
                        HideReturnsTransformationMethod.getInstance()
                    } else {
                        PasswordTransformationMethod.getInstance()
                    }
                    setImageResource(
                        if (visiblePassword) R.drawable.ic_visibility
                        else R.drawable.ic_visibility_off
                    )
                    contentDescription = if (visiblePassword) "Скрыть пароль" else "Показать пароль"
                    input.setSelection(input.text.length)
                    animatePress(this)
                }
            }
            inputContainer.addView(eye, FrameLayout.LayoutParams(
                dp(48),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            ))
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

        card.addView(titleView)
        card.addView(subtitleView)
        card.addView(inputContainer, LinearLayout.LayoutParams(
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
        SohrMotion.press(view, settings.animations)
    }

    private fun friendlyAuthError(raw: String?): String {
        val text = raw.orEmpty()
        return when {
            "PHONE_NUMBER_INVALID" in text -> "Номер телефона неверный. Введите его с + и кодом страны."
            "PHONE_NUMBER_FLOOD" in text -> "Слишком много попыток. Telegram временно ограничил отправку кода."
            "PHONE_CODE_INVALID" in text -> "Код неверный. Проверьте цифры и попробуйте ещё раз."
            "PHONE_CODE_EXPIRED" in text -> "Код уже истёк. Нажмите «Отправить код ещё раз»."
            "PASSWORD_HASH_INVALID" in text -> "Неверный облачный пароль."
            "API_ID" in text -> "Ошибка Telegram API. Нужно проверить API ID/API Hash приложения."
            text.isBlank() -> "Telegram не принял запрос. Попробуйте ещё раз."
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

    private fun cleanupStorage() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val currentName = "tdlib_session_" + settings.authGeneration
                filesDir.listFiles()
                    ?.filter { it.isDirectory && it.name.startsWith("tdlib_session_") && it.name != currentName }
                    ?.forEach { it.deleteRecursively() }
            }

            runCatching {
                val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
                cacheDir.walkTopDown()
                    .filter { it.isFile && it.lastModified() < cutoff }
                    .forEach { it.delete() }
            }

            runCatching {
                val files = cacheDir.walkTopDown().filter { it.isFile }.toList()
                var total = files.sumOf { it.length() }
                val maxBytes = 96L * 1024L * 1024L
                if (total > maxBytes) {
                    files.sortedBy { it.lastModified() }.forEach { file ->
                        if (total <= maxBytes) return@forEach
                        val size = file.length()
                        if (file.delete()) total -= size
                    }
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

    private fun loadVideos(inPlace: Boolean = false, quiet: Boolean = false) {
        val visibleTelegram = settings.videoSource == "telegram"
        if (loadJob?.isActive == true) {
            if (inPlace && visibleTelegram && !quiet) feedRefreshLabel?.text = "Уже проверяем…"
            else if (!inPlace) reloadRequested = true
            return
        }
        if (inPlace && visibleTelegram) setFeedRefreshLoading(true)
        loadJob = lifecycleScope.launch {
            if (!inPlace && visibleTelegram && !onboardingActive) {
                withContext(Dispatchers.Main) { showFeedSkeleton("Обновляем медиатеку…") }
            }
            try {
                val chat = client.send(TdApi.SearchPublicChat("t2x2_video"))
                channelChatId = chat.id
                runCatching { client.send(TdApi.OpenChat(chat.id)) }

                val zone = ZoneId.systemDefault()
                val cutoffDate = LocalDate.now(zone).minusDays(6)
                val cutoffEpoch = cutoffDate.atStartOfDay(zone).toEpochSecond()

                val collected = linkedMapOf<Long, VideoItem>()
                val cachedBeforeRefresh = withContext(Dispatchers.IO) { videoCache.load() }
                cachedBeforeRefresh.asSequence()
                    .filter { it.localPath != null || it.date.toLong() >= cutoffEpoch }
                    .forEach { collected[it.messageId] = it }

                var fromMessageId = 0L
                var page = 0
                var consecutiveOldPages = 0

                while (page < 24 && consecutiveOldPages < 2) {
                    val history = client.send(
                        TdApi.GetChatHistory(chat.id, fromMessageId, 0, 100, false)
                    )
                    if (history.messages.isEmpty()) break

                    var recentMessagesOnPage = 0
                    for (message in history.messages) {
                        if (message.date.toLong() < cutoffEpoch) continue
                        recentMessagesOnPage++
                        messageToVideo(message)?.let { collected[it.messageId] = it }
                    }

                    consecutiveOldPages =
                        if (recentMessagesOnPage == 0) consecutiveOldPages + 1 else 0

                    val last = history.messages.lastOrNull() ?: break
                    if (last.id == fromMessageId) break
                    fromMessageId = last.id
                    page++
                }

                val videos = collected.values
                    .filter { it.localPath != null || it.date.toLong() >= cutoffEpoch }
                    .sortedWith(compareBy<VideoItem> { it.date }.thenBy { it.messageId })

                val cachedById = cachedBeforeRefresh.associateBy { it.messageId }
                val videosWithCachedThumbs = videos.map { item ->
                    val cached = cachedById[item.messageId]
                    if (item.thumbnailPath.isNullOrBlank() && cached?.thumbnailPath?.let { File(it).exists() } == true) item.copy(thumbnailPath = cached.thumbnailPath) else item
                }
                val preparedVideos = if (settings.previews) {
                    videosWithCachedThumbs.chunked(6).flatMap { batch ->
                        coroutineScope {
                            batch.map { item ->
                                async(Dispatchers.IO) { attachThumbnail(item) }
                            }.awaitAll()
                        }
                    }
                } else {
                    videosWithCachedThumbs
                }

                withContext(Dispatchers.IO) {
                    videoCache.save(preparedVideos)
                }

                withContext(Dispatchers.Main) {
                    telegramVideos = preparedVideos
                    updateStatsSnapshot(preparedVideos)
                    scheduleSmartDownloads(preparedVideos)
                    if (settings.videoSource != "telegram") return@withContext
                    val changed = currentVideos.map { it.messageId } != preparedVideos.map { it.messageId }
                    currentVideos = preparedVideos
                    if (onboardingActive) {
                        return@withContext
                    }
                    if (inPlace && !changed) {
                        setFeedRefreshLoading(false)
                    } else {
                        currentDay = null
                        feedRefreshCompletedFlash = inPlace && !quiet
                        if (inPlace) suppressNextRootAnimation = true
                        showFeed(preparedVideos)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (settings.videoSource != "telegram") return@withContext
                    if (inPlace) {
                        setFeedRefreshLoading(false)
                        if (!quiet) {
                            Toast.makeText(
                                this@MainActivity,
                                e.message ?: "Не удалось проверить новые видео",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        showMessage(
                            "Не удалось загрузить записи",
                            e.message ?: "Неизвестная ошибка Telegram"
                        )
                    }
                }
            }
        }
    }

    private fun setFeedRefreshLoading(loading: Boolean, label: String? = null) {
        feedRefreshButton?.isEnabled = !loading
        feedRefreshLabel?.text = label
        feedRefreshLoader?.let { loader ->
            loader.animate().cancel()
            if (loading) {
                loader.visibility = View.VISIBLE
                loader.alpha = 0f
                loader.animate().alpha(0.86f).setDuration(120L).start()
            } else {
                loader.animate()
                    .alpha(0f)
                    .setDuration(140L)
                    .withEndAction { loader.visibility = View.GONE }
                    .start()
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
            is TdApi.MessageAnimation -> {
                val animation = content.animation
                val mime = animation.mimeType.ifBlank { "video/mp4" }
                val name = animation.fileName
                val isVideo = mime.startsWith("video/") ||
                    name.endsWith(".mp4", true) ||
                    name.endsWith(".webm", true)
                if (!isVideo) return null
                val file = animation.animation
                VideoItem(
                    messageId = message.id,
                    title = content.caption.text.trim().ifBlank {
                        name.ifBlank { "Запись стрима" }
                    },
                    date = message.date,
                    durationSeconds = animation.duration,
                    fileId = file.id,
                    fileSize = if (file.size > 0) file.size else file.expectedSize,
                    mimeType = mime,
                    thumbnailFileId = animation.thumbnail?.file?.id
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
        item.thumbnailPath?.takeIf { File(it).exists() }?.let { return item }
        val thumbId = item.thumbnailFileId ?: return item
        return try {
            val file = client.send(TdApi.DownloadFile(thumbId, 1, 0, 0, true))
            val path = file.local.path.takeIf { it.isNotBlank() && file.local.isDownloadingCompleted }
            item.copy(thumbnailPath = path)
        } catch (_: Exception) {
            item
        }
    }

    private fun updateStatsSnapshot(videos: List<VideoItem>) {
        val watched = watchedVideoIds()
        val watchedVideos = videos.filter { watched.contains(it.messageId.toString()) }
        getSharedPreferences("sohr_stats", MODE_PRIVATE).edit()
            .putInt("videos", videos.size)
            .putInt("watched", watchedVideos.size)
            .putLong("watched_seconds", watchedVideos.sumOf { it.durationSeconds.toLong() })
            .apply()
    }

    private fun handleSharedIntent(sourceIntent: Intent?) {
        if (sourceIntent?.action != Intent.ACTION_SEND) return
        if (!sourceIntent.type.orEmpty().startsWith("video/")) return
        val uri: Uri = if (android.os.Build.VERSION.SDK_INT >= 33) {
            sourceIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION") sourceIntent.getParcelableExtra(Intent.EXTRA_STREAM)
        } ?: return
        sourceIntent.action = null
        lifecycleScope.launch {
            val imported = runCatching { withContext(Dispatchers.IO) { videoCache.importSharedVideo(uri) } }.getOrNull() ?: return@launch
            val merged = (videoCache.load() + imported).distinctBy { it.messageId }
                .sortedWith(compareBy<VideoItem> { it.date }.thenBy { it.messageId })
            videoCache.save(merged)
            telegramVideos = (telegramVideos + imported).distinctBy { it.messageId }
                .sortedWith(compareBy<VideoItem> { it.date }.thenBy { it.messageId })
            updateStatsSnapshot(telegramVideos)
            if (telegramReady && settings.videoSource == "telegram") {
                currentVideos = telegramVideos
                currentDay = null
                suppressNextRootAnimation = true
                showFeed(currentVideos)
            }
        }
    }

    private fun watchedVideoIds(): Set<String> =
    getSharedPreferences("sohr_watched", MODE_PRIVATE).getStringSet("ids", emptySet())?.toSet() ?: emptySet()

    private fun isVideoWatched(messageId: Long): Boolean = watchedVideoIds().contains(messageId.toString())

    private fun markVideoWatched(messageId: Long): Boolean {
        val prefs = getSharedPreferences("sohr_watched", MODE_PRIVATE)
        val ids = prefs.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        val added = ids.add(messageId.toString())
        if (added) { prefs.edit().putStringSet("ids", ids).apply(); updateStatsSnapshot(currentVideos) }
        return added
    }

    private fun unmarkVideoWatched(messageId: Long): Boolean {
        val prefs = getSharedPreferences("sohr_watched", MODE_PRIVATE)
        val ids = prefs.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        val removed = ids.remove(messageId.toString())
        if (removed) { prefs.edit().putStringSet("ids", ids).apply(); updateStatsSnapshot(currentVideos) }
        return removed
    }

    private fun switchVideoSection(section: Int) {
        if (section !in 1..3 || videoSection == section || videoSectionSwitchLocked) return

        videoSectionSwitchLocked = true
        pendingVideoSectionCrossfade = true
        pendingVideoSectionDirection = if (section > videoSection) 1 else -1
        pendingRootSlide = 0
        suppressNextContentAnimation = false
        videoSection = section
        showFeed(currentVideos)

        root.postDelayed({
            videoSectionSwitchLocked = false
        }, if (settings.animations) 330L else 40L)
    }

    private fun showFeed(videos: List<VideoItem>) {
        currentVideos = videos
        if (!startupUpdateCheckDone) {
            startupUpdateCheckDone = true
            scheduleAutomaticUpdateCheck(delayMs = 900L, force = true)
        }
        completedUpdateNotice?.let { version ->
            val notes = completedUpdateNotes ?: fallbackReleaseNotes(version)
            completedUpdateNotice = null
            completedUpdateNotes = null
            root.postDelayed({
                ModernDialogs.showUpdateCompleted(
                    context = this,
                    palette = palette,
                    version = version,
                    notes = notes
                )
            }, 420L)
        }

        startupPhase = false
        startupStatusView = null
        auxiliaryScreen = null
        stopInlinePreview()
        primaryNav?.animate()?.cancel()
        primaryNav?.translationY = 0f
        primaryNav?.alpha = 1f
        primaryNav?.scaleX = 1f
        primaryNav?.scaleY = 1f
        unregisterSearchBackInterceptor()
        feedSearchOpen = false
        closeFeedSearch = null
        isPlayerScreen = false
        isSettingsScreen = false
        isAccountScreen = false
        isStreakScreen = false
        currentDay = null
        setFullscreen(false)
        applySystemTheme()

        val zone = ZoneId.systemDefault()
        val watched = watchedVideoIds()
        val watchedVideos = videos.filter { watched.contains(it.messageId.toString()) }
        val regularVideos = videos.filterNot { watched.contains(it.messageId.toString()) }

        fun groups(source: List<VideoItem>) =
            source.groupBy {
                Instant.ofEpochSecond(it.date.toLong()).atZone(zone).toLocalDate()
            }.map { (date, items) ->
                DayCollection(
                    date,
                    items.sortedWith(compareBy<VideoItem> { it.date }.thenBy { it.messageId })
                )
            }.sortedByDescending { it.date }

        val regularGroups = groups(regularVideos)
        val watchedGroups = groups(watchedVideos)
        val visibleGroups = when (videoSection) {
            2 -> emptyList()
            3 -> watchedGroups
            else -> regularGroups
        }
        val sectionTransitionDirection = pendingVideoSectionDirection

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val scroll = NestedScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(bg)
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(0, 0, 0, dp(16))
        }
        scroll.addView(
            body,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        page.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
            setBackgroundColor(bg)
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(
            TextView(this).apply {
                text = "SOHR"
                textSize = 25f
                gravity = Gravity.CENTER_VERTICAL
                includeFontPadding = false
                setTextColor(this@MainActivity.text)
                setTypeface(typeface, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )

        feedRefreshButton = null
        feedRefreshLabel = null
        feedRefreshCompletedFlash = false
        val syncLoader = LoadingWaveView(this, purple).apply {
            visibility = View.GONE
            alpha = 0f
            scaleX = 0.58f
            scaleY = 0.58f
            contentDescription = "Синхронизация"
        }
        feedRefreshLoader = syncLoader
        titleRow.addView(syncLoader, LinearLayout.LayoutParams(dp(34), dp(34)).apply {
            marginEnd = dp(4)
        })

        val downloadsButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_action_download)
            imageTintList = ColorStateList.valueOf(muted)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = roundedBg(palette.surfaceAlt, 18)
            contentDescription = "Загрузки"
            setOnClickListener {
                SohrMotion.press(this, settings.animations)
                showDownloadCenter()
            }
        }
        titleRow.addView(downloadsButton, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
            marginEnd = dp(6)
        })

        val searchButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_search)
            imageTintList = ColorStateList.valueOf(muted)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = roundedBg(palette.surfaceAlt, 18)
            contentDescription = "Поиск"
        }
        titleRow.addView(searchButton, LinearLayout.LayoutParams(dp(40), dp(40)))
        header.addView(titleRow)

        val sourceName = when {
            settings.guestMode && settings.videoSource == "telegram" -> "Гость • локальный режим"
            settings.videoSource == "twitch" -> "Twitch • @t2x2"
            else -> "Telegram • @t2x2_video"
        }
        header.addView(
            TextView(this).apply {
                text = when (videoSection) {
                    2 -> sourceName + " • Лента"
                    3 -> sourceName + " • " + watchedVideos.size + " просмотрено"
                    else -> sourceName + " • " + regularVideos.size + " не просмотрено"
                }
                textSize = 11.8f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(muted)
                maxLines = 1
                includeFontPadding = false
                setPadding(0, dp(2), 0, dp(10))
            }
        )

        val tabs = FrameLayout(this).apply {
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = roundedBg(palette.surfaceAlt, 18)
            clipChildren = true
            clipToPadding = true
        }
        val tabIndicator = View(this).apply {
            background = roundedBg(purple, 15)
            elevation = 0f
        }
        val tabButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            elevation = dp(2).toFloat()
        }

        listOf("Главная" to 1, "Лента" to 2, "Просмотренные" to 3).forEach { (label, section) ->
            val selected = videoSection == section
            val tab = TextView(this).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (selected) Color.WHITE else muted)
                background = null
                isClickable = true
                isFocusable = true
                tag = "sohr_video_section_tab"
                var swipeStartX = 0f
                var swipeStartY = 0f
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            swipeStartX = event.x
                            swipeStartY = event.y
                            false
                        }
                        MotionEvent.ACTION_UP -> {
                            val dx = event.x - swipeStartX
                            val dy = event.y - swipeStartY
                            if (
                                kotlin.math.abs(dx) >= dp(46) &&
                                kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.25f
                            ) {
                                val target = if (dx < 0f) {
                                    (videoSection + 1).coerceAtMost(3)
                                } else {
                                    (videoSection - 1).coerceAtLeast(1)
                                }
                                if (target != videoSection) switchVideoSection(target)
                                true
                            } else false
                        }
                        else -> false
                    }
                }
                setOnClickListener {
                    if (videoSection == section) return@setOnClickListener
                    animatePress(this)
                    switchVideoSection(section)
                }
            }
            tabButtons.addView(tab, LinearLayout.LayoutParams(0, dp(40), 1f))
        }

        tabs.addView(
            tabIndicator,
            FrameLayout.LayoutParams(0, dp(40), Gravity.START or Gravity.CENTER_VERTICAL)
        )
        tabs.addView(
            tabButtons,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40),
                Gravity.CENTER
            )
        )
        tabs.post {
            val usable = tabs.width - tabs.paddingLeft - tabs.paddingRight
            val slot = (usable / 3f).coerceAtLeast(0f)
            val params = tabIndicator.layoutParams as FrameLayout.LayoutParams
            params.width = slot.toInt()
            params.height = dp(40)
            tabIndicator.layoutParams = params
            val target = slot * (videoSection - 1)
            if (sectionTransitionDirection != 0 && settings.animations) {
                val previousSection = (videoSection - sectionTransitionDirection).coerceIn(1, 3)
                tabIndicator.translationX = slot * (previousSection - 1)
                tabIndicator.animate().cancel()
                tabIndicator.animate()
                    .translationX(target)
                    .setStartDelay(16L)
                    .setDuration(275L)
                    .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                    .start()
            } else {
                tabIndicator.translationX = target
            }
        }
        header.addView(
            tabs,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46))
        )

        scroll.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
                val shrink = (scrollY.toFloat() / dp(90).coerceAtLeast(1)).coerceIn(0f, 1f)
                titleRow.pivotX = 0f
                titleRow.pivotY = 0f
                titleRow.scaleX = 1f - 0.045f * shrink
                titleRow.scaleY = 1f - 0.045f * shrink
                titleRow.alpha = 1f - 0.10f * shrink

                primaryNav?.let { nav ->
                    nav.animate().cancel()
                    nav.clearAnimation()
                    nav.translationY = 0f
                    nav.alpha = 1f
                    nav.scaleX = 1f
                    nav.scaleY = 1f
                }
            }
        )

        val searchPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            alpha = 0f
            translationY = -dp(6).toFloat()
            setPadding(0, dp(10), 0, 0)
        }
        val searchInput = EditText(this).apply {
            hint = "Найти видео или дату"
            setSingleLine(true)
            textSize = 14.5f
            setTextColor(this@MainActivity.text)
            setHintTextColor(muted)
            setPadding(dp(14), 0, dp(14), 0)
            background = roundedBg(palette.surfaceAlt, 17)
        }
        searchPanel.addView(
            searchInput,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46))
        )

        val chipsScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, dp(8), 0, 0)
        }
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        chipsScroll.addView(
            chips,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        searchPanel.addView(
            chipsScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46))
        )
        header.addView(searchPanel)
        body.addView(header)

        val searchResultsHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(4), 0, dp(10))
        }
        body.addView(searchResultsHost)

        val contentHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        body.addView(contentHost)

        var searchFilter = "all"
        val chipViews = linkedMapOf<String, TextView>()

        fun updateChipStates() {
            chipViews.forEach { (key, chip) ->
                val selected = key == searchFilter
                chip.setTextColor(if (selected) Color.WHITE else muted)
                chip.background = roundedBg(
                    if (selected) purple else palette.surfaceAlt,
                    15
                )
            }
        }

        fun renderSearchResults() {
            if (!feedSearchOpen) return
            val query = searchInput.text?.toString().orEmpty().trim().lowercase(Locale.getDefault())
            val active = query.isNotBlank() || searchFilter != "all"
            if (!active) {
                searchResultsHost.visibility = View.GONE
                contentHost.visibility = View.VISIBLE
                return
            }

            val now = System.currentTimeMillis()
            val downloaded = downloadedVideoIds()
            val searchSavedIds = watchLaterIds() + favoriteIds()
            val filtered = videos.filter { item ->
                val watchedItem = watched.contains(item.messageId.toString())
                val ageMs = (now - item.date.toLong() * 1000L).coerceAtLeast(0L)
                val filterMatch = when (searchFilter) {
                    "new" -> !watchedItem && ageMs <= 24L * 60L * 60L * 1000L
                    "unwatched" -> !watchedItem
                    "watched" -> watchedItem
                    "downloaded" -> item.localPath != null || downloaded.contains(item.messageId.toString())
                    "saved" -> searchSavedIds.contains(item.messageId.toString())
                    else -> true
                }
                val dateText = Instant.ofEpochSecond(item.date.toLong())
                    .atZone(zone)
                    .toLocalDate()
                    .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))
                    .lowercase(Locale("ru"))
                val searchableText = buildString {
                    append(item.title.lowercase(Locale.getDefault()))
                    append(' ')
                    append(dateText)
                    append(' ')
                    append(item.source.lowercase(Locale.getDefault()))
                    append(' ')
                    if (item.source == "telegram") append("@t2x2_video")
                    if (item.source == "twitch") append("@t2x2 twitch")
                }
                    .replace('\n', ' ')
                    .replace(Regex("\\s+"), " ")
                filterMatch && (
                    query.isBlank() ||
                        searchableText.contains(query)
                    )
            }.sortedWith(
                compareByDescending<VideoItem> { it.date }
                    .thenByDescending { it.messageId }
            )

            searchResultsHost.removeAllViews()
            searchResultsHost.addView(
                TextView(this).apply {
                    text = if (filtered.isEmpty()) "Ничего не найдено" else "Найдено • " + filtered.size
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(if (filtered.isEmpty()) muted else this@MainActivity.text)
                    setPadding(dp(16), dp(10), dp(16), dp(6))
                }
            )

            if (filtered.isNotEmpty()) {
                searchResultsHost.addView(
                    RecyclerView(this).apply {
                        isNestedScrollingEnabled = false
                        overScrollMode = View.OVER_SCROLL_NEVER
                        layoutManager = LinearLayoutManager(this@MainActivity)
                        adapter = VideoAdapter(
                            items = filtered,
                            palette = palette,
                            animationsEnabled = settings.animations,
                            progressFor = { playbackProgress(it) },
                            onClick = { item, source -> openPlayer(item, sourceView = source) },
                            onLongClick = { item, source -> showVideoQuickActions(item, source) }
                        )
                        itemAnimator = null
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }

            val firstReveal = searchResultsHost.visibility != View.VISIBLE
            contentHost.visibility = View.GONE
            searchResultsHost.visibility = View.VISIBLE
            if (settings.animations && firstReveal) {
                searchResultsHost.animate().cancel()
                searchResultsHost.alpha = 0f
                searchResultsHost.translationY = dp(7).toFloat()
                searchResultsHost.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(SohrMotion.NORMAL)
                    .setInterpolator(SohrMotion.smooth())
                    .start()
            } else {
                searchResultsHost.alpha = 1f
                searchResultsHost.translationY = 0f
            }
        }

        listOf(
            "all" to "Все",
            "new" to "Новые",
            "unwatched" to "Не смотрел",
            "watched" to "Просмотрено",
            "saved" to "Сохранено",
            "downloaded" to "Скачано"
        ).forEach { (key, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 11.5f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(12), 0, dp(12), 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    searchFilter = key
                    updateChipStates()
                    renderSearchResults()
                }
            }
            chipViews[key] = chip
            chips.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)).apply {
                marginEnd = dp(7)
            })
        }
        updateChipStates()

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                (searchInput.tag as? Runnable)?.let { searchInput.removeCallbacks(it) }
                val render = Runnable { renderSearchResults() }
                searchInput.tag = render
                searchInput.postDelayed(render, 90L)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        fun closeSearch(): Boolean {
            if (!feedSearchOpen && searchPanel.visibility != View.VISIBLE) return false
            unregisterSearchBackInterceptor()
            feedSearchOpen = false

            (searchInput.tag as? Runnable)?.let(searchInput::removeCallbacks)
            searchInput.tag = null
            searchInput.clearFocus()
            searchFilter = "all"
            updateChipStates()

            searchPanel.animate().cancel()
            searchResultsHost.animate().cancel()
            contentHost.animate().cancel()

            if (settings.animations && searchPanel.visibility == View.VISIBLE) {
                contentHost.visibility = View.VISIBLE
                contentHost.alpha = 0f
                contentHost.translationY = dp(6).toFloat()
                contentHost.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(SohrMotion.NORMAL)
                    .setInterpolator(SohrMotion.smooth())
                    .start()

                if (searchResultsHost.visibility == View.VISIBLE) {
                    searchResultsHost.animate()
                        .alpha(0f)
                        .translationY(dp(5).toFloat())
                        .setDuration(SohrMotion.FAST)
                        .setInterpolator(SohrMotion.smooth())
                        .start()
                }

                searchPanel.animate()
                    .alpha(0f)
                    .translationY(-dp(6).toFloat())
                    .setDuration(SohrMotion.NORMAL)
                    .setInterpolator(SohrMotion.smooth())
                    .withEndAction {
                        searchPanel.visibility = View.GONE
                        searchPanel.alpha = 1f
                        searchPanel.translationY = 0f
                        searchResultsHost.visibility = View.GONE
                        searchResultsHost.alpha = 1f
                        searchResultsHost.translationY = 0f
                        searchInput.setText("")
                    }
                    .start()
            } else {
                searchPanel.visibility = View.GONE
                searchPanel.alpha = 1f
                searchPanel.translationY = 0f
                searchResultsHost.visibility = View.GONE
                searchResultsHost.alpha = 1f
                searchResultsHost.translationY = 0f
                contentHost.visibility = View.VISIBLE
                contentHost.alpha = 1f
                contentHost.translationY = 0f
                searchInput.setText("")
            }

            (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                ?.hideSoftInputFromWindow(searchInput.windowToken, 0)
            return true
        }

        closeFeedSearch = { closeSearch() }

        searchButton.setOnClickListener {
            animatePress(searchButton)
            val opening = !feedSearchOpen
            if (opening) {
                feedSearchOpen = true
                registerSearchBackInterceptor()
                searchPanel.visibility = View.VISIBLE
                if (settings.animations) {
                    searchPanel.animate().cancel()
                    contentHost.animate().cancel()
                    searchPanel.alpha = 0f
                    searchPanel.translationY = -dp(8).toFloat()
                    searchPanel.scaleX = 0.985f
                    searchPanel.scaleY = 0.985f
                    searchPanel.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(SohrMotion.NORMAL)
                        .setInterpolator(SohrMotion.smooth())
                        .start()
                    contentHost.animate()
                        .alpha(0.94f)
                        .scaleX(0.995f)
                        .scaleY(0.995f)
                        .setDuration(SohrMotion.FAST)
                        .setInterpolator(SohrMotion.smooth())
                        .withEndAction {
                            contentHost.alpha = 1f
                            contentHost.scaleX = 1f
                            contentHost.scaleY = 1f
                        }
                        .start()
                } else {
                    searchPanel.alpha = 1f
                    searchPanel.translationY = 0f
                }
                searchInput.requestFocus()
                searchInput.post {
                    (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                        ?.showSoftInput(searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
            } else {
                closeSearch()
            }
        }

        if (videoSection == 1) {
            contentHost.addView(buildTodayCard(regularVideos))
            t2x2LiveSlot = null
            contentHost.post { refreshT2x2Live() }

            val sinceLastVisit = regularVideos
                .asSequence()
                .filter {
                    previousVisitAtMs > 0L &&
                        it.date.toLong() * 1000L > previousVisitAtMs
                }
                .sortedWith(
                    compareByDescending<VideoItem> { it.date }
                        .thenByDescending { it.messageId }
                )
                .take(8)
                .toList()

            val continueVideos = videos
                .filter { playbackProgress(it) in 0.01f..0.985f }
                .sortedWith(
                    compareByDescending<VideoItem> { settings.lastPlayedAt(it.messageId) }
                        .thenByDescending { it.date }
                )
                .take(8)

            val sinceVisitIds = sinceLastVisit.mapTo(hashSetOf()) { it.messageId }
            val continueIds = continueVideos.mapTo(hashSetOf()) { it.messageId }
            val freshVideos = regularVideos
                .asSequence()
                .filterNot { it.messageId in continueIds || it.messageId in sinceVisitIds }
                .sortedWith(
                    compareByDescending<VideoItem> { it.date }
                        .thenByDescending { it.messageId }
                )
                .take(10)
                .toList()

            if (sinceLastVisit.isNotEmpty()) {
                contentHost.addView(
                    buildHomeVideoShelf(
                        title = "С прошлого раза",
                        subtitle = if (sinceLastVisit.size == 1) {
                            "1 новое видео после прошлого захода"
                        } else {
                            sinceLastVisit.size.toString() + " новых видео после прошлого захода"
                        },
                        items = sinceLastVisit,
                        mode = HomeVideoShelfAdapter.Mode.NEW
                    )
                )
            }

            if (continueVideos.isNotEmpty()) {
                contentHost.addView(
                    buildHomeVideoShelf(
                        title = "Продолжить просмотр",
                        subtitle = "С того места, где остановились",
                        items = continueVideos,
                        mode = HomeVideoShelfAdapter.Mode.CONTINUE
                    )
                )
            }

            if (freshVideos.isNotEmpty()) {
                contentHost.addView(
                    buildHomeVideoShelf(
                        title = "Новое",
                        subtitle = "Свежие видео, которые ещё не смотрели",
                        items = freshVideos,
                        mode = HomeVideoShelfAdapter.Mode.NEW
                    )
                )
            }

        } else {
            t2x2LiveSlot = null
            todayLiveStatusView = null
            todayLiveDot = null
        }

        val collectionHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(6))
        }
        collectionHeader.addView(
            TextView(this).apply {
                text = when (videoSection) {
                    2 -> "Лента"
                    3 -> "Просмотренные по дням"
                    else -> "Сборники по дням"
                }
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(this@MainActivity.text)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        collectionHeader.addView(
            TextView(this).apply {
                text = visibleGroups.size.toString()
                textSize = 11.5f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(purple)
                setPadding(dp(9), 0, dp(9), 0)
                background = roundedBg(palette.accentSoft, 13)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28))
        )
        contentHost.addView(collectionHeader)

        if (visibleGroups.isEmpty()) {
            contentHost.addView(
                TextView(this).apply {
                    text = when (videoSection) {
                        2 -> "Здесь позже появятся сообщения из Telegram-каналов."
                        3 -> "Просмотренных видео пока нет."
                        else -> "Новых непросмотренных видео пока нет."
                    }
                    textSize = 14.5f
                    gravity = Gravity.CENTER
                    setTextColor(muted)
                    setPadding(dp(28), dp(32), dp(28), dp(38))
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        } else {
            contentHost.addView(
                RecyclerView(this).apply {
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                    isNestedScrollingEnabled = false
                    layoutManager = LinearLayoutManager(this@MainActivity)
                    adapter = DayCollectionAdapter(
                        visibleGroups,
                        palette,
                        settings.animations
                    ) { showDayCollection(it) }
                    setBackgroundColor(bg)
                    setHasFixedSize(false)
                    itemAnimator = null
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        replaceRoot(withBottomNav(page, SohrTab.VIDEOS))
    }

    private fun buildHomeVideoShelf(
        title: String,
        subtitle: String,
        items: List<VideoItem>,
        mode: HomeVideoShelfAdapter.Mode
    ): View {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(4))
        }

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(8))
        }
        heading.addView(TextView(this).apply {
            text = title
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
        })
        heading.addView(TextView(this).apply {
            text = subtitle
            textSize = 11.5f
            setTextColor(muted)
            setPadding(0, dp(3), 0, 0)
        })
        section.addView(heading)

        val shelf = RecyclerView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            setHasFixedSize(true)
            setItemViewCacheSize(5)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            layoutManager = LinearLayoutManager(
                this@MainActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = HomeVideoShelfAdapter(
                items = items,
                palette = palette,
                animationsEnabled = settings.animations,
                mode = mode,
                progressFor = { playbackProgress(it) },
                lastPlayedAtFor = { settings.lastPlayedAt(it.messageId) },
                onClick = { item, source -> openPlayer(item, sourceView = source) },
                onLongClick = { item, source -> showVideoQuickActions(item, source) }
            )
            itemAnimator = null
            setPadding(dp(10), 0, dp(10), 0)
            clipToPadding = false
        }
        setupInlinePreviewShelf(shelf, items, mode)

        val compactCardWidth = (resources.displayMetrics.widthPixels * 0.60f).toInt()
            .coerceIn(dp(154), dp(205))
        val compactPreviewHeight = (compactCardWidth * 9f / 16f).toInt()
        val compactShelfHeight = compactPreviewHeight + dp(68)

        section.addView(
            shelf,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                compactShelfHeight
            )
        )
        return section
    }

    private fun setupInlinePreviewShelf(
        shelf: RecyclerView,
        items: List<VideoItem>,
        mode: HomeVideoShelfAdapter.Mode
    ) {
        if (settings.previewMode == "off" || mode != HomeVideoShelfAdapter.Mode.NEW || items.isEmpty()) return

        var pending: Runnable? = null
        fun cancelPending() {
            pending?.let(inlinePreviewHandler::removeCallbacks)
            pending = null
        }

        fun schedule() {
            cancelPending()
            val run = Runnable {
                if (!shelf.isAttachedToWindow || shelf.scrollState != RecyclerView.SCROLL_STATE_IDLE) return@Runnable
                val manager = shelf.layoutManager as? LinearLayoutManager ?: return@Runnable
                val first = manager.findFirstVisibleItemPosition()
                val last = manager.findLastVisibleItemPosition()
                if (first < 0 || last < first) return@Runnable

                val shelfCenter = shelf.width / 2f
                var bestPos = first
                var bestDistance = Float.MAX_VALUE
                for (position in first..last) {
                    val child = manager.findViewByPosition(position) ?: continue
                    val childCenter = (child.left + child.right) / 2f
                    val distance = kotlin.math.abs(childCenter - shelfCenter)
                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestPos = position
                    }
                }

                val item = items.getOrNull(bestPos) ?: return@Runnable
                val card = manager.findViewByPosition(bestPos) as? ViewGroup ?: return@Runnable
                val previewHost = card.getChildAt(0) as? FrameLayout ?: return@Runnable
                startInlinePreview(item, previewHost)
            }
            pending = run
            inlinePreviewHandler.postDelayed(run, 1_100L)
        }

        shelf.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    schedule()
                } else {
                    cancelPending()
                    stopInlinePreview()
                }
            }
        })
        shelf.post { schedule() }
    }

    private fun startInlinePreview(item: VideoItem, host: FrameLayout) {
        stopInlinePreview()

        val local = item.localPath?.let(::File)?.takeIf { it.exists() }
        if (local == null && settings.previewMode == "wifi") {
            val connectivity = getSystemService(android.net.ConnectivityManager::class.java)
            if (connectivity?.isActiveNetworkMetered == true) return
        }

        val uri = when {
            local != null -> Uri.fromFile(local).toString()
            item.source == "telegram" && streamServer != null && item.fileId > 0 -> {
                streamServer?.prefetch(item)
                streamServer!!.url(item)
            }
            else -> return
        }

        val player = inlinePreviewPlayer ?: androidx.media3.exoplayer.ExoPlayer.Builder(this)
            .build()
            .also { inlinePreviewPlayer = it }
        val view = inlinePreviewView ?: androidx.media3.ui.PlayerView(this).apply {
            useController = false
            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            setBackgroundColor(Color.BLACK)
            isClickable = false
            isFocusable = false
        }.also { inlinePreviewView = it }

        (view.parent as? ViewGroup)?.removeView(view)
        inlinePreviewHost = host
        view.alpha = 0f
        host.addView(
            view,
            minOf(1, host.childCount),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        player.volume = 0f
        player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
        player.playWhenReady = true
        view.player = player
        player.prepare()

        if (settings.animations) {
            view.animate().cancel()
            view.animate()
                .alpha(1f)
                .setDuration(SohrMotion.NORMAL)
                .setInterpolator(SohrMotion.smooth())
                .start()
        } else {
            view.alpha = 1f
        }

        val stop = Runnable { stopInlinePreview() }
        inlinePreviewStop = stop
        inlinePreviewHandler.postDelayed(stop, 6_200L)
    }

    private fun stopInlinePreview() {
        inlinePreviewStop?.let(inlinePreviewHandler::removeCallbacks)
        inlinePreviewStop = null

        inlinePreviewView?.let { view ->
            view.animate().cancel()
            (view.parent as? ViewGroup)?.removeView(view)
            view.player = null
        }
        inlinePreviewHost = null
        inlinePreviewPlayer?.runCatching {
            pause()
            clearMediaItems()
        }
    }

    private fun buildTodayCard(unwatchedVideos: List<VideoItem>): View {
        val hour = java.time.LocalTime.now().hour
        val greeting = when (hour) {
            in 5..11 -> "Доброе утро"
            in 12..17 -> "Добрый день"
            in 18..23 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
        val streak = streakTracker.currentStreak()

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(17), dp(14), dp(14), dp(14))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(palette.accentSoft, palette.surface)
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), palette.stroke)
            }
            elevation = dp(2).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener {
                animatePress(this)
                openT2x2OnTwitch()
            }
        }

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        copy.addView(TextView(this).apply {
            text = greeting
            textSize = 20f
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
        })
        val fallbackSummary = when (unwatchedVideos.size) {
            0 -> "Всё просмотрено"
            1 -> "1 новое"
            else -> unwatchedVideos.size.toString() + " новых"
        } + " • Стрик " + streak
        val summary = TextView(this).apply {
            text = fallbackSummary
            tag = fallbackSummary
            textSize = 11.8f
            includeFontPadding = false
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(muted)
            setPadding(0, dp(6), 0, 0)
        }
        copy.addView(summary)
        todaySummaryView = summary
        card.addView(
            copy,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val livePill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(11), 0)
            background = roundedBg(palette.surfaceAlt, 16)
        }
        val liveDot = LivePulseView(this).apply {
            setState(lastT2x2Live != null, settings.animations)
        }
        val liveText = TextView(this).apply {
            text = "T2x2 • не в сети"
            textSize = 11f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(muted)
        }
        livePill.addView(liveDot, LinearLayout.LayoutParams(dp(25), dp(25)).apply {
            marginEnd = dp(2)
        })
        livePill.addView(liveText)
        card.addView(
            livePill,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36))
        )

        todayLiveDot = liveDot
        todayLiveStatusView = liveText
        updateTodayLiveSummary(lastT2x2Live, lastT2x2LiveUnavailable)

        val wrapper = FrameLayout(this)
        wrapper.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(92)
            ).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
                topMargin = dp(8)
                bottomMargin = dp(4)
            }
        )
        return wrapper
    }

    private fun updateTodayLiveSummary(
        live: TwitchLiveStream?,
        unavailable: Boolean = false
    ) {
        val label = todayLiveStatusView ?: return
        val dot = todayLiveDot
        val isLive = live != null
        dot?.setState(isLive, settings.animations)
        if (isLive) {
            label.text = "T2x2 • в сети"
            label.setTextColor(Color.parseColor("#FF4458"))
            val title = live?.title
                ?.replace("\r\n", " ")
                ?.replace("\n", " ")
                ?.trim()
                ?.take(42)
                .orEmpty()
                .ifBlank { "T2x2 в эфире" }
            todaySummaryView?.text = "Эфир • " + liveElapsedLabel(live?.startedAt)
        } else {
            label.text = "T2x2 • не в сети"
            label.setTextColor(muted)
            todaySummaryView?.text = todaySummaryView?.tag as? String ?: "SOHR"
        }
    }

    private fun liveElapsedLabel(startedAt: String?): String {
        val minutes = runCatching {
            val start = java.time.Instant.parse(startedAt)
            java.time.Duration.between(start, java.time.Instant.now()).toMinutes().coerceAtLeast(0L)
        }.getOrDefault(0L)
        val hours = minutes / 60L
        val rest = minutes % 60L
        return if (hours > 0L) hours.toString() + " ч " + rest + " мин" else rest.toString() + " мин"
    }

    private fun watchLaterIds(): Set<String> =
        getSharedPreferences("sohr_watch_later", MODE_PRIVATE)
            .getStringSet("ids", emptySet())
            ?.toSet()
            ?: emptySet()

    private fun toggleWatchLater(item: VideoItem): Boolean {
        val prefs = getSharedPreferences("sohr_watch_later", MODE_PRIVATE)
        val ids = prefs.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        val key = item.messageId.toString()
        val added = if (ids.contains(key)) {
            ids.remove(key)
            false
        } else {
            ids.add(key)
            true
        }
        prefs.edit().putStringSet("ids", ids).apply()
        return added
    }

    private fun favoriteIds(): Set<String> =
        getSharedPreferences("sohr_favorites", MODE_PRIVATE)
            .getStringSet("ids", emptySet())
            ?.toSet()
            ?: emptySet()

    private fun toggleFavorite(item: VideoItem): Boolean {
        val prefs = getSharedPreferences("sohr_favorites", MODE_PRIVATE)
        val ids = prefs.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        val key = item.messageId.toString()
        val added = if (ids.contains(key)) {
            ids.remove(key)
            false
        } else {
            ids.add(key)
            true
        }
        prefs.edit().putStringSet("ids", ids).apply()
        return added
    }

    private fun downloadedVideoIds(): Set<String> {
        val legacy = getSharedPreferences("sohr_downloaded", MODE_PRIVATE)
            .getStringSet("ids", emptySet())
            ?.toSet()
            ?: emptySet()
        return legacy + downloadStore.entries().map { it.messageId.toString() }
    }

    private fun markDownloaded(item: VideoItem) {
        val prefs = getSharedPreferences("sohr_downloaded", MODE_PRIVATE)
        val ids = prefs.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.add(item.messageId.toString())
        prefs.edit().putStringSet("ids", ids).apply()
    }

    private fun showVideoQuickActions(item: VideoItem, sourceView: View) {
        SohrHaptics.longPress(sourceView)

        val dialog = BottomSheetDialog(this)
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(18))
            background = roundedBg(panel, 26)
        }

        val title = TextView(this).apply {
            text = item.title.ifBlank { "Видео" }
            textSize = 16f
            maxLines = 2
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(dp(8), dp(4), dp(8), dp(10))
        }
        sheet.addView(title)

        fun action(
            label: String,
            subtitle: String? = null,
            close: Boolean = true,
            block: () -> Unit
        ) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(9), dp(14), dp(9))
                background = roundedBg(palette.surfaceAlt, 16)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    SohrHaptics.tap(this)
                    animatePress(this)
                    if (close) dialog.dismiss()
                    block()
                }
            }
            row.addView(TextView(this).apply {
                text = label
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(this@MainActivity.text)
            })
            if (!subtitle.isNullOrBlank()) {
                row.addView(TextView(this).apply {
                    text = subtitle
                    textSize = 11f
                    setTextColor(muted)
                    setPadding(0, dp(3), 0, 0)
                })
            }
            sheet.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(7) }
            )
        }

        action("Смотреть", "Открыть видео") {
            openPlayer(item, sourceView = sourceView)
        }

        val saved = watchLaterIds().contains(item.messageId.toString())
        action(
            if (saved) "Убрать из «Смотреть позже»" else "Смотреть позже",
            if (saved) "Видео останется в медиатеке" else "Сохранить на потом"
        ) {
            val added = toggleWatchLater(item)
            Toast.makeText(
                this,
                if (added) "Добавлено в «Смотреть позже»" else "Убрано из «Смотреть позже»",
                Toast.LENGTH_SHORT
            ).show()
        }

        val favorite = favoriteIds().contains(item.messageId.toString())
        action(
            if (favorite) "Убрать из избранного" else "В избранное",
            if (favorite) "Оставить только в других списках" else "Закрепить в сохранённых"
        ) {
            val added = toggleFavorite(item)
            Toast.makeText(
                this,
                if (added) "Добавлено в избранное" else "Убрано из избранного",
                Toast.LENGTH_SHORT
            ).show()
        }

        action("Воспроизвести следующим", "Поставить первым в очереди") {
            experienceStore.addNext(item.messageId)
            Toast.makeText(this, "Будет следующим", Toast.LENGTH_SHORT).show()
        }

        action("В конец очереди", "Добавить после уже выбранных видео") {
            experienceStore.addLast(item.messageId)
            Toast.makeText(this, "Добавлено в очередь", Toast.LENGTH_SHORT).show()
        }

        val queueSize = experienceStore.queueIds().size
        if (queueSize > 0) {
            action("Очередь • " + queueSize, "Изменить порядок или очистить") {
                showPlaybackQueue()
            }
        }

        val watched = isVideoWatched(item.messageId)
        action(
            if (watched) "Отметить непросмотренным" else "Отметить просмотренным"
        ) {
            if (watched) unmarkVideoWatched(item.messageId) else markVideoWatched(item.messageId)
            suppressNextRootAnimation = true
            showFeed(currentVideos)
        }

        if (item.source == "telegram") {
            action("Скачать", "Сохранить копию в папку SOHR приложения") {
                downloadVideoQuick(item)
            }
        }

        val downloadedEntries = downloadStore.entries()
        if (downloadedEntries.isNotEmpty()) {
            action("Загрузки • " + downloadedEntries.size, "Открыть сохранённые видео") {
                showDownloadCenter()
            }
        }

        action("Поделиться") {
            val shareText = item.externalUrl?.takeIf { it.isNotBlank() }
                ?: ("SOHR • " + item.title)
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    },
                    "Поделиться"
                )
            )
        }

        dialog.setContentView(sheet)
        dialog.setOnShowListener {
            dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                ?.background = ColorDrawable(Color.TRANSPARENT)
        }
        dialog.show()
    }

    private fun downloadTwitchVod(item: VideoItem, hlsUrl: String) {
        if (!activeTwitchDownloads.add(item.messageId)) {
            Toast.makeText(this, "Видео уже загружается", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Загрузка началась", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    fun readText(url: String): String {
                        val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                            connectTimeout = 12_000
                            readTimeout = 20_000
                            instanceFollowRedirects = true
                            setRequestProperty("User-Agent", "SOHR/" + BuildConfig.VERSION_NAME)
                            setRequestProperty("Accept", "application/vnd.apple.mpegurl, application/x-mpegURL, */*")
                        }
                        return try {
                            val code = connection.responseCode
                            check(code in 200..299) { "Twitch вернул HTTP " + code }
                            connection.inputStream.bufferedReader().use { it.readText() }
                        } finally {
                            connection.disconnect()
                        }
                    }

                    fun absolute(base: String, child: String): String =
                        java.net.URI(base).resolve(child.trim()).toString()

                    val master = readText(hlsUrl)
                    val masterLines = master.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
                    var mediaUrl = hlsUrl
                    var bestBandwidth = -1L
                    for (index in masterLines.indices) {
                        val line = masterLines[index]
                        if (!line.startsWith("#EXT-X-STREAM-INF", true)) continue
                        val bandwidth = Regex("""BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE)
                            .find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                        val child = masterLines.drop(index + 1).firstOrNull { !it.startsWith("#") } ?: continue
                        if (bandwidth > bestBandwidth) {
                            bestBandwidth = bandwidth
                            mediaUrl = absolute(hlsUrl, child)
                        }
                    }

                    val playlist = if (mediaUrl == hlsUrl) master else readText(mediaUrl)
                    if (
                        playlist.lineSequence().any {
                            it.startsWith("#EXT-X-KEY", true) && !it.contains("METHOD=NONE", true)
                        }
                    ) {
                        error("Эта запись Twitch защищена и не может быть сохранена напрямую")
                    }

                    val lines = playlist.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
                    val initUri = lines.firstOrNull { it.startsWith("#EXT-X-MAP", true) }
                        ?.let { Regex("""URI="([^"]+)"""").find(it)?.groupValues?.getOrNull(1) }
                    val segments = lines.filter { !it.startsWith("#") }
                    check(segments.isNotEmpty()) { "Twitch не вернул сегменты видео" }

                    val dir = File(
                        getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir,
                        "SOHR"
                    ).apply { mkdirs() }
                    val safeName = item.title
                        .replace(Regex("""[\/:*?"<>|]"""), "_")
                        .take(64)
                        .ifBlank { "twitch_" + item.messageId }
                    val extension = if (initUri != null) "mp4" else "ts"
                    val temp = File(dir, "." + safeName + "_" + item.messageId + ".part")
                    val target = File(dir, safeName + "_" + item.messageId + "." + extension)

                    fun appendUrl(url: String, out: java.io.OutputStream) {
                        val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                            connectTimeout = 12_000
                            readTimeout = 30_000
                            instanceFollowRedirects = true
                            setRequestProperty("User-Agent", "SOHR/" + BuildConfig.VERSION_NAME)
                        }
                        try {
                            val code = connection.responseCode
                            check(code in 200..299) { "Не удалось скачать сегмент Twitch • HTTP " + code }
                            connection.inputStream.use { input -> input.copyTo(out, 128 * 1024) }
                        } finally {
                            connection.disconnect()
                        }
                    }

                    temp.outputStream().buffered(256 * 1024).use { out ->
                        initUri?.let { appendUrl(absolute(mediaUrl, it), out) }
                        segments.forEach { segment ->
                            if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                                error("Загрузка отменена")
                            }
                            appendUrl(absolute(mediaUrl, segment), out)
                        }
                    }
                    if (target.exists()) target.delete()
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                    target
                }
            }

            activeTwitchDownloads.remove(item.messageId)
            result.onSuccess { file ->
                markDownloaded(item)
                downloadStore.register(item, file, autoManaged = false)
                Toast.makeText(
                    this@MainActivity,
                    "Скачано • " + file.name,
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    this@MainActivity,
                    if (isTwitchNetworkFailure(error)) {
                        "Не удалось скачать Twitch-видео. Проверьте интернет."
                    } else {
                        "Не удалось скачать Twitch-видео."
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun downloadVideoQuick(
        item: VideoItem,
        autoManaged: Boolean = false,
        silent: Boolean = false
    ) {
        if (item.fileId > 0 && !activeManualDownloads.add(item.fileId)) {
            if (!silent) Toast.makeText(this, "Видео уже загружается", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            if (item.fileId > 0 && !silent) {
                Toast.makeText(this@MainActivity, "Загрузка началась", Toast.LENGTH_SHORT).show()
            }

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val source = item.localPath
                        ?.let(::File)
                        ?.takeIf { it.exists() }
                        ?: run {
                            check(telegramReady && item.fileId > 0) {
                                "Видео пока недоступно для скачивания"
                            }
                            val server = streamServer
                                ?: error("Загрузка Telegram пока недоступна")
                            server.downloadFully(item)
                        }

                    val ext = when {
                        item.mimeType.contains("webm", true) -> "webm"
                        item.mimeType.contains("quicktime", true) -> "mov"
                        else -> "mp4"
                    }
                    val dir = File(
                        getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir,
                        "SOHR"
                    ).apply { mkdirs() }
                    val safeName = item.title
                        .replace(Regex("""[\\/:*?"<>|]"""), "_")
                        .take(64)
                        .ifBlank { "video_" + item.messageId }
                    val target = File(dir, safeName + "_" + item.messageId + "." + ext)
                    source.copyTo(target, overwrite = true)

                    target
                }
            }

            if (item.fileId > 0) activeManualDownloads.remove(item.fileId)

            result.onSuccess { file ->
                markDownloaded(item)
                downloadStore.register(item, file, autoManaged = autoManaged)
                if (!silent) {
                    Toast.makeText(
                        this@MainActivity,
                        "Скачано • " + file.name,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.onFailure {
                if (!silent) {
                    Toast.makeText(
                        this@MainActivity,
                        "Не удалось скачать видео. Проверьте интернет и попробуйте ещё раз.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun scheduleSmartDownloads(videos: List<VideoItem>) {
        smartDownloadJob?.cancel()
        if (!settings.smartDownloads || !telegramReady || videos.isEmpty()) return

        val connectivity = getSystemService(android.net.ConnectivityManager::class.java)
        val network = connectivity?.activeNetwork ?: return
        val caps = connectivity.getNetworkCapabilities(network) ?: return
        val wifiOnly =
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) &&
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        if (!wifiOnly) return

        val watched = watchedVideoIds()
        val candidates = videos
            .asSequence()
            .filter { it.source == "telegram" && it.fileId > 0 && it.fileSize > 0L }
            .filterNot { watched.contains(it.messageId.toString()) }
            .filter { it.fileSize <= 2L * 1024L * 1024L * 1024L }
            .sortedWith(compareByDescending<VideoItem> { it.date }.thenByDescending { it.messageId })
            .take(2)
            .toList()

        smartDownloadJob = lifecycleScope.launch {
            val targetIds = candidates.mapTo(hashSetOf()) { it.messageId }

            downloadStore.autoEntries()
                .filterNot { it.messageId in targetIds }
                .forEach { old ->
                    withContext(Dispatchers.IO) {
                        runCatching { File(old.path).delete() }
                    }
                    downloadStore.remove(old.messageId)
                }

            candidates.forEach { item ->
                if (!downloadStore.contains(item.messageId)) {
                    val free = (getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir).usableSpace
                    if (free > item.fileSize + 256L * 1024L * 1024L) {
                        downloadVideoQuick(item, autoManaged = true, silent = true)
                        delay(350L)
                    }
                }
            }
        }
    }

    private fun showPlaybackQueue() {
        val dialog = BottomSheetDialog(this)
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(18))
            background = roundedBg(panel, 26)
        }

        val ids = experienceStore.queueIds()
        val byId = currentVideos.associateBy { it.messageId }
        val items = ids.mapNotNull(byId::get)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(3))
        }
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        labels.addView(TextView(this).apply {
            text = "Сессия просмотра"
            textSize = 19f
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
        })
        labels.addView(TextView(this).apply {
            text = if (items.isEmpty()) "Очередь пока пустая" else "Зажмите карточку и перетащите"
            textSize = 11.5f
            includeFontPadding = false
            setTextColor(muted)
            setPadding(0, dp(3), 0, 0)
        })
        header.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        if (items.isNotEmpty()) {
            header.addView(TextView(this).apply {
                text = "Очистить"
                textSize = 11.5f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(purple)
                setPadding(dp(10), dp(7), dp(10), dp(7))
                background = roundedBg(palette.accentSoft, 13)
                isClickable = true
                setOnClickListener {
                    SohrHaptics.confirm(this)
                    experienceStore.clearQueue()
                    dialog.dismiss()
                }
            })
        }
        sheet.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        if (items.isEmpty()) {
            sheet.addView(TextView(this).apply {
                text = "Добавьте видео через долгое нажатие → «Воспроизвести следующим» или «В конец очереди»."
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(muted)
                setPadding(dp(18), dp(26), dp(18), dp(28))
            })
        } else {
            val recycler = androidx.recyclerview.widget.RecyclerView(this).apply {
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MainActivity)
                overScrollMode = View.OVER_SCROLL_NEVER
                itemAnimator = null
                setHasFixedSize(false)
            }

            lateinit var adapter: PlaybackSessionAdapter
            adapter = PlaybackSessionAdapter(
                palette = palette,
                source = items,
                onPlay = { item ->
                    dialog.dismiss()
                    experienceStore.removeFromQueue(item.messageId)
                    openPlayer(item)
                },
                onRemove = { item ->
                    SohrHaptics.select(recycler)
                    experienceStore.removeFromQueue(item.messageId)
                    adapter.remove(item.messageId)
                }
            )
            recycler.adapter = adapter

            val touchHelper = androidx.recyclerview.widget.ItemTouchHelper(
                object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                    androidx.recyclerview.widget.ItemTouchHelper.UP or
                        androidx.recyclerview.widget.ItemTouchHelper.DOWN,
                    0
                ) {
                    override fun onMove(
                        recyclerView: androidx.recyclerview.widget.RecyclerView,
                        viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                        target: androidx.recyclerview.widget.RecyclerView.ViewHolder
                    ): Boolean {
                        val moved = adapter.move(
                            viewHolder.bindingAdapterPosition,
                            target.bindingAdapterPosition
                        )
                        if (moved) {
                            experienceStore.replaceQueue(adapter.ids())
                            SohrHaptics.select(recyclerView)
                        }
                        return moved
                    }

                    override fun onSwiped(
                        viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                        direction: Int
                    ) = Unit

                    override fun isLongPressDragEnabled(): Boolean = true

                    override fun onSelectedChanged(
                        viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder?,
                        actionState: Int
                    ) {
                        super.onSelectedChanged(viewHolder, actionState)
                        viewHolder?.itemView?.let { view ->
                            if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG) {
                                view.animate().cancel()
                                view.animate()
                                    .scaleX(1.025f)
                                    .scaleY(1.025f)
                                    .alpha(0.96f)
                                    .setDuration(SohrMotion.FAST)
                                    .start()
                            }
                        }
                    }

                    override fun clearView(
                        recyclerView: androidx.recyclerview.widget.RecyclerView,
                        viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder
                    ) {
                        super.clearView(recyclerView, viewHolder)
                        viewHolder.itemView.animate().cancel()
                        viewHolder.itemView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(SohrMotion.FAST)
                            .setInterpolator(SohrMotion.smooth())
                            .start()
                        experienceStore.replaceQueue(adapter.ids())
                    }
                }
            )
            touchHelper.attachToRecyclerView(recycler)

            sheet.addView(
                recycler,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    minOf(dp(390), items.size * dp(78) + dp(8))
                )
            )
        }

        dialog.setContentView(sheet)
        dialog.setOnShowListener {
            dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                ?.background = ColorDrawable(Color.TRANSPARENT)
        }
        dialog.show()
    }

    private fun showDownloadCenter() {
        auxiliaryScreen = "downloads"
        stopInlinePreview()
        val entries = downloadStore.entries()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(20))
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            imageTintList = ColorStateList.valueOf(this@MainActivity.text)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = roundedBg(palette.surfaceAlt, 21)
            contentDescription = "Назад"
            setOnClickListener {
                SohrMotion.press(this, settings.animations)
                auxiliaryScreen = null
                pendingRootSlide = -1
                showSelectedVideoSource()
            }
        }, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
            marginEnd = dp(11)
        })
        header.addView(TextView(this).apply {
            text = "Загрузки"
            textSize = 23f
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = formatBytes(downloadStore.totalBytes())
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(purple)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(9), dp(6), dp(9), dp(6))
            background = roundedBg(palette.accentSoft, 13)
        })
        page.addView(header)

        page.addView(TextView(this).apply {
            text = if (entries.isEmpty()) "Скачанных видео пока нет" else "Офлайн-копии SOHR"
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(4), 0, dp(14))
        })

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)

        if (entries.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Здесь появятся видео, которые вы скачаете из SOHR."
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(muted)
                setPadding(dp(18), dp(34), dp(18), dp(34))
            })
        } else {
            entries.forEach { entry ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(13), dp(11), dp(10), dp(11))
                    background = roundedBg(panel, 18)
                }
                row.addView(TextView(this).apply {
                    text = entry.title
                        .replace("\r\n", "\n")
                        .lineSequence()
                        .firstOrNull { it.isNotBlank() }
                        ?.trim()
                        .orEmpty()
                        .ifBlank { "Видео" }
                    textSize = 13.5f
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(this@MainActivity.text)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(TextView(this).apply {
                    text = (if (entry.autoManaged) "Авто • " else "") + formatBytes(entry.sizeBytes)
                    textSize = 10.5f
                    setTextColor(muted)
                    setPadding(dp(8), 0, dp(8), 0)
                })
                row.addView(TextView(this).apply {
                    text = "×"
                    textSize = 17f
                    gravity = Gravity.CENTER
                    setTextColor(muted)
                    background = roundedBg(palette.surfaceAlt, 14)
                    isClickable = true
                    setOnClickListener {
                        SohrHaptics.confirm(this)
                        downloadStore.remove(entry.messageId)
                        showDownloadCenter()
                    }
                }, LinearLayout.LayoutParams(dp(36), dp(36)))

                row.setOnClickListener {
                    val file = File(entry.path)
                    val item = currentVideos.firstOrNull { it.messageId == entry.messageId }
                    if (item != null && file.exists()) {
                        pendingRootSlide = 1
                        openPlayer(item.copy(localPath = file.absolutePath))
                    }
                }

                list.addView(row, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) })
            }
        }

        page.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        replaceRoot(withBottomNav(page, SohrTab.VIDEOS))
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 МБ"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.1f ГБ", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.0f МБ", mb)
        }
    }

    private fun showFeedSkeleton(message: String) {
        startupPhase = false
        startupStatusView = null

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(24))
        }
        val pulseViews = mutableListOf<View>()

        body.addView(TextView(this).apply {
            text = "SOHR"
            textSize = 25f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
        })
        body.addView(TextView(this).apply {
            text = message
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(5), 0, dp(14))
        })

        fun skeleton(height: Int, radius: Int = 18): View =
            View(this).apply {
                background = roundedBg(palette.surfaceAlt, radius)
                pulseViews += this
            }

        body.addView(
            skeleton(92, 24),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92)).apply {
                bottomMargin = dp(18)
            }
        )

        repeat(2) {
            val label = skeleton(18, 9)
            body.addView(
                label,
                LinearLayout.LayoutParams(dp(if (it == 0) 150 else 96), dp(18)).apply {
                    bottomMargin = dp(9)
                }
            )
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            repeat(2) {
                row.addView(
                    skeleton(154, 18),
                    LinearLayout.LayoutParams(0, dp(154), 1f).apply {
                        if (it == 0) marginEnd = dp(8) else marginStart = dp(8)
                    }
                )
            }
            body.addView(
                row,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(154)).apply {
                    bottomMargin = dp(20)
                }
            )
        }

        scroll.addView(
            body,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        page.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val animator = android.animation.ValueAnimator.ofFloat(0.52f, 0.92f).apply {
            duration = 720L
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { value ->
                val alpha = value.animatedValue as Float
                pulseViews.forEach { it.alpha = alpha }
            }
        }
        page.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (settings.animations && !animator.isStarted) animator.start()
            }
            override fun onViewDetachedFromWindow(v: View) {
                animator.cancel()
                page.removeOnAttachStateChangeListener(this)
            }
        })

        replaceRoot(withBottomNav(page, SohrTab.VIDEOS))
    }

    private fun refreshT2x2Live(slot: FrameLayout? = null) {
        twitchLiveJob?.cancel()

        val now = System.currentTimeMillis()
        if (lastT2x2LiveCheckedAt > 0L && now - lastT2x2LiveCheckedAt < t2x2LiveCacheMs) {
            updateTodayLiveSummary(lastT2x2Live, lastT2x2LiveUnavailable)
            slot?.takeIf { it.isAttachedToWindow }
                ?.let { renderT2x2Live(it, lastT2x2Live, lastT2x2LiveUnavailable) }
            return
        }

        val clientId = BuildConfig.TWITCH_CLIENT_ID.trim()
        val token = settings.twitchAccessToken
        if (clientId.isBlank() || token.isNullOrBlank()) {
            lastT2x2Live = null
            lastT2x2LiveUnavailable = true
            lastT2x2LiveCheckedAt = now
            updateTodayLiveSummary(null, unavailable = true)
            slot?.takeIf { it.isAttachedToWindow }
                ?.let { renderT2x2Live(it, null, unavailable = true) }
            return
        }

        twitchLiveJob = lifecycleScope.launch {
            try {
                val live = TwitchApi.loadLiveStream(clientId, token, "t2x2")
                lastT2x2Live = live
                lastT2x2LiveUnavailable = false
                lastT2x2LiveCheckedAt = System.currentTimeMillis()

                if (live != null) maybeNotifyT2x2Live(live)
                updateTodayLiveSummary(live)
                slot?.takeIf { it.isAttachedToWindow }
                    ?.let { renderT2x2Live(it, live) }
            } catch (_: TwitchAuthException) {
                lastT2x2Live = null
                lastT2x2LiveUnavailable = true
                lastT2x2LiveCheckedAt = System.currentTimeMillis()
                updateTodayLiveSummary(null, unavailable = true)
                slot?.takeIf { it.isAttachedToWindow }
                    ?.let { renderT2x2Live(it, null, unavailable = true) }
            } catch (e: Exception) {
                if (isTwitchNetworkFailure(e)) markTwitchNetworkFailure()
                lastT2x2Live = null
                lastT2x2LiveUnavailable = true
                lastT2x2LiveCheckedAt = System.currentTimeMillis()
                updateTodayLiveSummary(null, unavailable = true)
                slot?.takeIf { it.isAttachedToWindow }
                    ?.let { renderT2x2Live(it, null, unavailable = true) }
            }
        }
    }

    private fun blockTemporaryLive(login: String) {
        val normalized = login.trim().lowercase()
        if (normalized.isBlank()) return
        temporaryBlockedLiveLogins.remove(normalized)
        temporaryBlockedLiveLogins.add(normalized)
        while (temporaryBlockedLiveLogins.size > 12) {
            temporaryBlockedLiveLogins.remove(temporaryBlockedLiveLogins.first())
        }
    }

    private fun skipBlockedTemporaryPreview(slot: FrameLayout, live: TwitchLiveStream) {
        if (!temporaryLivePreviewEnabled || !live.testStream) return
        blockTemporaryLive(live.login)
        lastT2x2Live = null
        lastT2x2LiveUnavailable = false
        lastT2x2LiveCheckedAt = 0L
        if (slot.isAttachedToWindow) refreshT2x2Live(slot)
    }

    private fun renderT2x2Live(
        slot: FrameLayout,
        live: TwitchLiveStream?,
        unavailable: Boolean = false
    ) {
        updateTodayLiveSummary(live, unavailable)
        val animateIn = slot.childCount == 0
        slot.removeAllViews()
        slot.visibility = View.VISIBLE

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(12), 0)
            background = roundedBg(palette.surface, 20)
            elevation = dp(1).toFloat()
        }

        val pulse = LivePulseView(this).apply {
            setState(live != null, settings.animations)
        }
        card.addView(
            pulse,
            LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                marginEnd = dp(11)
            }
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        info.addView(TextView(this).apply {
            text = "T2x2"
            textSize = 16.5f
            includeFontPadding = false
            maxLines = 1
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
        })

        val status = TextView(this).apply {
            textSize = 11.4f
            includeFontPadding = false
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(5), 0, 0)
        }

        fun updateStatus() {
            if (live == null) {
                status.text =
                    if (unavailable) "Статус временно недоступен"
                    else "Не в сети"
                status.setTextColor(muted)
            } else {
                status.text = buildString {
                    append("В сети • ")
                    append(formatLiveDuration(live.startedAt))
                    append(" • ")
                    append(formatViewerCountCompact(live.viewerCount))
                    append(" зр.")
                }
                status.setTextColor(Color.parseColor("#43D18D"))
            }
        }
        updateStatus()
        info.addView(status)

        if (live != null) {
            val ticker = object : Runnable {
                override fun run() {
                    if (!slot.isAttachedToWindow || card.parent !== slot) return
                    updateStatus()
                    slot.postDelayed(this, 30_000L)
                }
            }
            slot.postDelayed(ticker, 30_000L)
        }

        card.addView(
            info,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val action = TextView(this).apply {
            text = if (live != null) "Twitch" else "Офлайн"
            textSize = 11.5f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (live != null) purple else muted)
            background = roundedBg(
                if (live != null) palette.accentSoft else palette.surfaceAlt,
                15
            )
            alpha = if (live != null) 1f else 0.72f
        }
        card.addView(
            action,
            LinearLayout.LayoutParams(dp(76), dp(36)).apply {
                marginStart = dp(10)
            }
        )

        card.isClickable = live != null
        card.isFocusable = live != null
        if (live != null) {
            card.setOnClickListener {
                animatePress(card)
                openT2x2OnTwitch()
            }
        }

        slot.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(72),
                Gravity.CENTER
            )
        )

        if (animateIn && settings.animations) {
            card.alpha = 0f
            card.translationY = dp(4).toFloat()
            card.scaleX = 0.994f
            card.scaleY = 0.994f
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220L)
                .setInterpolator(
                    android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
                )
                .start()
        }
    }

    private fun formatLiveDuration(startedAt: String): String {
        val startMs = runCatching { Instant.parse(startedAt).toEpochMilli() }.getOrNull()
            ?: return "эфир идёт"

        val totalMinutes =
            ((System.currentTimeMillis() - startMs).coerceAtLeast(0L) / 60_000L)
                .coerceAtLeast(0L)

        val days = totalMinutes / (24L * 60L)
        val hours = (totalMinutes % (24L * 60L)) / 60L
        val minutes = totalMinutes % 60L

        return when {
            days > 0L -> "$days д $hours ч"
            hours > 0L -> "$hours ч $minutes мин"
            else -> "$minutes мин"
        }
    }

    private fun formatViewerCountCompact(value: Int): String =
        when {
            value >= 1_000_000 -> String.format(Locale.US, "%.1f млн", value / 1_000_000.0)
            value >= 1_000 -> String.format(Locale.US, "%.1f тыс.", value / 1_000.0)
            else -> value.toString()
        }

    private fun t2x2Intent(): Intent {
        val url = Uri.parse("https://www.twitch.tv/t2x2")
        val twitchApp = Intent(Intent.ACTION_VIEW, url).apply {
            setPackage("tv.twitch.android.app")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (twitchApp.resolveActivity(packageManager) != null) {
            twitchApp
        } else {
            Intent(Intent.ACTION_VIEW, url).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    private fun openT2x2OnTwitch() {
        runCatching {
            startActivity(t2x2Intent())
        }.onFailure {
            Toast.makeText(
                this,
                "Не удалось открыть Twitch",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupT2x2Notifications() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    T2X2_NOTIFICATION_CHANNEL,
                    "Эфиры T2x2",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Уведомление, когда T2x2 начинает стрим"
                    enableVibration(true)
                }
            )
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val prefs = getSharedPreferences("sohr_runtime", MODE_PRIVATE)
            if (!prefs.getBoolean("t2x2_notification_permission_requested", false)) {
                prefs.edit()
                    .putBoolean("t2x2_notification_permission_requested", true)
                    .apply()
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    T2X2_NOTIFICATION_PERMISSION_REQUEST
                )
            }
        }
    }

    private fun maybeNotifyT2x2Live(live: TwitchLiveStream) {
        if (!live.login.equals("t2x2", ignoreCase = true)) return
        if (live.startedAt.isBlank()) return

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val prefs = getSharedPreferences("sohr_runtime", MODE_PRIVATE)
        val previousStart = prefs.getString("last_t2x2_notified_started_at", null)
        if (previousStart == live.startedAt) return

        val pendingIntent = PendingIntent.getActivity(
            this,
            2202,
            t2x2Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val manager = getSystemService(NotificationManager::class.java)
        val builder =
            if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(this, T2X2_NOTIFICATION_CHANNEL)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

        val notification = builder
            .setSmallIcon(R.drawable.ic_notification_live)
            .setContentTitle("T2x2 начал стрим")
            .setContentText(
                live.title.ifBlank { "T2x2 сейчас в эфире" }
            )
            .setStyle(
                Notification.BigTextStyle().bigText(
                    buildString {
                        append(live.title.ifBlank { "T2x2 сейчас в эфире" })
                        append("\n")
                        append("В эфире • ")
                        append(formatLiveDuration(live.startedAt))
                    }
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_EVENT)
            .setOnlyAlertOnce(true)
            .build()

        manager.notify(T2X2_NOTIFICATION_ID, notification)
        prefs.edit()
            .putString("last_t2x2_notified_started_at", live.startedAt)
            .apply()
    }

    private fun startT2x2LiveWatch() {
        if (t2x2WatchJob?.isActive == true) return

        t2x2WatchJob = lifecycleScope.launch {
            delay(1_200L)

            while (isActive) {
                val clientId = BuildConfig.TWITCH_CLIENT_ID.trim()
                val token = settings.twitchAccessToken

                if (
                    !settings.guestMode &&
                    clientId.isNotBlank() &&
                    !token.isNullOrBlank() &&
                    canAttemptTwitchNetwork()
                ) {
                    val result = runCatching {
                        TwitchApi.loadLiveStream(clientId, token, "t2x2")
                    }

                    if (result.isSuccess) {
                        val live = result.getOrNull()
                        lastT2x2Live = live
                        lastT2x2LiveUnavailable = false
                        lastT2x2LiveCheckedAt = System.currentTimeMillis()
                        if (live != null) maybeNotifyT2x2Live(live)
                        updateTodayLiveSummary(live)

                        t2x2LiveSlot
                            ?.takeIf { it.isAttachedToWindow }
                            ?.let { renderT2x2Live(it, live) }
                    } else {
                        result.exceptionOrNull()
                            ?.takeIf(::isTwitchNetworkFailure)
                            ?.let { markTwitchNetworkFailure() }
                        lastT2x2Live = null
                        lastT2x2LiveUnavailable = true
                        lastT2x2LiveCheckedAt = System.currentTimeMillis()
                        updateTodayLiveSummary(null, unavailable = true)
                    }
                }

                delay(60_000L)
            }
        }
    }

    private fun showDayCollection(collection: DayCollection) {
        currentDay = collection
        isPlayerScreen = false
        isSettingsScreen = false
        isAccountScreen = false
        setFullscreen(false)
        applySystemTheme()

        val sortedVideos = collection.videos.sortedWith(compareBy<VideoItem> { it.date }.thenBy { it.messageId })

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
            val sourceName = if (collection.videos.firstOrNull()?.source == "twitch") "Twitch • @t2x2" else "@t2x2_video"
            text = "${collection.videos.size} видео • $sourceName"
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

        val list = RecyclerView(this).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = VideoAdapter(
                items = sortedVideos,
                palette = palette,
                animationsEnabled = settings.animations,
                progressFor = { playbackProgress(it) },
                onClick = { item, source -> openPlayer(item, sourceView = source) },
                onLongClick = { item, source -> showVideoQuickActions(item, source) }
            )
            setBackgroundColor(bg)
            setHasFixedSize(true)
            itemAnimator = if (settings.animations) itemAnimator else null
        }

        page.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        replaceRoot(withBottomNav(page, SohrTab.VIDEOS))
    }

    private fun playbackProgress(item: VideoItem): Float {
        val durationMs = item.durationSeconds.toLong() * 1000L
        if (durationMs <= 0L) return 0f
        val positionMs = settings.playbackPosition(item.messageId)
        if (positionMs < 1_000L || positionMs >= durationMs - 10_000L) return 0f
        return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
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

    private fun prefetchNextCandidate(
        server: TelegramStreamServer?,
        item: VideoItem
    ) {
        if (server == null || item.fileId <= 0 || item.fileSize <= 0L) return
        val connectivity = getSystemService(android.net.ConnectivityManager::class.java)
        val maxBytes = if (connectivity?.isActiveNetworkMetered == true) {
            6L * 1024L * 1024L
        } else {
            24L * 1024L * 1024L
        }
        server.prefetchFraction(
            item = item,
            fraction = 0.15f,
            maxBytes = maxBytes
        )
    }

    private fun openPlayer(
        item: VideoItem,
        startSeconds: Int = 0,
        sourceView: View? = null
    ) {
        stopInlinePreview()
        val sourceBounds = sourceView?.let { source ->
            Rect().takeIf { rect -> source.getGlobalVisibleRect(rect) && !rect.isEmpty }
        }
        fullScreen = false
        if (item.source == "twitch") {
            openTwitchPlayer(item, startSeconds)
            return
        }
        twitchPlayerScreen?.destroy()
        twitchPlayerScreen = null
        twitchLivePlayerScreen?.destroy()
        twitchLivePlayerScreen = null
        val localFile = item.localPath?.let(::File)?.takeIf { it.exists() }
        val server = streamServer
        if (localFile == null && server == null) return

        feedRefreshButton = null
        feedRefreshLabel = null
        feedRefreshLoader = null
        if (localFile == null) server?.prefetch(item)
        val orderedForPlayback = (currentDay?.videos ?: currentVideos)
            .sortedWith(compareBy<VideoItem> { it.date }.thenBy { it.messageId })
        val byId = orderedForPlayback.associateBy { it.messageId }
        val queueItems = experienceStore.queueIds()
            .mapNotNull(byId::get)
            .filterNot { it.messageId == item.messageId }
        val currentIndex = orderedForPlayback.indexOfFirst { it.messageId == item.messageId }
        val naturalNext = if (currentIndex >= 0) orderedForPlayback.getOrNull(currentIndex + 1) else null
        val nextItem = queueItems.firstOrNull() ?: naturalNext
        nextItem?.takeIf { it.localPath == null }?.let { candidate ->
            prefetchNextCandidate(server, candidate)
        }
        playerScreen?.destroy()
        currentStreamingItem?.let { previous -> streamServer?.release(previous) }
        currentStreamingItem = if (localFile == null) item else null
        isSettingsScreen = false
        isAccountScreen = false
        isStreakScreen = false
        isPlayerScreen = true

        val requestedStartMs = startSeconds * 1000L
        val savedStartMs = settings.playbackPosition(item.messageId)
        val resumePositionMs = if (requestedStartMs > 0L) requestedStartMs else savedStartMs

        val createdPlayer = runCatching {
            PlayerScreen(
                activity = this,
                item = item,
                mediaUrl = localFile?.let { Uri.fromFile(it).toString() } ?: server!!.url(item),
                previewDataSourceFactory = {
                    localFile?.let { LocalFileMediaDataSource(it) }
                        ?: server!!.mediaDataSource(item)
                },
                settings = settings,
                startPositionMs = resumePositionMs,
                nextItem = nextItem,
                queueItems = queueItems,
                onPlayNext = { next ->
                    if (experienceStore.queueIds().contains(next.messageId)) {
                        experienceStore.removeFromQueue(next.messageId)
                    }
                    openPlayer(next)
                },
                onDownloadRequested = { requested -> downloadVideoQuick(requested) },
                isWatched = isVideoWatched(item.messageId),
                onWatchedChange = { watched, shouldBeWatched ->
                    if (shouldBeWatched) {
                        markVideoWatched(watched.messageId)
                    } else {
                        unmarkVideoWatched(watched.messageId)
                    }
                    val belongsToOpenSection =
                        (videoSection == 1 && !shouldBeWatched) ||
                            (videoSection == 2 && shouldBeWatched)
                    currentDay = currentDay?.let { day ->
                        if (belongsToOpenSection) {
                            if (day.videos.any { it.messageId == watched.messageId }) {
                                day
                            } else {
                                day.copy(
                                    videos = (day.videos + watched).sortedWith(
                                        compareBy<VideoItem> { it.date }.thenBy { it.messageId }
                                    )
                                )
                            }
                        } else {
                            day.copy(
                                videos = day.videos.filterNot {
                                    it.messageId == watched.messageId
                                }
                            )
                        }
                    }
                },
                onBack = { closeCurrentPlayerScreen() },
                onFullscreen = { setFullscreen(it) },
                onPlaybackStarted = {
                    settings.markPlayed(item.messageId)
                    streakTracker.markWatched()
                }
            )
        }.getOrElse { error ->
            currentStreamingItem?.let { streamed -> streamServer?.release(streamed) }
            currentStreamingItem = null
            isPlayerScreen = false
            showMessage(
                "Не удалось открыть видео",
                error.message ?: "Плеер не смог запуститься. Попробуйте ещё раз."
            )
            return
        }

        playerScreen = createdPlayer
        pendingRootSlide = 1
        runCatching {
            replaceRoot(createdPlayer.root)
            createdPlayer.animateEntranceFrom(sourceBounds)
        }.onFailure { error ->
            createdPlayer.destroy()
            playerScreen = null
            currentStreamingItem?.let { streamed -> streamServer?.release(streamed) }
            currentStreamingItem = null
            isPlayerScreen = false
            showMessage(
                "Не удалось открыть видео",
                error.message ?: "Не удалось показать экран плеера."
            )
        }
    }



    private fun openTwitchPlayer(item: VideoItem, startSeconds: Int = 0) {
        fullScreen = false
        val videoId = item.externalUrl
            ?.let { runCatching { Uri.parse(it).lastPathSegment }.getOrNull() }
            ?.removePrefix("v")
            ?.filter { it.isDigit() }
            .orEmpty()

        if (videoId.isBlank()) {
            showMessage("Не удалось открыть Twitch", "У записи нет корректного Twitch Video ID.")
            return
        }

        playerScreen?.destroy()
        playerScreen = null
        twitchPlayerScreen?.destroy()
        twitchPlayerScreen = null
        twitchLivePlayerScreen?.destroy()
        twitchLivePlayerScreen = null
        currentStreamingItem?.let { streamed -> streamServer?.release(streamed) }
        currentStreamingItem = null

        isSettingsScreen = false
        isAccountScreen = false
        isStreakScreen = false
        isPlayerScreen = true

        val requestedStartMs = startSeconds * 1000L
        val savedStartMs = settings.playbackPosition(item.messageId)
        val resumePositionMs = if (requestedStartMs > 0L) requestedStartMs else savedStartMs

        showLoading("Открываем запись Twitch…")
        lifecycleScope.launch {
            try {
                val hlsUrl = TwitchVodResolver.resolve(videoId)
                val orderedForPlayback = (currentDay?.videos ?: currentVideos)
                    .sortedWith(compareBy<VideoItem> { it.date }.thenBy { it.messageId })
                val currentIndex = orderedForPlayback.indexOfFirst { it.messageId == item.messageId }
                val nextItem = if (currentIndex >= 0) orderedForPlayback.getOrNull(currentIndex + 1) else null

                playerScreen = PlayerScreen(
                    activity = this@MainActivity,
                    item = item,
                    mediaUrl = hlsUrl,
                    previewDataSourceFactory = null,
                    settings = settings,
                    startPositionMs = resumePositionMs,
                    nextItem = nextItem,
                    onPlayNext = { next -> openPlayer(next) },
                    onDownloadRequested = { requested -> downloadTwitchVod(requested, hlsUrl) },
                    isWatched = isVideoWatched(item.messageId),
                    onWatchedChange = { watched, shouldBeWatched ->
                        if (shouldBeWatched) markVideoWatched(watched.messageId) else unmarkVideoWatched(watched.messageId)
                    },
                    onBack = { closeCurrentPlayerScreen() },
                    onFullscreen = { setFullscreen(it) },
                    onPlaybackStarted = {
                        settings.markPlayed(item.messageId)
                        streakTracker.markWatched()
                    }
                )

                pendingRootSlide = 1
                replaceRoot(playerScreen!!.root)
            } catch (e: Exception) {
                isPlayerScreen = false
                if (isTwitchNetworkFailure(e)) markTwitchNetworkFailure()
                showMessage(
                    if (isTwitchNetworkFailure(e)) "Нет подключения к Twitch" else "Не удалось открыть запись Twitch",
                    friendlyTwitchFailure(e)
                )
            }
        }
    }

    private fun openTwitchLivePlayer(live: TwitchLiveStream) {
        val clientId = BuildConfig.TWITCH_CLIENT_ID.trim()
        val token = settings.twitchAccessToken
        if (clientId.isBlank() || token.isNullOrBlank()) {
            pendingTwitchLiveAfterAuth = live
            startTwitchLogin()
            return
        }

        showLoading("Открываем эфир…")
        lifecycleScope.launch {
            try {
                val tokenInfo = TwitchApi.validateTokenInfo(clientId, token)
                val login = tokenInfo.login.takeIf { it.isNotBlank() }
                    ?: settings.twitchLogin.orEmpty()

                if (!tokenInfo.scopes.containsAll(setOf("chat:read", "chat:edit"))) {
                    pendingTwitchLiveAfterAuth = live
                    settings.twitchLogin = login.takeIf { it.isNotBlank() }
                    isPlayerScreen = false
                    startTwitchLogin()
                    return@launch
                }

                settings.twitchLogin = login.takeIf { it.isNotBlank() }

                val channelProfile = runCatching {
                    TwitchApi.loadUserProfile(clientId, token, live.login)
                }.getOrNull()

                playerScreen?.destroy()
                playerScreen = null
                twitchPlayerScreen?.destroy()
                twitchPlayerScreen = null
                twitchLivePlayerScreen?.destroy()
                twitchLivePlayerScreen = null
                currentStreamingItem?.let { streamed -> streamServer?.release(streamed) }
                currentStreamingItem = null

                isSettingsScreen = false
                isAccountScreen = false
                isStreakScreen = false
                isPlayerScreen = true

                twitchLivePlayerScreen = TwitchLivePlayerScreen(
                    activity = this@MainActivity,
                    live = live,
                    profileImageUrl = channelProfile?.profileImageUrl.orEmpty(),
                    showPartnerBadge = channelProfile?.broadcasterType == "partner",
                    accessToken = token,
                    accountLogin = login,
                    palette = palette,
                    animationsEnabled = settings.animations,
                    onBack = { closeCurrentPlayerScreen() },
                    onFullscreen = { setFullscreen(it) },
                    onChatScopeMissing = {
                        pendingTwitchLiveAfterAuth = live
                        startTwitchLogin()
                    }
                )

                pendingRootSlide = 1
                replaceRoot(twitchLivePlayerScreen!!.root)
            } catch (_: TwitchAuthException) {
                pendingTwitchLiveAfterAuth = live
                settings.twitchAccessToken = null
                settings.twitchLogin = null
                isPlayerScreen = false
                startTwitchLogin()
            } catch (e: Exception) {
                isPlayerScreen = false
                if (isTwitchNetworkFailure(e)) markTwitchNetworkFailure()
                showMessage(
                    if (isTwitchNetworkFailure(e)) "Нет подключения к Twitch" else "Не удалось открыть эфир",
                    friendlyTwitchFailure(e)
                )
            }
        }
    }


    private fun switchToAnotherTemporaryLive(blocked: TwitchLiveStream) {
        if (!temporaryLivePreviewEnabled || !blocked.testStream) return
        blockTemporaryLive(blocked.login)

        val clientId = BuildConfig.TWITCH_CLIENT_ID.trim()
        val token = settings.twitchAccessToken
        if (clientId.isBlank() || token.isNullOrBlank()) {
            isPlayerScreen = false
            showMessage("Не удалось переключить эфир", "Twitch сейчас недоступен.")
            return
        }

        showLoading("На эфире реклама Twitch • ищем другой…")
        lifecycleScope.launch {
            try {
                var next: TwitchLiveStream? = null
                val excluded = linkedSetOf<String>().apply {
                    add("t2x2")
                    addAll(temporaryBlockedLiveLogins)
                }

                for (attempt in 0 until 7) {
                    val candidate = TwitchApi.loadRandomLiveStream(
                        clientId = clientId,
                        accessToken = token,
                        excludeLogins = excluded
                    ) ?: break

                    val commercial = runCatching {
                        TwitchVodResolver.isCommercialBreak(candidate.login)
                    }.getOrDefault(false)

                    if (commercial) {
                        blockTemporaryLive(candidate.login)
                        excluded.add(candidate.login.lowercase())
                        continue
                    }

                    next = candidate
                    break
                }

                if (next != null) {
                    lastT2x2Live = next
                    lastT2x2LiveUnavailable = false
                    lastT2x2LiveCheckedAt = System.currentTimeMillis()
                    openTwitchLivePlayer(next)
                } else {
                    isPlayerScreen = false
                    showMessage(
                        "Тестовые эфиры заняты рекламой",
                        "SOHR не нашёл подходящий эфир без рекламной паузы. Обновите экран чуть позже."
                    )
                }
            } catch (e: Exception) {
                isPlayerScreen = false
                if (isTwitchNetworkFailure(e)) markTwitchNetworkFailure()
                showMessage(
                    if (isTwitchNetworkFailure(e)) "Нет подключения к Twitch" else "Не удалось переключить эфир",
                    friendlyTwitchFailure(e)
                )
            }
        }
    }

    private fun showSelectedVideoSource(forceRefresh: Boolean = false) {
        currentDay = null
        videoSection = 1

        if (settings.guestMode && settings.videoSource == "telegram") {
            lifecycleScope.launch {
                val cached = withContext(Dispatchers.IO) {
                    videoCache.load()
                        .sortedWith(compareBy<VideoItem> { it.date }.thenBy { it.messageId })
                }
                if (settings.videoSource != "telegram") return@launch
                telegramVideos = cached
                currentVideos = cached
                showFeed(cached)
            }
            return
        }

        if (settings.videoSource == "twitch") {
            if (!forceRefresh && twitchVideos.isNotEmpty()) {
                currentVideos = twitchVideos
                showFeed(twitchVideos)
            } else {
                loadTwitchVideos(inPlace = false)
            }
            return
        }

        if (!forceRefresh && telegramVideos.isNotEmpty()) {
            currentVideos = telegramVideos
            showFeed(telegramVideos)
            scheduleFeedAutoRefresh(delayMs = 700L, force = false)
            return
        }

        if (!forceRefresh) {
            lifecycleScope.launch {
                val cached = withContext(Dispatchers.IO) {
                    videoCache.load()
                        .sortedWith(compareBy<VideoItem> { it.date }.thenBy { it.messageId })
                }

                if (settings.videoSource != "telegram") return@launch

                if (cached.isNotEmpty()) {
                    telegramVideos = cached
                    currentVideos = cached
                    suppressNextRootAnimation = true
                    showFeed(cached)
                    // Warm-start from local cache first, then refresh quietly in place.
                    root.post {
                        if (settings.videoSource == "telegram") {
                            loadVideos(inPlace = true, quiet = true)
                        }
                    }
                } else {
                    loadVideos(inPlace = false)
                }
                scheduleFeedAutoRefresh(delayMs = 900L, force = false)
            }
        } else {
            loadVideos(inPlace = false)
            scheduleFeedAutoRefresh(delayMs = 700L, force = true)
        }
    }

    private fun hasValidatedInternet(): Boolean {
        return runCatching {
            val manager = getSystemService(ConnectivityManager::class.java) ?: return@runCatching true
            val network = manager.activeNetwork ?: return@runCatching false
            val capabilities = manager.getNetworkCapabilities(network) ?: return@runCatching false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrDefault(true)
    }

    private fun isTwitchNetworkFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        repeat(8) {
            val value = current ?: return@repeat
            if (
                value is UnknownHostException ||
                value is SocketTimeoutException ||
                value is ConnectException ||
                value is SocketException
            ) return true

            val message = value.message.orEmpty().lowercase(Locale.getDefault())
            if (
                "unable to resolve host" in message ||
                "no address associated with hostname" in message ||
                "failed to connect" in message ||
                "timeout" in message ||
                "timed out" in message
            ) return true
            current = value.cause
        }
        return false
    }

    private fun markTwitchNetworkFailure() {
        twitchNetworkCooldownUntilElapsed =
            android.os.SystemClock.elapsedRealtime() + 30_000L
    }

    private fun canAttemptTwitchNetwork(): Boolean {
        if (android.os.SystemClock.elapsedRealtime() < twitchNetworkCooldownUntilElapsed) return false
        return hasValidatedInternet()
    }

    private fun friendlyTwitchFailure(error: Throwable?): String {
        if (error != null && isTwitchNetworkFailure(error)) {
            return "Нет подключения к Twitch. Проверьте интернет и попробуйте ещё раз."
        }
        val raw = error?.message.orEmpty().trim()
        val lower = raw.lowercase(Locale.getDefault())
        if (
            raw.isBlank() ||
            "unable to resolve host" in lower ||
            "no address associated with hostname" in lower ||
            "java.net." in lower
        ) {
            return "Twitch временно недоступен. Попробуйте ещё раз позже."
        }
        return raw.take(140)
    }

    private fun loadTwitchVideos(inPlace: Boolean = false) {
        val clientId = BuildConfig.TWITCH_CLIENT_ID.trim()
        if (clientId.isBlank()) {
            showMessage("Twitch ещё не подключён", "В сборке отсутствует Twitch Client ID.")
            return
        }
        val token = settings.twitchAccessToken
        if (token.isNullOrBlank()) {
            if (!inPlace) showLoading("Открываем вход Twitch…")
            startTwitchLogin()
            return
        }
        if (twitchLoadJob?.isActive == true) {
            if (inPlace) feedRefreshLabel?.text = "Уже проверяем…"
            return
        }
        if (!canAttemptTwitchNetwork()) {
            if (inPlace) {
                setFeedRefreshLoading(false, "Нет сети")
                feedRefreshButton?.postDelayed({
                    setFeedRefreshLoading(false, "Проверить новые")
                }, 1_500L)
            } else {
                showMessage(
                    "Нет подключения к Twitch",
                    "Проверьте интернет и попробуйте ещё раз."
                )
            }
            return
        }
        if (inPlace) setFeedRefreshLoading(true) else showFeedSkeleton("Загружаем Twitch…")
        twitchLoadJob = lifecycleScope.launch {
            try {
                val twitchLogin = TwitchApi.validateToken(clientId, token)
                settings.twitchLogin = twitchLogin.takeIf { it.isNotBlank() }
                val videos = TwitchApi.loadArchives(clientId, token, "t2x2", 7)
                twitchVideos = videos
                currentVideos = videos
                currentDay = null
                feedRefreshCompletedFlash = inPlace
                if (inPlace) suppressNextRootAnimation = true
                showFeed(videos)
                if (pendingTwitchWelcome) {
                    pendingTwitchWelcome = false
                    root.postDelayed({
                        val account = settings.twitchLogin?.let { " @$it" }.orEmpty()
                        ModernDialogs.showNotice(
                            context = this@MainActivity,
                            palette = palette,
                            title = "Twitch подключён",
                            message = "Вход выполнен$account. Теперь записи Twitch доступны прямо в SOHR.",
                            button = "Готово"
                        )
                    }, 260L)
                }
            } catch (_: TwitchAuthException) {
                val refreshToken = settings.twitchRefreshToken
                if (!refreshToken.isNullOrBlank()) {
                    try {
                        val refreshed = TwitchApi.refreshAccessToken(clientId, refreshToken)
                        settings.twitchAccessToken = refreshed.accessToken
                        settings.twitchRefreshToken = refreshed.refreshToken ?: refreshToken
                        settings.twitchLogin = null
                        twitchLoadJob = null
                        loadTwitchVideos(inPlace = inPlace)
                        return@launch
                    } catch (_: Exception) {
                        // Fall through to a fresh Device Code login.
                    }
                }
                settings.twitchAccessToken = null
                settings.twitchRefreshToken = null
                settings.twitchOauthState = null
                settings.twitchLogin = null
                startTwitchLogin()
            } catch (e: Exception) {
                val networkFailure = isTwitchNetworkFailure(e)
                if (networkFailure) markTwitchNetworkFailure()
                if (inPlace) {
                    setFeedRefreshLoading(false, if (networkFailure) "Нет сети" else "Ошибка")
                    feedRefreshButton?.postDelayed({
                        setFeedRefreshLoading(false, "Проверить новые")
                    }, 1_500L)
                } else {
                    showMessage(
                        if (networkFailure) "Нет подключения к Twitch" else "Не удалось загрузить Twitch",
                        friendlyTwitchFailure(e)
                    )
                }
            }
        }
    }

    private fun startTwitchLogin() {
        val clientId = BuildConfig.TWITCH_CLIENT_ID.trim()
        if (clientId.isBlank()) {
            showMessage("Twitch ещё не подключён", "В сборке отсутствует Twitch Client ID.")
            return
        }
        if (!canAttemptTwitchNetwork()) {
            showMessage(
                "Нет подключения к Twitch",
                "Проверьте интернет и попробуйте ещё раз."
            )
            return
        }

        val state = java.util.UUID.randomUUID().toString().replace("-", "")
        settings.twitchOauthState = state

        val auth = Uri.parse("https://id.twitch.tv/oauth2/authorize").buildUpon()
            .appendQueryParameter("response_type", "token")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", TWITCH_REDIRECT_URI)
            .appendQueryParameter("scope", "chat:read chat:edit")
            .appendQueryParameter("state", state)
            .appendQueryParameter("force_verify", "false")
            .build()

        showTwitchOAuthDialog(auth)
    }

    private fun showTwitchOAuthDialog(authUri: Uri) {
        twitchAuthDialog?.dismiss()

        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            TextView(this).apply {
                text = "Вход в Twitch"
                textSize = 19f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(this@MainActivity.text)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val close = ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(muted)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = roundedBg(palette.surfaceAlt, 18)
            setOnClickListener {
                settings.twitchOauthState = null
                dialog.dismiss()
                showAccount()
            }
        }
        header.addView(close, LinearLayout.LayoutParams(dp(42), dp(42)))
        page.addView(header)

        page.addView(TextView(this).apply {
            text = "Войдите в Twitch и подтвердите доступ. После подтверждения SOHR сам завершит вход."
            textSize = 12.5f
            setTextColor(muted)
            setPadding(0, dp(7), 0, dp(10))
        })

        val webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0E0E10"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                private fun intercept(url: String?): Boolean {
                    if (url.isNullOrBlank()) return false
                    if (!url.startsWith(TWITCH_REDIRECT_URI, ignoreCase = true)) return false

                    handleTwitchOAuthCallback(Uri.parse(url))
                    return true
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                    intercept(request?.url?.toString())

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                    intercept(url)

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame != true) return
                    markTwitchNetworkFailure()
                    val html = """
                        <!doctype html>
                        <html>
                        <body style="margin:0;background:#0E0E10;color:#F7F7FA;font-family:sans-serif;
                                     display:flex;align-items:center;justify-content:center;height:100vh;text-align:center;">
                          <div style="padding:28px;max-width:360px;">
                            <div style="font-size:22px;font-weight:700;margin-bottom:10px;">Нет подключения к Twitch</div>
                            <div style="font-size:14px;line-height:1.45;color:#A7A5B3;">
                              Проверьте интернет и попробуйте открыть вход ещё раз.
                            </div>
                          </div>
                        </body>
                        </html>
                    """.trimIndent()
                    view?.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                }
            }

            loadUrl(authUri.toString())
        }

        page.addView(
            webView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { topMargin = dp(4) }
        )

        dialog.setContentView(page)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnCancelListener {
            settings.twitchOauthState = null
            twitchAuthDialog = null
        }
        dialog.setOnDismissListener {
            if (twitchAuthDialog === dialog) twitchAuthDialog = null
            runCatching {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }

        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(bg))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        twitchAuthDialog = dialog
    }

    private fun handleTwitchOAuthCallback(data: Uri) {
        fun decode(value: String): String =
            runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

        val params = linkedMapOf<String, String>()
        data.fragment.orEmpty()
            .split("&")
            .filter { it.contains("=") }
            .forEach { part ->
                params[decode(part.substringBefore("="))] = decode(part.substringAfter("="))
            }
        data.queryParameterNames.forEach { key ->
            data.getQueryParameter(key)?.let { params[key] = it }
        }

        val error = params["error"]
        if (!error.isNullOrBlank()) {
            twitchAuthDialog?.dismiss()
            twitchAuthDialog = null
            settings.twitchOauthState = null
            showMessage(
                "Вход Twitch отменён",
                params["error_description"]?.takeIf { it.isNotBlank() } ?: error
            )
            return
        }

        val expectedState = settings.twitchOauthState
        val returnedState = params["state"]
        if (expectedState.isNullOrBlank() || returnedState.isNullOrBlank() || expectedState != returnedState) {
            twitchAuthDialog?.dismiss()
            twitchAuthDialog = null
            settings.twitchOauthState = null
            showMessage(
                "Не удалось подтвердить вход Twitch",
                "Проверка безопасности входа не совпала. Попробуйте ещё раз."
            )
            return
        }

        val token = params["access_token"]
        if (token.isNullOrBlank()) {
            twitchAuthDialog?.dismiss()
            twitchAuthDialog = null
            settings.twitchOauthState = null
            showMessage(
                "Twitch не вернул токен",
                "Авторизация завершилась без access token."
            )
            return
        }

        twitchAuthDialog?.dismiss()
        twitchAuthDialog = null

        lifecycleScope.launch {
            try {
                showLoading("Проверяем Twitch…")
                val clientId = BuildConfig.TWITCH_CLIENT_ID.trim()
                val login = TwitchApi.validateToken(clientId, token)

                settings.twitchAccessToken = token
                settings.twitchRefreshToken = null
                settings.twitchOauthState = null
                settings.twitchLogin = login.takeIf { it.isNotBlank() }
                settings.videoSource = "twitch"
                suppressNextRootAnimation = true

                val pendingLive = pendingTwitchLiveAfterAuth
                pendingTwitchLiveAfterAuth = null
                if (pendingLive != null) {
                    pendingTwitchWelcome = false
                    openTwitchLivePlayer(pendingLive)
                } else {
                    pendingTwitchWelcome = true
                    loadTwitchVideos(inPlace = false)
                }
            } catch (e: Exception) {
                settings.twitchAccessToken = null
                settings.twitchRefreshToken = null
                settings.twitchOauthState = null
                settings.twitchLogin = null
                pendingTwitchLiveAfterAuth = null
                if (isTwitchNetworkFailure(e)) markTwitchNetworkFailure()
                showMessage(
                    if (isTwitchNetworkFailure(e)) "Нет подключения к Twitch" else "Не удалось подключить Twitch",
                    friendlyTwitchFailure(e)
                )
            }
        }
    }

    private fun handleTwitchAuthIntent(sourceIntent: Intent?, loadAfter: Boolean): Boolean {
        val data = sourceIntent?.data ?: return false
        if (data.scheme != "sohr" || data.host != "twitch-auth") return false
        sourceIntent.data = null
        handleTwitchOAuthCallback(data)
        return true
    }

    private fun showSettings() {
        val wasSettingsVisible = isSettingsScreen
        val visibleSettingsScroll =
            root.findViewWithTag<ScrollView>("sohr_settings_scroll")
                ?: primaryShell?.findViewWithTag("sohr_settings_scroll")
        if (visibleSettingsScroll != null) {
            settingsScrollY = visibleSettingsScroll.scrollY
        }
        val restoreScrollY = if (wasSettingsVisible) settingsScrollY else 0

        isSettingsScreen = true
        isAccountScreen = false
        isStreakScreen = false
        isPlayerScreen = false
        applySystemTheme()
        val screen = SettingsScreen(
            this,
            settings,
            onBack = { needsReload ->
                isSettingsScreen = false
                showSelectedVideoSource(forceRefresh = needsReload)
            },
            onAppearanceChanged = { light, accent, source ->
                animateSettingsPaletteReveal(light, accent, source)
            },
            onLanguageChanged = {
                showSettings()
            },
            onSmartDownloadsChanged = { enabled ->
                if (enabled) scheduleSmartDownloads(telegramVideos) else smartDownloadJob?.cancel()
            },
            onCheckUpdates = { checkForUpdates() }
        )
        val content = screen.build()
        val settingsScroll = content.findViewWithTag<ScrollView>("sohr_settings_scroll")
        settingsScroll?.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            settingsScrollY = scrollY
        }

        replaceRoot(withBottomNav(content, SohrTab.SETTINGS))

        if (wasSettingsVisible && restoreScrollY > 0) {
            restoreSettingsScrollPosition(settingsScroll, restoreScrollY)
        } else if (!wasSettingsVisible) {
            settingsScrollY = 0
        }
    }

    private fun showStreak() {
        isStreakScreen = true
        isSettingsScreen = false
        isAccountScreen = false
        isPlayerScreen = false
        currentDay = null
        setFullscreen(false)
        applySystemTheme()

        val streak = streakTracker.currentStreak()
        val totalDays = streakTracker.totalWatchedDays()
        val watchedToday = streakTracker.watchedToday()
        val flameColor = streakColor(streak)
        val next = nextStreakMilestone(streak)

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            setBackgroundColor(bg)
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
            setBackgroundColor(bg)
        }

        val title = TextView(this).apply {
            text = "Стрик"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
        }

        val subtitle = TextView(this).apply {
            text = "Дни подряд со стримером"
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(4), 0, dp(18))
        }

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = roundedBg(panel, 26)
        }

        val shouldIgniteToday = watchedToday && streakTracker.consumeIgnition()
        val fire = StreakFireView(this, flameColor).apply {
            setLit(watchedToday, animate = false)
        }

        val levelBadge = TextView(this).apply {
            text = streakLevelName(streak)
            textSize = 12f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(flameColor)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = roundedBg(palette.surfaceAlt, 14)
        }

        val count = TextView(this).apply {
            text = streak.toString()
            textSize = 44f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(flameColor)
            setPadding(0, dp(2), 0, 0)
        }

        val daysLabel = TextView(this).apply {
            text = when {
                streak == 1 -> "день подряд"
                streak in 2..4 -> "дня подряд"
                else -> "дней подряд"
            }
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
        }

        val status = TextView(this).apply {
            text = if (watchedToday) "Сегодня засчитано" else "Сегодня ещё не засчитано"
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (watchedToday) flameColor else muted)
            setPadding(dp(8), dp(10), dp(8), 0)
        }

        hero.addView(
            fire,
            LinearLayout.LayoutParams(dp(178), dp(178)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )
        hero.addView(levelBadge, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(4)
        })
        hero.addView(count)
        hero.addView(daysLabel)
        hero.addView(status)

        val progressCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = roundedBg(panel, 20)
        }

        val progressTitle = TextView(this).apply {
            text = if (next == null) {
                "Максимальный уровень огня"
            } else {
                "До следующего огня • " + (next - streak) + " дн."
            }
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
        }

        val progressSubtitle = TextView(this).apply {
            text = "Всего дней просмотра: " + totalDays
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(4), 0, dp(10))
        }

        val track = FrameLayout(this).apply {
            background = roundedBg(palette.surfaceAlt, 6)
            clipToOutline = true
        }

        val fill = View(this).apply {
            background = roundedBg(flameColor, 6)
        }

        track.addView(
            fill,
            FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val progress = streakProgress(streak)
        track.post {
            val params = fill.layoutParams as FrameLayout.LayoutParams
            params.width = (track.width * progress).toInt().coerceAtLeast(if (streak > 0) dp(8) else 0)
            fill.layoutParams = params
            if (settings.animations) {
                fill.scaleX = 0f
                fill.pivotX = 0f
                fill.animate()
                    .scaleX(1f)
                    .setDuration(520L)
                    .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                    .start()
            }
        }

        progressCard.addView(progressTitle)
        progressCard.addView(progressSubtitle)
        progressCard.addView(
            track,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(9)
            )
        )

        val protectionTokens = streakTracker.protectionTokens()
        val protectedDay = streakTracker.lastProtectedDay()
        val protectionCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBg(panel, 19)
        }

        protectionCard.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_streak_shield)
            imageTintList = ColorStateList.valueOf(
                if (protectionTokens > 0) purple else muted
            )
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = roundedBg(
                if (protectionTokens > 0) palette.accentSoft else palette.surfaceAlt,
                14
            )
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
            marginEnd = dp(11)
        })

        val protectionCopy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        protectionCopy.addView(TextView(this).apply {
            text = "Защита серии • " + protectionTokens + "/2"
            textSize = 14f
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
        })
        protectionCopy.addView(TextView(this).apply {
            text = when {
                protectedDay != null ->
                    "Щит сохранил серию " +
                        protectedDay.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))
                protectionTokens > 0 ->
                    "Один пропущенный день закроется автоматически"
                else ->
                    "Новый щит за каждые 7 дней просмотра"
            }
            textSize = 11.2f
            includeFontPadding = false
            setTextColor(muted)
            setPadding(0, dp(3), 0, 0)
        })
        protectionCard.addView(
            protectionCopy,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val weekTitle = TextView(this).apply {
            text = "Последние 7 дней"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(dp(2), dp(18), 0, dp(10))
        }

        val weekCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = roundedBg(panel, 20)
        }

        val dayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        streakTracker.recentDays(7).forEach { (date, done) ->
            val isToday = date == java.time.LocalDate.now()
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(5), dp(3), dp(5))
                background = if (isToday) roundedBg(palette.surfaceAlt, 14) else null
            }
            val mini = StreakFireView(this, flameColor, animated = false).apply {
                setLit(done, animate = false)
            }
            cell.addView(
                mini,
                LinearLayout.LayoutParams(dp(30), dp(30)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            )
            cell.addView(TextView(this).apply {
                text = dayNames[(date.dayOfWeek.value - 1).coerceIn(0, 6)]
                textSize = 10.5f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTypeface(typeface, if (isToday) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (done) this@MainActivity.text else muted)
                setPadding(0, dp(4), 0, 0)
            })
            weekCard.addView(
                cell,
                LinearLayout.LayoutParams(0, dp(58), 1f)
            )
        }

        val levelsTitle = TextView(this).apply {
            text = "Уровни огня"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(dp(2), dp(18), 0, dp(10))
        }

        val levels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = roundedBg(panel, 20)
        }

        data class FireLevel(val from: Int, val to: Int?, val range: String, val color: Int, val name: String)
        val levelData = listOf(
            FireLevel(1, 9, "1–9 дней", Color.parseColor("#FFC83D"), "Жёлтая искра"),
            FireLevel(10, 29, "10–29 дней", Color.parseColor("#A970FF"), "Фиолетовое пламя"),
            FireLevel(30, 59, "30–59 дней", Color.parseColor("#4D98FF"), "Синее пламя"),
            FireLevel(60, 99, "60–99 дней", Color.parseColor("#FF5367"), "Алое пламя"),
            FireLevel(100, 199, "100–199 дней", Color.parseColor("#B7E84A"), "Лаймовое пламя"),
            FireLevel(200, 499, "200–499 дней", Color.parseColor("#46D7C4"), "Бирюзовое пламя"),
            FireLevel(500, 999, "500–999 дней", Color.parseColor("#FF73B9"), "Розовое пламя"),
            FireLevel(1000, 2499, "1000–2499 дней", Color.parseColor("#9ED8FF"), "Ледяное пламя"),
            FireLevel(2500, 4999, "2500–4999 дней", Color.parseColor("#D58CFF"), "Аметистовое пламя"),
            FireLevel(5000, null, "5000+ дней", Color.WHITE, "Белое пламя")
        )

        levelData.forEach { entry ->
            val active = streak >= entry.from && (entry.to == null || streak <= entry.to)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = if (active) roundedBg(palette.surfaceAlt, 16) else null
            }
            val miniFire = StreakFireView(this, entry.color, animated = false).apply {
                setLit(active, animate = false)
            }
            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, 0, 0)
            }
            labels.addView(TextView(this).apply {
                text = entry.range
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(this@MainActivity.text)
            })
            labels.addView(TextView(this).apply {
                text = if (active) entry.name + " • сейчас" else entry.name
                textSize = 11.5f
                setTextColor(if (active) entry.color else muted)
                setPadding(0, dp(2), 0, 0)
            })
            row.addView(miniFire, LinearLayout.LayoutParams(dp(34), dp(34)))
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            levels.addView(row)
        }

        page.addView(title)
        page.addView(subtitle)
        page.addView(hero)
        page.addView(
            progressCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        )
        page.addView(
            protectionCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        )
        page.addView(weekTitle)
        page.addView(weekCard)
        page.addView(levelsTitle)
        page.addView(levels)

        scroll.addView(
            page,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        if (settings.animations) {
            hero.alpha = 0f
            hero.translationY = dp(14).toFloat()
            progressCard.alpha = 0f
            progressCard.translationY = dp(12).toFloat()
            levels.alpha = 0f
            levels.translationY = dp(10).toFloat()
        }

        replaceRoot(withBottomNav(scroll, SohrTab.STREAK))

        if (settings.animations) {
            hero.post {
                if (shouldIgniteToday) {
                    fire.alpha = 0.62f
                    fire.scaleX = 0.86f
                    fire.scaleY = 0.86f
                    fire.postDelayed({
                        fire.playIgnition()
                        fire.animate().cancel()
                        fire.animate()
                            .alpha(1f)
                            .scaleX(1.04f)
                            .scaleY(1.04f)
                            .setDuration(360L)
                            .setInterpolator(
                                android.view.animation.PathInterpolator(0.12f, 0.9f, 0.22f, 1f)
                            )
                            .withEndAction {
                                fire.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(180L)
                                    .start()
                            }
                            .start()
                    }, 120L)
                }
                val ease = android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
                hero.animate().alpha(1f).translationY(0f).setDuration(300L).setInterpolator(ease).start()
                progressCard.animate().alpha(1f).translationY(0f).setStartDelay(70L).setDuration(300L).setInterpolator(ease).start()
                levels.animate().alpha(1f).translationY(0f).setStartDelay(140L).setDuration(320L).setInterpolator(ease).start()
            }
        }
    }


    private fun streakLevelName(streak: Int): String = when {
        streak <= 0 -> "Огонь ещё не зажжён"
        streak < 10 -> "Жёлтая искра"
        streak < 30 -> "Фиолетовое пламя"
        streak < 60 -> "Синее пламя"
        streak < 100 -> "Алое пламя"
        streak < 200 -> "Лаймовое пламя"
        streak < 500 -> "Бирюзовое пламя"
        streak < 1000 -> "Розовое пламя"
        streak < 2500 -> "Ледяное пламя"
        streak < 5000 -> "Аметистовое пламя"
        else -> "Белое пламя"
    }

    private fun streakColor(streak: Int): Int = StreakFireView.colorForStreak(streak)

    private fun streakColorName(streak: Int): String = when {
        streak <= 0 -> "неактивный"
        streak < 10 -> "жёлтый"
        streak < 30 -> "фиолетовый"
        streak < 60 -> "синий"
        streak < 100 -> "алый"
        streak < 200 -> "лаймовый"
        streak < 500 -> "бирюзовый"
        streak < 1000 -> "розовый"
        streak < 2500 -> "ледяной"
        streak < 5000 -> "аметистовый"
        else -> "белый"
    }

    private fun streakRangeLabel(streak: Int): String = when {
        streak <= 0 -> "0 дней"
        streak < 10 -> "1–9 дней"
        streak < 30 -> "10–29 дней"
        streak < 60 -> "30–59 дней"
        streak < 100 -> "60–99 дней"
        streak < 200 -> "100–199 дней"
        streak < 500 -> "200–499 дней"
        streak < 1000 -> "500–999 дней"
        streak < 2500 -> "1000–2499 дней"
        streak < 5000 -> "2500–4999 дней"
        else -> "5000+ дней"
    }

    private fun nextStreakMilestone(streak: Int): Int? = when {
        streak < 10 -> 10
        streak < 30 -> 30
        streak < 60 -> 60
        streak < 100 -> 100
        streak < 200 -> 200
        streak < 500 -> 500
        streak < 1000 -> 1000
        streak < 2500 -> 2500
        streak < 5000 -> 5000
        else -> null
    }

    private fun streakProgress(streak: Int): Float {
        val start: Int
        val end: Int
        when {
            streak < 10 -> { start = 0; end = 10 }
            streak < 30 -> { start = 10; end = 30 }
            streak < 60 -> { start = 30; end = 60 }
            streak < 100 -> { start = 60; end = 100 }
            streak < 200 -> { start = 100; end = 200 }
            streak < 500 -> { start = 200; end = 500 }
            streak < 1000 -> { start = 500; end = 1000 }
            streak < 2500 -> { start = 1000; end = 2500 }
            streak < 5000 -> { start = 2500; end = 5000 }
            else -> return 1f
        }
        return ((streak - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
    }

    private fun animateThemeReveal(light: Boolean, source: View) {
        animateSettingsPaletteReveal(light, settings.themeAccent, source)
    }

    private fun animateAccentReveal(accent: String, source: View) {
        animateSettingsPaletteReveal(settings.lightTheme, AppThemes.preset(accent).key, source)
    }

    private fun restoreSettingsScrollPosition(
        scroll: ScrollView?,
        scrollY: Int,
        onReady: (() -> Unit)? = null
    ) {
        if (scroll == null) {
            onReady?.invoke()
            return
        }

        val requested = scrollY.coerceAtLeast(0)
        var attempts = 0

        val restore = object : Runnable {
            override fun run() {
                if (!scroll.isAttachedToWindow) {
                    onReady?.invoke()
                    return
                }

                val child = scroll.getChildAt(0)
                val maxScroll =
                    ((child?.measuredHeight ?: 0) - scroll.measuredHeight).coerceAtLeast(0)
                val layoutReady =
                    scroll.measuredHeight > 0 &&
                    child != null &&
                    child.measuredHeight > 0 &&
                    (requested == 0 || maxScroll >= requested || attempts >= 10)

                if (!layoutReady) {
                    attempts += 1
                    scroll.postOnAnimation(this)
                    return
                }

                val target = requested.coerceIn(0, maxScroll)
                scroll.scrollTo(0, target)
                settingsScrollY = target

                scroll.postOnAnimation {
                    if (scroll.isAttachedToWindow) {
                        val latestMax =
                            ((scroll.getChildAt(0)?.measuredHeight ?: 0) - scroll.measuredHeight)
                                .coerceAtLeast(0)
                        val latestTarget = requested.coerceIn(0, latestMax)
                        scroll.scrollTo(0, latestTarget)
                        settingsScrollY = latestTarget
                    }
                    onReady?.invoke()
                }
            }
        }

        scroll.post(restore)
    }

    private fun animateSettingsPaletteReveal(
        light: Boolean,
        accent: String,
        source: View
    ) {
        val normalizedAccent = AppThemes.preset(accent).key
        if (settings.lightTheme == light && settings.themeAccent == normalizedAccent) return

        val oldSettingsScrollY =
            root.findViewWithTag<ScrollView>("sohr_settings_scroll")?.scrollY
                ?: primaryShell?.findViewWithTag<ScrollView>("sohr_settings_scroll")?.scrollY
                ?: settingsScrollY
        settingsScrollY = oldSettingsScrollY

        if (!settings.animations || root.width <= 0 || root.height <= 0) {
            settings.lightTheme = light
            settings.themeAccent = normalizedAccent
            primaryShell = null
            primaryContentHost = null
            primaryNav = null
            primaryShellLightTheme = null
            primaryShellAccent = null
            applySystemTheme()
            showSettings()
            root.post {
                restoreSettingsScrollPosition(
                    root.findViewWithTag("sohr_settings_scroll"),
                    oldSettingsScrollY
                )
            }
            return
        }

        val oldShell = primaryShell
        val oldSystemColor = bg

        settings.lightTheme = light
        settings.themeAccent = normalizedAccent

        val newSystemColor = bg
        animateSystemChrome(oldSystemColor, newSystemColor, light, 320L)

        val nextContent = SettingsScreen(
            this,
            settings,
            onBack = { needsReload ->
                isSettingsScreen = false
                showSelectedVideoSource(forceRefresh = needsReload)
            },
            onAppearanceChanged = { nextLight, nextAccent, nextSource ->
                animateSettingsPaletteReveal(nextLight, nextAccent, nextSource)
            },
            onLanguageChanged = { showSettings() },
            onSmartDownloadsChanged = { enabled ->
                if (enabled) scheduleSmartDownloads(telegramVideos) else smartDownloadJob?.cancel()
            },
            onCheckUpdates = { checkForUpdates() }
        ).build()

        val nextSettingsScroll =
            nextContent.findViewWithTag<ScrollView>("sohr_settings_scroll")
        nextSettingsScroll?.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            settingsScrollY = scrollY
        }

        val nextHost = FrameLayout(this).apply {
            clipChildren = true
            clipToPadding = true
            addView(
                nextContent,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        lateinit var nextNav: SohrBottomNavView
        nextNav = SohrBottomNavView(this, palette, SohrTab.SETTINGS) { tab ->
            if (tab != currentPrimaryTab) {
                pendingRootSlide = if (tab.ordinal > currentPrimaryTab.ordinal) 1 else -1
                when (tab) {
                    SohrTab.VIDEOS -> showSelectedVideoSource()
                    SohrTab.SETTINGS -> showSettings()
                    SohrTab.STREAK -> showStreak()
                    SohrTab.ACCOUNT -> showAccount()
                }
            }
        }

        val nextShell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            addView(
                nextHost,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            addView(
                nextNav,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(74)
                ).apply {
                    marginStart = dp(14)
                    marginEnd = dp(14)
                    topMargin = dp(6)
                    bottomMargin = dp(8)
                }
            )
        }

        nextShell.visibility = View.INVISIBLE
        nextShell.alpha = 0.92f
        nextShell.scaleX = 0.992f
        nextShell.scaleY = 0.992f
        root.addView(
            nextShell,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val sourceLocation = IntArray(2)
        val rootLocation = IntArray(2)
        source.getLocationOnScreen(sourceLocation)
        root.getLocationOnScreen(rootLocation)

        val cx = sourceLocation[0] - rootLocation[0] + source.width / 2
        val cy = sourceLocation[1] - rootLocation[1] + source.height / 2

        nextShell.post {
            restoreSettingsScrollPosition(nextSettingsScroll, oldSettingsScrollY) {
                if (!nextShell.isAttachedToWindow) return@restoreSettingsScrollPosition

                nextShell.visibility = View.VISIBLE
                nextShell.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(280L)
                    .setInterpolator(
                        android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
                    )
                    .start()

                oldShell?.animate()
                    ?.alpha(0.82f)
                    ?.setDuration(220L)
                    ?.start()

                val maxX = maxOf(cx, nextShell.width - cx).toDouble()
                val maxY = maxOf(cy, nextShell.height - cy).toDouble()
                val finalRadius = kotlin.math.hypot(maxX, maxY).toFloat()

                ViewAnimationUtils.createCircularReveal(
                    nextShell,
                    cx.coerceIn(0, nextShell.width),
                    cy.coerceIn(0, nextShell.height),
                    0f,
                    finalRadius
                ).apply {
                    duration = 340L
                    interpolator =
                        android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            restoreSettingsScrollPosition(
                                nextSettingsScroll,
                                oldSettingsScrollY
                            )

                            if (oldShell != null && oldShell.parent === root) {
                                root.removeView(oldShell)
                            }

                            primaryShell = nextShell
                            primaryContentHost = nextHost
                            primaryNav = nextNav
                            primaryShellLightTheme = light
                            primaryShellAccent = normalizedAccent
                            currentPrimaryTab = SohrTab.SETTINGS
                            nextNav.syncSelected(SohrTab.SETTINGS, animate = false)

                            root.setBackgroundColor(bg)
                            applySystemTheme()
                        }
                    })
                    start()
                }
            }
        }
    }

    private fun showAccount() {
        if (settings.guestMode) {
            renderGuestAccount()
            return
        }
        isAccountScreen = true
        isSettingsScreen = false
        isStreakScreen = false
        isPlayerScreen = false
        currentDay = null
        setFullscreen(false)
        applySystemTheme()

        val loadingPage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bg)
            val spinner = buildBrandLoadingMark(52)
            val label = TextView(this@MainActivity).apply {
                text = "Загружаем аккаунты…"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(muted)
                setPadding(0, dp(14), 0, 0)
            }
            addView(spinner, LinearLayout.LayoutParams(dp(52), dp(52)))
            addView(label)
        }
        replaceRoot(withBottomNav(loadingPage, SohrTab.ACCOUNT))

        lifecycleScope.launch {
            try {
                val user = client.send(TdApi.GetMe())
                val token = settings.twitchAccessToken

                val telegramAvatar = async(Dispatchers.IO) {
                    val photoId = user.profilePhoto?.small?.id ?: return@async null
                    runCatching {
                        val file = client.send(TdApi.DownloadFile(photoId, 2, 0, 0, true))
                        file.local.path.takeIf { it.isNotBlank() && file.local.isDownloadingCompleted }
                    }.getOrNull()
                }

                val twitchProfile = if (!token.isNullOrBlank()) {
                    try {
                        TwitchApi.loadCurrentUser(BuildConfig.TWITCH_CLIENT_ID.trim(), token).also {
                            settings.twitchLogin = it.login.takeIf(String::isNotBlank)
                        }
                    } catch (_: TwitchAuthException) {
                        settings.twitchAccessToken = null
                        settings.twitchOauthState = null
                        settings.twitchLogin = null
                        null
                    } catch (_: Exception) {
                        null
                    }
                } else null

                renderAccount(user, telegramAvatar.await(), twitchProfile)
            } catch (e: Exception) {
                showMessage("Не удалось открыть аккаунт", e.message ?: "Ошибка Telegram")
            }
        }
    }

    private fun renderGuestAccount() {
        isAccountScreen = true
        isSettingsScreen = false
        isStreakScreen = false
        isPlayerScreen = false
        currentDay = null
        setFullscreen(false)
        applySystemTheme()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(34), dp(18), dp(24))
            setBackgroundColor(bg)
        }

        val avatar = ImageView(this).apply {
            setImageResource(R.drawable.sohr_brand_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = roundedBg(palette.surfaceAlt, 40)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        page.addView(avatar, LinearLayout.LayoutParams(dp(96), dp(96)))

        page.addView(TextView(this).apply {
            text = "Гость"
            textSize = 24f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(0, dp(14), 0, dp(5))
        })

        page.addView(TextView(this).apply {
            text = "Локальные записи, Streak и настройки доступны. Для новых Telegram-видео нужен вход."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(dp(16), 0, dp(16), dp(22))
        })

        val login = TextView(this).apply {
            text = "Войти в Telegram"
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = roundedBg(purple, 16)
            setOnClickListener {
                animatePress(this)
                settings.guestMode = false
                recreate()
            }
        }
        page.addView(login, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
        replaceRoot(withBottomNav(page, SohrTab.ACCOUNT))
    }

    private fun renderAccount(user: TdApi.User, avatarPath: String?, twitchProfile: TwitchProfile?) {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16), dp(18), dp(16), dp(24))
        }

        val title = TextView(this).apply {
            text = "Аккаунт"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
        }
        val subtitle = TextView(this).apply {
            text = "Telegram и Twitch"
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(4), 0, dp(18))
        }
        page.addView(title)
        page.addView(subtitle)

        val telegramCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = roundedBg(panel, 24)
        }

        val telegramAvatarFrame = FrameLayout(this).apply {
            background = roundedBg(palette.accentSoft, 46)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        val telegramAvatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedBg(panel, 42)
        }
        if (!avatarPath.isNullOrBlank() && File(avatarPath).exists()) {
            telegramAvatar.load(File(avatarPath)) {
                crossfade(settings.animations)
                transformations(CircleCropTransformation())
            }
        } else {
            telegramAvatar.load(R.drawable.sohr_brand_logo) {
                transformations(CircleCropTransformation())
            }
        }
        telegramAvatarFrame.addView(
            telegramAvatar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        telegramCard.addView(telegramAvatarFrame, LinearLayout.LayoutParams(dp(92), dp(92)))

        val fullName = listOf(user.firstName, user.lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Telegram" }

        telegramCard.addView(TextView(this).apply {
            text = fullName
            textSize = 21f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(0, dp(12), 0, dp(3))
        })
        telegramCard.addView(TextView(this).apply {
            text = "SOHR • Telegram"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(muted)
        })

        val phoneRaw = user.phoneNumber.orEmpty().let { if (it.startsWith("+")) it else "+$it" }
        var revealed = false
        val phoneRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(10), dp(12))
            background = roundedBg(palette.surfaceAlt, 16)
        }
        val phoneLabel = TextView(this).apply {
            text = "Номер телефона"
            textSize = 12f
            setTextColor(muted)
        }
        val phoneValue = TextView(this).apply {
            text = maskPhone(phoneRaw)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(0, dp(3), 0, 0)
        }
        val phoneTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(phoneLabel)
            addView(phoneValue)
        }
        val eye = ImageButton(this).apply {
            setImageResource(R.drawable.ic_visibility_off)
            imageTintList = ColorStateList.valueOf(purple)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundedBg(palette.surface, 18)
            contentDescription = "Показать номер"
            setOnClickListener {
                revealed = !revealed
                phoneValue.animate()
                    .alpha(0f)
                    .setDuration(if (settings.animations) 70L else 0L)
                    .withEndAction {
                        phoneValue.text = if (revealed) phoneRaw else maskPhone(phoneRaw)
                        phoneValue.alpha = 0f
                        phoneValue.animate()
                            .alpha(1f)
                            .setDuration(if (settings.animations) 120L else 0L)
                            .start()
                    }
                    .start()
                setImageResource(if (revealed) R.drawable.ic_visibility else R.drawable.ic_visibility_off)
                contentDescription = if (revealed) "Скрыть номер" else "Показать номер"
                animatePress(this)
            }
        }
        phoneRow.addView(phoneTexts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        phoneRow.addView(eye, LinearLayout.LayoutParams(dp(44), dp(44)))
        telegramCard.addView(
            phoneRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(18)
            }
        )

        val telegramLogout = TextView(this).apply {
            text = "Выйти из Telegram"
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = roundedBg(Color.parseColor("#D9435F"), 15)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                animatePress(this)
                confirmLogout()
            }
        }
        telegramCard.addView(
            telegramLogout,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                topMargin = dp(14)
            }
        )
        page.addView(telegramCard)

        page.addView(TextView(this).apply {
            text = "Twitch"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(dp(2), dp(20), 0, dp(10))
        })

        val twitchCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(20), dp(18), dp(18))
            background = roundedBg(panel, 24)
        }

        val twitchAvatarFrame = FrameLayout(this).apply {
            background = roundedBg(Color.parseColor("#9147FF"), 46)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        val twitchAvatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedBg(panel, 42)
        }

        val connected = !settings.twitchAccessToken.isNullOrBlank()
        val twitchLogin = twitchProfile?.login?.takeIf { it.isNotBlank() } ?: settings.twitchLogin
        val twitchName = twitchProfile?.displayName?.takeIf { it.isNotBlank() }
            ?: twitchLogin
            ?: "Twitch"

        if (!twitchProfile?.profileImageUrl.isNullOrBlank()) {
            twitchAvatar.load(twitchProfile?.profileImageUrl) {
                crossfade(settings.animations)
                transformations(CircleCropTransformation())
            }
        } else {
            twitchAvatar.load(R.drawable.sohr_brand_logo) {
                transformations(CircleCropTransformation())
            }
        }

        twitchAvatarFrame.addView(
            twitchAvatar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        twitchCard.addView(twitchAvatarFrame, LinearLayout.LayoutParams(dp(92), dp(92)))

        twitchCard.addView(TextView(this).apply {
            text = twitchName
            textSize = 21f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(0, dp(12), 0, dp(3))
        })

        twitchCard.addView(TextView(this).apply {
            text = if (connected) {
                if (twitchLogin.isNullOrBlank()) "SOHR • Twitch" else "@$twitchLogin • Twitch"
            } else {
                "Twitch не подключён"
            }
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(if (connected) muted else palette.accent)
        })

        val twitchAction = TextView(this).apply {
            text = if (connected) "Выйти из Twitch" else "Подключить Twitch"
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = roundedBg(
                if (connected) Color.parseColor("#D9435F") else Color.parseColor("#9147FF"),
                15
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                animatePress(this)
                if (connected) {
                    confirmTwitchLogout()
                } else {
                    startTwitchLogin()
                }
            }
        }
        twitchCard.addView(
            twitchAction,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                topMargin = dp(16)
            }
        )

        page.addView(twitchCard)

        val privacy = TextView(this).apply {
            text = "Аккаунты используются только для работы соответствующих источников в SOHR."
            textSize = 12f
            setTextColor(muted)
            setPadding(dp(4), dp(12), dp(4), 0)
        }
        page.addView(privacy)

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            setBackgroundColor(bg)
            addView(
                page,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }

        if (settings.animations) {
            telegramCard.alpha = 0f
            telegramCard.translationY = dp(10).toFloat()
            twitchCard.alpha = 0f
            twitchCard.translationY = dp(12).toFloat()
            telegramAvatar.scaleX = 0.88f
            telegramAvatar.scaleY = 0.88f
            twitchAvatar.scaleX = 0.88f
            twitchAvatar.scaleY = 0.88f
        }

        replaceRoot(withBottomNav(scroll, SohrTab.ACCOUNT))

        if (settings.animations) {
            val ease = android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
            telegramCard.post {
                telegramCard.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(250L)
                    .setInterpolator(ease)
                    .start()
                telegramAvatar.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(50L)
                    .setDuration(270L)
                    .setInterpolator(ease)
                    .start()
                twitchCard.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(80L)
                    .setDuration(270L)
                    .setInterpolator(ease)
                    .start()
                twitchAvatar.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(130L)
                    .setDuration(270L)
                    .setInterpolator(ease)
                    .start()
            }
        }
    }

    private fun maskPhone(phone: String): String {
        if (phone.length <= 5) return "••••"
        val visibleStart = phone.take(3)
        val visibleEnd = phone.takeLast(2)
        return visibleStart + " ••• ••• ••" + visibleEnd
    }

    private fun withBottomNav(content: View, selected: SohrTab): View {
        val rebuild = primaryShell == null ||
            primaryContentHost == null ||
            primaryNav == null ||
            primaryShellLightTheme != settings.lightTheme ||
            primaryShellAccent != settings.themeAccent

        if (rebuild) {
            val shell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(bg)
            }

            val host = FrameLayout(this).apply {
                clipChildren = true
                clipToPadding = true
            }

            val nav = SohrBottomNavView(this, palette, selected) { tab ->
                if (tab != currentPrimaryTab) {
                    pendingRootSlide = if (tab.ordinal > currentPrimaryTab.ordinal) 1 else -1
                    when (tab) {
                        SohrTab.VIDEOS -> showSelectedVideoSource()
                        SohrTab.SETTINGS -> showSettings()
                        SohrTab.STREAK -> showStreak()
                        SohrTab.ACCOUNT -> showAccount()
                    }
                }
            }

            installPressAnimations(content)
            host.addView(
                content,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            shell.addView(
                host,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )

            shell.addView(
                nav,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(74)
                ).apply {
                    marginStart = dp(14)
                    marginEnd = dp(14)
                    topMargin = dp(6)
                    bottomMargin = dp(8)
                }
            )

            primaryShell = shell
            primaryContentHost = host
            primaryNav = nav
            primaryShellLightTheme = settings.lightTheme
            primaryShellAccent = settings.themeAccent
            currentPrimaryTab = selected
            pendingRootSlide = 0
            return shell
        }

        val shell = primaryShell!!
        val host = primaryContentHost!!
        val nav = primaryNav!!
        nav.animate().cancel()
        nav.clearAnimation()
        nav.translationY = 0f
        nav.alpha = 1f
        nav.scaleX = 1f
        nav.scaleY = 1f

        val slide = pendingRootSlide
        pendingRootSlide = 0
        currentPrimaryTab = selected
        nav.syncSelected(selected, animate = settings.animations && slide != 0)

        while (host.childCount > 1) {
            val stale = host.getChildAt(0)
            stale.animate().cancel()
            stale.alpha = 1f
            stale.translationX = 0f
            stale.translationY = 0f
            stale.setLayerType(View.LAYER_TYPE_NONE, null)
            host.removeViewAt(0)
        }
        val old = if (host.childCount > 0) host.getChildAt(host.childCount - 1) else null
        old?.animate()?.cancel()
        old?.alpha = 1f
        old?.translationX = 0f
        old?.translationY = 0f
        old?.scaleX = 1f
        old?.scaleY = 1f

        val skipContentAnimation = suppressNextContentAnimation
        suppressNextContentAnimation = false
        val animateContent = settings.animations && !skipContentAnimation && old != null && old !== content
        val sectionCrossfade = pendingVideoSectionCrossfade
        val sectionDirection = pendingVideoSectionDirection
        pendingVideoSectionCrossfade = false
        pendingVideoSectionDirection = 0
        content.alpha = 1f
        content.translationX = 0f
        content.translationY = 0f

        installPressAnimations(content)
        val contentParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        if (animateContent && slide < 0 && old != null) {
            val oldIndex = host.indexOfChild(old).coerceAtLeast(0)
            host.addView(content, oldIndex, contentParams)
        } else {
            host.addView(content, contentParams)
        }

        if (old != null && old !== content) {
            if (animateContent) {
                old.animate().cancel()
                content.animate().cancel()
                old.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                content.setLayerType(View.LAYER_TYPE_HARDWARE, null)

                val telegramInterpolator = android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
                if (sectionCrossfade) {
                    val direction = if (sectionDirection == 0) 1 else sectionDirection
                    val travel = dp(20).toFloat() * direction
                    content.alpha = 0f
                    content.translationX = travel
                    content.scaleX = 1f
                    content.scaleY = 1f
                    old.alpha = 1f
                    old.translationX = 0f
                    old.animate()
                        .alpha(0f)
                        .translationX(-travel * 0.45f)
                        .setDuration(SohrMotion.FAST)
                        .setInterpolator(telegramInterpolator)
                        .start()
                    content.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .setStartDelay(28L)
                        .setDuration(SohrMotion.NORMAL)
                        .setInterpolator(SohrMotion.smooth())
                        .withEndAction {
                            old.animate().cancel()
                            old.alpha = 1f
                            old.translationX = 0f
                            old.translationY = 0f
                            content.alpha = 1f
                            content.translationX = 0f
                            content.translationY = 0f
                            old.setLayerType(View.LAYER_TYPE_NONE, null)
                            content.setLayerType(View.LAYER_TYPE_NONE, null)
                            if (old.parent === host) host.removeView(old)
                        }
                        .start()
                } else if (slide < 0) {
                    // Telegram pop: reveal the previous screen underneath and
                    // slide/fade only the current screen to the right.
                    content.alpha = 1f
                    content.translationX = 0f
                    old.alpha = 1f
                    old.translationX = 0f
                    old.animate()
                        .alpha(0f)
                        .translationX(dp(48).toFloat())
                        .setDuration(150L)
                        .setInterpolator(telegramInterpolator)
                        .withEndAction {
                            old.alpha = 1f
                            old.translationX = 0f
                            old.setLayerType(View.LAYER_TYPE_NONE, null)
                            content.setLayerType(View.LAYER_TYPE_NONE, null)
                            if (old.parent === host) host.removeView(old)
                        }
                        .start()
                } else {
                    // Telegram push: previous screen stays in place, the new
                    // screen fades in while travelling only 48dp from the right.
                    old.alpha = 1f
                    old.translationX = 0f
                    content.alpha = 0f
                    content.translationX = dp(48).toFloat()
                    content.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .setDuration(150L)
                        .setInterpolator(telegramInterpolator)
                        .withEndAction {
                            old.setLayerType(View.LAYER_TYPE_NONE, null)
                            content.setLayerType(View.LAYER_TYPE_NONE, null)
                            if (old.parent === host) host.removeView(old)
                        }
                        .start()
                }
            } else {
                host.removeView(old)
            }
        }

        return shell
    }




    private fun confirmTwitchLogout() {
        ModernDialogs.showConfirm(
            context = this,
            palette = palette,
            title = "Выйти из Twitch?",
            message = "SOHR отключит Twitch-аккаунт и отзовёт выданный приложению токен.",
            confirm = "Выйти",
            destructive = true
        ) {
            lifecycleScope.launch {
                val token = settings.twitchAccessToken
                var revokeFailed = false
                if (!token.isNullOrBlank()) {
                    try {
                        TwitchApi.revokeToken(BuildConfig.TWITCH_CLIENT_ID.trim(), token)
                    } catch (_: Exception) {
                        revokeFailed = true
                    }
                }

                settings.twitchAccessToken = null
                settings.twitchRefreshToken = null
                settings.twitchOauthState = null
                settings.twitchLogin = null
                twitchVideos = emptyList()
                if (settings.videoSource == "twitch") settings.videoSource = "telegram"

                showAccount()
                root.postDelayed({
                    ModernDialogs.showNotice(
                        context = this@MainActivity,
                        palette = palette,
                        title = "Twitch отключён",
                        message = if (revokeFailed) {
                            "Аккаунт удалён из SOHR. Twitch не подтвердил отзыв токена из-за ошибки сети."
                        } else {
                            "Аккаунт Twitch отключён от SOHR."
                        },
                        button = "Готово"
                    )
                }, 180L)
            }
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
                    settings.authPhone = null
                    client.send(TdApi.LogOut())
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, e.message ?: "Не удалось выйти", Toast.LENGTH_LONG).show()
                    showAccount()
                }
            }
        }
    }

    private fun setFullscreen(enabled: Boolean) {
        fun applySystemBars(fullscreen: Boolean) {
            ViewCompat.requestApplyInsets(root)
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.let {
                    if (fullscreen) {
                        it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                        it.systemBarsBehavior =
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        it.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                run {
                    window.decorView.systemUiVisibility =
                        if (fullscreen) {
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        } else {
                            View.SYSTEM_UI_FLAG_VISIBLE
                        }
                }
            }
        }

        try {
            // Never short-circuit on the Activity flag. PlayerScreen owns the
            // actual fullscreen state and can recover even if Activity state
            // got out of sync after navigation or an OEM-specific layout event.
            playerScreen?.setFullscreenMode(enabled)
            twitchPlayerScreen?.setFullscreenMode(enabled)
            twitchLivePlayerScreen?.setFullscreenMode(enabled)

            val actual =
                playerScreen?.isFullscreen
                    ?: twitchPlayerScreen?.isFullscreen
                    ?: twitchLivePlayerScreen?.isFullscreen
                    ?: false

            fullScreen = actual
            applySystemBars(actual)
        } catch (_: Throwable) {
            fullScreen = false
            runCatching { playerScreen?.exitFullscreen() }
            runCatching { twitchPlayerScreen?.exitFullscreen() }
            runCatching { twitchLivePlayerScreen?.exitFullscreen() }
            runCatching { applySystemBars(false) }
        }
    }

    private fun fallbackReleaseNotes(version: String): String = when (version) {
        "6.3.1" -> listOf(
            "Добавлен полноценный раздел «Сохранено» рядом с Главной и Историей.",
            "В «Сохранено» отдельно показываются «Избранное» и «Смотреть позже», как в медиатеке YouTube.",
            "Долгое нажатие на видео теперь умеет добавлять и убирать его из избранного.",
            "Поиск расширен: название, полный caption/описание, дата, источник и @t2x2.",
            "В поиск добавлен быстрый фильтр «Сохранено».",
            "Холодный запуск Telegram-главной сначала показывает локальный кэш, затем тихо обновляет его в фоне.",
            "Новая синяя ава SOHR остаётся иконкой приложения; старая облачная загрузка не изменена."
        ).joinToString(" • ")
        "6.3.0" -> listOf(
            "Добавлены «Моменты»: сохранение точной секунды, названия и быстрый переход обратно в видео.",
            "Точная перемотка получила дополнительную ленту кадров при движении пальца вверх от таймлайна.",
            "Streak 2.0 получил до двух защитных щитов: новый щит начисляется за каждые 7 дней просмотра.",
            "Очередь превращена в сессию просмотра с drag-and-drop перестановкой видео.",
            "Умное скачивание хранит до двух свежих непросмотренных Telegram-видео только по Wi‑Fi и не удаляет ручные загрузки.",
            "SOHR Recap вернулся отдельным экраном в настройках и умеет сохраняться картинкой, не занимая место на главной.",
            "Установлена новая синяя ава SOHR только как иконка приложения; старая облачная загрузка оставлена без изменений."
        ).joinToString(" • ")
        "6.2.8" -> listOf(
            "Полностью удалён лишний статичный логотип перед загрузкой приложения.",
            "Системный SplashScreen теперь прозрачный по значку и мгновенно передаёт экран старой облачной анимации.",
            "Старая LoadingWaveView с облачным контуром и её тайминги оставлены без изменений.",
            "Файл старого системного splash-логотипа удалён из проекта, чтобы он не вернулся случайно."
        ).joinToString(" • ")
        "6.2.7" -> listOf(
            "Возвращена старая облачная анимация загрузки из прошлой версии — без картинки внутри.",
            "Стартовый loading снова использует тот же классический облачный контур и прежние тайминги.",
            "Новая фирменная ава приложения сохранена только как иконка приложения и не вставляется в loading.",
            "Плеер на экране записи опущен ниже и получил ровный верхний отступ от шапки."
        ).joinToString(" • ")
        "6.2.6" -> listOf(
            "Исправлено ручное скачивание Telegram-видео: оно больше не конфликтует с range-загрузкой плеера.",
            "Месячный SOHR Recap и карточка «Сентябрь в SOHR» полностью удалены.",
            "Нижняя панель закреплена и больше не двигается и не ломается при медленном скролле.",
            "Панель управления видео и таймлайн стали компактнее и легче.",
            "Установлена новая фирменная ава SOHR напрямую как WebP-ресурс.",
            "Loading остаётся отдельным нативным знаком без картинки, неона и светящихся обводок."
        ).joinToString(" • ")
        "6.2.5" -> listOf(
            "Из loading-экранов полностью убрана вставленная картинка логотипа.",
            "Новый loading-знак рисуется нативно: без неона, без светящейся окантовки и без bitmap.",
            "Стартовый знак стал среднего размера и получил мягкое появление без резких эффектов.",
            "Системный SplashScreen использует отдельный чистый векторный знак SOHR.",
            "Внутренние загрузки и экран обновления используют тот же единый спокойный loading-стиль."
        ).joinToString(" • ")
        "6.2.4" -> listOf(
            "Установлен новый фирменный логотип SOHR как иконка приложения и главный знак загрузки.",
            "Добавлен системный SplashScreen с плавным переходом в фирменную анимацию облака.",
            "Загрузочные экраны используют единый облачный контур и новый логотип SOHR.",
            "Фоновые Twitch-проверки больше не показывают технические DNS/timeout ошибки.",
            "При проблемах сети Twitch используется проверка интернета, 30-секундный cooldown и понятное сообщение.",
            "Ошибка Twitch внутри окна входа заменена на аккуратный экран SOHR.",
            "Очищены кривые Twitch-заголовки и ссылки в плеере и блоке следующих видео.",
            "Поиск и переход в полноэкранный режим получили более плавные анимации."
        ).joinToString(" • ")
        "6.2.3" -> listOf(
            "Очищены кривые Twitch-заголовки и ссылки в плеере и блоке следующих видео.",
            "Экран видео стал компактнее и ровнее по типографике и отступам.",
            "Карточки следующих видео выровнены и получили плавное нажатие.",
            "Поиск открывается и закрывается плавнее, устранена отложенная перерисовка после выхода.",
            "Переход в полноэкранный режим и обратно получил плавную hero-анимацию."
        ).joinToString(" • ")
        "6.2.2" -> listOf(
            "Настройка «Превью видео» перенесена выше «Автовоспроизведения».",
            "Очередь и центр загрузок теперь используют быстрый in-memory cache вместо повторного JSON-разбора.",
            "Каталог видео читается и сохраняется вне главного UI-потока, а повторное чтение при одном обновлении убрано.",
            "Горизонтальные полки меньше пересоздают карточки при прокрутке.",
            "Исправлено краткое зависание кликов на переиспользованных карточках после быстрого тапа."
        ).joinToString(" • ")
        "6.2.1" -> listOf(
            "Исправлена нижняя панель: у самого низа страницы она всегда полностью возвращается на место.",
            "Строка под приветствием SOHR Сегодня стала компактнее и больше не обрезается многоточием.",
            "В центре загрузок добавлена полноценная кнопка «Назад».",
            "Настройка превью теперь выглядит как обычный компактный пункт и открывает небольшое окно выбора режима."
        ).joinToString(" • ")
        "6.2.0" -> listOf(
            "Добавлен блок «С прошлого раза» с новыми видео после предыдущего захода.",
            "Плеер получил MediaSession для системного управления воспроизведением.",
            "Карточка T2x2 показывает название и длительность текущего эфира.",
            "Добавлена управляемая очередь: следующим, в конец, изменение порядка и очистка.",
            "Следующее Telegram-видео предзагружается до 15% с ограничением трафика.",
            "Добавлены тихие inline-превью с режимами Wi‑Fi, Всегда и Выкл.",
            "Появился центр загрузок с офлайн-файлами, размерами и удалением.",
            "Анимации и тактильный отклик объединены в единую motion-систему SOHR.",
            "Шапка и нижняя панель мягко реагируют на прокрутку.",
            "Добавлен SOHR Recap с реальным временем просмотра, активным днём и стриками."
        ).joinToString(" • ")
        "6.1.4" -> listOf(
            "Убрана окантовка вокруг карточек «Продолжить» и «Новое».",
            "Исправлена высота карточек: название и дата больше не обрезаются.",
            "Текст карточек стал компактнее и ровнее.",
            "Один системный свайп назад теперь закрывает поиск целиком, даже при открытой клавиатуре.",
            "Сохранены восстановленные анимации карточек и нижней навигации."
        ).joinToString(" • ")
        "6.1.3" -> listOf(
            "Карточки «Продолжить» и «Новое» снова компактные и занимают около 60% ширины экрана.",
            "Видео и нижняя подпись визуально объединены в одну цельную карточку.",
            "Возвращены плавные анимации появления, нажатия и переходов карточек.",
            "Нижняя навигация больше не обрывает анимацию индикатора при смене экрана.",
            "Повторный тап по вкладке не теряется, если экран ещё завершает переход.",
            "Статус стрима теперь подписан как T2x2: серый офлайн и красный пульсирующий онлайн."
        ).joinToString(" • ")
        "6.1.2" -> listOf(
            "Карточки «Продолжить» и «Новое» снова компактные и ровные, без гигантского растягивания.",
            "Превью возвращены к аккуратным пропорциям и одинаковой высоте.",
            "Длинный Telegram-caption больше не раздувает карточку: на главной показывается только первая строка.",
            "Нижняя панель больше не перехватывает касания общим жестом.",
            "Убрана задержка-блокировка после нажатия на вкладку, поэтому быстрые переключения не теряются."
        ).joinToString(" • ")
        "6.1.1" -> listOf(
            "Главная выровнена: адаптивные 16:9 карточки и плавное прилипание карусели.",
            "Статус T2x2 находится в SOHR Сегодня: серый офлайн и красный пульсирующий онлайн.",
            "Отдельная карточка T2x2 под SOHR Сегодня удалена.",
            "Жест назад теперь закрывает поиск перед выходом с главной.",
            "Экран видео выровнен по единой сетке и правильному соотношению сторон.",
            "Telegram-caption разделяется на короткий заголовок и аккуратное описание."
        ).joinToString(" • ")
        "6.1.0" -> listOf(
            "Исправлен системный PiP: в маленьком окне теперь остаётся только видео.",
            "Добавлен экран «SOHR Сегодня» и очищена верхняя часть главной.",
            "Поиск раскрывается из шапки и фильтрует новые, непросмотренные, просмотренные и скачанные видео.",
            "Карточки плавно переходят в плеер, добавлены Predictive Back и ambient-фон.",
            "Долгое нажатие на видео открывает быстрые действия.",
            "Загрузка медиатеки теперь использует skeleton-анимацию.",
            "Добавлены более аккуратные микроанимации и тактильный отклик."
        ).joinToString(" • ")
        "6.0.15" -> listOf(
            "Видео из Telegram запускаются быстрее за счёт короткого стартового диапазона.",
            "Исправлено преждевременное завершение потока при временно недоступном диапазоне.",
            "Плеер автоматически восстанавливается после кратких сетевых ошибок.",
            "Повторное открытие видео использует уже загруженные TDLib-данные вместо холодного старта."
        ).joinToString(" • ")
        "6.0.14" -> listOf(
            "Автообновление Telegram теперь работает в фоне без мигания кнопки и лишних уведомлений.",
            "Новые сборники продолжают появляться автоматически сразу после обнаружения."
        ).joinToString(" • ")
        "6.0.13" -> listOf(
            "Telegram-сборники теперь автоматически проверяются каждые 15 секунд, пока открыт раздел.",
            "Новые сообщения канала запускают ускоренное обновление почти сразу.",
            "Перед чтением истории SOHR принудительно открывает Telegram-канал через TDLib.",
            "История загружается устойчивее и больше не обрывается из-за единичного старого сообщения.",
            "Недавний кэш безопасно объединяется с новыми данными вместо преждевременной очистки.",
            "Добавлена поддержка Telegram-видео, пришедших как Animation.",
            "GitHub-синк больше не перезаписывает каталог пустым результатом."
        ).joinToString(" • ")
        "6.0.12" -> listOf(
            "Видео-интерфейс стал компактнее и ровнее.",
            "Twitch-записи теперь используют адаптивный формат 16:9 вместо фиксированной высоты.",
            "Во всех основных диалогах убрана лишняя верхняя полоска.",
            "Окна выбора стали компактнее и меньше перекрывают экран.",
            "Исправлена кнопка настроек Twitch-видео.",
            "Локальные превью загружаются без блокировки интерфейса."
        ).joinToString(" • ")
        "6.0.11" -> listOf(
            "В интерфейсе завершён переход на обращение на «вы».",
            "В статистике убран лишний дублирующий текст под полосой прогресса.",
            "После обновления можно открыть аккуратный список изменений.",
            "Окно завершения обновления стало понятнее и удобнее."
        ).joinToString(" • ")
        else -> "Улучшения интерфейса • Исправления стабильности"
    }

    private fun checkForUpdates(manual: Boolean = true) {
        lifecycleScope.launch {
            if (manual) showLoading("Проверяем обновления…")
            try {
                val info = updateManager.check()
                if (info == null) {
                    if (manual) {
                        showSettings()
                        root.post {
                            ModernDialogs.showNotice(
                                context = this@MainActivity,
                                palette = palette,
                                title = "Обновлений нет",
                                message = "У вас последняя версия SOHR • " + BuildConfig.VERSION_NAME,
                                button = "Готово"
                            )
                        }
                    }
                    return@launch
                }

                val runtimePrefs = getSharedPreferences("sohr_runtime", MODE_PRIVATE)
                if (!manual && runtimePrefs.getInt("ignored_update_code", -1) == info.versionCode) return@launch
                if (manual) showSettings()

                root.post {
                    ModernDialogs.showConfirm(
                        context = this@MainActivity,
                        palette = palette,
                        title = "Доступна новая версия",
                        message = "SOHR ${info.versionName}",
                        confirm = "Обновить сейчас"
                    ) {
                        downloadAndInstallUpdate(info)
                    }
                }
            } catch (e: Exception) {
                if (manual) {
                    showSettings()
                    root.post {
                        ModernDialogs.showNotice(
                            context = this@MainActivity,
                            palette = palette,
                            title = "Не удалось проверить обновления",
                            message = e.message ?: "Ошибка сети",
                            button = "Понятно"
                        )
                    }
                }
            }
        }
    }

    private fun downloadAndInstallUpdate(info: UpdateInfo) {
        lifecycleScope.launch {
            showUpdateProgress(0)
            try {
                val apk = updateManager.download(info) { progress ->
                    runOnUiThread {
                        updateProgressLabel?.text = "Скачиваем SOHR " + info.versionName + " • " + progress + "%"
                    }
                }
                pendingUpdateApk = apk
                updateProgressLabel?.text = "Обновление готово"

                if (!updateManager.isSignatureCompatible(apk)) {
                    pendingUpdateApk = null
                    showSettings()
                    root.post {
                        ModernDialogs.showNotice(
                            context = this@MainActivity,
                            palette = palette,
                            title = "Нужна одноразовая переустановка",
                            message = "Текущая версия SOHR была подписана старым временным ключом. Android не разрешит обновить её поверх новой версии. Один раз установите первую версию с постоянной подписью после удаления старой — дальше обновления будут ставиться поверх без этого конфликта.",
                            button = "Понятно"
                        )
                    }
                    return@launch
                }

                getSharedPreferences("sohr_runtime", MODE_PRIVATE)
                    .edit()
                    .putString("pending_update_version_name", info.versionName)
                    .putString(
                        "pending_update_notes",
                        info.notes.ifBlank { fallbackReleaseNotes(info.versionName) }
                    )
                    .apply()

                if (updateManager.canRequestInstall()) {
                    root.postDelayed({ launchUpdateInstaller(apk) }, 260L)
                } else {
                    waitingForInstallPermission = true
                    Toast.makeText(
                        this@MainActivity,
                        "Разрешите SOHR устанавливать обновления — после возврата установка продолжится сама.",
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(updateManager.unknownSourcesIntent())
                }
            } catch (e: Exception) {
                pendingUpdateApk = null
                showSettings()
                Toast.makeText(
                    this@MainActivity,
                    "Не удалось скачать обновление: " + (e.message ?: "ошибка"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun buildBrandLoadingMark(sizeDp: Int): View =
        LoadingWaveView(this, purple)

    private fun showUpdateProgress(progress: Int) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(bg)
        }

        val spinner = buildBrandLoadingMark(58)
        val title = TextView(this).apply {
            text = "Обновление SOHR"
            textSize = 22f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(0, dp(18), 0, 0)
        }
        val label = TextView(this).apply {
            text = "Скачиваем… " + progress + "%"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(0, dp(7), 0, 0)
        }
        updateProgressLabel = label

        box.addView(spinner, LinearLayout.LayoutParams(dp(58), dp(58)))
        box.addView(title)
        box.addView(label)
        replaceRoot(box)
    }

    private fun launchUpdateInstaller(apk: File) {
        if (!apk.exists()) {
            showSettings()
            Toast.makeText(this, "Файл обновления не найден", Toast.LENGTH_LONG).show()
            return
        }

        try {
            startActivity(updateManager.installerIntent(apk))
        } catch (e: Exception) {
            showSettings()
            Toast.makeText(
                this,
                "Не удалось открыть установку: " + (e.message ?: "ошибка"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showLoading(message: String) {
        if (startupPhase && startupStatusView != null) {
            startupStatusView?.animate()?.cancel()
            startupStatusView?.text = message
            startupStatusView?.alpha = 0.72f
            startupStatusView?.animate()?.alpha(1f)?.setDuration(140L)?.start()
            return
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(bg)
        }

        val spinner = buildBrandLoadingMark(56)
        val label = TextView(this).apply {
            text = message
            setTextColor(muted)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        }

        box.addView(spinner, LinearLayout.LayoutParams(dp(56), dp(56)))
        box.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        replaceRoot(box)
    }

    private fun showMessage(title: String, description: String) {
        startupPhase = false
        startupStatusView = null
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
        val requestedSlide = pendingRootSlide
        pendingRootSlide = 0

        if (root.childCount == 1 && root.getChildAt(0) === view) {
            suppressNextRootAnimation = false
            return
        }

        val animate = settings.animations && !suppressNextRootAnimation
        suppressNextRootAnimation = false

        (view.parent as? ViewGroup)?.removeView(view)

        while (root.childCount > 1) {
            val stale = root.getChildAt(0)
            stale.animate().cancel()
            stale.alpha = 1f
            stale.translationX = 0f
            stale.translationY = 0f
            stale.scaleX = 1f
            stale.scaleY = 1f
            stale.setLayerType(View.LAYER_TYPE_NONE, null)
            root.removeViewAt(0)
        }

        val old = if (root.childCount > 0) root.getChildAt(root.childCount - 1) else null

        if (!animate || old == null || old === view) {
            root.removeAllViews()
            view.alpha = 1f
            view.translationX = 0f
            view.translationY = 0f
            installPressAnimations(view)
            root.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            return
        }

        val slide = if (requestedSlide != 0) requestedSlide else if (view === primaryShell) -1 else 0
        view.alpha = 1f
        view.translationX = 0f
        view.translationY = 0f

        installPressAnimations(view)
        val rootParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        if (slide < 0) {
            val oldIndex = root.indexOfChild(old).coerceAtLeast(0)
            root.addView(view, oldIndex, rootParams)
        } else {
            root.addView(view, rootParams)
        }

        old.animate().cancel()
        view.animate().cancel()
        old.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val telegramInterpolator = android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
        if (slide < 0) {
            view.alpha = 1f
            view.translationX = 0f
            old.alpha = 1f
            old.translationX = 0f
            old.animate()
                .alpha(0f)
                .translationX(dp(24).toFloat())
                .setDuration(220L)
                .setInterpolator(telegramInterpolator)
                .withEndAction {
                    old.alpha = 1f
                    old.translationX = 0f
                    old.setLayerType(View.LAYER_TYPE_NONE, null)
                    view.setLayerType(View.LAYER_TYPE_NONE, null)
                    if (old.parent === root) root.removeView(old)
                }
                .start()
        } else {
            old.alpha = 1f
            old.translationX = 0f
            view.alpha = 0f
            view.translationX = dp(24).toFloat()
            view.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(220L)
                .setInterpolator(telegramInterpolator)
                .withEndAction {
                    old.setLayerType(View.LAYER_TYPE_NONE, null)
                    view.setLayerType(View.LAYER_TYPE_NONE, null)
                    if (old.parent === root) root.removeView(old)
                }
                .start()
        }
    }

    private fun installPressAnimations(view: View) {
        if (!settings.animations) return

        fun attach(target: View) {
            val buttonLike =
                target is Button ||
                target is ImageButton ||
                (target is TextView && target.isClickable) ||
                (target is LinearLayout && target.isClickable)

            if (buttonLike && target !is SohrBottomNavView && target.tag != "sohr_video_section_tab") {
                target.setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            v.animate().cancel()
                            v.animate()
                                .scaleX(0.972f)
                                .scaleY(0.972f)
                                .alpha(0.94f)
                                .setDuration(SohrMotion.FAST / 2)
                                .setInterpolator(SohrMotion.smooth())
                                .start()
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.animate().cancel()
                            v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .alpha(1f)
                                .setDuration(SohrMotion.FAST)
                                .setInterpolator(SohrMotion.smooth())
                                .start()
                        }
                    }
                    false
                }
            }

            if (target is ViewGroup) {
                for (i in 0 until target.childCount) attach(target.getChildAt(i))
            }
        }

        attach(view)
    }

    private fun roundedBg(color: Int, radiusDp: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun animateSystemChrome(fromColor: Int, toColor: Int, light: Boolean, durationMs: Long) {
        val evaluator = android.animation.ArgbEvaluator()
        var appearanceSwitched = false
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                val color = evaluator.evaluate(fraction, fromColor, toColor) as Int
                root.setBackgroundColor(color)
                window.statusBarColor = color
                window.navigationBarColor = color
                if (!appearanceSwitched && fraction >= 0.55f) {
                    appearanceSwitched = true
                    applySystemBarIconAppearance(light)
                }
            }
        }.start()
    }

    private fun applySystemBarIconAppearance(light: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (light) mask else 0, mask)
        } else {
            @Suppress("DEPRECATION")
            run {
                window.decorView.systemUiVisibility =
                    if (light) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    private fun applySystemTheme() {
        root.setBackgroundColor(bg)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        applySystemBarIconAppearance(settings.lightTheme)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        unregisterSearchBackInterceptor()
        stopInlinePreview()
        inlinePreviewPlayer?.release()
        inlinePreviewPlayer = null
        inlinePreviewView = null
        playerScreen?.destroy()
        playerScreen = null
        twitchPlayerScreen?.destroy()
        twitchPlayerScreen = null
        twitchLivePlayerScreen?.destroy()
        twitchLivePlayerScreen = null
        twitchLiveJob?.cancel()
        twitchLiveJob = null
        t2x2WatchJob?.cancel()
        t2x2WatchJob = null
        twitchAuthJob?.cancel()
        twitchAuthJob = null
        streamServer?.stop()
        if (::client.isInitialized) client.close()
        super.onDestroy()
    }
    companion object {
        private const val TWITCH_REDIRECT_URI = "https://unknokable0.github.io/video-sohranenki/twitch-auth/"
        private const val T2X2_NOTIFICATION_CHANNEL = "t2x2_live"
        private const val T2X2_NOTIFICATION_ID = 2202
        private const val T2X2_NOTIFICATION_PERMISSION_REQUEST = 2203
    }
}
