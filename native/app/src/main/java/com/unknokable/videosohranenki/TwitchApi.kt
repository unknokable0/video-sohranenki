package com.unknokable.videosohranenki

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class TwitchAuthException(message: String = "Twitch authorization expired") : IOException(message)

data class TwitchProfile(
    val id: String,
    val login: String,
    val displayName: String,
    val profileImageUrl: String
)

data class TwitchLiveStream(
    val title: String,
    val viewerCount: Int,
    val thumbnailUrl: String,
    val startedAt: String,
    val url: String
)

object TwitchApi {
    suspend fun loadCurrentUser(clientId: String, accessToken: String): TwitchProfile = withContext(Dispatchers.IO) {
        val root = getJson("https://api.twitch.tv/helix/users", clientId, accessToken)
        val data = root.optJSONArray("data")
        if (data == null || data.length() == 0) throw IOException("Twitch не вернул профиль пользователя")
        val user = data.getJSONObject(0)
        TwitchProfile(
            id = user.optString("id"),
            login = user.optString("login"),
            displayName = user.optString("display_name").ifBlank { user.optString("login") },
            profileImageUrl = user.optString("profile_image_url")
        )
    }

    suspend fun validateToken(clientId: String, accessToken: String): String = withContext(Dispatchers.IO) {
        val connection = (URL("https://id.twitch.tv/oauth2/validate").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 12_000
            setRequestProperty("Authorization", "OAuth $accessToken")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 401) throw TwitchAuthException()
            if (code !in 200..299) throw IOException("Twitch validate: HTTP $code")
            val root = JSONObject(body)
            if (root.optString("client_id") != clientId) throw TwitchAuthException("Twitch Client ID mismatch")
            root.optString("login")
        } finally {
            connection.disconnect()
        }
    }

    suspend fun revokeToken(clientId: String, accessToken: String) = withContext(Dispatchers.IO) {
        val body = "client_id=" + java.net.URLEncoder.encode(clientId, "UTF-8") +
            "&token=" + java.net.URLEncoder.encode(accessToken, "UTF-8")
        val connection = (URL("https://id.twitch.tv/oauth2/revoke").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299 && code != 400) throw IOException("Twitch logout: HTTP $code")
        } finally {
            connection.disconnect()
        }
    }


    suspend fun loadLiveStream(clientId: String, accessToken: String, login: String): TwitchLiveStream? =
        withContext(Dispatchers.IO) {
            require(clientId.isNotBlank()) { "Twitch Client ID не настроен" }
            require(accessToken.isNotBlank()) { "Нужно войти в Twitch" }

            val root = getJson(
                "https://api.twitch.tv/helix/streams?user_login=" +
                    java.net.URLEncoder.encode(login, "UTF-8"),
                clientId,
                accessToken
            )
            val data = root.optJSONArray("data") ?: return@withContext null
            if (data.length() == 0) return@withContext null

            val stream = data.getJSONObject(0)
            val thumbnail = stream.optString("thumbnail_url")
                .replace("{width}", "640")
                .replace("{height}", "360")
                .replace("%{width}", "640")
                .replace("%{height}", "360")

            TwitchLiveStream(
                title = stream.optString("title").ifBlank { "$login в эфире" },
                viewerCount = stream.optInt("viewer_count", 0),
                thumbnailUrl = thumbnail,
                startedAt = stream.optString("started_at"),
                url = "https://www.twitch.tv/$login"
            )
        }

    suspend fun loadArchives(clientId: String, accessToken: String, login: String, days: Long = 7): List<VideoItem> =
        withContext(Dispatchers.IO) {
            require(clientId.isNotBlank()) { "Twitch Client ID не настроен" }
            require(accessToken.isNotBlank()) { "Нужно войти в Twitch" }

            val userRoot = getJson(
                "https://api.twitch.tv/helix/users?login=" + java.net.URLEncoder.encode(login, "UTF-8"),
                clientId,
                accessToken
            )
            val users = userRoot.optJSONArray("data")
            val userId = if (users != null && users.length() > 0) users.getJSONObject(0).optString("id") else ""
            if (userId.isBlank()) throw IOException("Канал Twitch @$login не найден")

            val cutoff = Instant.now().minus(days, ChronoUnit.DAYS).epochSecond
            val result = mutableListOf<VideoItem>()
            var cursor: String? = null
            var pages = 0
            var reachedOld = false

            while (pages < 4 && !reachedOld) {
                val url = buildString {
                    append("https://api.twitch.tv/helix/videos?user_id=")
                    append(java.net.URLEncoder.encode(userId, "UTF-8"))
                    append("&type=archive&first=100")
                    if (!cursor.isNullOrBlank()) {
                        append("&after=")
                        append(java.net.URLEncoder.encode(cursor, "UTF-8"))
                    }
                }
                val root = getJson(url, clientId, accessToken)
                val data = root.optJSONArray("data") ?: break
                for (i in 0 until data.length()) {
                    val o = data.getJSONObject(i)
                    val created = runCatching { Instant.parse(o.optString("created_at")).epochSecond }.getOrDefault(0L)
                    if (created in 1 until cutoff) { reachedOld = true; continue }
                    if (created <= 0L) continue
                    val id = o.optString("id")
                    if (id.isBlank()) continue
                    val numericId = id.toLongOrNull() ?: abs(id.hashCode().toLong()).coerceAtLeast(1L)
                    val thumb = o.optString("thumbnail_url")
                        .replace("%{width}", "640")
                        .replace("%{height}", "360")
                        .replace("{width}", "640")
                        .replace("{height}", "360")
                        .takeIf { it.isNotBlank() }
                    result += VideoItem(
                        messageId = -abs(numericId),
                        title = o.optString("title").ifBlank { "Стрим Twitch" },
                        date = created.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        durationSeconds = parseDuration(o.optString("duration")),
                        fileId = 0,
                        fileSize = 0L,
                        mimeType = "application/x-twitch-vod",
                        source = "twitch",
                        thumbnailUrl = thumb,
                        externalUrl = o.optString("url").takeIf { it.isNotBlank() } ?: "https://www.twitch.tv/videos/$id"
                    )
                }
                cursor = root.optJSONObject("pagination")?.optString("cursor")?.takeIf { it.isNotBlank() }
                if (cursor == null) break
                pages++
            }
            result.distinctBy { it.messageId }.sortedWith(compareBy<VideoItem> { it.date }.thenBy { it.messageId })
        }

    private fun getJson(url: String, clientId: String, accessToken: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Client-Id", clientId)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 401) throw TwitchAuthException()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(body).optString("message") }.getOrNull()
                throw IOException(message?.takeIf { it.isNotBlank() } ?: "Twitch API: HTTP $code")
            }
            return JSONObject(body)
        } finally { connection.disconnect() }
    }

    private fun parseDuration(value: String): Int {
        val match = Regex("""(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?""").matchEntire(value) ?: return 0
        return (match.groupValues.getOrNull(1)?.toIntOrNull() ?: 0) * 3600 +
            (match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0) * 60 +
            (match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0)
    }
}
