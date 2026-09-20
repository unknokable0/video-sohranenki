package com.unknokable.videosohranenki

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val sha256: String,
    val notes: String
)

class SohrUpdateManager(private val context: Context) {

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val json = readText(UPDATE_MANIFEST_URL)
        val obj = JSONObject(json)
        val info = UpdateInfo(
            versionCode = obj.getInt("versionCode"),
            versionName = obj.getString("versionName"),
            downloadUrl = obj.getString("downloadUrl"),
            sha256 = obj.optString("sha256").trim(),
            notes = obj.optString("notes").trim()
        )
        if (info.versionCode > BuildConfig.VERSION_CODE) info else null
    }

    suspend fun download(info: UpdateInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "SOHR-${info.versionName}.apk")
        if (target.exists()) target.delete()

        val connection = open(info.downloadUrl)
        val total = connection.contentLengthLong.coerceAtLeast(0L)
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(128 * 1024)
                var downloaded = 0L
                var lastProgress = -1
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0L) {
                        val progress = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                        if (progress != lastProgress) {
                            lastProgress = progress
                            onProgress(progress)
                        }
                    }
                }
            }
        }
        connection.disconnect()

        if (info.sha256.isNotBlank()) {
            val actual = sha256(target)
            if (!actual.equals(info.sha256, ignoreCase = true)) {
                target.delete()
                error("SHA-256 обновления не совпал")
            }
        }
        target
    }

    fun isSignatureCompatible(apk: File): Boolean {
        return runCatching {
            val currentDigests = signingDigests(
                context.packageManager.getPackageInfo(
                    context.packageName,
                    signingFlags()
                )
            )
            val archiveInfo = context.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                signingFlags()
            ) ?: return false
            val archiveDigests = signingDigests(archiveInfo)
            currentDigests.isNotEmpty() &&
                archiveDigests.isNotEmpty() &&
                currentDigests.any { it in archiveDigests }
        }.getOrDefault(false)
    }

    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun signingDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            val signers = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            signers.toList()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.toList().orEmpty()
        }

        return signatures.map { signature ->
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    fun canRequestInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun installerIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun readText(url: String): String {
        val connection = open(url)
        return connection.inputStream.bufferedReader().use { it.readText() }
            .also { connection.disconnect() }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "SOHR/${BuildConfig.VERSION_NAME}")
            connect()
            if (responseCode !in 200..299) error("HTTP $responseCode")
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val UPDATE_MANIFEST_URL =
            "https://github.com/unknokable0/video-sohranenki/releases/download/sohr-latest/update.json"
        private const val APK_MIME = "application/vnd.android.package-archive"
    }
}
