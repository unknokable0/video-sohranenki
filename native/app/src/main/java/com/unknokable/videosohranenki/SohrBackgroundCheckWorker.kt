package com.unknokable.videosohranenki

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class SohrBackgroundCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ensureChannels()
        checkUpdate()
        checkT2x2Live()
        return Result.success()
    }

    private suspend fun checkUpdate() {
        val info = runCatching {
            SohrUpdateManager(applicationContext).check()
        }.getOrNull() ?: return

        if (!canNotify()) return

        val prefs = applicationContext.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_LAST_UPDATE_NOTIFICATION, -1) == info.versionCode) return

        val launchIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_OPEN_UPDATE, true)
            }
            ?: return

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            6304,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, UPDATE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_update)
            .setContentTitle("Доступно обновление SOHR ${info.versionName}")
            .setContentText("Нажмите, чтобы обновить приложение")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    info.notes.ifBlank {
                        "Доступна новая версия SOHR ${info.versionName}. Нажмите, чтобы открыть обновление."
                    }
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        applicationContext
            .getSystemService(NotificationManager::class.java)
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

        val live = runCatching {
            TwitchApi.loadLiveStream(clientId, token, "t2x2")
        }.getOrNull() ?: return

        if (live.startedAt.isBlank() || !canNotify()) return

        val prefs = applicationContext.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_T2X2_NOTIFICATION, null) == live.startedAt) return

        val twitchIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(live.url.ifBlank { "https://www.twitch.tv/t2x2" })
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            2202,
            twitchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, T2X2_CHANNEL)
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        applicationContext
            .getSystemService(NotificationManager::class.java)
            .notify(T2X2_NOTIFICATION_ID, notification)

        prefs.edit()
            .putString(KEY_LAST_T2X2_NOTIFICATION, live.startedAt)
            .apply()
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                T2X2_CHANNEL,
                "Эфиры T2x2",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомляет, когда T2x2 начинает трансляцию"
                enableVibration(true)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                UPDATE_CHANNEL,
                "Обновления SOHR",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомляет о новых версиях SOHR"
                enableVibration(false)
            }
        )
    }

    companion object {
        const val EXTRA_OPEN_UPDATE = "sohr_open_update"

        private const val UNIQUE_WORK = "sohr_background_checks"
        private const val UNIQUE_IMMEDIATE_WORK = "sohr_background_check_now"
        private const val RUNTIME_PREFS = "sohr_runtime"
        private const val KEY_LAST_UPDATE_NOTIFICATION = "last_update_notified_code"
        private const val KEY_LAST_T2X2_NOTIFICATION = "last_t2x2_notified_started_at"

        private const val UPDATE_CHANNEL = "sohr_updates"
        private const val T2X2_CHANNEL = "t2x2_live"
        private const val UPDATE_NOTIFICATION_ID = 6304
        private const val T2X2_NOTIFICATION_ID = 2202

        fun schedule(context: Context) {
            val appContext = context.applicationContext
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val manager = WorkManager.getInstance(appContext)

            // Check immediately whenever SOHR schedules background monitoring.
            // Periodic WorkManager has a 15-minute platform minimum, so this
            // one-shot check removes the unnecessary startup delay.
            manager.enqueueUniqueWork(
                UNIQUE_IMMEDIATE_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SohrBackgroundCheckWorker>()
                    .setConstraints(constraints)
                    .build()
            )

            val request = PeriodicWorkRequestBuilder<SohrBackgroundCheckWorker>(
                15,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            manager.enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
