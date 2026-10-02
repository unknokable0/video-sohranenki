package com.unknokable.videosohranenki

import android.Manifest
import android.accounts.AccountManager
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
import androidx.browser.customtabs.CustomTabsIntent
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
    private var playerReturnView: View? = null
    private var twitchPlayerScreen: TwitchPlayerScreen? = null
    private var twitchLivePlayerScreen: TwitchLivePlayerScreen? = null
    private var youtubeResolveJob: kotlinx.coroutines.Job? = null
    private var youtubeChannels: List<YouTubeChannelSummary> = emptyList()
    private var youtubeFeedVideos: List<YouTubeFeedVideo> = emptyList()
    private var youtubeFeedLoading = false
    private var youtubeFeedJob: kotlinx.coroutines.Job? = null
    private var youtubeDurationEnrichJob: kotlinx.coroutines.Job? = null
    private var youtubeFeedAutoRefreshJob: kotlinx.coroutines.Job? = null
    private var lastYoutubeFeedRefreshAt = 0L
    private var lastYoutubeFeedAttemptAt = 0L
    private var youtubeFeedInitialAttempted = false
    private val youtubeFeedRetryCooldownMs = 30_000L
    private var openYouTubeChannelSummary: YouTubeChannelSummary? = null
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
    private var youtubeWebPlayer: WebView? = null
    private var twitchLiveJob: kotlinx.coroutines.Job? = null
    private var t2x2WatchJob: kotlinx.coroutines.Job? = null
    private var t2x2LiveSlot: FrameLayout? = null
    private var lastT2x2Live: TwitchLiveStream? = null
    private val t2x2LiveAccent = Color.parseColor("#E5484D")
    private lateinit var twitchCategoryTracker: TwitchCategoryTracker
    private var lastT2x2Timeline: TwitchCategoryTimeline? = null
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
    private var pendingPrimaryTabTransition = false
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
    private var pendingFeedScrollRestoreY: Int? = null
    private val telegramAutoRefreshIntervalMs = 15_000L
    private val twitchAutoRefreshIntervalMs = 180_000L
    private var startupUpdateCheckDone = false
    private var updateAutoCheckJob: kotlinx.coroutines.Job? = null
    private var updateNotificationWatchJob: kotlinx.coroutines.Job? = null
    private val automaticUpdateCheckIntervalMs = 45_000L
    private var onboardingActive = false
    private var videoSection = 1 // 1 home, 2 feed, 3 watched
    private var pendingVideoSectionCrossfade = false
    private var pendingVideoSectionDirection = 0
    private var videoSectionSwitchLocked = false
    private var pendingVideoSectionTarget: Int? = null
    private var settingsScrollY = 0
    private var todayLiveStatusView: TextView? = null
    private var todayLiveDot: LivePulseView? = null
    private var todaySummaryView: TextView? = null
    private var todayLiveCard: LinearLayout? = null
    private var todayLiveDetailsPanel: LinearLayout? = null
    private var todayLiveExpandIcon: ImageView? = null
    private var todayLiveExpanded = false
    private var todayLiveExpandAnimator: android.animation.ValueAnimator? = null
    private var auxiliaryScreen: String? = null
    private val inlinePreviewHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var inlinePreviewPlayer: androidx.media3.exoplayer.ExoPlayer? = null
    private var inlinePreviewView: androidx.media3.ui.PlayerView? = null
    private var inlinePreviewHost: FrameLayout? = null
    private var inlinePreviewStop: Runnable? = null
    private var feedSearchOpen = false
    private var closeFeedSearch: (() -> Boolean)? = null
    private var activeSearchInput: EditText? = null
    private var searchSystemBackCallback: android.window.OnBackInvokedCallback? = null
    private var predictiveBackTarget: View? = null
    private var twitchNetworkCooldownUntilElapsed = 0L
    private val activeManualDownloads = linkedSetOf<Int>()
    private val activeTwitchDownloads = linkedSetOf<Long>()
    private var openUpdateFromNotification = false

    private var telegramChannelHub: TelegramChannelHub? = null
    private var telegramChannels: List<TelegramChannelSummary> = emptyList()
    private var telegramChannelsLoading = false
    private var telegramChannelRefreshJob: kotlinx.coroutines.Job? = null
    private var openTelegramChannelSummary: TelegramChannelSummary? = null
    private var openTelegramPosts: List<TelegramChannelPost> = emptyList()
    private var openTelegramUnreadAtOpen: Int = 0
    private var openTelegramChannelScrollY: Int = 0
    private var telegramChannelRender: TelegramChannelRender? = null
    private var telegramChannelReturnView: View? = null
    private var telegramAudioPlayer: androidx.media3.exoplayer.ExoPlayer? = null
    private var telegramAudioMessageId: Long = 0L
    private var telegramUnreadBadgeView: TextView? = null

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

        YouTubeNativeResolver.initialize(applicationContext)

        settings = AppSettings(this)
        twitchCategoryTracker = TwitchCategoryTracker(this)
        lastT2x2Timeline = twitchCategoryTracker.timeline()
        openUpdateFromNotification =
            intent?.getBooleanExtra(SohrBackgroundCheckWorker.EXTRA_OPEN_UPDATE, false) == true
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
                if (playerScreen?.isMiniMode != true && closeCurrentPlayerScreen()) return

                if (auxiliaryScreen == "telegram_channel") {
                    closeTelegramChannelToFeed()
                    return
                }

                if (auxiliaryScreen == "youtube_channel") {
                    auxiliaryScreen = null
                    openYouTubeChannelSummary = null
                    videoSection = 2
                    pendingRootSlide = -1
                    showFeed(currentVideos)
                    return
                }

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
                if (playerScreen?.isMiniMode == true) {
                    closeCurrentPlayerScreen()
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
                            scheduleFeedAutoRefresh(delayMs = 250L, force = true)
                        }

                        val hub = telegramChannelHub
                        if (hub != null && hub.isTracked(update.message.chatId)) {
                            val post = hub.absorbNewMessage(update.message)
                            if (post != null) {
                                runOnUiThread { handleRealtimeTelegramChannelPost(post) }
                            }
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
        if (intent.getBooleanExtra(SohrBackgroundCheckWorker.EXTRA_OPEN_UPDATE, false)) {
            openUpdateFromNotification = true
            intent.removeExtra(SohrBackgroundCheckWorker.EXTRA_OPEN_UPDATE)
            if (!startupPhase) {
                root.post { consumeUpdateNotificationIntent() }
            }
        }
        if (!handleTwitchAuthIntent(intent, loadAfter = true)) handleSharedIntent(intent)
    }

    @Deprecated("Activity result API kept for the platform Google account picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == YOUTUBE_ACCOUNT_PICK_REQUEST) {
            if (resultCode == RESULT_OK) {
                val accountName = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                settings.youtubeAccountName = accountName
                settings.youtubeBrowserConnected = true

                // Open the browser-backed chooser with the selected account hinted.
                // The browser owns the real YouTube session; SOHR only keeps the label.
                launchYouTubeCustomTab(
                    youtubeAccountChooserUrl(
                        targetUrl = "https://m.youtube.com/account",
                        accountName = accountName
                    )
                )

                if (isAccountScreen) {
                    root.postDelayed({
                        if (!isFinishing && isAccountScreen) showAccount()
                    }, 240L)
                }
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onResume() {
        super.onResume()
        startT2x2LiveWatch()
        startUpdateNotificationWatch()
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
            scheduleAutomaticUpdateCheck(delayMs = 0L, force = false)
        }
    }

    private fun startUpdateNotificationWatch() {
        if (updateNotificationWatchJob?.isActive == true) return
        updateNotificationWatchJob = lifecycleScope.launch {
            delay(1_500L)
            while (isActive) {
                runCatching { checkForUpdates(manual = false) }
                delay(60_000L)
            }
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
                        loadTwitchVideos(inPlace = true, quiet = true)
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
        youtubeResolveJob?.cancel()
        youtubeResolveJob = null

        val outgoingPlayer = playerScreen
        if (outgoingPlayer?.isMiniMode == true) {
            outgoingPlayer.flushPlaybackPosition()
            val playerRoot = outgoingPlayer.root
            runCatching { if (playerRoot.parent === root) root.removeView(playerRoot) }
            playerScreen = null
            isPlayerScreen = false
            playerReturnView = null
            outgoingPlayer.destroy()
            currentStreamingItem?.let { streamed -> streamServer?.release(streamed) }
            currentStreamingItem = null
            playerBackInProgress = false
            return true
        }

        val exactReturnView = playerReturnView
        playerReturnView = null
        val outgoingTwitchPlayer = twitchPlayerScreen
        val outgoingTwitchLivePlayer = twitchLivePlayerScreen
        val outgoingYouTubeWebPlayer = youtubeWebPlayer
        youtubeWebPlayer = null

        outgoingPlayer?.flushPlaybackPosition()
        outgoingTwitchLivePlayer?.prepareForExit()

        playerScreen = null
        twitchPlayerScreen = null
        twitchLivePlayerScreen = null
        isPlayerScreen = false
        pendingRootSlide = -1
        suppressNextContentAnimation = true

        val channelToRestore =
            if (auxiliaryScreen == "telegram_channel") openTelegramChannelSummary else null
        val day = currentDay
        if (exactReturnView != null) {
            replaceRoot(exactReturnView)
        } else if (channelToRestore != null) {
            currentDay = null
            renderTelegramChannel(
                channel = channelToRestore,
                loading = false,
                canLoadOlder = openTelegramPosts.size >= 60
            )
        } else if (day != null && day.videos.isNotEmpty()) {
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
                outgoingYouTubeWebPlayer?.let { webView ->
                    runCatching {
                        webView.stopLoading()
                        webView.loadUrl("about:blank")
                        webView.destroy()
                    }
                }
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
        if (android.os.Build.VERSION.SDK_INT < 26 || isInPictureInPictureMode) return

        var restore: (() -> Unit)? = null
        val prepared = when {
            playerScreen?.player?.isPlaying == true -> {
                val screen = playerScreen!!
                if (screen.prepareForPictureInPicture()) {
                    restore = { screen.restoreFromPictureInPicture() }
                    true
                } else false
            }
            twitchPlayerScreen?.isPlayingForPictureInPicture() == true -> {
                val screen = twitchPlayerScreen!!
                if (screen.prepareForPictureInPicture()) {
                    restore = { screen.restoreFromPictureInPicture() }
                    true
                } else false
            }
            twitchLivePlayerScreen?.isPlayingForPictureInPicture() == true -> {
                val screen = twitchLivePlayerScreen!!
                if (screen.prepareForPictureInPicture()) {
                    restore = { screen.restoreFromPictureInPicture() }
                    true
                } else false
            }
            else -> false
        }
        if (!prepared) return

        val serviceIntent = Intent(this, PlaybackKeepAliveService::class.java)
        startForegroundService(serviceIntent)

        val entered = runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }.getOrDefault(false)

        if (!entered) {
            restore?.invoke()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!isInPictureInPictureMode) {
            playerScreen?.restoreFromPictureInPicture()
            twitchPlayerScreen?.restoreFromPictureInPicture()
            twitchLivePlayerScreen?.restoreFromPictureInPicture()

            val anyPlaying =
                playerScreen?.player?.isPlaying == true ||
                    twitchPlayerScreen?.isPlayingForPictureInPicture() == true ||
                    twitchLivePlayerScreen?.isPlayingForPictureInPicture() == true
            if (!anyPlaying) {
                stopService(Intent(this, PlaybackKeepAliveService::class.java))
            }
        }
    }

    private fun registerSearchBackInterceptor() {
        if (Build.VERSION.SDK_INT < 33 || searchSystemBackCallback != null) return

        val callback = android.window.OnBackInvokedCallback {
            if (!feedSearchOpen) return@OnBackInvokedCallback

            val input = activeSearchInput
            val imeVisible = input?.let {
                ViewCompat.getRootWindowInsets(it)
                    ?.isVisible(WindowInsetsCompat.Type.ime())
            } == true

            if (imeVisible && input != null) {
                (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                    ?.hideSoftInputFromWindow(input.windowToken, 0)
                input.clearFocus()
            } else {
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
            FrameLayout.LayoutParams(dp(84), dp(84), Gravity.CENTER)
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
                if (telegramChannelHub == null) {
                    telegramChannelHub = TelegramChannelHub(client)
                }

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
                refreshTelegramChannels(silent = true)
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
        if (inPlace && visibleTelegram && !quiet) setFeedRefreshLoading(true)
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
                        if (!quiet) setFeedRefreshLoading(false)
                    } else {
                        currentDay = null
                        feedRefreshCompletedFlash = inPlace && !quiet
                        if (inPlace) {
                            suppressNextRootAnimation = true
                            suppressNextContentAnimation = true
                            if (quiet) rememberFeedScrollPosition()
                        }
                        showFeed(preparedVideos)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (settings.videoSource != "telegram") return@withContext
                    if (inPlace) {
                        if (!quiet) setFeedRefreshLoading(false)
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

    private fun scheduleYouTubeFeedAutoRefresh(delayMs: Long = 45_000L) {
        if (youtubeFeedAutoRefreshJob?.isActive == true) return

        youtubeFeedAutoRefreshJob = lifecycleScope.launch {
            if (delayMs > 0L) delay(delayMs)
            while (isActive) {
                if (
                    videoSection != 2 ||
                    auxiliaryScreen != null ||
                    isPlayerScreen ||
                    isSettingsScreen ||
                    isAccountScreen ||
                    isStreakScreen
                ) {
                    delay(1_000L)
                    continue
                }

                loadYouTubeFeed(force = true, quiet = true)
                while (isActive && youtubeFeedJob?.isActive == true) {
                    delay(250L)
                }
                delay(60_000L)
            }
        }
    }

    private fun rememberFeedScrollPosition() {
        root.findViewWithTag<NestedScrollView>("sohr_feed_scroll")
            ?.scrollY
            ?.let { pendingFeedScrollRestoreY = it }
    }

    private fun restoreFeedScrollPosition(scroll: NestedScrollView) {
        val requested = pendingFeedScrollRestoreY ?: return
        pendingFeedScrollRestoreY = null
        scroll.post {
            val child = scroll.getChildAt(0)
            val maxScroll = ((child?.height ?: 0) - scroll.height).coerceAtLeast(0)
            scroll.scrollTo(0, requested.coerceIn(0, maxScroll))
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
                    chatId = message.chatId,
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
                    chatId = message.chatId,
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
                    chatId = message.chatId,
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
        if (section !in 1..3 || videoSection == section) return

        if (videoSectionSwitchLocked) {
            pendingVideoSectionTarget = section
            return
        }

        videoSectionSwitchLocked = true
        val previousSection = videoSection
        val direction = if (section > previousSection) 1 else -1

        fun commitSectionChange() {
            pendingVideoSectionCrossfade = true
            pendingVideoSectionDirection = direction
            pendingRootSlide = 0
            suppressNextRootAnimation = true
            suppressNextContentAnimation = false
            videoSection = section
            showFeed(currentVideos)

            root.postDelayed({
                videoSectionSwitchLocked = false
                val queued = pendingVideoSectionTarget
                pendingVideoSectionTarget = null
                if (queued != null && queued != videoSection) {
                    switchVideoSection(queued)
                }
            }, if (settings.animations) 230L else 16L)
        }

        val tabs = root.findViewWithTag<FrameLayout>("sohr_video_section_tabs")
        val indicator = root.findViewWithTag<View>("sohr_video_tab_indicator")
        val fromLabel = root.findViewWithTag<TextView>("sohr_video_section_label_$previousSection")
        val toLabel = root.findViewWithTag<TextView>("sohr_video_section_label_$section")

        if (
            settings.animations &&
            tabs != null &&
            indicator != null &&
            tabs.width > 0
        ) {
            val usable = tabs.width - tabs.paddingLeft - tabs.paddingRight
            val slot = (usable / 3f).coerceAtLeast(0f)
            val target = slot * (section - 1)

            indicator.animate().cancel()
            indicator.animate()
                .translationX(target)
                .setDuration(190L)
                .setInterpolator(SohrMotion.smooth())
                .start()

            val colorAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 180L
                interpolator = SohrMotion.smooth()
                addUpdateListener { animator ->
                    val progress = animator.animatedFraction
                    val fromColor = android.graphics.ColorUtils.blendARGB(Color.WHITE, muted, progress)
                    val toColor = android.graphics.ColorUtils.blendARGB(muted, Color.WHITE, progress)
                    fromLabel?.setTextColor(fromColor)
                    toLabel?.setTextColor(toColor)
                }
            }
            colorAnimator.start()

            root.postDelayed({ commitSectionChange() }, 170L)
        } else {
            commitSectionChange()
        }
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
            tag = "sohr_feed_scroll"
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
                    2 -> "YouTube • каналы и новые видео"
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
            tag = "sohr_video_section_tabs"
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = roundedBg(palette.surfaceAlt, 18)
            clipChildren = true
            clipToPadding = true
        }
        val tabIndicator = View(this).apply {
            tag = "sohr_video_tab_indicator"
            background = roundedBg(purple, 15)
            elevation = 0f
        }
        val tabButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            elevation = dp(2).toFloat()
        }

        telegramUnreadBadgeView = null
        listOf("Главная" to 1, "Лента" to 2, "Просмотренные" to 3).forEach { (label, section) ->
            val selected = videoSection == section
            val tab = FrameLayout(this).apply {
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

            val labelView = TextView(this).apply {
                tag = "sohr_video_section_label_$section"
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (selected) Color.WHITE else muted)
                includeFontPadding = false
                isClickable = false
                isFocusable = false
            }

            val labelRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                isClickable = false
                isFocusable = false
                addView(
                    labelView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }

            if (section == 2 && false) {
                val badge = TextView(this).apply {
                    textSize = 9.8f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    includeFontPadding = false
                    minWidth = dp(20)
                    setPadding(dp(6), 0, dp(6), 0)
                    setTextColor(Color.WHITE)
                    background = roundedBg(
                        if (selected) Color.argb(58, 255, 255, 255) else purple,
                        10
                    )
                    visibility = View.GONE
                    scaleX = 0.84f
                    scaleY = 0.84f
                    alpha = 0f
                }
                telegramUnreadBadgeView = badge
                labelRow.addView(
                    badge,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(20)
                    ).apply {
                        marginStart = dp(6)
                    }
                )
            }

            tab.addView(
                labelRow,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
            tabButtons.addView(tab, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        updateTelegramUnreadBadge(animated = false)

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
            tabIndicator.animate().cancel()
            tabIndicator.translationX = slot * (videoSection - 1)
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
        activeSearchInput = searchInput

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
            tag = "sohr_video_section_content"
        }
        body.addView(contentHost)

        if (videoSection == 2) {
            searchButton.visibility = View.GONE
            contentHost.addView(
                YouTubeFeedUi.buildDirectory(
                    activity = this,
                    settings = settings,
                    channels = youtubeChannels,
                    videos = youtubeFeedVideos,
                    loading = youtubeFeedLoading,
                    onOpenChannel = { channel, _ -> openYouTubeChannelScreen(channel) },
                    onOpenVideo = { video, _ -> openYouTubeVideo(video) },
                    onRefresh = { loadYouTubeFeed(force = true) }
                )
            )

            replaceRoot(withBottomNav(page, SohrTab.VIDEOS))
            restoreFeedScrollPosition(scroll)
            root.postDelayed({ consumeUpdateNotificationIntent() }, 220L)

            val youtubeNow = System.currentTimeMillis()
            val youtubeHasAnyContent =
                youtubeChannels.isNotEmpty() || youtubeFeedVideos.isNotEmpty()

            if (
                !youtubeFeedLoading &&
                !youtubeHasAnyContent &&
                (
                    !youtubeFeedInitialAttempted ||
                        youtubeNow - lastYoutubeFeedAttemptAt >= youtubeFeedRetryCooldownMs
                )
            ) {
                loadYouTubeFeed(force = false)
            } else if (
                !youtubeFeedLoading &&
                youtubeHasAnyContent &&
                youtubeNow - lastYoutubeFeedRefreshAt > 45_000L
            ) {
                loadYouTubeFeed(force = true, quiet = true)
            }
            scheduleYouTubeFeedAutoRefresh()
            return
        }

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
                        settings.animations && pendingRootSlide >= 0
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
        restoreFeedScrollPosition(scroll)
        root.postDelayed({ consumeUpdateNotificationIntent() }, 220L)
    }

    private fun consumeUpdateNotificationIntent() {
        if (!openUpdateFromNotification) return
        openUpdateFromNotification = false
        intent?.removeExtra(SohrBackgroundCheckWorker.EXTRA_OPEN_UPDATE)
        checkForUpdates(manual = true)
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
                animationsEnabled = settings.animations && pendingRootSlide >= 0,
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
        todayLiveExpandAnimator?.cancel()
        todayLiveExpanded = false

        val hour = java.time.LocalTime.now().hour
        val greeting = when (hour) {
            in 5..11 -> "Доброе утро"
            in 12..17 -> "Добрый день"
            in 18..23 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
        val streak = streakTracker.currentStreak()

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(palette.accentSoft, palette.surface)
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), palette.stroke)
            }
            elevation = dp(2).toFloat()
            clipChildren = true
            clipToPadding = true
            isClickable = true
            isFocusable = true
            setOnClickListener {
                SohrHaptics.select(this)
                setTodayLiveExpanded(!todayLiveExpanded)
            }
        }
        todayLiveCard = card

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(17), dp(14), dp(14), dp(14))
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
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.05f)
            setTextColor(muted)
            setPadding(0, dp(6), 0, 0)
        }
        copy.addView(summary)
        todaySummaryView = summary

        topRow.addView(
            copy,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val livePill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(7), 0, dp(7), 0)
            background = roundedBg(palette.surfaceAlt, 15)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                SohrHaptics.select(this)
                setTodayLiveExpanded(!todayLiveExpanded)
            }
        }

        val liveDot = LivePulseView(this).apply {
            setLiveColor(t2x2LiveAccent)
            setState(lastT2x2Live != null, settings.animations)
        }
        val liveText = TextView(this).apply {
            text = "T2x2 • не в сети"
            textSize = 10.8f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(muted)
        }
        val expandIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron_right)
            imageTintList = android.content.res.ColorStateList.valueOf(muted)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            rotation = 90f
            contentDescription = "Показать детали эфира"
        }

        livePill.addView(liveDot, LinearLayout.LayoutParams(dp(20), dp(20)).apply {
            marginEnd = dp(4)
        })
        livePill.addView(liveText)
        livePill.addView(expandIcon, LinearLayout.LayoutParams(dp(18), dp(18)).apply {
            marginStart = dp(3)
        })
        topRow.addView(
            livePill,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34))
        )

        card.addView(
            topRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(108))
        )

        val detailsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            alpha = 0f
            translationY = -dp(8).toFloat()
            setPadding(dp(17), 0, dp(17), dp(17))
            isClickable = true
            setOnClickListener {
                if (todayLiveExpanded) setTodayLiveExpanded(false)
            }
        }
        card.addView(
            detailsPanel,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        todayLiveDot = liveDot
        todayLiveStatusView = liveText
        todayLiveExpandIcon = expandIcon
        todayLiveDetailsPanel = detailsPanel
        renderTodayLiveExpandedPanel(detailsPanel, lastT2x2Live, lastT2x2LiveUnavailable)
        updateTodayLiveSummary(lastT2x2Live, lastT2x2LiveUnavailable)

        val wrapper = FrameLayout(this)
        wrapper.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(108)
            ).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
                topMargin = dp(8)
                bottomMargin = dp(4)
            }
        )
        return wrapper
    }

    private fun setTodayLiveExpanded(expanded: Boolean, animated: Boolean = true) {
        val card = todayLiveCard ?: return
        val details = todayLiveDetailsPanel ?: return
        val icon = todayLiveExpandIcon
        if (todayLiveExpanded == expanded && todayLiveExpandAnimator == null) return

        todayLiveExpandAnimator?.cancel()
        todayLiveExpandAnimator = null
        todayLiveExpanded = expanded

        if (expanded) {
            renderTodayLiveExpandedPanel(details, lastT2x2Live, lastT2x2LiveUnavailable)
            details.visibility = View.VISIBLE
        }

        val collapsedHeight = dp(108)
        details.measure(
            View.MeasureSpec.makeMeasureSpec(card.width.coerceAtLeast(dp(280)), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val expandedHeight = (collapsedHeight + details.measuredHeight + dp(2))
            .coerceAtLeast(dp(246))

        val currentHeight = card.layoutParams.height
            .takeIf { it > 0 }
            ?: if (expanded) collapsedHeight else expandedHeight
        val targetHeight = if (expanded) expandedHeight else collapsedHeight

        icon?.animate()?.cancel()
        icon?.animate()
            ?.rotation(if (expanded) -90f else 90f)
            ?.setDuration(if (settings.animations && animated) SohrMotion.NORMAL else 0L)
            ?.setInterpolator(SohrMotion.smooth())
            ?.start()

        if (!settings.animations || !animated) {
            card.layoutParams = card.layoutParams.apply { height = targetHeight }
            card.requestLayout()
            details.alpha = if (expanded) 1f else 0f
            details.translationY = 0f
            details.visibility = if (expanded) View.VISIBLE else View.GONE
            return
        }

        if (expanded) {
            details.alpha = 0f
            details.translationY = -dp(5).toFloat()
            details.animate().cancel()
            details.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(35L)
                .setDuration(190L)
                .setInterpolator(SohrMotion.smooth())
                .withStartAction {
                    animateTodayLiveExpandedChildren(details)
                }
                .start()
        } else {
            details.animate().cancel()
            details.animate()
                .alpha(0f)
                .translationY(-dp(6).toFloat())
                .setDuration(SohrMotion.FAST)
                .setInterpolator(SohrMotion.exit())
                .start()
        }

        val animator = android.animation.ValueAnimator.ofInt(currentHeight, targetHeight).apply {
            duration = 360L
            interpolator = SohrMotion.smooth()
            addUpdateListener { value ->
                card.layoutParams = card.layoutParams.apply {
                    height = value.animatedValue as Int
                }
                card.requestLayout()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!todayLiveExpanded) {
                        details.visibility = View.GONE
                        details.translationY = -dp(8).toFloat()
                    }
                    todayLiveExpandAnimator = null
                }
            })
        }
        todayLiveExpandAnimator = animator
        animator.start()
    }

    private fun formatT2x2Clock(epochMs: Long): String =
        java.time.Instant.ofEpochMilli(epochMs)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalTime()
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

    private fun formatT2x2SegmentRange(
        segment: TwitchCategorySegment,
        live: TwitchLiveStream?
    ): String {
        val start = formatT2x2Clock(segment.startedAtMs)
        val end = segment.endedAtMs?.let(::formatT2x2Clock)
            ?: if (live != null) "сейчас" else "—"
        return "$start → $end"
    }

    private fun openGameSearch(gameName: String) {
        if (gameName.isBlank()) return
        val uri = Uri.Builder()
            .scheme("https")
            .authority("www.google.com")
            .appendPath("search")
            .appendQueryParameter("q", "$gameName игра")
            .build()
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }.onFailure {
            Toast.makeText(this, "Не удалось открыть поиск", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderTodayLiveExpandedPanel(
        panel: LinearLayout,
        live: TwitchLiveStream?,
        unavailable: Boolean
    ) {
        panel.removeAllViews()

        val divider = View(this).apply {
            setBackgroundColor(palette.stroke)
            alpha = 0.48f
        }
        panel.addView(
            divider,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                bottomMargin = dp(15)
            }
        )

        val timeline = if (live != null) {
            lastT2x2Timeline
                ?.takeIf { it.streamStartedAt == live.startedAt }
                ?: twitchCategoryTracker.observe(live).also { lastT2x2Timeline = it }
        } else {
            lastT2x2Timeline ?: twitchCategoryTracker.timeline().also {
                lastT2x2Timeline = it
            }
        }

        val eyebrow = TextView(this).apply {
            text = when {
                live != null -> "СЕЙЧАС В ЭФИРЕ"
                unavailable -> "TWITCH"
                else -> "ПОСЛЕДНИЙ ЭФИР"
            }
            textSize = 9.6f
            includeFontPadding = false
            letterSpacing = 0.08f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (live != null) purple else muted)
        }
        panel.addView(eyebrow)

        val headline = TextView(this).apply {
            text = when {
                live != null -> live.title.ifBlank { "Эфир T2x2" }
                unavailable -> "Статус временно недоступен"
                else -> "T2x2 сейчас не в сети"
            }
            textSize = 17f
            includeFontPadding = false
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(0, dp(5), 0, 0)
        }
        panel.addView(headline)

        val meta = TextView(this).apply {
            text = if (live != null) {
                val started = runCatching {
                    java.time.Instant.parse(live.startedAt)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalTime()
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                }.getOrNull()
                buildString {
                    append(formatLiveDuration(live.startedAt))
                    append(" в эфире")
                    if (live.viewerCount > 0) {
                        append("  •  ")
                        append(formatViewerCountCompact(live.viewerCount))
                        append(" зр.")
                    }
                    if (!started.isNullOrBlank()) {
                        append("  •  с ")
                        append(started)
                    }
                }
            } else if (timeline.recent.isNotEmpty()) {
                "История последнего эфира"
            } else {
                "История появится после следующего эфира"
            }
            textSize = 11.4f
            includeFontPadding = false
            setTextColor(muted)
            setPadding(0, dp(6), 0, dp(15))
        }
        panel.addView(meta)

        val recent = timeline.recent.take(4)
        if (recent.isNotEmpty()) {
            panel.addView(TextView(this).apply {
                text = "ИСТОРИЯ ЭФИРА"
                textSize = 9.6f
                includeFontPadding = false
                letterSpacing = 0.07f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(muted)
                setPadding(0, 0, 0, dp(6))
            })

            recent.forEachIndexed { index, segment ->
                val active = live != null && segment.endedAtMs == null

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(48)
                    tag = "t2x2_timeline_row"
                }

                val rail = FrameLayout(this)
                val lineParams = when {
                    recent.size <= 1 -> null
                    index == 0 -> FrameLayout.LayoutParams(
                        dp(1), dp(24), Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    ).apply { topMargin = dp(24) }
                    index == recent.lastIndex -> FrameLayout.LayoutParams(
                        dp(1), dp(24), Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    )
                    else -> FrameLayout.LayoutParams(
                        dp(1), dp(48), Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    )
                }
                if (lineParams != null) {
                    rail.addView(
                        View(this).apply {
                            setBackgroundColor(palette.stroke)
                            alpha = 0.55f
                        },
                        lineParams
                    )
                }
                rail.addView(
                    View(this).apply {
                        background = roundedBg(
                            if (active) purple else palette.stroke,
                            99
                        )
                    },
                    FrameLayout.LayoutParams(
                        dp(if (active) 7 else 6),
                        dp(if (active) 7 else 6),
                        Gravity.CENTER
                    )
                )
                row.addView(rail, LinearLayout.LayoutParams(dp(20), dp(48)).apply {
                    marginEnd = dp(6)
                })

                val copy = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val gameLink = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Найти ${segment.gameName} в браузере"
                    setOnClickListener {
                        SohrHaptics.tap(this)
                        animatePress(this)
                        openGameSearch(segment.gameName)
                    }
                }
                gameLink.addView(
                    TextView(this).apply {
                        text = segment.gameName
                        textSize = 12.4f
                        includeFontPadding = false
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setTypeface(typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
                        setTextColor(this@MainActivity.text)
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                gameLink.addView(
                    ImageView(this).apply {
                        setImageResource(R.drawable.ic_action_open)
                        imageTintList = android.content.res.ColorStateList.valueOf(
                            if (active) purple else muted
                        )
                        background = roundedBg(
                            if (active) palette.accentSoft else palette.surfaceAlt,
                            9
                        )
                        setPadding(dp(5), dp(5), dp(5), dp(5))
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                    },
                    LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                        marginStart = dp(7)
                    }
                )
                copy.addView(
                    gameLink,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                copy.addView(
                    TextView(this).apply {
                        text = formatT2x2SegmentRange(segment, live)
                        textSize = 10.1f
                        includeFontPadding = false
                        setTextColor(if (active) purple else muted)
                        setPadding(0, dp(3), 0, 0)
                    }
                )

                row.addView(
                    copy,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )

                row.addView(
                    TextView(this).apply {
                        text = formatCategoryDuration(segment.elapsedMs())
                        textSize = 10.1f
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(if (active) purple else muted)
                        background = roundedBg(
                            if (active) palette.accentSoft else palette.surfaceAlt,
                            11
                        )
                        setPadding(dp(8), 0, dp(8), 0)
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(25)
                    ).apply {
                        marginStart = dp(8)
                    }
                )

                panel.addView(
                    row,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(48)
                    )
                )
            }
        }

        val twitchButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = roundedBg(palette.accentSoft, 17)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                SohrHaptics.tap(this)
                animatePress(this)
                openT2x2OnTwitch()
            }
        }
        twitchButton.addView(
            TextView(this).apply {
                text = if (live != null) "Перейти на эфир" else "Открыть T2x2 на Twitch"
                textSize = 12.8f
                includeFontPadding = false
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(purple)
            }
        )
        twitchButton.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_action_open)
                imageTintList = android.content.res.ColorStateList.valueOf(purple)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            },
            LinearLayout.LayoutParams(dp(16), dp(16)).apply {
                marginStart = dp(7)
            }
        )

        panel.addView(
            twitchButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
            ).apply {
                topMargin = dp(14)
            }
        )
    }

    private fun animateTodayLiveExpandedChildren(panel: LinearLayout) {
        if (!settings.animations) return
        var delay = 35L
        for (index in 1 until panel.childCount) {
            val child = panel.getChildAt(index)
            child.animate().cancel()
            child.alpha = 0f
            child.translationY = dp(7).toFloat()
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(190L)
                .setInterpolator(SohrMotion.smooth())
                .start()
            delay = (delay + if (child.tag == "t2x2_timeline_row") 28L else 18L)
                .coerceAtMost(155L)
        }
    }

    private fun updateTodayLiveSummary(
        live: TwitchLiveStream?,
        unavailable: Boolean = false
    ) {
        val label = todayLiveStatusView ?: return
        val dot = todayLiveDot
        val isLive = live != null
        dot?.setLiveColor(t2x2LiveAccent)
        dot?.setState(isLive, settings.animations)
        todayLiveDetailsPanel?.let { details ->
            renderTodayLiveExpandedPanel(details, live, unavailable)
            if (todayLiveExpanded) {
                details.post {
                    val card = todayLiveCard ?: return@post
                    details.measure(
                        View.MeasureSpec.makeMeasureSpec(
                            card.width.coerceAtLeast(dp(280)),
                            View.MeasureSpec.EXACTLY
                        ),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                    card.layoutParams = card.layoutParams.apply {
                        height = (dp(108) + details.measuredHeight + dp(2))
                            .coerceAtLeast(dp(246))
                    }
                    card.requestLayout()
                }
            }
        }

        if (isLive && live != null) {
            val timeline = lastT2x2Timeline
                ?.takeIf { it.streamStartedAt == live.startedAt }
                ?: twitchCategoryTracker.observe(live).also { lastT2x2Timeline = it }

            label.text = "T2x2 • в сети"
            label.setTextColor(this@MainActivity.text)

            val current = timeline.current
            val previous = timeline.previous
            val firstLine = buildString {
                append(current?.gameName?.takeIf { it.isNotBlank() } ?: live.gameName.ifBlank { "Без категории" })
                current?.let {
                    append(" • ")
                    append(formatCategoryDuration(it.elapsedMs()))
                }
            }
            val secondLine = buildString {
                append("Эфир ")
                append(liveElapsedLabel(live.startedAt))
                if (previous != null) {
                    append(" • до этого ")
                    append(previous.gameName)
                    append(" ")
                    append(formatCategoryDuration(previous.elapsedMs()))
                }
            }
            todaySummaryView?.text = firstLine + "\n" + secondLine
        } else {
            label.text = "T2x2 • не в сети"
            label.setTextColor(muted)
            todaySummaryView?.text = todaySummaryView?.tag as? String ?: "SOHR"
        }
    }

    private fun formatCategoryDuration(durationMs: Long): String {
        val totalMinutes = (durationMs.coerceAtLeast(0L) / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours > 0L -> "$hours ч $minutes мин"
            totalMinutes > 0L -> "$totalMinutes мин"
            else -> "<1 мин"
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

                if (live != null) {
                    lastT2x2Timeline = twitchCategoryTracker.observe(live)
                    maybeNotifyT2x2Live(live)
                } else {
                    lastT2x2Timeline = twitchCategoryTracker.markOffline()
                }
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

    private fun updateT2x2LiveStatusView(
        status: TextView,
        live: TwitchLiveStream?,
        unavailable: Boolean
    ) {
        if (live == null) {
            status.maxLines = 2
            status.text =
                if (unavailable) "Статус временно недоступен"
                else "Не в сети"
            status.setTextColor(muted)
            return
        }

        val timeline = lastT2x2Timeline
            ?.takeIf { it.streamStartedAt == live.startedAt }
            ?: twitchCategoryTracker.observe(live).also { lastT2x2Timeline = it }

        val current = timeline.current
        val previous = timeline.previous
        status.maxLines = if (previous != null) 3 else 2
        status.text = buildString {
            append(
                current?.gameName?.takeIf { it.isNotBlank() }
                    ?: live.gameName.ifBlank { "Без категории" }
            )
            current?.let {
                append(" • ")
                append(formatCategoryDuration(it.elapsedMs()))
            }
            append("\nЭфир ")
            append(formatLiveDuration(live.startedAt))
            append(" • ")
            append(formatViewerCountCompact(live.viewerCount))
            append(" зр.")
            if (previous != null) {
                append("\nДо этого • ")
                append(previous.gameName)
                append(" • ")
                append(formatCategoryDuration(previous.elapsedMs()))
            }
        }
        status.setTextColor(muted)
    }

    private fun renderT2x2Live(
        slot: FrameLayout,
        live: TwitchLiveStream?,
        unavailable: Boolean = false
    ) {
        updateTodayLiveSummary(live, unavailable)

        val renderKey = when {
            live != null -> "live:${live.startedAt}:${live.gameId}"
            unavailable -> "offline:unavailable"
            else -> "offline"
        }

        // Polling should update content, not rebuild the whole card and make it jump.
        if (slot.tag == renderKey && slot.childCount > 0) {
            slot.findViewWithTag<LivePulseView>("t2x2_live_pulse")
                ?.setState(live != null, settings.animations)
            slot.findViewWithTag<TextView>("t2x2_live_status")
                ?.let { updateT2x2LiveStatusView(it, live, unavailable) }
            return
        }

        val animateIn = slot.childCount == 0
        slot.tag = renderKey
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
            tag = "t2x2_live_pulse"
            setLiveColor(t2x2LiveAccent)
            setState(live != null, settings.animations)
        }
        card.addView(
            pulse,
            LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                marginEnd = dp(9)
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
            tag = "t2x2_live_status"
            textSize = 11.2f
            includeFontPadding = false
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.03f)
            setPadding(0, dp(4), 0, 0)
        }
        updateT2x2LiveStatusView(status, live, unavailable)
        info.addView(status)

        if (live != null) {
            val ticker = object : Runnable {
                override fun run() {
                    if (!slot.isAttachedToWindow || card.parent !== slot) return
                    updateT2x2LiveStatusView(
                        status,
                        lastT2x2Live,
                        lastT2x2LiveUnavailable
                    )
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
            LinearLayout.LayoutParams(dp(72), dp(34)).apply {
                marginStart = dp(9)
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
                dp(94),
                Gravity.CENTER
            )
        )

        if (animateIn && settings.animations) {
            card.alpha = 0f
            card.translationY = dp(3).toFloat()
            card.scaleX = 0.996f
            card.scaleY = 0.996f
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180L)
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
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Уведомляет, когда T2x2 начинает трансляцию"
                    enableVibration(true)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    UPDATE_NOTIFICATION_CHANNEL,
                    "Обновления SOHR",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Уведомляет о новых версиях SOHR"
                    enableVibration(false)
                }
            )
        }

        SohrBackgroundCheckWorker.schedule(this)
        SohrNotificationWatchService.start(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val prefs = getSharedPreferences("sohr_runtime", MODE_PRIVATE)
            val alreadyRequested =
                prefs.getBoolean("notification_permission_requested", false) ||
                    prefs.getBoolean("t2x2_notification_permission_requested", false)
            if (!alreadyRequested) {
                prefs.edit()
                    .putBoolean("notification_permission_requested", true)
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
            .setContentTitle("T2x2 в эфире")
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
            .setPriority(Notification.PRIORITY_HIGH)
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
            
            // Check immediately when SOHR becomes active.
            delay(0L)

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
                        if (live != null) {
                            lastT2x2Timeline = twitchCategoryTracker.observe(live)
                            maybeNotifyT2x2Live(live)
                        } else {
                            lastT2x2Timeline = twitchCategoryTracker.markOffline()
                        }
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

                delay(4_000L)
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

    private fun prefetchNextCandidates(
        server: TelegramStreamServer?,
        candidates: List<VideoItem>
    ) {
        if (server == null || candidates.isEmpty()) return
        val connectivity = getSystemService(android.net.ConnectivityManager::class.java)
        val metered = connectivity?.isActiveNetworkMetered == true
        val limit = if (metered) 1 else 3
        val maxBytes = if (metered) {
            5L * 1024L * 1024L
        } else {
            14L * 1024L * 1024L
        }

        candidates
            .asSequence()
            .filter { it.source != "twitch" }
            .filter { it.localPath.isNullOrBlank() && it.fileId > 0 && it.fileSize > 0L }
            .take(limit)
            .forEachIndexed { index, candidate ->
                val fraction =
                    if (index == 0) 0.18f
                    else 0.10f
                server.prefetchFraction(
                    item = candidate,
                    fraction = fraction,
                    maxBytes = if (index == 0) maxBytes else maxBytes / 2L
                )
            }
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
        prefetchNextCandidates(
            server = server,
            candidates = (queueItems + orderedForPlayback.drop((currentIndex + 1).coerceAtLeast(0)))
                .filterNot { it.messageId == item.messageId }
                .distinctBy { it.messageId }
        )
        playerReturnView = capturePlayerReturnView()
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
                },
                onMiniModeChanged = { enabled -> handlePlayerMiniMode(enabled) }
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

        playerReturnView = capturePlayerReturnView()
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
                    twitchVideoId = videoId,
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
                    },
                    onMiniModeChanged = { enabled -> handlePlayerMiniMode(enabled) }
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

    private fun loadYouTubeFeed(
        force: Boolean = false,
        quiet: Boolean = false
    ) {
        if (youtubeFeedJob?.isActive == true) return

        val hadContent = youtubeChannels.isNotEmpty() || youtubeFeedVideos.isNotEmpty()
        if (!force && hadContent) return

        val now = System.currentTimeMillis()
        if (
            !force &&
            youtubeFeedInitialAttempted &&
            !hadContent &&
            now - lastYoutubeFeedAttemptAt < youtubeFeedRetryCooldownMs
        ) {
            return
        }

        youtubeFeedInitialAttempted = true
        lastYoutubeFeedAttemptAt = now
        youtubeFeedLoading = !quiet && !hadContent
        if (youtubeFeedLoading && videoSection == 2 && auxiliaryScreen == null) {
            suppressNextRootAnimation = true
            suppressNextContentAnimation = true
            showFeed(currentVideos)
        }

        youtubeFeedJob = lifecycleScope.launch {
            val snapshot = try {
                kotlinx.coroutines.withTimeout(10_000L) {
                    withContext(Dispatchers.IO) { YouTubeFeedRepository.loadAll() }
                }
            } catch (_: Throwable) {
                YouTubeFeedSnapshot(youtubeChannels, youtubeFeedVideos)
            } finally {
                youtubeFeedLoading = false
                youtubeFeedJob = null
                lastYoutubeFeedRefreshAt = System.currentTimeMillis()
            }

            val changed =
                snapshot.channels != youtubeChannels ||
                    snapshot.videos != youtubeFeedVideos

            youtubeChannels = snapshot.channels
            youtubeFeedVideos = snapshot.videos

            if (
                videoSection == 2 &&
                auxiliaryScreen == null &&
                !isFinishing &&
                (!hadContent || changed || youtubeChannels.isEmpty())
            ) {
                if (hadContent) rememberFeedScrollPosition()
                suppressNextRootAnimation = true
                suppressNextContentAnimation = true
                showFeed(currentVideos)
            }

            // Durations are optional metadata. Enrich a small visible slice in the
            // background after the list is already on screen, never blocking skeleton exit.
            if (
                snapshot.channels.isNotEmpty() &&
                youtubeDurationEnrichJob?.isActive != true
            ) {
                youtubeDurationEnrichJob = lifecycleScope.launch {
                    val enriched = runCatching {
                        kotlinx.coroutines.withTimeout(22_000L) {
                            YouTubeFeedRepository.enrichSnapshotDurations(snapshot)
                        }
                    }.getOrNull()

                    youtubeDurationEnrichJob = null
                    if (enriched == null || enriched == snapshot) return@launch

                    val durationChanged = enriched.videos != youtubeFeedVideos
                    youtubeChannels = enriched.channels
                    youtubeFeedVideos = enriched.videos

                    if (
                        durationChanged &&
                        videoSection == 2 &&
                        auxiliaryScreen == null &&
                        !isFinishing
                    ) {
                        rememberFeedScrollPosition()
                        suppressNextRootAnimation = true
                        suppressNextContentAnimation = true
                        showFeed(currentVideos)
                    }
                }
            }
        }
    }

    private fun openYouTubeChannelScreen(channel: YouTubeChannelSummary) {
        stopInlinePreview()
        auxiliaryScreen = "youtube_channel"
        openYouTubeChannelSummary = channel
        pendingRootSlide = 1

        val content = YouTubeFeedUi.buildChannel(
            activity = this,
            settings = settings,
            channel = channel,
            onBack = {
                auxiliaryScreen = null
                openYouTubeChannelSummary = null
                videoSection = 2
                pendingRootSlide = -1
                showFeed(currentVideos)
            },
            onOpenVideo = { video, _ -> openYouTubeVideo(video) }
        )
        replaceRoot(withBottomNav(content, SohrTab.VIDEOS))
    }

    private fun openYouTubeVideo(video: YouTubeFeedVideo) {
        stopInlinePreview()
        fullScreen = false
        val returnView = capturePlayerReturnView()
        playerReturnView = returnView

        youtubeResolveJob?.cancel()
        youtubeResolveJob = null
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

        val loadingPage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bg)

            val mark = buildBrandLoadingMark(54)
            val label = TextView(this@MainActivity).apply {
                text = "Открываем видео…"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(muted)
                setPadding(0, dp(14), 0, 0)
            }
            addView(mark, LinearLayout.LayoutParams(dp(54), dp(54)))
            addView(label)
        }

        pendingRootSlide = 1
        replaceRoot(loadingPage)

        youtubeResolveJob = lifecycleScope.launch {
            val firstResolve = runCatching {
                YouTubeNativeResolver.resolve(video)
            }
            val resolved = firstResolve.getOrElse { firstError ->
                if (
                    firstError is YouTubeNetworkException ||
                    firstError is YouTubeUnavailableException ||
                    firstError is YouTubeSessionInitException
                ) {
                    delay(320L)
                    runCatching {
                        YouTubeNativeResolver.resolve(video)
                    }.getOrElse { retryError ->
                        youtubeResolveJob = null
                        handleYouTubeNativeFailure(video, retryError, returnView)
                        return@launch
                    }
                } else {
                    youtubeResolveJob = null
                    handleYouTubeNativeFailure(video, firstError, returnView)
                    return@launch
                }
            }

            if (!isActive || !isPlayerScreen) return@launch

            val item = video.toVideoItem().copy(
                title = resolved.title,
                durationSeconds = resolved.durationSeconds,
                mimeType = "video/*",
                thumbnailUrl = resolved.thumbnailUrl
            )
            val resumePositionMs = settings.playbackPosition(item.messageId)

            val createdPlayer = runCatching {
                PlayerScreen(
                    activity = this@MainActivity,
                    item = item,
                    mediaUrl = resolved.mediaUrl,
                    secondaryAudioUrl = resolved.secondaryAudioUrl,
                    previewDataSourceFactory = null,
                    settings = settings,
                    startPositionMs = resumePositionMs,
                    onBack = { closeCurrentPlayerScreen() },
                    onFullscreen = { setFullscreen(it) },
                    onPlaybackStarted = {
                        settings.markPlayed(item.messageId)
                        streakTracker.markWatched()
                    },
                    onMiniModeChanged = { enabled -> handlePlayerMiniMode(enabled) }
                )
            }.getOrElse { error ->
                youtubeResolveJob = null
                isPlayerScreen = false
                playerReturnView = null
                pendingRootSlide = -1
                if (returnView != null) {
                    replaceRoot(returnView)
                } else {
                    videoSection = 2
                    showFeed(currentVideos)
                }
                showMessage(
                    "Не удалось открыть видео",
                    error.message ?: "Нативный SOHR-плеер не смог запустить поток."
                )
                return@launch
            }

            youtubeResolveJob = null
            if (!isPlayerScreen) {
                createdPlayer.destroy()
                return@launch
            }

            playerScreen = createdPlayer
            pendingRootSlide = 1
            replaceRoot(createdPlayer.root)
        }
    }

    private fun configureYouTubeWebView(webView: WebView) {
        webView.setBackgroundColor(Color.BLACK)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.setSupportMultipleWindows(false)
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = false
    }

    private fun defaultBrowserPackage(): String? {
        val probe = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://accounts.google.com/")
        ).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        return packageManager
            .resolveActivity(probe, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
            ?.takeIf { it.isNotBlank() && it != packageName }
    }

    private fun launchYouTubeCustomTab(url: String): Boolean {
        val colorScheme =
            if (settings.lightTheme) CustomTabsIntent.COLOR_SCHEME_LIGHT
            else CustomTabsIntent.COLOR_SCHEME_DARK

        val customTab = CustomTabsIntent.Builder()
            .setShowTitle(false)
            .setUrlBarHidingEnabled(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setToolbarColor(bg)
            .setNavigationBarColor(bg)
            .setColorScheme(colorScheme)
            .build()

        defaultBrowserPackage()?.let { customTab.intent.setPackage(it) }

        return runCatching {
            customTab.launchUrl(this, Uri.parse(url))
            true
        }.getOrElse {
            showMessage(
                "Не удалось открыть YouTube",
                "Проверьте браузер по умолчанию и попробуйте ещё раз."
            )
            false
        }
    }

    private fun youtubeAccountChooserUrl(
        targetUrl: String,
        accountName: String? = settings.youtubeAccountName
    ): String {
        val encodedTarget = Uri.encode(targetUrl)
        val emailHint = accountName
            ?.takeIf { it.isNotBlank() }
            ?.let { "&Email=" + Uri.encode(it) }
            .orEmpty()
        return "https://accounts.google.com/AccountChooser" +
            "?service=youtube&continue=" + encodedTarget + emailHint
    }

    private fun launchYouTubeAccountPicker() {
        val chooser = AccountManager.newChooseAccountIntent(
            null,
            null,
            arrayOf("com.google"),
            "Выберите Google-аккаунт для YouTube",
            null,
            null,
            null
        )

        runCatching {
            startActivityForResult(chooser, YOUTUBE_ACCOUNT_PICK_REQUEST)
        }.getOrElse {
            // Devices without a compatible account picker still get the browser chooser.
            if (launchYouTubeCustomTab(youtubeAccountChooserUrl("https://m.youtube.com/"))) {
                settings.youtubeBrowserConnected = true
            }
        }
    }

    private fun openYouTubeAccountRequired(
        video: YouTubeFeedVideo,
        returnView: View?,
        chooseAccount: Boolean
    ) {
        youtubeResolveJob?.cancel()
        youtubeResolveJob = null

        isPlayerScreen = false
        playerReturnView = null
        pendingRootSlide = -1
        suppressNextContentAnimation = true

        if (returnView != null) {
            replaceRoot(returnView)
        } else {
            videoSection = 2
            showFeed(currentVideos)
        }

        val target = "https://m.youtube.com/watch?v=" + Uri.encode(video.videoId)
        val url = if (chooseAccount) youtubeAccountChooserUrl(target) else target
        if (launchYouTubeCustomTab(url)) {
            settings.youtubeBrowserConnected = true
        }
    }


    private fun openYouTubeWebPlayer(
        video: YouTubeFeedVideo,
        returnView: View?
    ) {
        youtubeResolveJob?.cancel()
        youtubeResolveJob = null
        youtubeWebPlayer?.let { old ->
            runCatching {
                old.stopLoading()
                old.loadUrl("about:blank")
                old.destroy()
            }
        }
        youtubeWebPlayer = null

        isSettingsScreen = false
        isAccountScreen = false
        isStreakScreen = false
        isPlayerScreen = true
        playerReturnView = returnView
        fullScreen = false
        setFullscreen(false)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            imageTintList = ColorStateList.valueOf(text)
            background = roundedBg(palette.surfaceAlt, 18)
            setPadding(dp(11), dp(11), dp(11), dp(11))
            contentDescription = "Назад"
            setOnClickListener { closeCurrentPlayerScreen() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))

        header.addView(
            TextView(this).apply {
                text = video.title
                textSize = 16.5f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                includeFontPadding = false
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(this@MainActivity.text)
                setPadding(dp(12), 0, dp(4), 0)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        page.addView(header)

        val webView = WebView(this)
        configureYouTubeWebView(webView)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString().orEmpty()
                return if (
                    url.startsWith("https://www.youtube.com/", ignoreCase = true) ||
                    url.startsWith("https://m.youtube.com/", ignoreCase = true) ||
                    url.startsWith("https://accounts.google.com/", ignoreCase = true)
                ) {
                    false
                } else {
                    true
                }
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val value = url.orEmpty()
                return !(
                    value.startsWith("https://www.youtube.com/", ignoreCase = true) ||
                        value.startsWith("https://m.youtube.com/", ignoreCase = true) ||
                        value.startsWith("https://accounts.google.com/", ignoreCase = true)
                    )
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
            }
        }
        youtubeWebPlayer = webView

        page.addView(
            webView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { topMargin = dp(12) }
        )

        webView.loadUrl("https://m.youtube.com/watch?v=" + Uri.encode(video.videoId))

        val webRoot = FrameLayout(this).apply {
            setBackgroundColor(bg)
            addView(
                page,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        suppressNextContentAnimation = true
        replaceRoot(webRoot)
    }

    private fun handleYouTubeNativeFailure(
        video: YouTubeFeedVideo,
        error: Throwable,
        returnView: View?
    ) {
        when (error) {
            is YouTubeAgeRestrictedException,
            is YouTubeNetworkException ->
                showYouTubePlaybackError(video, error, returnView)

            is YouTubeSignInRequiredException ->
                openYouTubeAccountRequired(
                    video = video,
                    returnView = returnView,
                    chooseAccount = !settings.youtubeBrowserConnected
                )

            else ->
                // Public video fallback stays visually inside SOHR.
                openYouTubeWebPlayer(video, returnView)
        }
    }

    private fun showYouTubePlaybackError(
        video: YouTubeFeedVideo,
        error: Throwable,
        returnView: View?
    ) {
        youtubeResolveJob = null
        isPlayerScreen = true
        playerReturnView = returnView
        pendingRootSlide = 0
        fullScreen = false
        setFullscreen(false)

        // Never replace SOHR playback with YouTube's embedded player.
        // Available videos stay on the native Media3 PlayerScreen. When YouTube
        // refuses to expose a playable stream, keep the user inside the same
        // SOHR visual language and explain the failure without external buttons.
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(18))
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            imageTintList = android.content.res.ColorStateList.valueOf(text)
            background = roundedBg(palette.surfaceAlt, 18)
            setPadding(dp(11), dp(11), dp(11), dp(11))
            contentDescription = "Назад"
            setOnClickListener { closeCurrentPlayerScreen() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))

        header.addView(
            TextView(this).apply {
                this.text = video.title
                textSize = 16.5f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                includeFontPadding = false
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(this@MainActivity.text)
                setPadding(dp(12), 0, dp(4), 0)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        page.addView(header)

        val playerCard = FrameLayout(this).apply {
            background = roundedBg(Color.BLACK, 20)
            clipToOutline = true
        }

        val message = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(22), dp(28), dp(22))
        }

        message.addView(
            TextView(this).apply {
                text = "!"
                textSize = 22f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(purple)
                background = roundedBg(panel, 22)
            },
            LinearLayout.LayoutParams(dp(54), dp(54)).apply {
                bottomMargin = dp(16)
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )

        message.addView(
            TextView(this).apply {
                text = when (error) {
                    is YouTubeAgeRestrictedException -> "Видео ограничено YouTube"
                    is YouTubeSignInRequiredException -> "YouTube требует аккаунт"
                    is YouTubeSessionInitException -> "Не удалось подготовить YouTube-сессию"
                    is YouTubeNetworkException -> "Нет связи с YouTube"
                    else -> "Видео сейчас недоступно"
                }
                textSize = 18f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                includeFontPadding = false
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        message.addView(
            TextView(this).apply {
                text = when (error) {
                    is YouTubeAgeRestrictedException ->
                        "YouTube подтвердил возрастное ограничение для этого ролика. SOHR его не обходит."
                    is YouTubeSignInRequiredException ->
                        "YouTube требует аккаунт для этого ролика. Вход откроется в системном браузере, где можно выбрать уже сохранённый Google-аккаунт."
                    is YouTubeSessionInitException ->
                        "BotGuard-модуль не успел подготовить обычную гостевую сессию даже после повторной попытки. Это технический сбой, а не подтверждённое ограничение ролика."
                    is YouTubeNetworkException ->
                        "Проверьте подключение к интернету и откройте ролик ещё раз."
                    else ->
                        "Нативный плеер SOHR не получил совместимый поток для этого ролика."
                }
                textSize = 12.5f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#B7B5C4"))
                setPadding(0, dp(9), 0, 0)
                includeFontPadding = false
                setLineSpacing(0f, 1.12f)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        if (error is YouTubeSignInRequiredException) {
            message.addView(
                TextView(this).apply {
                    text = "Подключить YouTube"
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    background = roundedBg(purple, 15)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        animatePress(this)
                        if (settings.youtubeAccountName.isNullOrBlank()) {
                            launchYouTubeAccountPicker()
                        } else {
                            openYouTubeAccountRequired(
                                video = video,
                                returnView = returnView,
                                chooseAccount = true
                            )
                        }
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(48)
                ).apply { topMargin = dp(18) }
            )
        }

        playerCard.addView(
            message,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )

        val availableWidth =
            (resources.displayMetrics.widthPixels - dp(28)).coerceAtLeast(dp(240))
        val playerHeight = (availableWidth * 9f / 16f).toInt()
        page.addView(
            playerCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                playerHeight
            ).apply { topMargin = dp(14) }
        )

        page.addView(
            TextView(this).apply {
                text = video.channelTitle
                textSize = 12.5f
                setTextColor(muted)
                includeFontPadding = false
                setPadding(dp(4), dp(10), dp(4), 0)
            }
        )

        val errorRoot = FrameLayout(this).apply {
            setBackgroundColor(bg)
            addView(
                page,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        suppressNextContentAnimation = true
        replaceRoot(errorRoot)
    }

    private fun refreshTelegramChannels(silent: Boolean = false) {
        if (!telegramReady || settings.guestMode) return
        val hub = telegramChannelHub ?: TelegramChannelHub(client).also { telegramChannelHub = it }
        if (telegramChannelRefreshJob?.isActive == true) return

        telegramChannelsLoading = true
        if (!silent && videoSection == 2 && auxiliaryScreen == null) {
            suppressNextRootAnimation = true
            showFeed(currentVideos)
        }

        telegramChannelRefreshJob = lifecycleScope.launch {
            val loaded = runCatching { hub.refreshChannels() }
                .getOrElse { emptyList() }

            telegramChannels = if (loaded.isNotEmpty()) loaded else hub.cachedChannels()
            updateTelegramUnreadBadge(animated = true)
            telegramChannelsLoading = false
            telegramChannelRefreshJob = null

            if (
                videoSection == 2 &&
                auxiliaryScreen == null &&
                !isFinishing
            ) {
                suppressNextRootAnimation = true
                showFeed(currentVideos)
            }

            lifecycleScope.launch {
                runCatching { hub.warmRecentPosts(limit = 18) }
            }
        }
    }

    private fun openTelegramChannel(channel: TelegramChannelSummary, sourceView: View? = null) {
        if (!telegramReady) return
        stopInlinePreview()
        stopTelegramChannelAudio()
        auxiliaryScreen = "telegram_channel"
        telegramChannelReturnView =
            root.takeIf { it.childCount > 0 }?.getChildAt(root.childCount - 1)
        openTelegramChannelSummary = channel
        val hub = telegramChannelHub
        openTelegramPosts = hub?.cachedPosts(channel.chatId).orEmpty()
        openTelegramUnreadAtOpen = channel.unreadCount.coerceAtLeast(0)
        openTelegramChannelScrollY =
            if (openTelegramUnreadAtOpen > 0) 0 else telegramChannelScrollPosition(channel.chatId)
        pendingRootSlide = 1

        renderTelegramChannel(
            channel = channel,
            loading = openTelegramPosts.isEmpty(),
            canLoadOlder = openTelegramPosts.isNotEmpty(),
            // Cached channel content should appear immediately. Animating every
            // message card while the root itself is transitioning creates a
            // visible stutter on entry.
            animatePosts = false
        )

        lifecycleScope.launch {
            val activeHub = telegramChannelHub ?: return@launch
            val posts = runCatching { activeHub.loadPosts(channel.chatId, limit = 32) }
                .getOrElse {
                    if (auxiliaryScreen == "telegram_channel" && openTelegramChannelSummary?.chatId == channel.chatId) {
                        showMessage("Не удалось открыть канал", it.message ?: "Ошибка Telegram")
                    }
                    return@launch
                }

            if (auxiliaryScreen != "telegram_channel" || openTelegramChannelSummary?.chatId != channel.chatId) {
                return@launch
            }

            val previousPosts = openTelegramPosts
            openTelegramPosts = posts
            activeHub.markViewed(channel.chatId, posts)
            val freshSummary = activeHub.cachedSummary(channel.chatId)?.copy(unreadCount = 0)
                ?: channel.copy(unreadCount = 0)
            openTelegramChannelSummary = freshSummary
            telegramChannels = activeHub.cachedChannels()
            updateTelegramUnreadBadge(animated = true)

            val visibleFreshTail =
                if (previousPosts.isNotEmpty() && posts.size >= previousPosts.size) {
                    posts.takeLast(previousPosts.size)
                } else {
                    posts
                }
            val visibleContentChanged =
                previousPosts.isEmpty() ||
                    previousPosts.size != visibleFreshTail.size ||
                    previousPosts.zip(visibleFreshTail).any { (oldPost, newPost) ->
                        // Rebuild only when visible content/layout actually
                        // changed. View/reaction counters update frequently and
                        // must not tear down the whole channel screen.
                        oldPost.message.id != newPost.message.id ||
                            oldPost.text != newPost.text ||
                            oldPost.kind != newPost.kind ||
                            oldPost.previewPath != newPost.previewPath ||
                            oldPost.mediaAlbumId != newPost.mediaAlbumId ||
                            oldPost.isPinned != newPost.isPinned
                    }

            if (visibleContentChanged) {
                suppressNextRootAnimation = true
                renderTelegramChannel(
                    channel = freshSummary,
                    loading = false,
                    canLoadOlder = posts.size >= 32,
                    animatePosts = false
                )
            }
        }
    }

    private fun renderTelegramChannel(
        channel: TelegramChannelSummary,
        loading: Boolean,
        canLoadOlder: Boolean,
        animatePosts: Boolean = true
    ) {
        auxiliaryScreen = "telegram_channel"
        val render = TelegramChannelUi.buildChannel(
            activity = this,
            settings = settings,
            channel = channel,
            posts = openTelegramPosts,
            loading = loading,
            canLoadOlder = canLoadOlder,
            unreadCountAtOpen = openTelegramUnreadAtOpen,
            initialScrollY = openTelegramChannelScrollY,
            animatePosts = animatePosts,
            onScrollYChanged = { y -> openTelegramChannelScrollY = y },
            onBack = { closeTelegramChannelToFeed() },
            onLoadOlder = { loadOlderTelegramChannelPosts() },
            onVideo = { post, source -> openTelegramChannelVideo(post, source) },
            onPhoto = { post, source -> openTelegramChannelPhoto(post, source) },
            onVoice = { post, source -> toggleTelegramChannelAudio(post, source) },
            onDownload = { post -> downloadTelegramChannelPost(post) }
        )
        telegramChannelRender = render
        replaceRoot(render.root)
    }

    private fun closeTelegramChannelToFeed() {
        stopTelegramChannelAudio()
        val channel = openTelegramChannelSummary
        channel?.let { saveTelegramChannelScrollPosition(it.chatId, openTelegramChannelScrollY) }
        val returnView = telegramChannelReturnView

        auxiliaryScreen = null
        openTelegramChannelSummary = null
        openTelegramPosts = emptyList()
        openTelegramUnreadAtOpen = 0
        openTelegramChannelScrollY = 0
        telegramChannelRender = null
        telegramChannelReturnView = null
        videoSection = 2
        pendingRootSlide = -1
        suppressNextRootAnimation = false

        if (returnView != null) {
            replaceRoot(returnView)
        } else {
            suppressNextRootAnimation = true
            showFeed(currentVideos)
        }
    }

    private fun telegramChannelScrollPosition(chatId: Long): Int =
        getSharedPreferences("sohr_telegram_channel_state", MODE_PRIVATE)
            .getInt("scroll_$chatId", 0)
            .coerceAtLeast(0)

    private fun saveTelegramChannelScrollPosition(chatId: Long, scrollY: Int) {
        getSharedPreferences("sohr_telegram_channel_state", MODE_PRIVATE)
            .edit()
            .putInt("scroll_$chatId", scrollY.coerceAtLeast(0))
            .apply()
    }

    private fun downloadTelegramChannelPost(post: TelegramChannelPost) {
        val fileId = post.fileId ?: return
        if (fileId <= 0) return
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "Загрузка началась", Toast.LENGTH_SHORT).show()
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val loaded = client.send(TdApi.DownloadFile(fileId, 20, 0, 0, true))
                    val source = loaded.local.path
                        .takeIf { loaded.local.isDownloadingCompleted && it.isNotBlank() }
                        ?.let(::File)
                        ?.takeIf { it.exists() }
                        ?: error("Файл не загрузился")

                    val pictures = post.mimeType.startsWith("image/", true)
                    val baseDir = getExternalFilesDir(
                        if (pictures) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
                    ) ?: filesDir
                    val dir = File(baseDir, "SOHR").apply { mkdirs() }
                    val ext = when {
                        post.mimeType.contains("jpeg", true) || post.mimeType.contains("jpg", true) -> "jpg"
                        post.mimeType.contains("png", true) -> "png"
                        post.mimeType.contains("webp", true) -> "webp"
                        post.mimeType.contains("ogg", true) -> "ogg"
                        post.mimeType.contains("mpeg", true) -> "mp3"
                        post.mimeType.contains("webm", true) -> "webm"
                        else -> "mp4"
                    }
                    val target = File(dir, "sohr_${post.message.id}.$ext")
                    source.copyTo(target, overwrite = true)
                    target
                }
            }
            result.onSuccess { file ->
                Toast.makeText(
                    this@MainActivity,
                    "Скачано • ${file.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                Toast.makeText(
                    this@MainActivity,
                    "Не удалось скачать файл",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadOlderTelegramChannelPosts() {
        val channel = openTelegramChannelSummary ?: return
        val hub = telegramChannelHub ?: return
        val oldest = openTelegramPosts.firstOrNull() ?: return

        lifecycleScope.launch {
            val older = runCatching {
                hub.loadPosts(channel.chatId, fromMessageId = oldest.message.id, limit = 60)
            }.getOrDefault(emptyList())

            if (auxiliaryScreen != "telegram_channel" || openTelegramChannelSummary?.chatId != channel.chatId) {
                return@launch
            }

            if (older.isEmpty()) return@launch
            openTelegramPosts = (older + openTelegramPosts)
                .distinctBy { it.message.id }
                .sortedWith(compareBy<TelegramChannelPost> { it.message.date }.thenBy { it.message.id })
            suppressNextRootAnimation = true
            renderTelegramChannel(
                channel,
                loading = false,
                canLoadOlder = older.size >= 60,
                animatePosts = false
            )
        }
    }

    private fun handleRealtimeTelegramChannelPost(post: TelegramChannelPost) {
        val hub = telegramChannelHub ?: return
        telegramChannels = hub.cachedChannels()
        updateTelegramUnreadBadge(animated = true)

        val channel = openTelegramChannelSummary
        if (
            auxiliaryScreen == "telegram_channel" &&
            channel != null &&
            channel.chatId == post.message.chatId
        ) {
            openTelegramPosts = (openTelegramPosts + post)
                .distinctBy { it.message.id }
                .sortedWith(compareBy<TelegramChannelPost> { it.message.date }.thenBy { it.message.id })

            val updated = hub.cachedSummary(channel.chatId)?.copy(unreadCount = 0) ?: channel.copy(unreadCount = 0)
            openTelegramChannelSummary = updated

            val previousScroll = telegramChannelRender?.scroll
            val wasNearBottom = previousScroll?.let { scroll ->
                val contentHeight = scroll.getChildAt(0)?.height ?: 0
                contentHeight - (scroll.scrollY + scroll.height) < dp(180)
            } ?: true

            suppressNextRootAnimation = true
            renderTelegramChannel(
                updated,
                loading = false,
                canLoadOlder = openTelegramPosts.size >= 60,
                animatePosts = false
            )
            if (wasNearBottom) {
                telegramChannelRender?.scroll?.post {
                    telegramChannelRender?.scroll?.smoothScrollTo(
                        0,
                        telegramChannelRender?.scroll?.getChildAt(0)?.height ?: 0
                    )
                }
            }

            lifecycleScope.launch {
                hub.markViewed(channel.chatId, listOf(post))
                telegramChannels = hub.cachedChannels()
                updateTelegramUnreadBadge(animated = true)
            }
            return
        }

        if (videoSection == 2 && auxiliaryScreen == null) {
            suppressNextRootAnimation = true
            showFeed(currentVideos)
        }
    }

    private fun telegramUnreadCount(): Int =
        telegramChannels.sumOf { it.unreadCount.coerceAtLeast(0) }

    private fun updateTelegramUnreadBadge(animated: Boolean) {
        val badge = telegramUnreadBadgeView ?: return
        val count = telegramUnreadCount()
        if (count <= 0) {
            if (badge.visibility != View.VISIBLE) return
            if (animated && settings.animations) {
                badge.animate().cancel()
                badge.animate()
                    .alpha(0f)
                    .scaleX(0.78f)
                    .scaleY(0.78f)
                    .setDuration(SohrMotion.FAST)
                    .setInterpolator(SohrMotion.smooth())
                    .withEndAction {
                        badge.visibility = View.GONE
                    }
                    .start()
            } else {
                badge.visibility = View.GONE
                badge.alpha = 0f
            }
            return
        }

        badge.text = if (count > 99) "99+" else count.toString()
        badge.visibility = View.VISIBLE
        if (animated && settings.animations) {
            badge.animate().cancel()
            badge.alpha = 0f
            badge.scaleX = 0.72f
            badge.scaleY = 0.72f
            badge.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(SohrMotion.NORMAL)
                .setInterpolator(SohrMotion.smooth())
                .start()
        } else {
            badge.alpha = 1f
            badge.scaleX = 1f
            badge.scaleY = 1f
        }
    }

    private fun openTelegramChannelVideo(post: TelegramChannelPost, source: View?) {
        val fileId = post.fileId ?: return
        if (fileId <= 0 || post.fileSize <= 0L) return
        stopTelegramChannelAudio()

        val item = VideoItem(
            messageId = post.message.id,
            chatId = post.message.chatId,
            title = post.text.ifBlank {
                if (post.kind == "video_note") "Видеосообщение" else "Видео"
            },
            date = post.message.date,
            durationSeconds = post.durationSeconds,
            fileId = fileId,
            fileSize = post.fileSize,
            mimeType = post.mimeType.ifBlank { "video/mp4" },
            thumbnailPath = post.previewPath
        )
        openPlayer(item, sourceView = source)
    }

    private fun openTelegramChannelPhoto(post: TelegramChannelPost, source: View?) {
        val path = post.previewPath ?: return
        val file = File(path)
        if (!file.exists()) return

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val frame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            alpha = 0f
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            load(file) { crossfade(false) }
            scaleX = 0.988f
            scaleY = 0.988f
        }
        frame.addView(
            image,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        var closing = false
        fun closePhoto() {
            if (closing) return
            closing = true
            SohrHaptics.tap(image)
            if (!settings.animations) {
                dialog.dismiss()
                return
            }
            frame.animate().cancel()
            image.animate().cancel()
            frame.animate()
                .alpha(0f)
                .setDuration(SohrMotion.FAST)
                .start()
            image.animate()
                .scaleX(0.988f)
                .scaleY(0.988f)
                .setDuration(SohrMotion.FAST)
                .setInterpolator(SohrMotion.smooth())
                .withEndAction { dialog.dismiss() }
                .start()
        }

        frame.setOnClickListener { closePhoto() }
        image.setOnClickListener { closePhoto() }
        dialog.setContentView(frame)
        dialog.setOnShowListener {
            if (!settings.animations) {
                frame.alpha = 1f
                image.scaleX = 1f
                image.scaleY = 1f
                return@setOnShowListener
            }
            frame.animate()
                .alpha(1f)
                .setDuration(140L)
                .start()
            image.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180L)
                .setInterpolator(SohrMotion.smooth())
                .start()
        }
        dialog.show()
    }

    private fun toggleTelegramChannelAudio(post: TelegramChannelPost, source: View?) {
        val fileId = post.fileId ?: return
        if (fileId <= 0 || post.fileSize <= 0L) return

        if (telegramAudioMessageId == post.message.id && telegramAudioPlayer != null) {
            val player = telegramAudioPlayer ?: return
            if (player.isPlaying) {
                player.pause()
                source?.alpha = 0.82f
            } else {
                player.play()
                source?.alpha = 1f
            }
            return
        }

        stopTelegramChannelAudio()
        val server = streamServer ?: return
        val item = VideoItem(
            messageId = post.message.id,
            chatId = post.message.chatId,
            title = post.text.ifBlank { if (post.kind == "voice") "Голосовое" else "Аудио" },
            date = post.message.date,
            durationSeconds = post.durationSeconds,
            fileId = fileId,
            fileSize = post.fileSize,
            mimeType = post.mimeType.ifBlank { "audio/ogg" }
        )
        server.prefetch(item)

        val player = androidx.media3.exoplayer.ExoPlayer.Builder(this).build()
        telegramAudioPlayer = player
        telegramAudioMessageId = post.message.id
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(server.url(item)))
        player.prepare()
        player.playWhenReady = true
        source?.alpha = 1f

        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    stopTelegramChannelAudio()
                }
            }
        })
    }

    private fun stopTelegramChannelAudio() {
        telegramAudioPlayer?.release()
        telegramAudioPlayer = null
        telegramAudioMessageId = 0L
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

    private fun loadTwitchVideos(inPlace: Boolean = false, quiet: Boolean = false) {
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
            if (inPlace && !quiet) feedRefreshLabel?.text = "Уже проверяем…"
            return
        }
        if (!canAttemptTwitchNetwork()) {
            if (inPlace && !quiet) {
                setFeedRefreshLoading(false, "Нет сети")
                feedRefreshButton?.postDelayed({
                    setFeedRefreshLoading(false, "Проверить новые")
                }, 1_500L)
            } else if (!inPlace) {
                showMessage(
                    "Нет подключения к Twitch",
                    "Проверьте интернет и попробуйте ещё раз."
                )
            }
            return
        }
        if (inPlace) {
            if (!quiet) setFeedRefreshLoading(true)
        } else {
            showFeedSkeleton("Загружаем Twitch…")
        }
        twitchLoadJob = lifecycleScope.launch {
            try {
                val twitchLogin = TwitchApi.validateToken(clientId, token)
                settings.twitchLogin = twitchLogin.takeIf { it.isNotBlank() }
                val videos = TwitchApi.loadArchives(clientId, token, "t2x2", 7)
                val changed = twitchVideos != videos
                twitchVideos = videos
                if (settings.videoSource != "twitch") return@launch
                currentVideos = videos
                if (inPlace && !changed) {
                    if (!quiet) setFeedRefreshLoading(false)
                } else {
                    currentDay = null
                    feedRefreshCompletedFlash = inPlace && !quiet
                    if (inPlace) {
                        suppressNextRootAnimation = true
                        suppressNextContentAnimation = true
                        if (quiet) rememberFeedScrollPosition()
                    }
                    showFeed(videos)
                }
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
                        loadTwitchVideos(inPlace = inPlace, quiet = quiet)
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
                if (inPlace && !quiet) {
                    setFeedRefreshLoading(false, if (networkFailure) "Нет сети" else "Ошибка")
                    feedRefreshButton?.postDelayed({
                        setFeedRefreshLoading(false, "Проверить новые")
                    }, 1_500L)
                } else if (!inPlace) {
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
                pendingPrimaryTabTransition = true
                suppressNextContentAnimation = false
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

                val twitchProfile = async(Dispatchers.IO) {
                    if (token.isNullOrBlank()) return@async null

                    kotlinx.coroutines.withTimeoutOrNull(5_000L) {
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
                    }
                }

                renderAccount(user, telegramAvatar.await(), twitchProfile.await())
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
            text = "Telegram, Twitch и YouTube"
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

        page.addView(TextView(this).apply {
            text = "YouTube"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(dp(2), dp(20), 0, dp(10))
        })

        val youtubeConnected = settings.youtubeBrowserConnected
        val youtubeAccount = settings.youtubeAccountName?.takeIf { it.isNotBlank() }
        val youtubeName = youtubeAccount
            ?.substringBefore('@')
            ?.replace('.', ' ')
            ?.replace('_', ' ')
            ?.trim()
            ?.split(' ')
            ?.joinToString(" ") { part ->
                part.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
                }
            }
            ?.takeIf { it.isNotBlank() }
            ?: "YouTube"

        val youtubeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(20), dp(18), dp(18))
            background = roundedBg(panel, 24)
        }

        val youtubeInitial = youtubeAccount
            ?.trim()
            ?.take(1)
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
            ?: "Y"

        val youtubeAvatarFrame = FrameLayout(this).apply {
            background = roundedBg(palette.surfaceAlt, 46)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        val youtubeAvatar = TextView(this).apply {
            text = youtubeInitial
            textSize = 34f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = roundedBg(purple, 42)
        }
        youtubeAvatarFrame.addView(
            youtubeAvatar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        youtubeCard.addView(youtubeAvatarFrame, LinearLayout.LayoutParams(dp(92), dp(92)))

        youtubeCard.addView(TextView(this).apply {
            text = youtubeName
            textSize = 21f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(0, dp(12), 0, dp(3))
        })

        youtubeCard.addView(TextView(this).apply {
            text = when {
                youtubeConnected && !youtubeAccount.isNullOrBlank() ->
                    "$youtubeAccount • YouTube"
                youtubeConnected ->
                    "Google-аккаунт выбран • YouTube"
                else ->
                    "YouTube не подключён"
            }
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(if (youtubeConnected) muted else palette.accent)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        })

        val youtubeAction = TextView(this).apply {
            text = if (youtubeConnected) "Выйти из YouTube" else "Подключить YouTube"
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = roundedBg(
                if (youtubeConnected) Color.parseColor("#DF4563") else purple,
                15
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                animatePress(this)
                if (youtubeConnected) {
                    confirmYouTubeLogout()
                } else {
                    launchYouTubeAccountPicker()
                }
            }
        }
        youtubeCard.addView(
            youtubeAction,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                topMargin = dp(16)
            }
        )
        page.addView(youtubeCard)

        val privacy = TextView(this).apply {
            text = "SOHR не видит пароль Google. Выбранный аккаунт используется только как привязка YouTube; обычные доступные ролики остаются в нативном SOHR-плеере."
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
            youtubeCard.alpha = 0f
            youtubeCard.translationY = dp(12).toFloat()
            youtubeAvatar.scaleX = 0.88f
            youtubeAvatar.scaleY = 0.88f
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
                youtubeCard.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(145L)
                    .setDuration(270L)
                    .setInterpolator(ease)
                    .start()
                youtubeAvatar.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(185L)
                    .setDuration(260L)
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
                    // Keep the navigation bar stable and animate only the page
                    // content. This makes primary navigation feel alive without
                    // moving the whole app shell.
                    pendingRootSlide =
                        if (tab.ordinal > currentPrimaryTab.ordinal) 1 else -1
                    pendingPrimaryTabTransition = true
                    suppressNextContentAnimation = false
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
        val primaryTabTransition = pendingPrimaryTabTransition
        pendingPrimaryTabTransition = false
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
        if (animateContent && slide < 0 && old != null && !primaryTabTransition) {
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
                    // Never keep two feed pages stacked during Home / Feed / Watched.
                    // The new header is already complete and its indicator starts from the
                    // previous slot, so remove the old page immediately and animate only body.
                    content.alpha = 1f
                    content.translationX = 0f
                    content.translationY = 0f
                    content.scaleX = 1f
                    content.scaleY = 1f

                    old.animate().cancel()
                    old.setLayerType(View.LAYER_TYPE_NONE, null)
                    if (old.parent === host) host.removeView(old)

                    val sectionBody = content.findViewWithTag<View>("sohr_video_section_content")
                    if (sectionBody != null) {
                        sectionBody.animate().cancel()
                        val bodyOffset =
                            dp(10).toFloat() * if (sectionDirection >= 0) 1f else -1f
                        sectionBody.alpha = 0.90f
                        sectionBody.translationX = bodyOffset
                        sectionBody.translationY = 0f
                        sectionBody.scaleX = 1f
                        sectionBody.scaleY = 1f
                        sectionBody.animate()
                            .alpha(1f)
                            .translationX(0f)
                            .setDuration(210L)
                            .setInterpolator(SohrMotion.smooth())
                            .withEndAction {
                                sectionBody.alpha = 1f
                                sectionBody.translationX = 0f
                                content.setLayerType(View.LAYER_TYPE_NONE, null)
                            }
                            .start()
                    } else {
                        content.setLayerType(View.LAYER_TYPE_NONE, null)
                    }
                } else if (primaryTabTransition) {
                    val direction = if (slide >= 0) 1f else -1f
                    val incomingOffset = dp(14).toFloat() * direction
                    val outgoingOffset = -dp(8).toFloat() * direction

                    content.alpha = 0f
                    content.translationX = incomingOffset
                    content.translationY = dp(3).toFloat()
                    content.scaleX = 0.995f
                    content.scaleY = 0.995f

                    old.alpha = 1f
                    old.translationX = 0f
                    old.translationY = 0f
                    old.scaleX = 1f
                    old.scaleY = 1f

                    old.animate()
                        .alpha(0.72f)
                        .translationX(outgoingOffset)
                        .scaleX(0.998f)
                        .scaleY(0.998f)
                        .setDuration(190L)
                        .setInterpolator(SohrMotion.exit())
                        .start()

                    content.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(250L)
                        .setInterpolator(SohrMotion.smooth())
                        .withEndAction {
                            old.animate().cancel()
                            old.alpha = 1f
                            old.translationX = 0f
                            old.translationY = 0f
                            old.scaleX = 1f
                            old.scaleY = 1f
                            content.alpha = 1f
                            content.translationX = 0f
                            content.translationY = 0f
                            content.scaleX = 1f
                            content.scaleY = 1f
                            old.setLayerType(View.LAYER_TYPE_NONE, null)
                            content.setLayerType(View.LAYER_TYPE_NONE, null)
                            if (old.parent === host) host.removeView(old)
                        }
                        .start()
                } else if (slide < 0) {
                    // Real collection pop: keep the rebuilt feed fixed underneath
                    // and move only the outgoing collection. This removes the
                    // "double motion" shake on Back.
                    content.alpha = 1f
                    content.translationX = 0f
                    old.alpha = 1f
                    old.translationX = 0f
                    old.animate()
                        .alpha(0f)
                        .translationX(dp(34).toFloat())
                        .setDuration(210L)
                        .setInterpolator(telegramInterpolator)
                        .withEndAction {
                            old.alpha = 1f
                            old.translationX = 0f
                            old.setLayerType(View.LAYER_TYPE_NONE, null)
                            content.setLayerType(View.LAYER_TYPE_NONE, null)
                            if (old.parent === host) host.removeView(old)
                        }
                        .start()
                } else if (slide == 0) {
                    old.alpha = 1f
                    old.translationY = 0f
                    content.alpha = 0f
                    content.translationY = dp(6).toFloat()
                    content.scaleX = 0.997f
                    content.scaleY = 0.997f

                    old.animate()
                        .alpha(0.78f)
                        .setDuration(140L)
                        .setInterpolator(SohrMotion.exit())
                        .start()

                    content.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(210L)
                        .setInterpolator(SohrMotion.smooth())
                        .withEndAction {
                            old.animate().cancel()
                            old.alpha = 1f
                            old.translationY = 0f
                            content.alpha = 1f
                            content.translationY = 0f
                            content.scaleX = 1f
                            content.scaleY = 1f
                            old.setLayerType(View.LAYER_TYPE_NONE, null)
                            content.setLayerType(View.LAYER_TYPE_NONE, null)
                            if (old.parent === host) host.removeView(old)
                        }
                        .start()
                } else {
                    old.alpha = 1f
                    old.translationX = 0f
                    content.alpha = 0f
                    content.translationX = dp(34).toFloat()
                    content.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .setDuration(210L)
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




    private fun confirmYouTubeLogout() {
        ModernDialogs.showConfirm(
            context = this,
            palette = palette,
            title = "Выйти из YouTube?",
            message = "SOHR отключит выбранный YouTube-аккаунт. Ваш Google-аккаунт в браузере останется без изменений.",
            confirm = "Выйти",
            destructive = true
        ) {
            settings.youtubeBrowserConnected = false
            settings.youtubeAccountName = null
            suppressNextRootAnimation = true
            suppressNextContentAnimation = true
            showAccount()
            root.postDelayed({
                ModernDialogs.showNotice(
                    context = this@MainActivity,
                    palette = palette,
                    title = "YouTube отключён",
                    message = "YouTube-аккаунт отключён от SOHR.",
                    button = "Готово"
                )
            }, 180L)
        }
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
            requestedOrientation =
                if (actual) {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
            applySystemBars(actual)
        } catch (_: Throwable) {
            fullScreen = false
            requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            runCatching { playerScreen?.exitFullscreen() }
            runCatching { twitchPlayerScreen?.exitFullscreen() }
            runCatching { twitchLivePlayerScreen?.exitFullscreen() }
            runCatching { applySystemBars(false) }
        }
    }

    private fun fallbackReleaseNotes(version: String): String = when (version) {
        "6.7.2" -> listOf(
            "Исправлена обработка ошибок YouTube в нативном SOHR-плеере.",
            "Возрастные ограничения YouTube больше не показывают сырой английский текст: вместо этого открывается аккуратный экран SOHR без попыток обхода ограничения.",
            "Сетевые ошибки получили понятный экран с кнопкой Повторить, а обычные доступные ролики по-прежнему открываются через нативный Media3 PlayerScreen без WebView и iframe."
        ).joinToString(" • ")
        "6.7.1" -> listOf(
            "YouTube-видео больше не открываются через WebView или iframe: SOHR получает прямой медиапоток и передаёт его обычному нативному Media3-плееру.",
            "YouTube теперь использует тот же интерфейс PlayerScreen, что и остальные видео SOHR: собственные кнопки, таймлайн, fullscreen, жесты, скорость, точная перемотка, PiP и сохранение позиции.",
            "Экран ожидания при открытии ролика выполнен в стиле SOHR, а при недоступном прямом потоке приложение корректно возвращается в Ленту без скрытого запуска официального YouTube-плеера."
        ).joinToString(" • ")
        "6.7.0" -> listOf(
            "Twitch muted-карточка стала компактной: она больше не растягивается на весь экран, показывает короткий текст Авторские права · ещё время и отдельную кнопку Пропустить.",
            "Muted-участки на таймлайне теперь используют активный цвет темы SOHR вместо фиксированного жёлтого, сохраняя более толстое выделение для заметности.",
            "Точная перемотка переделана по YouTube-принципу: обычный drag быстро перемещает по ролику, а если увести палец вверх, включается замедленный fine scrub с фиксацией по секундам.",
            "Вкладка Лента больше не является каталогом Telegram-каналов: она переделана под YouTube-каналы WT2X2 и Берлога T2x2.",
            "SOHR сам получает данные каналов и свежие ролики, показывает аватар, название и handle, а нажатие на канал открывает отдельную страницу его видео внутри приложения.",
            "Общая YouTube-лента скрывает вероятные повторы между каналами по video ID и похожести заголовка/описания в близкий период.",
            "YouTube-видео визуально воспроизводятся через полноценный SOHR-плеер: YouTube controls скрыты, а управление отрисовывает SOHR — свой play/pause, themed-таймлайн, Осталось, fullscreen, double tap ±10, long-press 2× и точный scrub; системный Back возвращает из канала обратно в Ленту."
        ).joinToString(" • ")
        "6.6.15" -> listOf(
            "Исправлена главная причина, почему muted-участки Twitch не были видны: Twitch VOD в приложении фактически открывается через основной native PlayerScreen, и теперь логика авторских ограничений подключена именно к нему.",
            "Muted-интервалы загружаются только для видео из Twitch-раздела и используют тот же HLS master URL, который прямо сейчас воспроизводит плеер.",
            "Участки без звука выделяются заметными жёлтыми сегментами прямо на основной полосе времени с точными границами начала и конца.",
            "Если muted-участки найдены, над таймлайном появляется компактная подсказка; для Telegram и обычных видео она вообще не создаётся.",
            "При входе в участок без звука поверх видео плавно появляется карточка Звук вырезан Twitch с оставшимся временем и кнопкой Пропустить.",
            "Кнопка Пропустить переводит воспроизведение сразу за конец текущего muted-интервала; горизонтальный drag по видео по-прежнему не перематывает запись."
        ).joinToString(" • ")
        "6.6.14" -> listOf(
            "Горизонтальное ведение пальцем по самому Twitch-видео теперь полностью перехватывается SOHR: встроенная страница Twitch больше не получает drag и не может случайно менять таймкод.",
            "В обычном плеере перемотка по изображению также остаётся отключённой — менять позицию можно только через таймлайн, кнопки и двойной тап ±10 секунд.",
            "Участки Twitch VOD без звука из-за авторских прав теперь заметнее: они выделяются прямо на основной полосе времени жёлтыми сегментами с точными границами.",
            "Над таймлайном появляется короткая подсказка о жёлтых участках только когда такие интервалы действительно найдены.",
            "Во время заглушённого участка поверх видео показывается компактное уведомление с оставшимся временем и кнопкой Пропустить, которая переносит сразу за конец muted-интервала.",
            "Определение muted-сегментов по-прежнему исключает Twitch unmuted HLS и не помечает обычные участки записи как авторские ограничения."
        ).joinToString(" • ")
        "6.6.13" -> listOf(
            "Обычный горизонтальный свайп по самому видео больше не перематывает запись: перемотка доступна только через таймлайн и двойной тап ±10 секунд.",
            "То же поведение применено к Twitch VOD — случайное ведение пальцем по изображению больше не меняет таймкод.",
            "Вертикальные жесты остаются только для перехода в fullscreen и обратно; управление громкостью свайпом отсутствует.",
            "Исправлено распознавание Twitch copyright-muted HLS: unmuted-сегменты больше не попадают в зону авторских ограничений.",
            "Полоски muted-участков, всплывающее уведомление и кнопка Пропустить теперь опираются только на реальные muted-сегменты Twitch.",
            "Сохраняются фиксация лучшего качества Twitch, video-only PiP, автоматический возврат в портрет и компактное Осталось в плеере."
        ).joinToString(" • ")
        "6.6.12" -> listOf(
            "Twitch VOD теперь заранее определяет участки, где Twitch заглушил звук из-за авторских прав.",
            "Заглушенные интервалы отмечаются отдельной тонкой полосой прямо на таймлайне, поэтому их видно ещё до воспроизведения.",
            "При входе в muted-отрезок поверх видео плавно появляется компактная карточка Звук заглушён Twitch с оставшимся временем.",
            "Кнопка Пропустить переводит воспроизведение сразу за конец текущего заглушенного участка и не влияет на остальные таймкоды.",
            "Уведомление автоматически исчезает после выхода из muted-отрезка и не показывается на обычных участках записи.",
            "Логика использует реальные HLS-сегменты Twitch, а не пытается определять отсутствие звука на слух."
        ).joinToString(" • ")
        "6.6.11" -> listOf(
            "Нижняя навигация получила единый живой переход между Видео, Стрик, Настройки и Аккаунт: мягкий slide, crossfade и лёгкий scale без движения самой панели.",
            "Быстрые переключения вкладок больше не выглядят как мгновенная подмена экрана — активный индикатор и новый контент двигаются синхронно.",
            "Обновление контента внутри уже открытой вкладки теперь использует короткий fade и подъём вместо резкого пересоздания.",
            "Экран Настроек получил аккуратное каскадное появление первых блоков: заголовок, статистика, источник, язык, оформление и остальные элементы.",
            "Анимации остаются короткими и отключаются общей настройкой Анимации, поэтому интерфейс не становится тяжёлым."
        ).joinToString(" • ")
        "6.6.10" -> listOf(
            "Режим Лучшее в обычном Media3-плеере теперь действительно включает максимальный поддерживаемый битрейт вместо обычного адаптивного Auto.",
            "Auto вынесен в отдельный режим и больше не называется лучшим качеством.",
            "Twitch VOD удерживает Source/максимальный поток на протяжении просмотра и не откатывается обратно в Auto после первых секунд.",
            "Выбор Лучшее в Twitch сохраняет постоянную фиксацию максимального варианта; Auto и конкретное качество остаются ручными режимами.",
            "Окно выбора качества в горизонтальном режиме показывает только целые строки и больше не обрезает нижний пункт.",
            "Inline-плеер Twitch стал компактнее, сохранив правильное 16:9 и полноценные элементы управления."
        ).joinToString(" • ")
        "6.6.9" -> listOf(
            "Twitch VOD больше не стартует в случайном низком Auto: первые секунды SOHR контролирует доступные варианты и выбирает Source/Оригинал либо самое высокое реальное качество.",
            "Авто остаётся отдельным ручным режимом адаптивного качества, а пункт Лучшее доступное выбирает конкретный максимальный поток.",
            "Окна выбора в горизонтальном режиме больше не обрезают последнюю строку: размеры стали компактнее, нижний край получил запас и корректное скругление.",
            "Списки выбора теперь ограничиваются целыми строками и аккуратно прокручиваются вместо визуального обрубания снизу.",
            "Все исправления 6.6.8 сохранены: Осталось до конца, YouTube-подобные жесты без свайпа громкости, автопортрет после fullscreen, video-only PiP для Telegram/Twitch и Google-поиск игр T2x2."
        ).joinToString(" • ")
        "6.6.8" -> listOf(
            "В обычном SOHR-плеере и Twitch VOD теперь постоянно видно понятную метку Осталось до конца видео.",
            "Выход из полноэкранного режима автоматически возвращает телефон в портретную ориентацию, без ручного переворота.",
            "Жесты стали ближе к YouTube: горизонтальная перемотка, двойной тап ±10 секунд, удержание 2×, pinch fit/fill и свайпы fullscreen/mini.",
            "Регулировка громкости свайпом полностью удалена — звук меняется только системными кнопками телефона.",
            "Внутренний мини-плеер остаётся компактным: видео, название, Осталось, тематический progress, play/pause и закрытие.",
            "Android PiP теперь переносит именно видео для Telegram, Twitch VOD и Twitch live; уменьшенный интерфейс SOHR вместо ролика больше не используется.",
            "Название игры в истории T2x2 открывает Google-поиск по запросу название игры + игра.",
            "Иконка внешнего перехода перерисована, выровнена и помещена в отдельную компактную кнопку.",
            "Таймлайн Twitch VOD и остальные полоски прогресса используют цвет выбранной темы; красный остаётся только у LIVE-индикатора T2x2.",
            "В релиз также входят исправления 6.6.7: полный таймлайн T2x2 без обрезания, интервалы игр с–до, плавный возврат из сборников и приоритет Source-качества Twitch."
        ).joinToString(" • ")
        "6.6.7" -> listOf(
            "Раскрытая карточка T2x2 больше не обрезается снизу: высота рассчитывается по реальному содержимому без жёсткого лимита.",
            "Карточка открывается и закрывается нажатием по всей её площади, кроме самостоятельных кнопок и ссылок.",
            "Каждая игра в истории показывает понятный интервал времени начала и конца, длительность и получила отдельную ссылку на поиск игры в Twitch.",
            "Возврат из сборника получил настоящий плавный pop-переход без повторной анимации карточек ленты и подрагивания.",
            "Полоска прогресса видео теперь использует цвет выбранной темы вместо фиксированного красного.",
            "Twitch VOD при запуске предпочитает Source/Оригинал, а при его отсутствии — самое высокое доступное качество; ручной выбор качества сохранён.",
            "Telegram-видео продолжают воспроизводиться из исходного файла TDLib без перекодирования внутри SOHR."
        ).joinToString(" • ")
        "6.6.6" -> listOf(
            "Раскрытая карточка T2x2 получила полностью выровненную и более чистую композицию без тяжёлых одинаковых плашек.",
            "История эфира теперь выглядит как настоящая timeline: время слева, непрерывная линия с точками, игра по центру и компактная длительность справа.",
            "Текущий сегмент выделяется мягким акцентом, а остальные остаются спокойными и читаемыми.",
            "Верх карточки получил более ясную иерархию статуса, названия эфира и метаданных без лишней подсказки про повторное касание.",
            "Кнопка Twitch стала отдельной аккуратной CTA с иконкой внешнего перехода.",
            "Детали и строки timeline появляются лёгким каскадом, а раскрытие высоты стало мягче и ровнее."
        ).joinToString(" • ")
        "6.6.5" -> listOf(
            "Карточка T2x2 на главной теперь раскрывается прямо на месте красивой плавной анимацией вместо мгновенного перехода в Twitch.",
            "В раскрытом виде показываются название эфира, длительность, зрители, время начала и последние категории T2x2 с их временем.",
            "Стрелка состояния плавно поворачивается, контент появляется через мягкий fade/slide, а высота карточки меняется через единый SOHR motion.",
            "Повторный тап по раскрытой карточке сворачивает её обратно; отдельная кнопка Перейти на эфир открывает Twitch и не ломает состояние карточки.",
            "История категорий хранит до пяти последних сегментов для компактной ленты активности."
        ).joinToString(" • ")
        "6.6.4" -> listOf(
            "Индикатор T2x2 стал спокойнее: без неонового свечения, с матовой красной точкой, одним тонким ripple-кольцом и едва заметным breathing.",
            "Красный цвет по-прежнему используется только в live-индикаторе.",
            "Открытие Telegram-канала больше не запускает каскад анимаций карточек и локальных превью.",
            "Начальная позиция канала выставляется до показа сообщений, поэтому экран больше не прыгает сверху вниз при входе.",
            "Фоновое обновление канала не пересобирает весь экран из-за изменений просмотров или реакций.",
            "При первом открытии загружается более лёгкая порция сообщений, а старые посты остаются доступны через загрузку истории."
        ).joinToString(" • ")
        "6.6.3" -> listOf(
            "Live-индикатор T2x2 стал меньше и минималистичнее: Twitch-подобная красная точка, тонкая основная волна и мягкая вторая волна.",
            "Красный цвет остаётся только внутри live-индикатора; текст, карточки и остальные элементы сохраняют тему SOHR.",
            "Live-карточка обновляет статус на месте и больше не пересобирается при обычном polling, поэтому исчезают лишние прыжки и мерцание.",
            "Быстрые переключения между разделами больше не теряются: последний тап ставится в очередь и выполняется сразу после текущего перехода.",
            "Настройки видеоплеера защищены от повторного открытия поверх себя, а fullscreen-панель корректно удерживает управление до закрытия.",
            "Иконка Умного скачивания стала отдельной cloud-bolt и больше не похожа на обычную кнопку обновления."
        ).joinToString(" • ")
        "6.6.2" -> listOf(
            "T2x2 live-индикатор оставляет красный цвет только маленькому пульсирующему кружку и его волнам.",
            "Две live-волны получили одинаковую геометрию, ровный интервал и более плавное затухание без наложения.",
            "Панель управления видеоплеером стала ещё компактнее: уменьшены play, качество, настройки, fullscreen, таймлайн, время и отступы.",
            "Сохранены текущая категория T2x2, время в категории, длительность эфира и предыдущая категория."
        ).joinToString(" • ")
        "6.6.1" -> listOf(
            "T2x2 live-индикатор снова красный, но теперь с двумя аккуратными последовательными волнами; красным остаётся только сам индикатор.",
            "В live-карточке показываются текущая категория, время в ней, длительность эфира и предыдущая категория с её временем.",
            "Интерфейс видеоплеера стал заметно компактнее: меньше play/control-кнопки, панель качества, таймлайн и отступы.",
            "Telegram-фото показываются целиком по их реальному соотношению сторон, а полноэкранное открытие получило стабильную лёгкую анимацию.",
            "Реакции Telegram только отображаются — поставить или убрать реакцию через SOHR нельзя.",
            "Каналы прогревают последние посты в фоне, используют кэш при входе и не выполняют лишнюю повторную перерисовку."
        ).joinToString(" • ")
        "6.6.0" -> listOf(
            "Исправлены Telegram TextUrl: ссылки за текстом теперь действительно нажимаются.",
            "Канал запоминает позицию чтения; поиск, меню поста, скачивание и реальные Telegram-реакции встроены прямо в SOHR.",
            "Predictive Back возвращает готовую ленту без пересборки и рывка, а непрочитанные продолжают обновляться как в Telegram.",
            "Мини-плеер теперь остаётся поверх других экранов SOHR и разворачивается обратно без потери позиции.",
            "Автоматические главы из таймкодов добавлены в описание и на таймлайн.",
            "Prefetch заранее подготавливает до трёх следующих Telegram-видео на нормальной сети.",
            "Плеер снижает лишнюю фоновую работу во время спокойного просмотра и мгновенно возвращает полную плавность при касании.",
            "Haptics стали аккуратнее, а основные зоны нажатия увеличены до удобного размера.",
            "Фоновая проверка T2x2 ускорена примерно до 8 секунд; WorkManager остаётся резервом."
        ).joinToString(" • ")
        "6.5.1" -> listOf(
            "Фоновые уведомления T2x2 и обновлений теперь поддерживаются отдельным лёгким watch-сервисом даже когда интерфейс SOHR закрыт.",
            "WorkManager остаётся резервным каналом, а foreground-watch проверяет эфир чаще без зависимости от открытого Activity.",
            "Telegram TextUrl и другие текстовые entities отображаются как настоящие кликабельные ссылки за текстом.",
            "Возврат из Telegram-канала восстанавливает уже готовую ленту вместо полной пересборки, поэтому исчезает рывок экрана."
        ).joinToString(" • ")
        "6.5.0" -> listOf(
            "Новая аватарка SOHR.",
            "Фото и видео, отправленные одним Telegram-альбомом, собираются в одну медиагруппу.",
            "Добавлен разделитель непрочитанных и быстрый переход к последним сообщениям.",
            "Долгое нажатие по тексту копирует его, а ссылки и @упоминания остаются кликабельными.",
            "Realtime-обновления канала пересобирают медиагруппы, поэтому альбомы не распадаются на отдельные карточки."
        ).joinToString(" • ")
        "6.4.4" -> listOf(
            "Финальная полировка Telegram-ленты.",
            "Счётчик непрочитанных возле «Ленты» работает как у папок Telegram.",
            "Галочка верификации теперь стоит ровно сразу возле имени канала, а длинные названия аккуратно обрезаются.",
            "Лента доступна и при выбранном Twitch, и при Telegram.",
            "Внутри каналов есть подписчики, даты, крупные медиа, реакции, просмотры, кликабельные ссылки, email и @упоминания."
        ).joinToString(" • ")
        "6.4.3" -> listOf(
            "У вкладки «Лента» появился живой счётчик непрочитанных сообщений, как у папок в Telegram.",
            "Счётчик суммирует непрочитанные посты по каналам, обновляется сразу при новом сообщении и уменьшается после чтения.",
            "Бейдж аккуратно появляется и скрывается с SOHR-анимацией и показывает 99+ для больших значений."
        ).joinToString(" • ")
        "6.4.2" -> listOf(
            "«Лента» с Telegram-каналами теперь работает независимо от выбранного источника видео — и в режиме Twitch, и в режиме Telegram.",
            "Галочки верификации выровнены и заменены на аккуратный отдельный значок.",
            "В шапке канала показывается количество подписчиков.",
            "Посты получили разделители по датам, более крупные медиа, реакции, просмотры и время в стиле Telegram.",
            "Ссылки и @упоминания внутри текста распознаются и открываются по нажатию.",
            "Для появления постов и элементов сохранены плавные SOHR-анимации."
        ).joinToString(" • ")
        "6.4.1" -> listOf(
            "«Лента» теперь выглядит как список каналов Telegram, но в стиле SOHR.",
            "Каналы идут плоскими строками без отдельных карточек: крупная аватарка, название, верификация, время и непрочитанные.",
            "У последнего поста показывается компактное превью фото или видео прямо в строке канала.",
            "Нажатие по строке открывает историю канала внутри SOHR, а новые посты продолжают появляться через TDLib в реальном времени."
        ).joinToString(" • ")
        "6.4.0" -> listOf(
            "В «Ленте» появились Telegram-каналы @t2x2_video, @beerloga_t2x2, @t2xtwitch и @wT2x2.",
            "Аватар, название, верификация, последнее сообщение, время и непрочитанное берутся прямо из Telegram.",
            "Канал и история сообщений открываются внутри SOHR без перехода в Telegram.",
            "Новые посты появляются через TDLib UpdateNewMessage сразу после получения обновления.",
            "Поддержаны текст, фото, видео, видеосообщения, голосовые, GIF, аудио, файлы, стикеры и опросы."
        ).joinToString(" • ")
        "6.3.9" -> listOf(
            "Вернулась заметная плавная анимация перехода нижней панели без задержки переключения экрана.",
            "Синхронизация экрана больше не обрывает движение индикатора вкладки.",
            "Иконка «Умное скачивание» заменена на ровное облако со стрелкой загрузки.",
            "Иконка «Оформление» заменена на отдельную аккуратную палитру."
        ).joinToString(" • ")
        "6.3.8" -> listOf(
            "Нижняя панель снова переключает раздел сразу, без next-frame задержки.",
            "T2x2 проверяется сразу и каждые 4 секунды, пока SOHR активен.",
            "Фоновые проверки T2x2 и обновлений запускаются сразу, затем через 1, 3, 5 и 10 минут с 15-минутным системным fallback.",
            "Уведомления T2x2 и обновлений переведены на новые HIGH-каналы v2.",
            "Иконка «Умное скачивание» стала ровной стрелкой загрузки со smart-акцентом.",
            "Иконка «Оформление» перерисована в чистом стиле SOHR.",
            "Облачная загрузка стала примерно на 25% тоньше без изменения формы и непрерывной анимации."
        ).joinToString(" • ")
        "6.3.7" -> listOf(
            "Нижняя панель снова реагирует сразу: выбранная вкладка рисуется в первый кадр, экран открывается без искусственной задержки.",
            "Проверка старта эфира T2x2 запускается сразу и повторяется каждые 4 секунды, пока SOHR открыт.",
            "Проверка обновлений в активном приложении стала чаще, а фоновые уведомления получили новые HIGH-каналы.",
            "Фоновая проверка дополнительно запускается через 2 и 5 минут, затем работает по системному 15-минутному расписанию.",
            "Иконка «Умное скачивание» перерисована как ровное облако со стрелкой загрузки.",
            "Облачная loading-анимация стала примерно на 25% тоньше, сохранив непрерывное плавное движение без неона."
        ).joinToString(" • ")
        "6.3.6" -> listOf(
            "Поиск теперь первым свайпом назад скрывает клавиатуру и сохраняет запрос.",
            "Главные вкладки переключаются мягким crossfade без дёрганий всего экрана.",
            "Плеер получил более удобные элементы управления и тёмную прозрачную play/pause-кнопку.",
            "Полоса прогресса видео использует выбранный цвет оформления SOHR.",
            "Проверки обновлений и старта эфира T2x2 стали быстрее, уведомления приходят понятнее.",
            "Умное скачивание получило отдельную аккуратную иконку.",
            "Выбор темы и цвета получил плавные анимации и тактильный отклик."
        ).joinToString(" • ")
        "6.3.5" -> listOf(
            "Верхние вкладки больше не анимируют весь экран одновременно с индикатором: переключение стало спокойнее и без рывка.",
            "Контент вкладки получает только короткое мягкое проявление, а шапка остаётся визуально стабильной.",
            "Убран ambient-градиент со всего экрана плеера — цветная полоса над видео больше не должна отделяться от интерфейса.",
            "Облачная загрузка стала толще, плотнее и заметнее без неона и картинок.",
            "Движущийся сегмент облака не останавливается: он только плавно замедляется и снова ускоряется.",
            "Стартовое облако слегка увеличено до среднего хорошо читаемого размера.",
            "Исправлена устаревшая CI-проверка, из-за которой 6.3.4 не публиковалась несмотря на успешную сборку APK."
        ).joinToString(" • ")
        "6.3.4" -> listOf(
            "Верхние вкладки «Главная», «Лента» и «Просмотренные» переключаются чистым crossfade без сдвига и дёрганья всего экрана.",
            "Убран отдельный цветной зазор между шапкой и видео у Telegram- и Twitch-записей.",
            "Облачная загрузка стала плотнее и заметнее, сохранив старую форму, отсутствие неона и непрерывное движение без остановки.",
            "Добавлены фоновые уведомления о новых версиях SOHR даже когда приложение закрыто.",
            "Уведомления о старте трансляции T2x2 теперь также поддерживаются фоновой проверкой и не дублируются на один эфир.",
            "Тап по уведомлению обновления открывает SOHR и сразу запускает проверку новой версии."
        ).joinToString(" • ")
        "6.3.3" -> listOf(
            "Новая сине-голубая ава SOHR установлена отдельным launcher-ресурсом.",
            "Умное скачивание получило отдельную иконку облака с автоматизацией, не похожую на обновление.",
            "Старая облачная loading-анимация сохранена без картинок и неона, а сегмент теперь движется непрерывно без паузы на цикле.",
            "Ручные и умные Telegram-загрузки переведены на независимую очередь TDLib и больше не должны конфликтовать со стримингом.",
            "Старые cached-видео сохраняют chatId для устойчивых офлайн-загрузок.",
            "«Моменты» остаются полностью удалены; в плеере используется обычная кнопка «Скачать»."
        ).joinToString(" • ")
        "6.3.2" -> listOf(
            "Главные вкладки обновлены: «Главная», «Лента», «Просмотренные».",
            "Переходы между верхними вкладками стали плавнее и больше не накладывают два экрана.",
            "«Моменты» и SOHR Recap полностью удалены из приложения и проекта.",
            "В плеере вместо «Моментов» возвращена кнопка загрузки.",
            "Исправлен конфликт Telegram downloadFile: ручная загрузка и потоковое воспроизведение больше не отменяют друг друга.",
            "Добавлена загрузка Twitch VOD из плеера.",
            "Нижняя навигация жёстко сохраняет правильное положение при медленном скролле.",
            "Панель управления видео стала компактнее, а таймлайн визуально тоньше.",
            "Вернулась чистая облачная загрузка без картинки и неона; сегмент движется непрерывно с мягким замедлением.",
            "Умное скачивание получило отдельную иконку, а новая синяя ава установлена как launcher icon."
        ).joinToString(" • ")
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

                if (!manual) {
                    maybeNotifyUpdateAvailable(info)
                    return@launch
                }

                showSettings()
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

    private fun maybeNotifyUpdateAvailable(info: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val prefs = getSharedPreferences("sohr_runtime", MODE_PRIVATE)
        if (prefs.getInt("last_update_notified_code", -1) == info.versionCode) return

        val launchIntent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(SohrBackgroundCheckWorker.EXTRA_OPEN_UPDATE, true)
            }
            ?: return

        val pendingIntent = PendingIntent.getActivity(
            this,
            UPDATE_NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val manager = getSystemService(NotificationManager::class.java)
        val builder =
            if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(this, UPDATE_NOTIFICATION_CHANNEL)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

        val notification = builder
            .setSmallIcon(R.drawable.ic_notification_update)
            .setContentTitle("Доступно обновление SOHR " + info.versionName)
            .setContentText("Нужно обновить приложение • нажмите, чтобы продолжить")
            .setStyle(
                Notification.BigTextStyle().bigText(
                    info.notes.ifBlank {
                        "Доступна новая версия SOHR " + info.versionName + ". Нажмите, чтобы обновить."
                    }
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setPriority(Notification.PRIORITY_HIGH)
            .build()

        manager.notify(UPDATE_NOTIFICATION_ID, notification)
        prefs.edit()
            .putInt("last_update_notified_code", info.versionCode)
            .apply()
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

        val miniOverlay = playerScreen
            ?.takeIf { it.isMiniMode }
            ?.root
            ?.takeIf { it.parent === root }
        if (miniOverlay != null && view !== miniOverlay) {
            suppressNextRootAnimation = false
            (view.parent as? ViewGroup)?.removeView(view)

            val stale = (0 until root.childCount)
                .map { root.getChildAt(it) }
                .filter { it !== miniOverlay }
            stale.forEach {
                it.animate().cancel()
                root.removeView(it)
            }

            installPressAnimations(view)
            root.addView(
                view,
                0,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            playerReturnView = view
            miniOverlay.bringToFront()
            return
        }

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

    private fun handlePlayerMiniMode(enabled: Boolean) {
        val screen = playerScreen ?: return
        val playerRoot = screen.root

        if (enabled) {
            isPlayerScreen = false
            val underlay = playerReturnView
            if (underlay != null && underlay.parent !== root) {
                (underlay.parent as? ViewGroup)?.removeView(underlay)
                root.addView(
                    underlay,
                    0,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
            playerRoot.setBackgroundColor(Color.TRANSPARENT)
            playerRoot.bringToFront()
        } else {
            val underlay = (0 until root.childCount)
                .map { root.getChildAt(it) }
                .firstOrNull { it !== playerRoot }
            if (underlay != null) {
                playerReturnView = underlay
                root.removeView(underlay)
            }
            isPlayerScreen = true
            playerRoot.setBackgroundColor(bg)
            playerRoot.bringToFront()
        }
    }

    private fun capturePlayerReturnView(): View? {
        val existingPlayerRoot = playerScreen?.root
        return (root.childCount - 1 downTo 0)
            .map { root.getChildAt(it) }
            .firstOrNull { it !== existingPlayerRoot }
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
        stopTelegramChannelAudio()
        telegramChannelRefreshJob?.cancel()
        telegramChannelRefreshJob = null
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
        updateNotificationWatchJob?.cancel()
        updateNotificationWatchJob = null
        twitchAuthJob?.cancel()
        twitchAuthJob = null
        youtubeWebPlayer?.let { webView ->
            runCatching {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }
        youtubeWebPlayer = null
        streamServer?.stop()
        if (::client.isInitialized) client.close()
        super.onDestroy()
    }
    companion object {
        private const val TWITCH_REDIRECT_URI = "https://unknokable0.github.io/video-sohranenki/twitch-auth/"
        private const val T2X2_NOTIFICATION_CHANNEL = "t2x2_live_v2"
        private const val UPDATE_NOTIFICATION_CHANNEL = "sohr_updates_v2"
        private const val T2X2_NOTIFICATION_ID = 2202
        private const val UPDATE_NOTIFICATION_ID = 6304
        private const val T2X2_NOTIFICATION_PERMISSION_REQUEST = 2203
        private const val YOUTUBE_ACCOUNT_PICK_REQUEST = 6902
    }
}
