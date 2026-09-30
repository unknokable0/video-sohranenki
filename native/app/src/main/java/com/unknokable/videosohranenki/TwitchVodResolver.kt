package com.unknokable.videosohranenki

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class TwitchCommercialBreakException : IOException("На эфире сейчас реклама Twitch")

data class TwitchMutedRange(
    val startMs: Long,
    val endMs: Long
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)
}

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

    suspend fun resolveMutedSegments(
        videoId: String,
        connectTimeoutMs: Int = 8_000,
        readTimeoutMs: Int = 12_000
    ): List<TwitchMutedRange> = withContext(Dispatchers.IO) {
        runCatching {
            val masterUrl = resolve(videoId, connectTimeoutMs, readTimeoutMs)
            val master = fetchPlaylistText(masterUrl, connectTimeoutMs, readTimeoutMs)
            if (master.isBlank()) return@runCatching emptyList()

            val mediaUrl = selectBestVodMediaPlaylist(masterUrl, master)
                ?: return@runCatching emptyList()
            val media = fetchPlaylistText(mediaUrl, connectTimeoutMs, readTimeoutMs)
            parseMutedRanges(media)
        }.getOrDefault(emptyList())
    }

    private fun selectBestVodMediaPlaylist(masterUrl: String, master: String): String? {
        val lines = master.lineSequence().map { it.trim() }.toList()
        var bestUrl: String? = null
        var bestScore = Int.MIN_VALUE

        for (i in lines.indices) {
            val header = lines[i]
            if (!header.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) continue

            var j = i + 1
            while (j < lines.size && (lines[j].isBlank() || lines[j].startsWith("#"))) j++
            if (j >= lines.size) continue

            val uri = lines[j]
            val absolute = runCatching { URL(URL(masterUrl), uri).toString() }.getOrNull() ?: continue
            val combined = (header + " " + uri).lowercase()
            if ("audio_only" in combined) continue

            val height = Regex("""RESOLUTION=\d+x(\d+)""", RegexOption.IGNORE_CASE)
                .find(header)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: 0
            val bandwidth = Regex("""BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE)
                .find(header)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: 0
            val sourceBoost = if (
                "chunked" in combined ||
                "source" in combined ||
                "name=\"source\"" in combined
            ) 1_000_000_000 else 0
            val score = sourceBoost + height * 100_000 + bandwidth.coerceAtMost(99_999)

            if (score > bestScore) {
                bestScore = score
                bestUrl = absolute
            }
        }

        return bestUrl ?: selectMediaPlaylists(masterUrl, master).firstOrNull()
    }

    private fun parseMutedRanges(mediaPlaylist: String): List<TwitchMutedRange> {
        if (mediaPlaylist.isBlank()) return emptyList()

        val ranges = mutableListOf<TwitchMutedRange>()
        var cursorMs = 0.0
        var pendingDurationMs: Double? = null
        var openStartMs: Long? = null
        var openEndMs = 0L

        fun flushOpenRange() {
            val start = openStartMs ?: return
            if (openEndMs > start) {
                ranges += TwitchMutedRange(start, openEndMs)
            }
            openStartMs = null
            openEndMs = 0L
        }

        mediaPlaylist.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                val seconds = line.substringAfter(":")
                    .substringBefore(",")
                    .trim()
                    .toDoubleOrNull()
                    ?.coerceAtLeast(0.0)
                pendingDurationMs = seconds?.times(1000.0)
                return@forEach
            }

            val duration = pendingDurationMs ?: return@forEach
            if (line.isBlank() || line.startsWith("#")) return@forEach

            val startMs = cursorMs.toLong().coerceAtLeast(0L)
            cursorMs += duration
            val endMs = cursorMs.toLong().coerceAtLeast(startMs)

            val lower = line.lowercase()
            val muted =
                "-unmuted." in lower ||
                    "-muted." in lower ||
                    "index-muted" in lower

            if (muted) {
                if (openStartMs == null) openStartMs = startMs
                openEndMs = endMs
            } else {
                flushOpenRange()
            }

            pendingDurationMs = null
        }

        flushOpenRange()

        if (ranges.size <= 1) return ranges

        val merged = mutableListOf<TwitchMutedRange>()
        ranges.forEach { range ->
            val previous = merged.lastOrNull()
            if (previous != null && range.startMs - previous.endMs <= 750L) {
                merged[merged.lastIndex] = previous.copy(
                    endMs = maxOf(previous.endMs, range.endMs)
                )
            } else {
                merged += range
            }
        }
        return merged
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

    suspend fun isCommercialBreakUrl(
        masterUrl: String,
        connectTimeoutMs: Int = 5_000,
        readTimeoutMs: Int = 6_000
    ): Boolean = withContext(Dispatchers.IO) {
        containsCommercialBreak(masterUrl, connectTimeoutMs, readTimeoutMs)
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
            "&allow_spectre=true" +
            "&fast_bread=true" +
            "&playlist_include_framerate=true" +
            "&platform=web" +
            "&player=twitchweb" +
            "&player_backend=mediaplayer" +
            "&reassignment_supported=true" +
            "&rtqos=control" +
            "&type=any" +
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

            val mediaUrls = selectMediaPlaylists(masterUrl, master)
            if (mediaUrls.isEmpty()) return@runCatching false

            mediaUrls.take(4).any { mediaUrl ->
                val media = fetchPlaylistText(mediaUrl, connectTimeoutMs, readTimeoutMs)
                hasCommercialMarkers(media)
            }
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

    private fun selectMediaPlaylists(masterUrl: String, master: String): List<String> {
        val lines = master.lineSequence().map { it.trim() }.toList()
        val video = mutableListOf<String>()
        val fallback = mutableListOf<String>()

        for (i in lines.indices) {
            val header = lines[i]
            if (!header.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) continue

            var j = i + 1
            while (j < lines.size && (lines[j].isBlank() || lines[j].startsWith("#"))) j++
            if (j >= lines.size) continue

            val uri = lines[j]
            val absolute = runCatching { URL(URL(masterUrl), uri).toString() }.getOrNull() ?: continue
            fallback += absolute

            if (!header.contains("audio_only", ignoreCase = true)) {
                video += absolute
            }
        }

        return (video + fallback).distinct()
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
