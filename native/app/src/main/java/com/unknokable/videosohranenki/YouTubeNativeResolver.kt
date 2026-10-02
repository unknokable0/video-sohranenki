package com.unknokable.videosohranenki

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.Locale

data class YouTubeNativeSource(
    val mediaUrl: String,
    val secondaryAudioUrl: String? = null,
    val durationSeconds: Int,
    val title: String,
    val thumbnailUrl: String?
)

sealed class YouTubeNativeResolveException(message: String) : Exception(message)

class YouTubeAgeRestrictedException : YouTubeNativeResolveException(
    "Этот ролик имеет возрастное ограничение YouTube."
)

class YouTubeSignInRequiredException : YouTubeNativeResolveException(
    "YouTube временно требует проверку сессии для этого ролика."
)

class YouTubeUnavailableException : YouTubeNativeResolveException(
    "Этот ролик сейчас недоступен для воспроизведения в SOHR."
)

class YouTubeNetworkException : YouTubeNativeResolveException(
    "Не удалось связаться с YouTube. Проверьте интернет и попробуйте ещё раз."
)

/**
 * Resolves YouTube videos into direct media streams for SOHR's native Media3 PlayerScreen.
 *
 * Resolution order:
 * 1) NewPipeExtractor, which remains the normal path and handles most videos.
 * 2) Direct YouTube InnerTube client fallbacks that do not use YouTube's embedded player.
 *
 * SOHR deliberately does not use embedded-player age-gate workarounds. If YouTube reports
 * age verification/restriction, the resolver stops instead of trying clients that could
 * weaken that restriction.
 */
object YouTubeNativeResolver {
    @Volatile
    private var initialized = false
    private val initLock = Any()

    private const val INNERTUBE_API_KEY =
        "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

    private data class InnerTubeClient(
        val name: String,
        val version: String,
        val numericName: Int,
        val userAgent: String,
        val extraContext: JSONObject
    )

