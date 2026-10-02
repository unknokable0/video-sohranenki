package com.unknokable.videosohranenki

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class YouTubeFeedVideo(
    val videoId: String,
    val title: String,
    val description: String,
    val publishedAt: Long,
    val thumbnailUrl: String,
    val channelId: String,
    val channelTitle: String,
    val handle: String,
    val durationSeconds: Int = 0
) {
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$videoId"

    fun toVideoItem(): VideoItem = VideoItem(
        messageId = stableMessageId(videoId),
        title = title,
        date = publishedAt.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
        durationSeconds = durationSeconds,
        fileId = 0,
        fileSize = 0L,
        mimeType = "text/html",
        source = "youtube",
        thumbnailUrl = thumbnailUrl,
        externalUrl = watchUrl
    )

    private fun stableMessageId(value: String): Long {
        var hash = 1125899906842597L
        value.forEach { hash = hash * 31L + it.code }
        return -((hash and Long.MAX_VALUE).coerceAtLeast(1L))
    }
}

data class YouTubeChannelSummary(
    val channelId: String,
    val handle: String,
    val title: String,
    val avatarUrl: String,
    val channelUrl: String,
    val videos: List<YouTubeFeedVideo>
)

data class YouTubeFeedSnapshot(
    val channels: List<YouTubeChannelSummary>,
    val videos: List<YouTubeFeedVideo>
)

object YouTubeFeedRepository {
    private data class Source(val handle: String, val url: String, val fallbackTitle: String)

    private val sources = listOf(
        Source("@wt2x2", "https://www.youtube.com/@wt2x2", "WT2X2"),
        Source("@beerloga_t2x2", "https://www.youtube.com/@beerloga_t2x2", "Берлога T2x2")
    )

    private data class ChannelVisual(
        val title: String,
        val avatarUrl: String
    )

    private val durationCache = ConcurrentHashMap<String, Int>()
    private val channelIdCache = ConcurrentHashMap<String, String>()
    private val channelVisualCache = ConcurrentHashMap<String, ChannelVisual>()
    private val thumbnailCache = ConcurrentHashMap<String, String>()
    private val durationGate = Semaphore(4)

    suspend fun loadAll(): YouTubeFeedSnapshot = coroutineScope {
        val channels = sources.map { source ->
            async(Dispatchers.IO) {
                withTimeoutOrNull(8_000L) {
                    loadChannel(source)
                }
            }
        }.mapNotNull { deferred ->
            runCatching { deferred.await() }.getOrNull()
        }
        YouTubeFeedSnapshot(
            channels = channels,
            videos = deduplicate(channels.flatMap { it.videos }.sortedByDescending { it.publishedAt })
        )
    }

    suspend fun enrichSnapshotDurations(
        snapshot: YouTubeFeedSnapshot,
        perChannelLimit: Int = 6
    ): YouTubeFeedSnapshot = coroutineScope {
        val channels = snapshot.channels.map { channel ->
            async(Dispatchers.IO) {
                val visible = channel.videos.take(perChannelLimit)
                val enriched = enrichDurations(visible)
                channel.copy(
                    videos = enriched + channel.videos.drop(visible.size)
                )
            }
        }.mapNotNull { deferred ->
            runCatching { deferred.await() }.getOrNull()
        }

        if (channels.isEmpty()) {
            snapshot
        } else {
            YouTubeFeedSnapshot(
                channels = channels,
                videos = deduplicate(
                    channels.flatMap { it.videos }.sortedByDescending { it.publishedAt }
                )
            )
        }
    }

