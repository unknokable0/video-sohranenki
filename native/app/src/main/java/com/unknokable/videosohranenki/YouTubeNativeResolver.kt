package com.unknokable.videosohranenki

import android.content.Context
import android.net.Uri
import com.zemer.cipher.CipherDeobfuscator
import com.zemer.cipher.ZemerCipher
import com.zemer.cipher.potoken.PoTokenGenerator
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
    "YouTube требует подтверждённую сессию для этого ролика."
)

class YouTubePoTokenException : YouTubeNativeResolveException(
    "SOHR не удалось подготовить защищённую сессию YouTube."
)

class YouTubeUnavailableException : YouTubeNativeResolveException(
    "Этот ролик сейчас недоступен для воспроизведения в SOHR."
)

class YouTubeNetworkException : YouTubeNativeResolveException(
    "Не удалось связаться с YouTube. Проверьте интернет и попробуйте ещё раз."
)

object YouTubeNativeResolver {
    @Volatile
    private var newPipeInitialized = false
    private val newPipeInitLock = Any()

    @Volatile
    private var zemerInitialized = false
    private val zemerInitLock = Any()

    @Volatile
    private var poTokenGenerator: PoTokenGenerator = PoTokenGenerator()
    private val poTokenGeneratorLock = Any()

    private const val FALLBACK_WEB_API_KEY =
        "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val FALLBACK_WEB_VERSION = "2.20260708.00.00"
    private const val WEB_CLIENT_NAME = 1
    private const val BOOTSTRAP_TTL_MS = 3L * 60L * 60L * 1000L
    private const val WEB_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/141 Mobile Safari/537.36"

    private data class WebBootstrap(
        val visitorData: String,
        val apiKey: String,
        val clientVersion: String,
        val fetchedAtMs: Long
    )

    @Volatile
    private var cachedBootstrap: WebBootstrap? = null
    private val bootstrapLock = Any()

    fun initialize(context: Context) {
        if (zemerInitialized) return
        synchronized(zemerInitLock) {
            if (zemerInitialized) return
            ZemerCipher.initialize(
                context = context.applicationContext,
                debugLogging = false
            )
            zemerInitialized = true
        }
    }

