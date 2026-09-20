package com.unknokable.videosohranenki

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
}