    // VISIONOS is currently the cleanest JS-less ordinary-playback fallback.
    // TVHTML5 downgraded is retained as a second, conservative non-embedded attempt.
    private val innerTubeClients = listOf(
        InnerTubeClient(
            name = "VISIONOS",
            version = "1.02",
            numericName = 101,
            userAgent =
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) " +
                    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
            extraContext = JSONObject()
                .put("deviceMake", "Apple")
                .put("deviceModel", "RealityDevice17,1")
                .put("osName", "visionOS")
                .put("osVersion", "26.5.23O471")
        ),
        InnerTubeClient(
            name = "TVHTML5",
            version = "5.20260707",
            numericName = 7,
            userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
            extraContext = JSONObject()
        )
    )

    suspend fun resolve(video: YouTubeFeedVideo): YouTubeNativeSource =
        withContext(Dispatchers.IO) {
            ensureInitialized()

            var primaryFailure: YouTubeNativeResolveException? = null
            try {
                val info = StreamInfo.getInfo(video.watchUrl)
                return@withContext sourceFromNewPipe(video, info)
            } catch (error: Throwable) {
                val mapped = mapFailure(error)
                if (mapped is YouTubeAgeRestrictedException) throw mapped
                primaryFailure = mapped
            }

            try {
                resolveViaInnerTube(video)
            } catch (error: Throwable) {
                val mapped = mapFailure(error)
                if (mapped is YouTubeAgeRestrictedException) throw mapped

                throw when {
                    mapped is YouTubeNetworkException &&
                        primaryFailure is YouTubeNetworkException -> mapped
                    mapped is YouTubeSignInRequiredException -> mapped
                    mapped is YouTubeUnavailableException &&
                        primaryFailure is YouTubeSignInRequiredException ->
                            primaryFailure ?: mapped
                    else -> mapped
                }
            }
        }

    private fun sourceFromNewPipe(
        video: YouTubeFeedVideo,
        info: StreamInfo
    ): YouTubeNativeSource {
        val progressive = info.videoStreams
            .asSequence()
            .filter { it.isUrl && !it.isVideoOnly }
            .filter { isDirectHttpUrl(it.content) }
            .sortedWith(
                compareByDescending<VideoStream> { if (it.height in 1..1080) 1 else 0 }
                    .thenByDescending { if (it.height in 1..1080) it.height else 0 }
                    .thenByDescending { it.bitrate }
            )
            .firstOrNull()

        val splitVideo = info.videoStreams
            .asSequence()
            .filter { it.isUrl && it.isVideoOnly }
            .filter { isDirectHttpUrl(it.content) }
            .sortedWith(
                compareByDescending<VideoStream> {
                    when {
                        it.height in 1..1080 -> 2
                        it.height > 1080 -> 1
                        else -> 0
                    }
                }
                    .thenByDescending { if (it.height in 1..1080) it.height else 0 }
                    .thenByDescending { it.bitrate }
            )
            .firstOrNull()

        val splitAudio = info.audioStreams
            .asSequence()
            .filter { it.isUrl }
            .filter { isDirectHttpUrl(it.content) }
            .sortedWith(
                compareByDescending<AudioStream> { it.averageBitrate }
                    .thenByDescending { it.bitrate }
            )
            .firstOrNull()

        val hls = info.hlsUrl.takeIf(::isDirectHttpUrl)
        val dash = info.dashMpdUrl.takeIf(::isDirectHttpUrl)

        val mediaUrl: String
        val secondaryAudioUrl: String?

        when {
            progressive != null -> {
                mediaUrl = progressive.content
                secondaryAudioUrl = null
            }
            hls != null -> {
                mediaUrl = hls
                secondaryAudioUrl = null
            }
            dash != null -> {
                mediaUrl = dash
                secondaryAudioUrl = null
            }
            splitVideo != null && splitAudio != null -> {
                mediaUrl = splitVideo.content
                secondaryAudioUrl = splitAudio.content
            }
            else -> throw YouTubeUnavailableException()
        }

        return YouTubeNativeSource(
            mediaUrl = mediaUrl,
            secondaryAudioUrl = secondaryAudioUrl,
            durationSeconds = info.duration
                .coerceIn(0L, Int.MAX_VALUE.toLong())
                .toInt(),
            title = info.name.ifBlank { video.title },
            thumbnailUrl = video.thumbnailUrl
        )
    }

    private fun resolveViaInnerTube(video: YouTubeFeedVideo): YouTubeNativeSource {
        var lastFailure: YouTubeNativeResolveException = YouTubeUnavailableException()

        for (client in innerTubeClients) {
            val response = try {
                requestInnerTubePlayer(video.videoId, client)
            } catch (error: UnknownHostException) {
                throw YouTubeNetworkException()
            } catch (error: SocketTimeoutException) {
                lastFailure = YouTubeNetworkException()
                continue
            } catch (error: Throwable) {
                lastFailure = mapFailure(error)
                continue
            }

            val playability = response.optJSONObject("playabilityStatus")
            val status = playability?.optString("status").orEmpty()
            val detail = listOfNotNull(
                playability?.optString("reason"),
                playability?.optJSONObject("errorScreen")?.toString()
            ).joinToString(" ").lowercase(Locale.ROOT)

            if (isAgeRestrictedStatus(status, detail)) {
                throw YouTubeAgeRestrictedException()
            }

            if (status != "OK") {
                lastFailure = when {
                    status == "LOGIN_REQUIRED" ||
                        "sign in to confirm" in detail ||
                        "not a bot" in detail ||
                        "login required" in detail -> YouTubeSignInRequiredException()
                    "private" in detail ||
                        "unavailable" in detail ||
                        "not available" in detail -> YouTubeUnavailableException()
                    else -> YouTubeUnavailableException()
                }
                continue
            }

            val streaming = response.optJSONObject("streamingData")
            if (streaming == null) {
                lastFailure = YouTubeUnavailableException()
                continue
            }

            val formats = streaming.optJSONArray("formats").asObjects()
            val adaptive = streaming.optJSONArray("adaptiveFormats").asObjects()

            val progressive = formats
                .asSequence()
                .filter(::hasDirectUrl)
                .filter(::isProgressiveVideo)
                .filterNot(::isDrmFormat)
                .sortedWith(innerTubeVideoComparator())
                .firstOrNull()

            if (progressive != null) {
                return innerTubeSource(video, response, progressive.optString("url"), null)
            }

            val splitVideo = adaptive
                .asSequence()
                .filter(::hasDirectUrl)
                .filter { it.optString("mimeType").startsWith("video/") }
                .filterNot(::isDrmFormat)
                .sortedWith(innerTubeVideoComparator())
                .firstOrNull()

            val splitAudio = adaptive
                .asSequence()
                .filter(::hasDirectUrl)
                .filter { it.optString("mimeType").startsWith("audio/") }
                .filterNot(::isDrmFormat)
                .sortedWith(
                    compareByDescending<JSONObject> {
                        if (it.optString("mimeType").contains("mp4a")) 1 else 0
                    }.thenByDescending { it.optInt("bitrate", 0) }
                )
                .firstOrNull()

            if (splitVideo != null && splitAudio != null) {
                return innerTubeSource(
                    video = video,
                    response = response,
                    mediaUrl = splitVideo.optString("url"),
                    secondaryAudioUrl = splitAudio.optString("url")
                )
            }

            val hls = streaming.optString("hlsManifestUrl")
            if (isDirectHttpUrl(hls)) {
                return innerTubeSource(video, response, hls, null)
            }

            lastFailure = YouTubeUnavailableException()
        }

        throw lastFailure
    }

    private fun requestInnerTubePlayer(
        videoId: String,
        client: InnerTubeClient
    ): JSONObject {
        val clientJson = JSONObject()
            .put("clientName", client.name)
            .put("clientVersion", client.version)
            .put("hl", "ru")
            .put("gl", "PL")

        val keys = client.extraContext.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            clientJson.put(key, client.extraContext.get(key))
        }

        val body = JSONObject()
            .put(
                "context",
                JSONObject().put("client", clientJson)
            )
            .put("videoId", videoId)

        val endpoint =
            "https://www.youtube.com/youtubei/v1/player" +
                "?prettyPrint=false&key=$INNERTUBE_API_KEY"

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 18_000
            instanceFollowRedirects = true
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.7")
            setRequestProperty("User-Agent", client.userAgent)
            setRequestProperty("Origin", "https://www.youtube.com")
            setRequestProperty("X-YouTube-Client-Name", client.numericName.toString())
            setRequestProperty("X-YouTube-Client-Version", client.version)
        }

        try {
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                if (code == 408 || code == 429 || code >= 500) {
                    throw YouTubeNetworkException()
                }
                throw YouTubeUnavailableException()
            }

            return JSONObject(raw)
        } finally {
            connection.disconnect()
        }
    }

    private fun innerTubeSource(
        video: YouTubeFeedVideo,
        response: JSONObject,
        mediaUrl: String,
        secondaryAudioUrl: String?
    ): YouTubeNativeSource {
        val details = response.optJSONObject("videoDetails")
        val title = details?.optString("title").orEmpty().ifBlank { video.title }
        val duration = details?.optString("lengthSeconds")
            ?.toLongOrNull()
            ?.coerceIn(0L, Int.MAX_VALUE.toLong())
            ?.toInt()
            ?: video.durationSeconds

        return YouTubeNativeSource(
            mediaUrl = mediaUrl,
            secondaryAudioUrl = secondaryAudioUrl,
            durationSeconds = duration,
            title = title,
            thumbnailUrl = video.thumbnailUrl
        )
    }

    private fun innerTubeVideoComparator(): Comparator<JSONObject> =
        compareByDescending<JSONObject> {
            val mime = it.optString("mimeType")
            when {
                mime.contains("avc1") && mime.startsWith("video/mp4") -> 3
                mime.startsWith("video/mp4") -> 2
                else -> 1
            }
        }.thenByDescending {
            val height = it.optInt("height", 0)
            when {
                height in 1..1080 -> height
                height > 1080 -> 1
                else -> 0
            }
        }.thenByDescending { it.optInt("bitrate", 0) }

    private fun hasDirectUrl(format: JSONObject): Boolean =
        isDirectHttpUrl(format.optString("url"))

    private fun isProgressiveVideo(format: JSONObject): Boolean {
        val mime = format.optString("mimeType")
        if (!mime.startsWith("video/")) return false
        return format.optInt("audioChannels", 0) > 0 ||
            format.has("audioQuality")
    }

    private fun isDrmFormat(format: JSONObject): Boolean =
        format.has("drmFamilies") ||
            format.has("drmTrackType") ||
            format.optString("type").contains("DRM", ignoreCase = true)

    private fun isAgeRestrictedStatus(status: String, detail: String): Boolean =
        status == "AGE_CHECK_REQUIRED" ||
            status == "AGE_VERIFICATION_REQUIRED" ||
            "age-restricted" in detail ||
            "age restricted" in detail ||
            "confirm your age" in detail ||
            "age verification" in detail

    private fun JSONArray?.asObjects(): List<JSONObject> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.let(::add)
            }
        }
    }

    private fun mapFailure(error: Throwable): YouTubeNativeResolveException {
        if (error is YouTubeNativeResolveException) return error
        if (error is UnknownHostException || error is SocketTimeoutException) {
            return YouTubeNetworkException()
        }

        val exceptionNames = mutableListOf<String>()
        val messages = mutableListOf<String>()
        var cause: Throwable? = error
        while (cause != null) {
            exceptionNames += cause.javaClass.simpleName
            cause.message?.takeIf { it.isNotBlank() }?.let(messages::add)
            cause = cause.cause
        }

        val classChain = exceptionNames.joinToString(" ").lowercase(Locale.ROOT)
        val combined = messages.joinToString(" ").lowercase(Locale.ROOT)

        return when {
            "age-restricted" in combined ||
                "age restricted" in combined ||
                "confirm your age" in combined ||
                "sign in to confirm your age" in combined ||
                "ageverificationrequired" in classChain ->
                    YouTubeAgeRestrictedException()

            "signinconfirmnotbot" in classChain ||
                "loginrequired" in classChain ||
                "cannot be watched anonymously" in combined ||
                "sign in to confirm you're not a bot" in combined ||
                "sign in to confirm you’re not a bot" in combined ||
                "login required" in combined ->
                    YouTubeSignInRequiredException()

            "private video" in combined ||
                "video unavailable" in combined ||
                "not available" in combined ||
                "content not available" in combined ->
                    YouTubeUnavailableException()

            "timed out" in combined ||
                "unable to resolve host" in combined ||
                "network is unreachable" in combined ->
                    YouTubeNetworkException()

            else -> YouTubeUnavailableException()
        }
    }

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
            NewPipe.init(
                SohrExtractorDownloader(),
                Localization("ru", "RU"),
                ContentCountry("PL")
            )
            initialized = true
        }
    }

    private fun isDirectHttpUrl(value: String?): Boolean =
        !value.isNullOrBlank() &&
            (value.startsWith("https://") || value.startsWith("http://"))
}

private class SohrExtractorDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val connection = (URL(request.url()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            requestMethod = request.httpMethod()
            useCaches = false
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/140 Mobile Safari/537.36"
            )
            setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.7")

            request.headers().forEach { (name, values) ->
                values.forEachIndexed { index, value ->
                    if (index == 0) {
                        setRequestProperty(name, value)
                    } else {
                        addRequestProperty(name, value)
                    }
                }
            }
        }

        try {
            request.dataToSend()?.let { body ->
                connection.doOutput = true
                connection.outputStream.use { it.write(body) }
            }

            val code = connection.responseCode
            val bodyStream = if (code in 200..399) {
                runCatching { connection.inputStream }.getOrNull()
            } else {
                connection.errorStream
            }
            val body = bodyStream
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key!! }
                .mapValues { it.value ?: emptyList() }

            return Response(
                code,
                connection.responseMessage.orEmpty(),
                headers,
                body,
                connection.url.toString()
            )
        } finally {
            connection.disconnect()
        }
    }
}