    suspend fun resolve(video: YouTubeFeedVideo): YouTubeNativeSource =
        withContext(Dispatchers.IO) {
            ensureNewPipeInitialized()

            var firstFailure: YouTubeNativeResolveException? = null
            try {
                val info = StreamInfo.getInfo(video.watchUrl)
                return@withContext sourceFromNewPipe(video, info)
            } catch (error: Throwable) {
                // NewPipe can classify an anonymous/anti-bot response as age-restricted even
                // when the same public video is playable through SOHR's authenticated BotGuard
                // session. Treat NewPipe as a hint only and always let the stronger PO Token
                // /player path make the final age-gate decision.
                firstFailure = mapFailure(error)
            }

            if (!zemerInitialized) {
                throw firstFailure ?: YouTubeUnavailableException()
            }

            try {
                resolveWithPoToken(video)
            } catch (error: Throwable) {
                val mapped = mapFailure(error)
                if (mapped is YouTubeAgeRestrictedException) throw mapped

                throw when {
                    mapped is YouTubeNetworkException &&
                        firstFailure is YouTubeNetworkException -> mapped
                    mapped is YouTubeSignInRequiredException -> mapped
                    mapped is YouTubeUnavailableException &&
                        firstFailure is YouTubeSignInRequiredException ->
                            firstFailure ?: mapped
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

    private data class PoTokenSession(
        val bootstrap: WebBootstrap,
        val playerRequestPoToken: String,
        val streamingDataPoToken: String
    )

    private fun invalidatePoTokenSession() {
        synchronized(bootstrapLock) {
            cachedBootstrap = null
        }
        synchronized(poTokenGeneratorLock) {
            poTokenGenerator = PoTokenGenerator()
        }
    }

    private fun generatePoTokenSession(videoId: String): PoTokenSession {
        var lastFailure: Throwable? = null

        repeat(2) { attempt ->
            if (attempt > 0) {
                invalidatePoTokenSession()
            }

            val bootstrap = try {
                loadWebBootstrap()
            } catch (error: YouTubeNetworkException) {
                throw error
            } catch (error: Throwable) {
                lastFailure = error
                return@repeat
            }

            val generator = synchronized(poTokenGeneratorLock) { poTokenGenerator }
            val tokens = try {
                generator.getWebClientPoToken(
                    videoId = videoId,
                    sessionId = bootstrap.visitorData
                )
            } catch (error: Throwable) {
                lastFailure = error
                null
            }

            if (
                tokens != null &&
                tokens.playerRequestPoToken.isNotBlank() &&
                tokens.streamingDataPoToken.isNotBlank()
            ) {
                return PoTokenSession(
                    bootstrap = bootstrap,
                    playerRequestPoToken = tokens.playerRequestPoToken,
                    streamingDataPoToken = tokens.streamingDataPoToken
                )
            }
        }

        invalidatePoTokenSession()
        throw YouTubePoTokenException()
    }

    private suspend fun resolveWithPoToken(video: YouTubeFeedVideo): YouTubeNativeSource {
        val session = generatePoTokenSession(video.videoId)
        val bootstrap = session.bootstrap

        val signatureTimestamp = runCatching {
            CipherDeobfuscator.signatureTimestamp()
        }.getOrNull()

        val response = requestWebPlayer(
            videoId = video.videoId,
            bootstrap = bootstrap,
            playerPoToken = session.playerRequestPoToken,
            signatureTimestamp = signatureTimestamp
        )

        val playability = response.optJSONObject("playabilityStatus")
        val status = playability?.optString("status").orEmpty()
        val detail = buildString {
            append(playability?.optString("reason").orEmpty())
            append(' ')
            append(playability?.optJSONObject("errorScreen")?.toString().orEmpty())
        }.lowercase(Locale.ROOT)

        if (isAgeRestrictedStatus(status, detail)) {
            throw YouTubeAgeRestrictedException()
        }

        if (status != "OK") {
            throw when {
                "not a bot" in detail ||
                    "confirm you're not a bot" in detail ||
                    "confirm you’re not a bot" in detail -> {
                        invalidatePoTokenSession()
                        YouTubePoTokenException()
                    }
                status == "LOGIN_REQUIRED" ||
                    "sign in to confirm" in detail ||
                    "login required" in detail ->
                        YouTubeSignInRequiredException()
                "private" in detail ||
                    "unavailable" in detail ||
                    "not available" in detail ->
                        YouTubeUnavailableException()
                else -> YouTubeUnavailableException()
            }
        }

        val streaming = response.optJSONObject("streamingData")
            ?: throw YouTubeUnavailableException()
        val formats = streaming.optJSONArray("formats").asObjects()
        val adaptive = streaming.optJSONArray("adaptiveFormats").asObjects()

        val splitVideoCandidates = adaptive
            .asSequence()
            .filter { it.optString("mimeType").startsWith("video/") }
            .filterNot(::isDrmFormat)
            .sortedWith(webVideoComparator())
            .take(5)
            .toList()

        val splitAudioCandidates = adaptive
            .asSequence()
            .filter { it.optString("mimeType").startsWith("audio/") }
            .filterNot(::isDrmFormat)
            .sortedWith(
                compareByDescending<JSONObject> {
                    if (it.optString("mimeType").contains("mp4a")) 1 else 0
                }.thenByDescending { it.optInt("bitrate", 0) }
            )
            .take(4)
            .toList()

        val splitVideoUrl = resolveFirstFormatUrl(
            candidates = splitVideoCandidates,
            videoId = video.videoId,
            streamingPoToken = session.streamingDataPoToken
        )
        val splitAudioUrl = resolveFirstFormatUrl(
            candidates = splitAudioCandidates,
            videoId = video.videoId,
            streamingPoToken = session.streamingDataPoToken
        )

        if (
            splitVideoUrl != null &&
            splitAudioUrl != null &&
            probeMediaUrl(splitVideoUrl) &&
            probeMediaUrl(splitAudioUrl)
        ) {
            return sourceFromWebResponse(
                video = video,
                response = response,
                mediaUrl = splitVideoUrl,
                secondaryAudioUrl = splitAudioUrl
            )
        }

        val progressiveCandidates = formats
            .asSequence()
            .filter(::isProgressiveVideo)
            .filterNot(::isDrmFormat)
            .sortedWith(webVideoComparator())
            .take(5)
            .toList()

        val progressiveUrl = resolveFirstFormatUrl(
            candidates = progressiveCandidates,
            videoId = video.videoId,
            streamingPoToken = session.streamingDataPoToken
        )

        if (progressiveUrl != null && probeMediaUrl(progressiveUrl)) {
            return sourceFromWebResponse(
                video = video,
                response = response,
                mediaUrl = progressiveUrl,
                secondaryAudioUrl = null
            )
        }

        val hls = streaming.optString("hlsManifestUrl")
            .takeIf(::isDirectHttpUrl)
            ?.let { appendPoToken(it, session.streamingDataPoToken) }

        if (hls != null) {
            return sourceFromWebResponse(
                video = video,
                response = response,
                mediaUrl = hls,
                secondaryAudioUrl = null
            )
        }

        runCatching { CipherDeobfuscator.onStreamRejected() }
        throw YouTubeUnavailableException()
    }

    private suspend fun resolveFirstFormatUrl(
        candidates: List<JSONObject>,
        videoId: String,
        streamingPoToken: String
    ): String? {
        for (format in candidates) {
            val resolved = resolveFormatUrl(
                format = format,
                videoId = videoId,
                streamingPoToken = streamingPoToken
            )
            if (resolved != null) return resolved
        }
        return null
    }

    private suspend fun resolveFormatUrl(
        format: JSONObject,
        videoId: String,
        streamingPoToken: String
    ): String? {
        var url = format.optString("url").takeIf(::isDirectHttpUrl)

        if (url == null) {
            val signatureCipher = format.optString("signatureCipher")
                .ifBlank { format.optString("cipher") }
            if (signatureCipher.isNotBlank()) {
                url = CipherDeobfuscator.deobfuscateStreamUrl(
                    signatureCipher = signatureCipher,
                    videoId = videoId
                )
            }
        }

        if (!isDirectHttpUrl(url)) return null

        val transformed = CipherDeobfuscator.transformNParamInUrl(url!!)
        return appendPoToken(transformed, streamingPoToken)
    }

    private fun requestWebPlayer(
        videoId: String,
        bootstrap: WebBootstrap,
        playerPoToken: String,
        signatureTimestamp: Int?
    ): JSONObject {
        val client = JSONObject()
            .put("clientName", "WEB")
            .put("clientVersion", bootstrap.clientVersion)
            .put("hl", "ru")
            .put("gl", "PL")
            .put("visitorData", bootstrap.visitorData)

        val body = JSONObject()
            .put("context", JSONObject().put("client", client))
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .put(
                "serviceIntegrityDimensions",
                JSONObject().put("poToken", playerPoToken)
            )

        if (signatureTimestamp != null && signatureTimestamp > 0) {
            body.put(
                "playbackContext",
                JSONObject().put(
                    "contentPlaybackContext",
                    JSONObject().put("signatureTimestamp", signatureTimestamp)
                )
            )
        }

        val endpoint =
            "https://www.youtube.com/youtubei/v1/player" +
                "?prettyPrint=false&key=" + bootstrap.apiKey

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
            setRequestProperty("User-Agent", WEB_USER_AGENT)
            setRequestProperty("Origin", "https://www.youtube.com")
            setRequestProperty("X-Origin", "https://www.youtube.com")
            setRequestProperty("Referer", "https://www.youtube.com/")
            setRequestProperty("X-Goog-Api-Format-Version", "1")
            setRequestProperty("X-Goog-Visitor-Id", bootstrap.visitorData)
            setRequestProperty("X-YouTube-Client-Name", WEB_CLIENT_NAME.toString())
            setRequestProperty("X-YouTube-Client-Version", bootstrap.clientVersion)
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

    private fun loadWebBootstrap(): WebBootstrap {
        val now = System.currentTimeMillis()
        cachedBootstrap?.let { cached ->
            if (now - cached.fetchedAtMs < BOOTSTRAP_TTL_MS) return cached
        }

        synchronized(bootstrapLock) {
            val freshNow = System.currentTimeMillis()
            cachedBootstrap?.let { cached ->
                if (freshNow - cached.fetchedAtMs < BOOTSTRAP_TTL_MS) return cached
            }

            val page = fetchText(
                "https://www.youtube.com/?hl=ru&gl=PL",
                WEB_USER_AGENT
            )

            val visitorData = firstGroup(
                page,
                Regex("""\"VISITOR_DATA\":\"([^\"]+)\""""),
                Regex("""\"visitorData\":\"([^\"]+)\"""")
            )?.let(::decodeBootstrapValue)
                ?.takeIf { it.startsWith("Cg") }
                ?: throw YouTubePoTokenException()

            val apiKey = firstGroup(
                page,
                Regex("""\"INNERTUBE_API_KEY\":\"([^\"]+)\"""")
            )?.let(::decodeBootstrapValue)
                ?.takeIf { it.isNotBlank() }
                ?: FALLBACK_WEB_API_KEY

            val clientVersion = firstGroup(
                page,
                Regex("""\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"""")
            )?.let(::decodeBootstrapValue)
                ?.takeIf { it.isNotBlank() }
                ?: FALLBACK_WEB_VERSION

            return WebBootstrap(
                visitorData = visitorData,
                apiKey = apiKey,
                clientVersion = clientVersion,
                fetchedAtMs = freshNow
            ).also { cachedBootstrap = it }
        }
    }

    private fun sourceFromWebResponse(
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

    private fun webVideoComparator(): Comparator<JSONObject> =
        compareByDescending<JSONObject> {
            val mime = it.optString("mimeType")
            when {
                mime.startsWith("video/mp4") && mime.contains("avc1") -> 3
                mime.startsWith("video/mp4") -> 2
                else -> 1
            }
        }.thenByDescending {
            val height = it.optInt("height", 0)
            when {
                height in 1..1080 -> 10_000 + height
                height > 1080 -> 1
                else -> 0
            }
        }.thenByDescending { it.optInt("bitrate", 0) }

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

    private fun probeMediaUrl(url: String): Boolean {
        val connection = runCatching {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("Range", "bytes=0-1023")
                setRequestProperty("User-Agent", WEB_USER_AGENT)
                setRequestProperty("Accept", "*/*")
            }
        }.getOrNull() ?: return false

        return try {
            val code = connection.responseCode
            if (code != 200 && code != 206) return false
            val stream = runCatching { connection.inputStream }.getOrNull() ?: return false
            stream.use {
                val buffer = ByteArray(1024)
                it.read(buffer) > 0
            }
        } catch (_: Throwable) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun appendPoToken(url: String, token: String): String {
        if (token.isBlank()) return url
        val separator = if ('?' in url) "&" else "?"
        return url + separator + "pot=" + Uri.encode(token)
    }

    private fun JSONArray?.asObjects(): List<JSONObject> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.let(::add)
            }
        }
    }

    private fun decodeBootstrapValue(raw: String): String =
        raw.replace("\\u003d", "=")
            .replace("\\u0026", "&")
            .replace("\\u002f", "/")
            .replace("\\/", "/")

    private fun fetchText(url: String, userAgent: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.7")
            setRequestProperty("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                if (code == 408 || code == 429 || code >= 500) {
                    throw YouTubeNetworkException()
                }
                throw YouTubeUnavailableException()
            }
            return connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun firstGroup(raw: String, vararg regexes: Regex): String? {
        regexes.forEach { regex ->
            regex.find(raw)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
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

            "potoken" in classChain ||
                "botguard" in combined ||
                "integrity token" in combined ||
                "po token" in combined ->
                    YouTubePoTokenException()

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

    private fun ensureNewPipeInitialized() {
        if (newPipeInitialized) return
        synchronized(newPipeInitLock) {
            if (newPipeInitialized) return
            NewPipe.init(
                SohrExtractorDownloader(),
                Localization("ru", "RU"),
                ContentCountry("PL")
            )
            newPipeInitialized = true
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
