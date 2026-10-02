package com.unknokable.videosohranenki

import android.content.Context
import android.net.Uri
import com.zemer.cipher.CipherDeobfuscator
import com.zemer.cipher.ZemerCipher
import com.zemer.cipher.potoken.PoTokenResult
import com.zemer.cipher.potoken.PoTokenWebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    "YouTube не подтвердил обычную сессию воспроизведения."
)

class YouTubeSessionInitException : YouTubeNativeResolveException(
    "SOHR не смог подготовить защищённую YouTube-сессию."
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

    private val poTokenLock = Mutex()
    private var poTokenWebView: PoTokenWebView? = null
    private var poTokenSessionId: String? = null
    private var poTokenSessionPot: String? = null
    @Volatile
    private var applicationContext: Context? = null

    private const val FALLBACK_WEB_API_KEY =
        "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val FALLBACK_WEB_VERSION = "2.20260708.00.00"
    private const val MWEB_CLIENT_VERSION = "2.20260708.05.00"
    private const val MWEB_CLIENT_NAME = 2
    private const val VISIONOS_CLIENT_VERSION = "1.02"
    private const val VISIONOS_CLIENT_NAME = 101
    private const val WEB_SAFARI_CLIENT_VERSION = "2.20260708.00.00"
    private const val WEB_SAFARI_CLIENT_NAME = 1
    private const val BOOTSTRAP_TTL_MS = 3L * 60L * 60L * 1000L
    private const val WEB_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/141 Mobile Safari/537.36"
    private const val MWEB_USER_AGENT =
        "Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 " +
            "Mobile/15E148 Safari/604.1,gzip(gfe)"
    private const val VISIONOS_USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"
    private const val WEB_SAFARI_USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.5 Safari/605.1.15,gzip(gfe)"

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
        applicationContext = context.applicationContext
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

            val failures = mutableListOf<YouTubeNativeResolveException>()

            try {
                val info = StreamInfo.getInfo(video.watchUrl)
                return@withContext sourceFromNewPipe(video, info)
            } catch (error: Throwable) {
                // NewPipe can confuse anti-bot responses with availability errors. Keep it as
                // one candidate and let current Innertube clients make the final decision.
                failures += mapFailure(error)
            }

            try {
                return@withContext resolveWithVisionOs(video)
            } catch (error: Throwable) {
                val mapped = mapFailure(error)
                if (mapped is YouTubeAgeRestrictedException) throw mapped
                failures += mapped
            }

            try {
                return@withContext resolveWithWebSafariHls(video)
            } catch (error: Throwable) {
                val mapped = mapFailure(error)
                if (mapped is YouTubeAgeRestrictedException) throw mapped
                failures += mapped
            }

            if (zemerInitialized) {
                try {
                    return@withContext resolveWithPoToken(video)
                } catch (error: Throwable) {
                    val mapped = mapFailure(error)
                    if (mapped is YouTubeAgeRestrictedException) throw mapped
                    failures += mapped
                }
            }

            val signIn = failures.firstOrNull { it is YouTubeSignInRequiredException }
            val network = failures.firstOrNull { it is YouTubeNetworkException }
            throw signIn ?: network ?: YouTubeUnavailableException()
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

    private suspend fun resolveWithVisionOs(video: YouTubeFeedVideo): YouTubeNativeSource {
        val bootstrap = loadWebBootstrap()
        val response = requestVisionOsPlayer(video.videoId, bootstrap)
        val playability = response.optJSONObject("playabilityStatus")
        val status = playability?.optString("status").orEmpty()
        val detail = playabilityDetail(playability)

        if (isAgeRestrictedStatus(status, detail)) throw YouTubeAgeRestrictedException()
        if (status != "OK") {
            throw when {
                status == "LOGIN_REQUIRED" ||
                    "sign in to confirm" in detail ||
                    "login required" in detail ->
                        YouTubeSignInRequiredException()
                else -> YouTubeUnavailableException()
            }
        }

        return sourceFromPlayerResponse(
            video = video,
            response = response,
            streamingPoToken = null,
            userAgent = VISIONOS_USER_AGENT,
            referer = "https://www.youtube.com/"
        )
    }

    private suspend fun resolveWithWebSafariHls(video: YouTubeFeedVideo): YouTubeNativeSource {
        val bootstrap = loadWebBootstrap()
        val response = requestWebSafariPlayer(video.videoId, bootstrap)
        val playability = response.optJSONObject("playabilityStatus")
        val status = playability?.optString("status").orEmpty()
        val detail = playabilityDetail(playability)

        if (isAgeRestrictedStatus(status, detail)) throw YouTubeAgeRestrictedException()
        if (status != "OK") {
            throw when {
                status == "LOGIN_REQUIRED" ||
                    "sign in to confirm" in detail ||
                    "login required" in detail ->
                        YouTubeSignInRequiredException()
                else -> YouTubeUnavailableException()
            }
        }

        val streaming = response.optJSONObject("streamingData")
            ?: throw YouTubeUnavailableException()
        val hls = streaming.optString("hlsManifestUrl").takeIf(::isDirectHttpUrl)
            ?: throw YouTubeUnavailableException()

        if (!probeMediaUrl(hls, WEB_SAFARI_USER_AGENT, "https://www.youtube.com/")) {
            throw YouTubeUnavailableException()
        }

        return sourceFromWebResponse(
            video = video,
            response = response,
            mediaUrl = hls,
            secondaryAudioUrl = null
        )
    }

    private suspend fun sourceFromPlayerResponse(
        video: YouTubeFeedVideo,
        response: JSONObject,
        streamingPoToken: String?,
        userAgent: String,
        referer: String
    ): YouTubeNativeSource {
        val streaming = response.optJSONObject("streamingData")
            ?: throw YouTubeUnavailableException()
        val formats = streaming.optJSONArray("formats").asObjects()
        val adaptive = streaming.optJSONArray("adaptiveFormats").asObjects()

        val progressiveCandidates = formats
            .asSequence()
            .filter(::isProgressiveVideo)
            .filterNot(::isDrmFormat)
            .sortedWith(webVideoComparator())
            .take(8)
            .toList()

        val progressiveUrl = resolveFirstFormatUrl(
            candidates = progressiveCandidates,
            videoId = video.videoId,
            streamingPoToken = streamingPoToken
        )
        if (
            progressiveUrl != null &&
            probeMediaUrl(progressiveUrl, userAgent, referer)
        ) {
            return sourceFromWebResponse(
                video = video,
                response = response,
                mediaUrl = progressiveUrl,
                secondaryAudioUrl = null
            )
        }

        val splitVideoCandidates = adaptive
            .asSequence()
            .filter { it.optString("mimeType").startsWith("video/") }
            .filterNot(::isDrmFormat)
            .sortedWith(webVideoComparator())
            .take(8)
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
            .take(6)
            .toList()

        val splitVideoUrl = resolveFirstFormatUrl(
            candidates = splitVideoCandidates,
            videoId = video.videoId,
            streamingPoToken = streamingPoToken
        )
        val splitAudioUrl = resolveFirstFormatUrl(
            candidates = splitAudioCandidates,
            videoId = video.videoId,
            streamingPoToken = streamingPoToken
        )
        if (
            splitVideoUrl != null &&
            splitAudioUrl != null &&
            probeMediaUrl(splitVideoUrl, userAgent, referer) &&
            probeMediaUrl(splitAudioUrl, userAgent, referer)
        ) {
            return sourceFromWebResponse(
                video = video,
                response = response,
                mediaUrl = splitVideoUrl,
                secondaryAudioUrl = splitAudioUrl
            )
        }

        val hls = streaming.optString("hlsManifestUrl").takeIf(::isDirectHttpUrl)
        if (
            hls != null &&
            probeMediaUrl(hls, userAgent, referer)
        ) {
            return sourceFromWebResponse(
                video = video,
                response = response,
                mediaUrl = hls,
                secondaryAudioUrl = null
            )
        }

        val dash = streaming.optString("dashManifestUrl").takeIf(::isDirectHttpUrl)
        if (
            dash != null &&
            probeMediaUrl(dash, userAgent, referer)
        ) {
            return sourceFromWebResponse(
                video = video,
                response = response,
                mediaUrl = dash,
                secondaryAudioUrl = null
            )
        }

        throw YouTubeUnavailableException()
    }

    private suspend fun resolveWithPoToken(video: YouTubeFeedVideo): YouTubeNativeSource {
        val bootstrap = loadWebBootstrap()
        val poTokens = createStablePoTokens(
            videoId = video.videoId,
            sessionId = bootstrap.visitorData
        )

        val signatureTimestamp = runCatching {
            CipherDeobfuscator.signatureTimestamp()
        }.getOrNull()

        var response = requestMwebPlayer(
            videoId = video.videoId,
            bootstrap = bootstrap,
            playerPoToken = null,
            signatureTimestamp = signatureTimestamp
        )

        var playability = response.optJSONObject("playabilityStatus")
        var status = playability?.optString("status").orEmpty()
        var detail = playabilityDetail(playability)

        if (isAgeRestrictedStatus(status, detail)) {
            throw YouTubeAgeRestrictedException()
        }

        if (
            status != "OK" &&
            (
                "not a bot" in detail ||
                    "sign in to confirm" in detail
            )
        ) {
            response = requestMwebPlayer(
                videoId = video.videoId,
                bootstrap = bootstrap,
                playerPoToken = poTokens.streamingDataPoToken,
                signatureTimestamp = signatureTimestamp
            )
            playability = response.optJSONObject("playabilityStatus")
            status = playability?.optString("status").orEmpty()
            detail = playabilityDetail(playability)

            if (isAgeRestrictedStatus(status, detail)) {
                throw YouTubeAgeRestrictedException()
            }
        }

        if (status != "OK") {
            throw when {
                status == "LOGIN_REQUIRED" ||
                    "sign in to confirm" in detail ||
                    "not a bot" in detail ||
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
            streamingPoToken = poTokens.streamingDataPoToken
        )
        val splitAudioUrl = resolveFirstFormatUrl(
            candidates = splitAudioCandidates,
            videoId = video.videoId,
            streamingPoToken = poTokens.streamingDataPoToken
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
            streamingPoToken = poTokens.streamingDataPoToken
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
            ?.let { appendPoToken(it, poTokens.streamingDataPoToken) }

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

    private suspend fun createStablePoTokens(
        videoId: String,
        sessionId: String
    ): PoTokenResult {
        var lastError: Throwable? = null

        repeat(2) {
            try {
                return poTokenLock.withLock {
                    val context = applicationContext ?: throw YouTubeSessionInitException()

                    var generator = poTokenWebView
                    val needsRecreate =
                        generator == null ||
                            generator.isExpired ||
                            generator.isDead ||
                            poTokenSessionId != sessionId ||
                            poTokenSessionPot.isNullOrBlank()

                    if (needsRecreate) {
                        generator?.close()
                        poTokenWebView = null
                        poTokenSessionId = null
                        poTokenSessionPot = null

                        val fresh = withTimeout(50_000L) {
                            PoTokenWebView.getNewPoTokenGenerator(context)
                        }

                        val sessionPot = try {
                            withTimeout(20_000L) {
                                fresh.generatePoToken(sessionId)
                            }
                        } catch (error: Throwable) {
                            fresh.close()
                            throw error
                        }

                        poTokenWebView = fresh
                        poTokenSessionId = sessionId
                        poTokenSessionPot = sessionPot
                        generator = fresh
                    }

                    val activeGenerator = generator ?: throw YouTubeSessionInitException()
                    val sessionPot = poTokenSessionPot ?: throw YouTubeSessionInitException()
                    val videoPot = withTimeout(20_000L) {
                        activeGenerator.generatePoToken(videoId)
                    }

                    PoTokenResult(
                        playerRequestPoToken = sessionPot,
                        streamingDataPoToken = videoPot
                    )
                }
            } catch (error: Throwable) {
                lastError = error
                poTokenLock.withLock {
                    poTokenWebView?.close()
                    poTokenWebView = null
                    poTokenSessionId = null
                    poTokenSessionPot = null
                }
            }
        }

        val failure = YouTubeSessionInitException()
        lastError?.let { runCatching { failure.initCause(it) } }
        throw failure
    }

    private suspend fun resolveFirstFormatUrl(
        candidates: List<JSONObject>,
        videoId: String,
        streamingPoToken: String?
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
        streamingPoToken: String?
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
        return if (streamingPoToken.isNullOrBlank()) {
            transformed
        } else {
            appendPoToken(transformed, streamingPoToken)
        }
    }

    private fun playabilityDetail(playability: JSONObject?): String =
        buildString {
            append(playability?.optString("reason").orEmpty())
            append(' ')
            append(playability?.optJSONObject("errorScreen")?.toString().orEmpty())
        }.lowercase(Locale.ROOT)

    private fun requestVisionOsPlayer(
        videoId: String,
        bootstrap: WebBootstrap
    ): JSONObject {
        val client = JSONObject()
            .put("clientName", "VISIONOS")
            .put("clientVersion", VISIONOS_CLIENT_VERSION)
            .put("deviceMake", "Apple")
            .put("deviceModel", "RealityDevice17,1")
            .put("userAgent", VISIONOS_USER_AGENT)
            .put("osName", "visionOS")
            .put("osVersion", "26.5.23O471")
            .put("hl", "ru")
            .put("gl", "PL")
            .put("visitorData", bootstrap.visitorData)

        return requestInnertubePlayer(
            videoId = videoId,
            bootstrap = bootstrap,
            client = client,
            clientNameId = VISIONOS_CLIENT_NAME,
            clientVersion = VISIONOS_CLIENT_VERSION,
            userAgent = VISIONOS_USER_AGENT,
            origin = "https://www.youtube.com",
            referer = "https://www.youtube.com/"
        )
    }

    private fun requestWebSafariPlayer(
        videoId: String,
        bootstrap: WebBootstrap
    ): JSONObject {
        val client = JSONObject()
            .put("clientName", "WEB")
            .put("clientVersion", WEB_SAFARI_CLIENT_VERSION)
            .put("userAgent", WEB_SAFARI_USER_AGENT)
            .put("hl", "ru")
            .put("gl", "PL")
            .put("visitorData", bootstrap.visitorData)

        return requestInnertubePlayer(
            videoId = videoId,
            bootstrap = bootstrap,
            client = client,
            clientNameId = WEB_SAFARI_CLIENT_NAME,
            clientVersion = WEB_SAFARI_CLIENT_VERSION,
            userAgent = WEB_SAFARI_USER_AGENT,
            origin = "https://www.youtube.com",
            referer = "https://www.youtube.com/"
        )
    }

    private fun requestInnertubePlayer(
        videoId: String,
        bootstrap: WebBootstrap,
        client: JSONObject,
        clientNameId: Int,
        clientVersion: String,
        userAgent: String,
        origin: String,
        referer: String
    ): JSONObject {
        val body = JSONObject()
            .put("context", JSONObject().put("client", client))
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)

        val endpoint =
            "https://www.youtube.com/youtubei/v1/player" +
                "?prettyPrint=false&key=" + bootstrap.apiKey

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 16_000
            instanceFollowRedirects = true
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.7")
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Origin", origin)
            setRequestProperty("X-Origin", origin)
            setRequestProperty("Referer", referer)
            setRequestProperty("X-Goog-Api-Format-Version", "1")
            setRequestProperty("X-Goog-Visitor-Id", bootstrap.visitorData)
            setRequestProperty("X-YouTube-Client-Name", clientNameId.toString())
            setRequestProperty("X-YouTube-Client-Version", clientVersion)
        }

        try {
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
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

    private fun requestMwebPlayer(
        videoId: String,
        bootstrap: WebBootstrap,
        playerPoToken: String?,
        signatureTimestamp: Int?
    ): JSONObject {
        val client = JSONObject()
            .put("clientName", "MWEB")
            .put("clientVersion", MWEB_CLIENT_VERSION)
            .put("userAgent", MWEB_USER_AGENT)
            .put("hl", "ru")
            .put("gl", "PL")
            .put("visitorData", bootstrap.visitorData)

        val body = JSONObject()
            .put("context", JSONObject().put("client", client))
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)

        if (!playerPoToken.isNullOrBlank()) {
            body.put(
                "serviceIntegrityDimensions",
                JSONObject().put("poToken", playerPoToken)
            )
        }

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
            setRequestProperty("User-Agent", MWEB_USER_AGENT)
            setRequestProperty("Origin", "https://m.youtube.com")
            setRequestProperty("X-Origin", "https://m.youtube.com")
            setRequestProperty("Referer", "https://m.youtube.com/")
            setRequestProperty("X-Goog-Api-Format-Version", "1")
            setRequestProperty("X-Goog-Visitor-Id", bootstrap.visitorData)
            setRequestProperty("X-YouTube-Client-Name", MWEB_CLIENT_NAME.toString())
            setRequestProperty("X-YouTube-Client-Version", MWEB_CLIENT_VERSION)
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
                ?: throw YouTubeSignInRequiredException()

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

    private fun probeMediaUrl(
        url: String,
        userAgent: String = MWEB_USER_AGENT,
        referer: String = "https://m.youtube.com/"
    ): Boolean {
        val connection = runCatching {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("Range", "bytes=0-1023")
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Referer", referer)
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

            "signinconfirmnotbot" in classChain ||
                "loginrequired" in classChain ||
                "cannot be watched anonymously" in combined ||
                "sign in to confirm you're not a bot" in combined ||
                "sign in to confirm you’re not a bot" in combined ||
                "login required" in combined ||
                "potoken" in classChain ->
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