    private suspend fun loadChannel(source: Source): YouTubeChannelSummary = withContext(Dispatchers.IO) {
        val cachedId = channelIdCache[source.handle]
        var page = ""
        val channelId = if (!cachedId.isNullOrBlank()) {
            cachedId
        } else {
            page = fetchText(source.url + "?hl=ru&gl=PL")
            firstGroup(
                page,
                Regex(""""channelId":"(UC[^"]+)""""),
                Regex(""""browseId":"(UC[^"]+)""""),
                Regex(""""externalId":"(UC[^"]+)""""),
                Regex("""youtube\.com/channel/(UC[A-Za-z0-9_-]+)"""),
                Regex("""<link[^>]+rel="canonical"[^>]+href="https://www\.youtube\.com/channel/(UC[A-Za-z0-9_-]+)"""", RegexOption.IGNORE_CASE)
            ).orEmpty().also { resolved ->
                if (resolved.isNotBlank()) channelIdCache[source.handle] = resolved
            }
        }

        if (channelId.isBlank()) {
            throw IllegalStateException("Не удалось определить YouTube Channel ID для ${source.handle}")
        }

        val cachedVisual = channelVisualCache[source.handle]

        val parsedTitle = if (page.isBlank()) {
            ""
        } else {
            firstGroup(
                page,
                Regex("""<meta\s+property="og:title"\s+content="([^"]+)"""", RegexOption.IGNORE_CASE),
                Regex("""<meta\s+content="([^"]+)"\s+property="og:title"""", RegexOption.IGNORE_CASE)
            )?.let(::decodeHtml)?.trim().orEmpty()
        }
        val title =
            parsedTitle.takeIf { it.isNotBlank() }
                ?: cachedVisual?.title?.takeIf { it.isNotBlank() }
                ?: source.fallbackTitle

        val parsedAvatar = if (page.isBlank()) {
            ""
        } else {
            firstGroup(
                page,
                Regex("""<meta\s+property="og:image"\s+content="([^"]+)"""", RegexOption.IGNORE_CASE),
                Regex("""<meta\s+content="([^"]+)"\s+property="og:image"""", RegexOption.IGNORE_CASE)
            )?.let(::decodeHtml).orEmpty()
        }
        val avatar =
            parsedAvatar.takeIf { it.isNotBlank() }
                ?: cachedVisual?.avatarUrl.orEmpty()

        channelVisualCache[source.handle] = ChannelVisual(title, avatar)

        val feed = fetchText("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
        val videos = parseFeed(feed, channelId, title, source.handle)

        YouTubeChannelSummary(channelId, source.handle, title, avatar, source.url, videos)
    }

    private fun parseFeed(
        xml: String,
        channelId: String,
        channelTitle: String,
        handle: String
    ): List<YouTubeFeedVideo> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xml))
        }
        val result = mutableListOf<YouTubeFeedVideo>()
        var inEntry = false
        var videoId = ""
        var title = ""
        var description = ""
        var thumbnail = ""
        var published = 0L

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.substringAfter(':')) {
                        "entry" -> {
                            inEntry = true
                            videoId = ""
                            title = ""
                            description = ""
                            thumbnail = ""
                            published = 0L
                        }
                        "videoId" -> if (inEntry) videoId = parser.nextText().trim()
                        "title" -> if (inEntry) title = parser.nextText().trim()
                        "published" -> if (inEntry) {
                            published = runCatching { Instant.parse(parser.nextText().trim()).epochSecond }.getOrDefault(0L)
                        }
                        "description" -> if (inEntry) description = parser.nextText().trim()
                        "thumbnail" -> if (inEntry) thumbnail = parser.getAttributeValue(null, "url").orEmpty()
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name.substringAfter(':') == "entry" && inEntry) {
                    if (videoId.isNotBlank() && title.isNotBlank()) {
                        val stableThumbnail =
                            thumbnail.takeIf { it.isNotBlank() }
                                ?: thumbnailCache[videoId]
                                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                        thumbnailCache[videoId] = stableThumbnail

                        result += YouTubeFeedVideo(
                            videoId = videoId,
                            title = decodeHtml(title),
                            description = decodeHtml(description),
                            publishedAt = published,
                            thumbnailUrl = stableThumbnail,
                            channelId = channelId,
                            channelTitle = channelTitle,
                            handle = handle
                        )
                    }
                    inEntry = false
                }
            }
            parser.next()
        }
        return result.sortedByDescending { it.publishedAt }.take(30)
    }

    private suspend fun enrichDurations(
        videos: List<YouTubeFeedVideo>
    ): List<YouTubeFeedVideo> = coroutineScope {
        videos.map { video ->
            async(Dispatchers.IO) {
                val cached = durationCache[video.videoId]
                val duration = cached ?: durationGate.withPermit {
                    loadDurationSeconds(video.videoId)
                }.also { resolved ->
                    if (resolved > 0) durationCache[video.videoId] = resolved
                }
                video.copy(durationSeconds = duration)
            }
        }.awaitAll()
    }

    private fun loadDurationSeconds(videoId: String): Int {
        val page = runCatching {
            fetchText("https://www.youtube.com/watch?v=$videoId&hl=ru&gl=PL")
        }.getOrNull() ?: return 0

        val seconds = firstGroup(
            page,
            Regex("\"lengthSeconds\":\"(\\d+)\"")
        )?.toLongOrNull()
        if (seconds != null) {
            return seconds.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }

        val approxMs = firstGroup(
            page,
            Regex("\"approxDurationMs\":\"(\\d+)\"")
        )?.toLongOrNull()
        return ((approxMs ?: 0L) / 1000L)
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun deduplicate(input: List<YouTubeFeedVideo>): List<YouTubeFeedVideo> {
        val kept = mutableListOf<YouTubeFeedVideo>()
        input.forEach { candidate ->
            if (kept.none { existing -> isDuplicate(existing, candidate) }) kept += candidate
        }
        return kept
    }

    private fun isDuplicate(a: YouTubeFeedVideo, b: YouTubeFeedVideo): Boolean {
        if (a.videoId == b.videoId) return true
        if (abs(a.publishedAt - b.publishedAt) > 5L * 24L * 60L * 60L) return false

        val titleScore = jaccard(normalizedTokens(a.title), normalizedTokens(b.title))
        if (titleScore >= 0.72) return true

        val descriptionScore = jaccard(
            normalizedTokens(a.description).take(60).toSet(),
            normalizedTokens(b.description).take(60).toSet()
        )
        return titleScore >= 0.58 && descriptionScore >= 0.78
    }

    private fun normalizedTokens(raw: String): Set<String> {
        val stop = setOf(
            "t2x2", "т2х2", "т2x2", "стрим", "стрима", "нарезка", "нарезки",
            "лучшее", "видео", "новое", "новый", "часть", "youtube", "ютуб"
        )
        return raw
            .lowercase(Locale.getDefault())
            .replace(Regex("""https?://\S+"""), " ")
            .replace(Regex("""@[a-z0-9_\-]+"""), " ")
            .replace(Regex("""[^a-zа-яё0-9]+""", RegexOption.IGNORE_CASE), " ")
            .split(' ')
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 3 && it !in stop }
            .toSet()
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.count { it in b }
        val union = a.size + b.size - intersection
        return if (union <= 0) 0.0 else intersection.toDouble() / union.toDouble()
    }

    private fun fetchText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4_500
            readTimeout = 6_500
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/141 Mobile Safari/537.36")
            setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.7")
            setRequestProperty("Accept", "text/html,application/atom+xml,application/xml;q=0.9,*/*;q=0.8")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("YouTube HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
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

    private fun decodeHtml(raw: String): String =
        raw.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
}
