package com.unknokable.videosohranenki

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

data class YouTubeNativeSource(
    val mediaUrl: String,
    val durationSeconds: Int,
    val title: String,
    val thumbnailUrl: String?
)

sealed class YouTubeNativeResolveException(message: String) : Exception(message)

class YouTubeAgeRestrictedException : YouTubeNativeResolveException(
    "Этот ролик имеет возрастное ограничение YouTube."
)

class YouTubeUnavailableException : YouTubeNativeResolveException(
    "Этот ролик сейчас недоступен для воспроизведения в SOHR."
)

class YouTubeNetworkException : YouTubeNativeResolveException(
    "Не удалось связаться с YouTube. Проверьте интернет и попробуйте ещё раз."
)

/**
 * Resolves YouTube watch pages into a direct media stream and hands that
 * stream to SOHR's normal native Media3 PlayerScreen.
 */
object YouTubeNativeResolver {
    @Volatile
    private var initialized = false
    private val initLock = Any()

    suspend fun resolve(video: YouTubeFeedVideo): YouTubeNativeSource =
        withContext(Dispatchers.IO) {
            ensureInitialized()

            val info = try {
                StreamInfo.getInfo(video.watchUrl)
            } catch (error: Throwable) {
                throw mapFailure(error)
            }

            val progressive = info.videoStreams
                .asSequence()
                .filter { it.isUrl && !it.isVideoOnly }
                .filter { it.content.startsWith("https://") || it.content.startsWith("http://") }
                .sortedWith(
                    compareByDescending<VideoStream> { it.height }
                        .thenByDescending { it.bitrate }
                )
                .firstOrNull()

            val mediaUrl = progressive?.content
                ?: info.hlsUrl.takeIf(::isDirectHttpUrl)
                ?: info.dashMpdUrl.takeIf(::isDirectHttpUrl)
                ?: throw YouTubeUnavailableException()

            YouTubeNativeSource(
                mediaUrl = mediaUrl,
                durationSeconds = info.duration
                    .coerceIn(0L, Int.MAX_VALUE.toLong())
                    .toInt(),
                title = info.name.ifBlank { video.title },
                thumbnailUrl = video.thumbnailUrl
            )
        }

    private fun mapFailure(error: Throwable): YouTubeNativeResolveException {
        if (error is YouTubeNativeResolveException) return error
        if (error is UnknownHostException || error is SocketTimeoutException) {
            return YouTubeNetworkException()
        }

        val messages = mutableListOf<String>()
        var cause: Throwable? = error
        while (cause != null) {
            cause.message?.takeIf { it.isNotBlank() }?.let(messages::add)
            cause = cause.cause
        }
        val combined = messages.joinToString(" ").lowercase(Locale.ROOT)

        return when {
            "age-restricted" in combined ||
                "age restricted" in combined ||
                "cannot be watched anonymously" in combined ||
                "confirm your age" in combined ||
                "sign in to confirm your age" in combined ->
                    YouTubeAgeRestrictedException()

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
