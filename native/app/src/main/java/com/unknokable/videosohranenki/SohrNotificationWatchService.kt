package com.unknokable.videosohranenki

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SohrNotificationWatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels()
        startInForeground()
        startWatchLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (watchJob?.isActive != true) startWatchLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        watchJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                WATCH_NOTIFICATION_ID,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(this, WATCH_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_update)
            .setContentTitle("SOHR")
            .setContentText("Фоновые уведомления активны")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        val type =
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            else 0
        ServiceCompat.startForeground(
            this,
            WATCH_NOTIFICATION_ID,
            notification,
            type
        )
    }

    private fun startWatchLoop() {
        watchJob?.cancel()
        watchJob = scope.launch {
            var updateTick = 0
            while (isActive) {
                checkT2x2Live()
                if (updateTick % UPDATE_EVERY_TICKS == 0) {
                    checkUpdate()
                }
                updateTick++
                delay(T2X2_CHECK_INTERVAL_MS)
            }
        }
    }

    private suspend fun checkUpdate() {
        val info = runCatching {
            SohrUpdateManager(applicationContext).check()
        }.getOrNull() ?: return

        if (!canNotify()) return

        val prefs = getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_LAST_UPDATE_NOTIFICATION, -1) == info.versionCode) return

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
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

        val notification = NotificationCompat.Builder(this, UPDATE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_update)
            .setContentTitle("Доступно обновление SOHR ${info.versionName}")
            .setContentText("Нажмите, чтобы обновить приложение")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    info.notes.ifBlank { "Доступна новая версия SOHR ${info.versionName}." }
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(UPDATE_NOTIFICATION_ID, notification)

        prefs.edit()
            .putInt(KEY_LAST_UPDATE_NOTIFICATION, info.versionCode)
            .apply()
    }

    private suspend fun checkT2x2Live() {
        val settings = AppSettings(applicationContext)
        if (settings.guestMode) return

        val clientId = BuildConfig.TWITCH_CLIENT_ID.trim()
        val token = settings.twitchAccessToken?.trim().orEmpty()
        if (clientId.isBlank() || token.isBlank()) return

        val liveResult = runCatching {
            TwitchApi.loadLiveStream(clientId, token, "t2x2")
        }
        if (liveResult.isFailure) return

        val live = liveResult.getOrNull()
        val categoryTracker = TwitchCategoryTracker(applicationContext)
        if (live == null) {
            categoryTracker.markOffline()
            return
        }
        categoryTracker.observe(live)

        if (live.startedAt.isBlank() || !canNotify()) return

        val prefs = getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_T2X2_NOTIFICATION, null) == live.startedAt) return

        val twitchIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(live.url.ifBlank { "https://www.twitch.tv/t2x2" })
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

        val pendingIntent = PendingIntent.getActivity(
            this,
            T2X2_NOTIFICATION_ID,
            twitchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, T2X2_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_live)
            .setContentTitle("T2x2 в эфире")
            .setContentText(live.title.ifBlank { "T2x2 начал трансляцию" })
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    live.title.ifBlank { "T2x2 начал трансляцию" }
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(T2X2_NOTIFICATION_ID, notification)

        prefs.edit()
            .putString(KEY_LAST_T2X2_NOTIFICATION, live.startedAt)
            .apply()
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                WATCH_CHANNEL,
                "Фоновые уведомления SOHR",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Поддерживает быструю фоновую проверку эфиров и обновлений"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                T2X2_CHANNEL,
                "Эфиры T2x2",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомляет, когда T2x2 начинает трансляцию"
                enableVibration(true)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                UPDATE_CHANNEL,
                "Обновления SOHR",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомляет о новых версиях SOHR"
                enableVibration(true)
            }
        )
    }

    companion object {
        private const val WATCH_CHANNEL = "sohr_background_watch_v1"
        private const val T2X2_CHANNEL = "t2x2_live_v2"
        private const val UPDATE_CHANNEL = "sohr_updates_v2"

        private const val WATCH_NOTIFICATION_ID = 6506
        private const val T2X2_NOTIFICATION_ID = 2202
        private const val UPDATE_NOTIFICATION_ID = 6304

        private const val RUNTIME_PREFS = "sohr_runtime"
        private const val KEY_LAST_UPDATE_NOTIFICATION = "last_update_notified_code"
        private const val KEY_LAST_T2X2_NOTIFICATION = "last_t2x2_notified_started_at"

        private const val T2X2_CHECK_INTERVAL_MS = 8_000L
        private const val UPDATE_EVERY_TICKS = 5

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context.applicationContext, SohrNotificationWatchService::class.java)
                )
            }
        }
    }
}
