package com.unknokable.videosohranenki

import fi.iki.elonen.NanoHTTPD
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * Twitch exposes future live segments with #EXT-X-TWITCH-PREFETCH.
 * Media3 ignores this Twitch-specific tag, so this localhost playlist proxy
 * translates prefetch entries into ordinary HLS segments.
 *
 * Video segment bytes are still fetched directly from Twitch/CDN by Media3.
 */
class TwitchLowLatencyProxy(
    port: Int = 0
) : NanoHTTPD("127.0.0.1", port) {

    @Volatile
    private var started = false

    @Synchronized
    fun ensureStarted(): Boolean {
        if (started) return true
        return try {
            start(5_000, false)
            started = true
            true
        } catch (_: Throwable) {
            started = false
            false
        }
    }

    fun playlistUrl(upstreamUrl: String): String {
        if (!started && !ensureStarted()) return upstreamUrl
        val encoded = URLEncoder.encode(upstreamUrl, "UTF-8")
        return "http://127.0.0.1:$listeningPort/playlist?u=$encoded"
    }

    override fun stop() {
        started = false
        super.stop()
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.uri != "/playlist") {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/plain",
                "Not found"
            )
        }

        val raw = session.parameters["u"]?.firstOrNull().orEmpty()
        val upstream = decodeUrl(raw)
        if (!upstream.startsWith("https://") && !upstream.startsWith("http://")) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "text/plain",
                "Bad playlist URL"
            )
        }

        return try {
            val source = fetchText(upstream)
            val rewritten =
                if (isMasterPlaylist(source)) rewriteMaster(upstream, source)
                else rewriteMedia(upstream, source)

            newFixedLengthResponse(
                Response.Status.OK,
                "application/vnd.apple.mpegurl",
                rewritten
            ).apply {
                addHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
                addHeader("Pragma", "no-cache")
                addHeader("Expires", "0")
                addHeader("Access-Control-Allow-Origin", "*")
            }
        } catch (e: Throwable) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                "Playlist proxy error: " + (e.message ?: "unknown")
            ).apply {
                addHeader("Cache-Control", "no-store")
            }
        }
    }

    private fun fetchText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 4_500
            readTimeout = 6_000
            useCaches = false
            setRequestProperty("Accept", "application/vnd.apple.mpegurl,application/x-mpegURL,text/plain,*/*")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
            setRequestProperty("Referer", "https://www.twitch.tv/")
            setRequestProperty("Origin", "https://www.twitch.tv")
        }

        return try {
            val code = connection.responseCode
            if (code !in 200..299) error("Twitch playlist HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun isMasterPlaylist(text: String): Boolean =
        text.lineSequence().any {
            it.trim().startsWith("#EXT-X-STREAM-INF", ignoreCase = true) ||
                it.trim().startsWith("#EXT-X-MEDIA:", ignoreCase = true)
        }

    private fun rewriteMaster(baseUrl: String, text: String): String {
        val out = ArrayList<String>()
        var expectVariantUri = false

        for (original in text.lineSequence()) {
            val line = original.trim()

            when {
                line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) -> {
                    out += original
                    expectVariantUri = true
                }

                expectVariantUri && line.isNotBlank() && !line.startsWith("#") -> {
                    out += playlistUrl(resolve(baseUrl, line))
                    expectVariantUri = false
                }

                line.startsWith("#EXT-X-MEDIA:", ignoreCase = true) ||
                    line.startsWith("#EXT-X-I-FRAME-STREAM-INF:", ignoreCase = true) -> {
                    out += rewriteUriAttribute(
                        line = original,
                        baseUrl = baseUrl,
                        localPlaylist = true
                    )
                }

                else -> out += original
            }
        }

        return out.joinToString("\n", postfix = "\n")
    }

    private fun rewriteMedia(baseUrl: String, text: String): String {
        val lines = text.lineSequence().toList()

        val durations = lines.mapNotNull { original ->
            val line = original.trim()
            if (!line.startsWith("#EXTINF:", ignoreCase = true)) return@mapNotNull null
            line.substringAfter(":").substringBefore(",").toDoubleOrNull()
        }

        var targetDuration: Double? = null
        for (original in lines) {
            val line = original.trim()
            if (line.startsWith("#EXT-X-TARGETDURATION:", ignoreCase = true)) {
                targetDuration = line.substringAfter(":").toDoubleOrNull()
                break
            }
        }

        val estimatedDuration =
            if (durations.isNotEmpty()) durations.average()
            else targetDuration ?: 2.0

        val prefetchUris = mutableListOf<String>()
        val regularUris = linkedSetOf<String>()
        val out = ArrayList<String>()
        var hadEndList = false

        for (original in lines) {
            val line = original.trim()

            when {
                line.startsWith("#EXT-X-TWITCH-PREFETCH:", ignoreCase = true) -> {
                    val rawUri = line.substringAfter(":").trim().trim('"')
                    if (rawUri.isNotBlank()) {
                        prefetchUris += resolve(baseUrl, rawUri)
                    }
                }

                line.equals("#EXT-X-ENDLIST", ignoreCase = true) -> {
                    hadEndList = true
                }

                line.isNotBlank() && !line.startsWith("#") -> {
                    val absolute = resolve(baseUrl, line)
                    regularUris += absolute
                    out += absolute
                }

                line.startsWith("#EXT-X-KEY:", ignoreCase = true) ||
                    line.startsWith("#EXT-X-MAP:", ignoreCase = true) -> {
                    out += rewriteUriAttribute(
                        line = original,
                        baseUrl = baseUrl,
                        localPlaylist = false
                    )
                }

                else -> out += original
            }
        }

        val durationText = String.format(Locale.US, "%.3f", estimatedDuration)
        prefetchUris
            .asSequence()
            .distinct()
            .filterNot { it in regularUris }
            .take(2)
            .forEach { uri ->
                out += "#EXTINF:$durationText,"
                out += uri
            }

        if (hadEndList) out += "#EXT-X-ENDLIST"

        return out.joinToString("\n", postfix = "\n")
    }

    private fun rewriteUriAttribute(
        line: String,
        baseUrl: String,
        localPlaylist: Boolean
    ): String {
        val regex = Regex("""URI="([^"]+)"""", RegexOption.IGNORE_CASE)
        return regex.replace(line) { match ->
            val absolute = resolve(baseUrl, match.groupValues[1])
            val target = if (localPlaylist) playlistUrl(absolute) else absolute
            "URI=\"$target\""
        }
    }

    private fun resolve(baseUrl: String, value: String): String =
        runCatching { URL(URL(baseUrl), value).toString() }
            .getOrDefault(value)

    private fun decodeUrl(raw: String): String =
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            raw
        } else {
            runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        }
}
