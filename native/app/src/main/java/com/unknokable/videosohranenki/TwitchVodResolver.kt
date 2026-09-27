package com.unknokable.videosohranenki

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class TwitchCommercialBreakException : IOException("На эфире сейчас реклама Twitch")

object TwitchVodResolver {
    private const val GQL_URL = "https://gql.twitch.tv/gql"

    // Public client id used by Twitch's current web player. Twitch Helix does not expose raw VOD playback URLs.
    private const val TWITCH_WEB_CLIENT_ID = "ue6666qo983tsx6so1t0vnawi233wa"

    private const val PLAYBACK_QUERY =
        "query PlaybackAccessToken_Template(\$login: String!, \$isLive: Boolean!, \$vodID: ID!, \$isVod: Boolean!, \$playerType: String!) { " +
            "streamPlaybackAccessToken(channelName: \$login, params: {platform: \"web\", playerBackend: \"mediaplayer\", playerType: \$playerType}) @include(if: \$isLive) { value signature __typename } " +
            "videoPlaybackAccessToken(id: \$vodID, params: {platform: \"web\", playerBackend: \"mediaplayer\", playerType: \$playerType}) @include(if: \$isVod) { value signature __typename } }"

    suspend fun resolve(
        videoId: String,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 15_000
    ): String = withContext(Dispatchers.IO) {
        require(videoId.all(Char::isDigit) && videoId.isNotBlank()) { "Некорректный Twitch Video ID" }

        val payload = JSONObject().apply {
            put("operationName", "PlaybackAccessToken_Template")
            put("query", PLAYBACK_QUERY)
            put("variables", JSONObject().apply {
                put("isLive", false)
                put("login", "")
                put("isVod", true)
                put("vodID", videoId)
                put("playerType", "site")
            })
        }.toString()

        val connection = (URL(GQL_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs.coerceIn(1_500, 15_000)
            readTimeout = readTimeoutMs.coerceIn(2_000, 20_000)
            doOutput = true
            setRequestProperty("Client-ID", TWITCH_WEB_CLIENT_ID)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
        }

        val body = try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("Twitch playback token: HTTP $code")
            text
        } finally {
            connection.disconnect()
        }

        val root = JSONObject(body)
        if (root.has("errors")) {
            val message = root.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
            throw IOException(message?.takeIf { it.isNotBlank() } ?: "Twitch не выдал токен воспроизведения")
        }

        val tokenNode = root.optJSONObject("data")
            ?.optJSONObject("videoPlaybackAccessToken")
            ?: throw IOException("Twitch не выдал доступ к этой записи")

        val signature = tokenNode.optString("signature")
        val token = tokenNode.optString("value")
        if (signature.isBlank() || token.isBlank()) {
            throw IOException("Пустой токен воспроизведения Twitch")
        }

        val encodedSig = URLEncoder.encode(signature, "UTF-8")
        val encodedToken = URLEncoder.encode(token, "UTF-8")

        "https://usher.ttvnw.net/vod/$videoId.m3u8" +
            "?allow_source=true" +
            "&allow_audio_only=false" +
            "&playlist_include_framerate=true" +
            "&platform=web" +
            "&player=twitchweb" +
            "&supported_codecs=h264" +
            "&sig=$encodedSig" +
            "&token=$encodedToken"
    }

    suspend fun resolveLiveChecked(
        login: String,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 15_000
    ): String = withContext(Dispatchers.IO) {
        val hlsUrl = resolveLive(login, connectTimeoutMs, readTimeoutMs)
        if (containsCommercialBreak(hlsUrl, connectTimeoutMs, readTimeoutMs)) {
            throw TwitchCommercialBreakException()
        }
        hlsUrl
    }

    suspend fun isCommercialBreak(
        login: String,
        connectTimeoutMs: Int = 8_000,
        readTimeoutMs: Int = 10_000
    ): Boolean = withContext(Dispatchers.IO) {
        val hlsUrl = resolveLive(login, connectTimeoutMs, readTimeoutMs)
        containsCommercialBreak(hlsUrl, connectTimeoutMs, readTimeoutMs)
    }

