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
import java.util.concurrent.ConcurrentHashMap

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

    suspend fun expandSnapshotVideos(
        snapshot: YouTubeFeedSnapshot,
        perChannelLimit: Int = 30
    ): YouTubeFeedSnapshot = coroutineScope {
        val channels = snapshot.channels.map { channel ->
            async(Dispatchers.IO) {
                val page = runCatching {
                    withTimeoutOrNull(7_000L) {
                        fetchText(channel.channelUrl + "/videos?hl=ru&gl=PL")
                    }
                }.getOrNull()

                if (page.isNullOrBlank()) {
                    channel
                } else {
                    val pageVideos = parseVideosPage(
                        page = page,
                        channel = channel,
                        limit = perChannelLimit
                    )
                    channel.copy(
                        videos = mergeChannelVideos(
                            primary = channel.videos,
                            secondary = pageVideos,
                            limit = perChannelLimit
                        )
                    )
                }
            }
        }.mapNotNull { deferred ->
            runCatching { deferred.await() }.getOrNull()
        }

        if (channels.isEmpty()) {
            snapshot
        } else {
            YouTubeFeedSnapshot(
                channels = channels,
                videos = channels
                    .flatMap { it.videos }
                    .distinctBy { it.videoId }
                    .sortedByDescending { it.publishedAt }
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
                            thumbnailCache[videoId]
                                ?: sharpThumbnailUrl(videoId)
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

    private fun parseVideosPage(
        page: String,
        channel: YouTubeChannelSummary,
        limit: Int
    ): List<YouTubeFeedVideo> {
        val rendererMatches = Regex(
            """"(?:videoRenderer|gridVideoRenderer)":\{"videoId":"([A-Za-z0-9_-]{11})""""
        )
            .findAll(page)
            .distinctBy { it.groupValues.getOrNull(1).orEmpty() }
            .take(limit)
            .toList()

        if (rendererMatches.isEmpty()) return emptyList()

        val existing = channel.videos.associateBy { it.videoId }
        var fallbackEpoch =
            channel.videos
                .map { it.publishedAt }
                .filter { it > 0L }
                .minOrNull()
                ?: Instant.now().epochSecond

        return rendererMatches.mapNotNull { match ->
            val videoId = match.groupValues.getOrNull(1).orEmpty()
            if (videoId.isBlank()) return@mapNotNull null

            val markerIndex = match.range.first
            val titleRaw =
                readJsonStringAfter(
                    raw = page,
                    startAt = markerIndex,
                    key = "\"title\":{\"runs\":[{\"text\":\"",
                    maxDistance = 8_000
                )
                    ?: readJsonStringAfter(
                        raw = page,
                        startAt = markerIndex,
                        key = "\"title\":{\"simpleText\":\"",
                        maxDistance = 8_000
                    )

            val base = existing[videoId]
            val title =
                titleRaw
                    ?.let(::decodeJsonEscapes)
                    ?.let(::decodeHtml)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: base?.title
                    ?: return@mapNotNull null

            val publishedRaw =
                readJsonStringAfter(
                    raw = page,
                    startAt = markerIndex,
                    key = "\"publishedTimeText\":{\"simpleText\":\"",
                    maxDistance = 12_000
                )
                    ?: readJsonStringAfter(
                        raw = page,
                        startAt = markerIndex,
                        key = "\"publishedTimeText\":{\"runs\":[{\"text\":\"",
                        maxDistance = 12_000
                    )

            val parsedPublished =
                publishedRaw
                    ?.let(::decodeJsonEscapes)
                    ?.let(::decodeHtml)
                    ?.let(::parsePublishedTimeText)

            val publishedAt =
                base?.publishedAt?.takeIf { it > 0L }
                    ?: parsedPublished
                    ?: run {
                        fallbackEpoch = (fallbackEpoch - 86_400L).coerceAtLeast(0L)
                        fallbackEpoch
                    }

            val durationRaw =
                readJsonStringAfter(
                    raw = page,
                    startAt = markerIndex,
                    key = "\"lengthText\":{\"simpleText\":\"",
                    maxDistance = 12_000
                )
                    ?: readJsonStringAfter(
                        raw = page,
                        startAt = markerIndex,
                        key = "\"lengthText\":{\"runs\":[{\"text\":\"",
                        maxDistance = 12_000
                    )

            val parsedDuration =
                durationRaw
                    ?.let(::decodeJsonEscapes)
                    ?.let(::parseDurationText)
                    ?: 0

            val pageThumbnail =
                readBestThumbnailAfter(
                    raw = page,
                    startAt = markerIndex,
                    maxDistance = 14_000
                )

            val thumbnailUrl =
                pageThumbnail
                    ?.takeIf { it.isNotBlank() }
                    ?: thumbnailCache[videoId]
                    ?: sharpThumbnailUrl(videoId)

            thumbnailCache[videoId] = thumbnailUrl
            if (parsedDuration > 0) durationCache[videoId] = parsedDuration

            YouTubeFeedVideo(
                videoId = videoId,
                title = title,
                description = base?.description.orEmpty(),
                publishedAt = publishedAt,
                thumbnailUrl = thumbnailUrl,
                channelId = channel.channelId,
                channelTitle = channel.title,
                handle = channel.handle,
                durationSeconds =
                    parsedDuration.takeIf { it > 0 }
                        ?: base?.durationSeconds
                        ?: 0
            )
        }
    }

    private fun sharpThumbnailUrl(videoId: String): String =
        "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"

    private fun readBestThumbnailAfter(
        raw: String,
        startAt: Int,
        maxDistance: Int
    ): String? {
        val key = "\"thumbnail\":{\"thumbnails\":["
        val keyIndex = raw.indexOf(key, startAt)
        if (keyIndex < 0 || keyIndex - startAt > maxDistance) return null

        val blockStart = keyIndex + key.length
        val blockEnd = raw.indexOf("]", blockStart)
        if (blockEnd < 0 || blockEnd - blockStart > 12_000) return null

        val block = raw.substring(blockStart, blockEnd)
        return Regex(
            """\{"url":"([^"]+)","width":(\d+),"height":(\d+)\}"""
        )
            .findAll(block)
            .mapNotNull { match ->
                val url = match.groupValues.getOrNull(1)
                    ?.let(::decodeJsonEscapes)
                    ?.takeIf { it.startsWith("http") }
                    ?: return@mapNotNull null
                val width = match.groupValues.getOrNull(2)?.toLongOrNull() ?: 0L
                val height = match.groupValues.getOrNull(3)?.toLongOrNull() ?: 0L
                Triple(url, width, height)
            }
            .maxByOrNull { (_, width, height) -> width * height }
            ?.first
    }

    private fun parseDurationText(raw: String): Int? {
        val parts = raw.trim().split(":").mapNotNull { it.trim().toIntOrNull() }
        if (parts.isEmpty() || parts.size > 3) return null

        val seconds = when (parts.size) {
            1 -> parts[0].toLong()
            2 -> parts[0] * 60L + parts[1]
            else -> parts[0] * 3_600L + parts[1] * 60L + parts[2]
        }
        return seconds.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun parsePublishedTimeText(raw: String): Long? {
        val normalized = raw
            .lowercase(java.util.Locale.ROOT)
            .replace('\u00A0', ' ')
            .replace("streamed", "")
            .replace("premiered", "")
            .replace("назад", "")
            .trim()

        if (
            normalized == "только что" ||
            normalized == "just now" ||
            normalized == "сейчас"
        ) {
            return Instant.now().epochSecond
        }

        val amount = Regex("""(\d+)""")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: return null

        val now = java.time.ZonedDateTime.now()
        val published = when {
            Regex("""секунд|секунда|секунды|second|seconds|sec|secs""").containsMatchIn(normalized) ->
                now.minusSeconds(amount)
            Regex("""минут|минута|минуты|minute|minutes|min|mins""").containsMatchIn(normalized) ->
                now.minusMinutes(amount)
            Regex("""час|часа|часов|hour|hours|hr|hrs""").containsMatchIn(normalized) ->
                now.minusHours(amount)
            Regex("""день|дня|дней|day|days""").containsMatchIn(normalized) ->
                now.minusDays(amount)
            Regex("""недел|week|weeks""").containsMatchIn(normalized) ->
                now.minusWeeks(amount)
            Regex("""месяц|месяца|месяцев|month|months""").containsMatchIn(normalized) ->
                now.minusMonths(amount)
            Regex("""год|года|лет|year|years""").containsMatchIn(normalized) ->
                now.minusYears(amount)
            else -> return null
        }
        return published.toEpochSecond()
    }

    private fun mergeChannelVideos(
        primary: List<YouTubeFeedVideo>,
        secondary: List<YouTubeFeedVideo>,
        limit: Int
    ): List<YouTubeFeedVideo> {
        val merged = LinkedHashMap<String, YouTubeFeedVideo>()
        primary.forEach { merged[it.videoId] = it }
        secondary.forEach { candidate ->
            val current = merged[candidate.videoId]
            merged[candidate.videoId] = if (current == null) {
                candidate
            } else {
                current.copy(
                    thumbnailUrl =
                        candidate.thumbnailUrl.takeIf { it.isNotBlank() }
                            ?: current.thumbnailUrl,
                    publishedAt =
                        current.publishedAt.takeIf { it > 0L }
                            ?: candidate.publishedAt,
                    durationSeconds =
                        candidate.durationSeconds.takeIf { it > 0 }
                            ?: current.durationSeconds
                )
            }
        }
        return merged.values
            .sortedByDescending { it.publishedAt }
            .take(limit)
    }

    private fun readJsonStringAfter(
        raw: String,
        startAt: Int,
        key: String,
        maxDistance: Int
    ): String? {
        val keyIndex = raw.indexOf(key, startAt)
        if (keyIndex < 0 || keyIndex - startAt > maxDistance) return null

        val valueStart = keyIndex + key.length
        var escaped = false
        for (index in valueStart until raw.length) {
            val ch = raw[index]
            if (escaped) {
                escaped = false
                continue
            }
            when (ch) {
                '\\' -> escaped = true
                '"' -> return raw.substring(valueStart, index)
            }
        }
        return null
    }

    private fun decodeJsonEscapes(raw: String): String =
        raw.replace("\\u0026", "&")
            .replace("\\u003d", "=")
            .replace("\\u003c", "<")
            .replace("\\u003e", ">")
            .replace("\\/", "/")
            .replace("\\n", " ")
            .replace("\\r", " ")
            .replace("\\t", " ")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")

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

    private fun deduplicate(input: List<YouTubeFeedVideo>): List<YouTubeFeedVideo> =
        input.distinctBy { it.videoId }

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