    suspend fun resolveLive(
        login: String,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 15_000
    ): String = withContext(Dispatchers.IO) {
        val channel = login.trim().lowercase()
        require(channel.matches(Regex("[a-z0-9_]{3,25}"))) { "Некорректный Twitch login" }

        val payload = JSONObject().apply {
            put("operationName", "PlaybackAccessToken_Template")
            put("query", PLAYBACK_QUERY)
            put("variables", JSONObject().apply {
                put("isLive", true)
                put("login", channel)
                put("isVod", false)
                put("vodID", "")
                put("playerType", "site")
            })
        }.toString()

        val connection = (URL(GQL_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs.coerceIn(1_500, 15_000)
            readTimeout = readTimeoutMs.coerceIn(2_000, 20_000)
            doOutput = true
            setRequestProperty("Client-ID", TWITCH_WEB_CLIENT_ID)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
        }

        val body = try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("Twitch live token: HTTP " + code)
            text
        } finally {
            connection.disconnect()
        }

        val root = JSONObject(body)
        if (root.has("errors")) {
            val message = root.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
            throw IOException(message?.takeIf { it.isNotBlank() } ?: "Twitch не выдал токен прямого эфира")
        }

        val tokenNode = root.optJSONObject("data")
            ?.optJSONObject("streamPlaybackAccessToken")
            ?: throw IOException("Twitch не выдал доступ к прямому эфиру")

        val signature = tokenNode.optString("signature")
        val token = tokenNode.optString("value")
        if (signature.isBlank() || token.isBlank()) {
            throw IOException("Пустой токен прямого эфира Twitch")
        }

        val encodedSig = URLEncoder.encode(signature, "UTF-8")
        val encodedToken = URLEncoder.encode(token, "UTF-8")
        val randomP = (System.currentTimeMillis() % 9_000_000L + 1_000_000L).toString()

        "https://usher.ttvnw.net/api/channel/hls/" + channel + ".m3u8" +
            "?allow_source=true" +
            "&allow_audio_only=true" +
            "&fast_bread=true" +
            "&playlist_include_framerate=true" +
            "&player_backend=mediaplayer" +
            "&supported_codecs=h264" +
            "&p=" + randomP +
            "&sig=" + encodedSig +
            "&token=" + encodedToken
    }

    private fun containsCommercialBreak(
        masterUrl: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): Boolean {
        return runCatching {
            val master = fetchPlaylistText(masterUrl, connectTimeoutMs, readTimeoutMs)
            if (hasCommercialMarkers(master)) return@runCatching true

            val mediaUrl = selectMediaPlaylist(masterUrl, master) ?: return@runCatching false
            val media = fetchPlaylistText(mediaUrl, connectTimeoutMs, readTimeoutMs)
            hasCommercialMarkers(media)
        }.getOrDefault(false)
    }

    private fun fetchPlaylistText(
        url: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs.coerceIn(1_500, 15_000)
            readTimeout = readTimeoutMs.coerceIn(2_000, 20_000)
            setRequestProperty("Accept", "application/vnd.apple.mpegurl,application/x-mpegURL,text/plain,*/*")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) return ""
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun selectMediaPlaylist(masterUrl: String, master: String): String? {
        val lines = master.lineSequence().map { it.trim() }.toList()
        var fallback: String? = null

        for (i in lines.indices) {
            val header = lines[i]
            if (!header.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) continue

            var j = i + 1
            while (j < lines.size && (lines[j].isBlank() || lines[j].startsWith("#"))) j++
            if (j >= lines.size) continue

            val uri = lines[j]
            val absolute = runCatching { URL(URL(masterUrl), uri).toString() }.getOrNull() ?: continue
            if (fallback == null) fallback = absolute

            if (!header.contains("audio_only", ignoreCase = true)) {
                return absolute
            }
        }
        return fallback
    }

    private fun hasCommercialMarkers(playlist: String): Boolean {
        if (playlist.isBlank()) return false
        val lower = playlist.lowercase()
        return listOf(
            "stitched-ad",
            "commercial break in progress",
            "x-tv-twitch-ad",
            "twitch-ad",
            "amazon-adsystem",
            "ad-signifier",
            "server-ads"
        ).any(lower::contains)
    }

}
