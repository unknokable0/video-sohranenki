package com.unknokable.videosohranenki

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlin.math.max

object TwitchContentAnalyzer {
    private const val GQL_URL = "https://gql.twitch.tv/gql"
    private const val TWITCH_WEB_CLIENT_ID = "ue6666qo983tsx6so1t0vnawi233wa"

    private const val QUERY =
        "query(\$id: ID!) { video(id: \$id) { id seekPreviewsURL moments(first: 100, momentRequestType: VIDEO_CHAPTER_MARKERS) { edges { node { positionMilliseconds description details { ... on GameChangeMomentDetails { game { displayName } } } } } } } }"

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val labeler by lazy {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.54f)
                .build()
        )
    }

    private data class Marker(val startSec: Int, val raw: String)

    private data class Storyboard(
        val width: Int,
        val height: Int,
        val cols: Int,
        val rows: Int,
        val intervalSec: Double,
        val count: Int,
        val imageUrls: List<String>
    )

    private data class TimelinePoint(
        val storyboardIndex: Int,
        val timeSec: Int,
        val hash: Long,
        val diff: Int
    )

    private data class CandidateRange(
        var startIndex: Int,
        var endIndex: Int,
        var motionRatio: Double = 0.0
    )

    private data class OcrInfo(
        val fullText: String,
        val title: String?,
        val youtubeLike: Boolean,
        val playerLike: Boolean,
        val confidence: Int
    )

    private data class Probe(
        val timelineIndex: Int,
        val timeSec: Int,
        val ocr: OcrInfo,
        val labels: List<Pair<String, Float>>,
        val score: Int
    )

    private data class TitleAnchor(
        val timeSec: Int,
        val title: String,
        val confidence: Int
    )

    private class StripCache(private val maxEntries: Int = 3) :
        LinkedHashMap<String, Bitmap>(maxEntries, 0.75f, true) {

        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            val remove = size > maxEntries
            if (remove) eldest?.value?.takeIf { !it.isRecycled }?.recycle()
            return remove
        }

        fun recycleAll() {
            values.forEach { if (!it.isRecycled) it.recycle() }
            clear()
        }
    }

    suspend fun analyze(
        videoId: String,
        durationSeconds: Int,
        onProgress: suspend (Int, String) -> Unit = { _, _ -> }
    ): SmartAnalysisResult = withContext(Dispatchers.IO) {
        require(videoId.isNotBlank() && videoId.all(Char::isDigit)) {
            "Некорректный Twitch Video ID"
        }

        onProgress(5, "Получаем карту стрима…")
        val (markers, storyboardUrl) = fetchExtras(videoId)
        val normalizedMarkers = normalizeMarkers(markers, durationSeconds)

        if (storyboardUrl.isNullOrBlank()) {
            val games = gameChapters(normalizedMarkers, durationSeconds)
            onProgress(100, "Готово")
            return@withContext SmartAnalysisResult(games, 0, false)
        }

        val storyboard = fetchStoryboard(storyboardUrl)
        coroutineContext.ensureActive()

        onProgress(12, "Сканируем весь стрим по превью…")
        val timeline = scanTimeline(
            storyboard = storyboard,
            durationSeconds = durationSeconds,
            onProgress = { done, total ->
                val percent = 12 + ((done * 34) / max(1, total))
                onProgress(percent.coerceAtMost(46), "Ищем начало и конец видео…")
            }
        )

        coroutineContext.ensureActive()

        val gameSegments = gameChapters(normalizedMarkers, durationSeconds)
        val dynamicCandidates = detectDynamicVideoRanges(
            timeline = timeline,
            markers = normalizedMarkers
        ).toMutableList()

        onProgress(48, "Проверяем ролики и ищем названия…")

        val probeIndices = buildProbeIndices(
            timeline = timeline,
            candidates = dynamicCandidates,
            markers = normalizedMarkers
        )

        val probes = analyzeProbes(
            storyboard = storyboard,
            timeline = timeline,
            probeTimelineIndices = probeIndices,
            onProgress = { done, total ->
                val percent = 48 + ((done * 38) / max(1, total))
                onProgress(percent.coerceAtMost(86), "Читаем названия на экране…")
            }
        )

        coroutineContext.ensureActive()

        addEvidenceCandidates(
            timeline = timeline,
            probes = probes,
            candidates = dynamicCandidates
        )

        val videos = buildVideoChapters(
            timeline = timeline,
            candidates = dynamicCandidates,
            probes = probes,
            markers = normalizedMarkers,
            durationSeconds = durationSeconds
        )

        onProgress(91, "Уточняем границы…")

        val combined = combineStrict(
            games = gameSegments,
            videos = videos,
            durationSeconds = durationSeconds
        )

        onProgress(100, "Готово")
        SmartAnalysisResult(
            chapters = combined,
            scannedFrames = timeline.size,
            usedOcr = probes.isNotEmpty()
        )
    }

    private fun fetchExtras(videoId: String): Pair<List<Marker>, String?> {
        val payload = JSONObject().apply {
            put("query", QUERY)
            put("variables", JSONObject().apply { put("id", videoId) })
        }.toString()

        val root = JSONObject(
            postJson(
                GQL_URL,
                payload,
                mapOf("Client-ID" to TWITCH_WEB_CLIENT_ID)
            )
        )

        if (root.has("errors")) {
            val msg = root.optJSONArray("errors")
                ?.optJSONObject(0)
                ?.optString("message")
            throw IOException(
                msg?.takeIf { it.isNotBlank() } ?: "Twitch не вернул структуру стрима"
            )
        }

        val video = root.optJSONObject("data")
            ?.optJSONObject("video")
            ?: throw IOException("Twitch не нашёл запись")

        val markers = mutableListOf<Marker>()
        val edges = video.optJSONObject("moments")?.optJSONArray("edges")

        if (edges != null) {
            for (i in 0 until edges.length()) {
                val node = edges.optJSONObject(i)?.optJSONObject("node") ?: continue
                val startMs = node.optLong("positionMilliseconds", -1L)
                if (startMs < 0L) continue

                val game = node.optJSONObject("details")
                    ?.optJSONObject("game")
                    ?.optString("displayName")
                    .orEmpty()
                    .trim()

                val description = node.optString("description").trim()
                markers += Marker(
                    startSec = (startMs / 1000L).toInt().coerceAtLeast(0),
                    raw = description.ifBlank { game }
                )
            }
        }

        return markers.sortedBy { it.startSec } to
            video.optString("seekPreviewsURL").takeIf { it.startsWith("http") }
    }

    private fun normalizeMarkers(
        markers: List<Marker>,
        durationSeconds: Int
    ): List<Marker> {
        val out = mutableListOf<Marker>()

        for (marker in markers.sortedBy { it.startSec }) {
            if (durationSeconds > 0 && marker.startSec > durationSeconds) continue
            val previous = out.lastOrNull()
            if (previous != null && previous.raw.equals(marker.raw, ignoreCase = true)) continue
            out += marker
        }

        if (out.isEmpty() || out.first().startSec > 20) {
            out.add(0, Marker(0, out.firstOrNull()?.raw.orEmpty()))
        }
        return out
    }

    private fun fetchStoryboard(url: String): Storyboard {
        val text = getText(url)
        val root = JSONArray(text)
        var best: Storyboard? = null

        for (i in 0 until root.length()) {
            val entry = root.optJSONObject(i) ?: continue
            val width = entry.optInt("width")
            val height = entry.optInt("height")
            val cols = entry.optInt("cols")
            val rows = entry.optInt("rows")
            val count = entry.optInt("count")
            val interval = entry.optDouble("interval")
            val imagesJson = entry.optJSONArray("images") ?: continue

            if (
                width <= 0 || height <= 0 || cols <= 0 || rows <= 0 ||
                count <= 0 || interval <= 0.0
            ) continue

            val images = mutableListOf<String>()
            for (j in 0 until imagesJson.length()) {
                val file = imagesJson.optString(j)
                if (file.isNotBlank()) images += URL(URL(url), file).toString()
            }
            if (images.isEmpty()) continue

            val candidate = Storyboard(
                width = width,
                height = height,
                cols = cols,
                rows = rows,
                intervalSec = interval,
                count = count,
                imageUrls = images
            )

            if (best == null || candidate.width > best!!.width) best = candidate
        }

        return best ?: throw IOException("Twitch не отдал storyboard")
    }

    private suspend fun scanTimeline(
        storyboard: Storyboard,
        durationSeconds: Int,
        onProgress: suspend (Int, Int) -> Unit
    ): List<TimelinePoint> {
        val targetPoints = 900
        val step = max(1, ceil(storyboard.count / targetPoints.toDouble()).toInt())
        val indexes = mutableListOf<Int>()

        var index = 0
        while (index < storyboard.count) {
            indexes += index
            index += step
        }
        if (indexes.lastOrNull() != storyboard.count - 1) {
            indexes += (storyboard.count - 1).coerceAtLeast(0)
        }

        val cache = StripCache(2)
        val out = ArrayList<TimelinePoint>(indexes.size)
        var previousHash: Long? = null

        try {
            for ((position, storyboardIndex) in indexes.withIndex()) {
                coroutineContext.ensureActive()
                val frame = storyboardFrame(
                    storyboard,
                    storyboardIndex,
                    cache,
                    targetWidth = 180
                ) ?: continue

                val hash = mainContentHash(frame)
                val diff = previousHash?.let { hamming(it, hash) } ?: 0
                val time = (storyboardIndex * storyboard.intervalSec)
                    .toInt()
                    .coerceIn(0, durationSeconds.coerceAtLeast(0))

                out += TimelinePoint(storyboardIndex, time, hash, diff)
                previousHash = hash
                if (!frame.isRecycled) frame.recycle()

                if (position % 12 == 0 || position == indexes.lastIndex) {
                    onProgress(position + 1, indexes.size)
                }
            }
        } finally {
            cache.recycleAll()
        }

        return out
    }

    private fun detectDynamicVideoRanges(
        timeline: List<TimelinePoint>,
        markers: List<Marker>
    ): List<CandidateRange> {
        if (timeline.size < 3) return emptyList()

        val out = mutableListOf<CandidateRange>()
        var activeStart: Int? = null
        var highMotion = 0
        var totalMotionPoints = 0
        var quietStreak = 0

        fun finish(endIndex: Int) {
            val start = activeStart ?: return
            if (endIndex <= start) {
                activeStart = null
                highMotion = 0
                totalMotionPoints = 0
                quietStreak = 0
                return
            }

            val duration = timeline[endIndex].timeSec - timeline[start].timeSec
            val ratio = if (totalMotionPoints > 0) {
                highMotion.toDouble() / totalMotionPoints.toDouble()
            } else 0.0

            if (duration >= 40 && ratio >= 0.34) {
                out += CandidateRange(start, endIndex, ratio)
            }

            activeStart = null
            highMotion = 0
            totalMotionPoints = 0
            quietStreak = 0
        }

        for (i in 1 until timeline.size) {
            val point = timeline[i]
            val chatLike = isChatLike(markerAt(markers, point.timeSec))

            if (!chatLike) {
                if (activeStart != null) finish((i - 1).coerceAtLeast(0))
                continue
            }

            val recentStart = (i - 2).coerceAtLeast(1)
            val recent = (recentStart..i).map { timeline[it].diff }
            val strongCount = recent.count { it >= 15 }
            val strongNow = point.diff >= 17
            val veryStrongNow = point.diff >= 27
            val sustained = strongCount >= 2

            if (activeStart == null) {
                if (veryStrongNow || sustained) {
                    activeStart = (i - 2).coerceAtLeast(0)
                    highMotion = recent.count { it >= 15 }
                    totalMotionPoints = recent.size
                    quietStreak = 0
                }
                continue
            }

            totalMotionPoints += 1
            if (strongNow) highMotion += 1

            if (point.diff <= 8) quietStreak += 1 else quietStreak = 0

            if (quietStreak >= 3) {
                finish((i - 2).coerceAtLeast(activeStart!!))
            }
        }

        if (activeStart != null) finish(timeline.lastIndex)
        return mergeCandidateRanges(out)
    }

    private fun mergeCandidateRanges(ranges: List<CandidateRange>): List<CandidateRange> {
        if (ranges.isEmpty()) return emptyList()
        val out = mutableListOf<CandidateRange>()

        for (range in ranges.sortedBy { it.startIndex }) {
            val previous = out.lastOrNull()
            if (previous == null || range.startIndex - previous.endIndex > 2) {
                out += range.copy()
            } else {
                previous.endIndex = max(previous.endIndex, range.endIndex)
                previous.motionRatio = max(previous.motionRatio, range.motionRatio)
            }
        }
        return out
    }

    private fun buildProbeIndices(
        timeline: List<TimelinePoint>,
        candidates: List<CandidateRange>,
        markers: List<Marker>
    ): List<Int> {
        if (timeline.isEmpty()) return emptyList()
        val wanted = linkedSetOf<Int>()

        for (candidate in candidates) {
            for (offset in -1..5) {
                wanted += (candidate.startIndex + offset).coerceIn(0, timeline.lastIndex)
            }
            for (offset in -2..2) {
                wanted += (candidate.endIndex + offset).coerceIn(0, timeline.lastIndex)
            }

            val length = candidate.endIndex - candidate.startIndex
            val stride = max(1, length / 6)
            var i = candidate.startIndex
            while (i <= candidate.endIndex) {
                wanted += i
                i += stride
            }
        }

        for (i in 1 until timeline.size) {
            val point = timeline[i]
            if (!isChatLike(markerAt(markers, point.timeSec))) continue
            if (point.diff >= 22) {
                wanted += (i - 1).coerceAtLeast(0)
                wanted += i
                if (i + 1 < timeline.size) wanted += i + 1
            }
        }

        val safetyStride = max(1, timeline.size / 110)
        var i = 0
        while (i < timeline.size) {
            if (isChatLike(markerAt(markers, timeline[i].timeSec))) wanted += i
            i += safetyStride
        }

        return wanted
            .filter { it in timeline.indices }
            .distinct()
            .sorted()
            .take(170)
    }

    private suspend fun analyzeProbes(
        storyboard: Storyboard,
        timeline: List<TimelinePoint>,
        probeTimelineIndices: List<Int>,
        onProgress: suspend (Int, Int) -> Unit
    ): List<Probe> {
        val cache = StripCache(3)
        val out = mutableListOf<Probe>()

        try {
            for ((position, timelineIndex) in probeTimelineIndices.withIndex()) {
                coroutineContext.ensureActive()
                val point = timeline.getOrNull(timelineIndex) ?: continue
                val frame = storyboardFrame(
                    storyboard,
                    point.storyboardIndex,
                    cache,
                    targetWidth = 720
                ) ?: continue

                val ocr = recognize(frame)
                val labels = recognizeLabels(frame)
                val score = videoEvidenceScore(ocr, labels, point.diff)

                out += Probe(timelineIndex, point.timeSec, ocr, labels, score)
                if (!frame.isRecycled) frame.recycle()

                if (position % 4 == 0 || position == probeTimelineIndices.lastIndex) {
                    onProgress(position + 1, probeTimelineIndices.size)
                }
            }
        } finally {
            cache.recycleAll()
        }

        return out
    }

    private fun addEvidenceCandidates(
        timeline: List<TimelinePoint>,
        probes: List<Probe>,
        candidates: MutableList<CandidateRange>
    ) {
        if (timeline.isEmpty()) return

        for (probe in probes) {
            if (probe.score < 6) continue

            val start = (probe.timelineIndex - 2).coerceAtLeast(0)
            val end = (probe.timelineIndex + 4).coerceAtMost(timeline.lastIndex)

            val overlapping = candidates.firstOrNull {
                start <= it.endIndex + 2 && end >= it.startIndex - 2
            }

            if (overlapping != null) {
                overlapping.startIndex = minOf(overlapping.startIndex, start)
                overlapping.endIndex = maxOf(overlapping.endIndex, end)
            } else {
                candidates += CandidateRange(start, end, 0.5)
            }
        }

        val merged = mergeCandidateRanges(candidates)
        candidates.clear()
        candidates.addAll(merged)
    }

    private fun buildVideoChapters(
        timeline: List<TimelinePoint>,
        candidates: List<CandidateRange>,
        probes: List<Probe>,
        markers: List<Marker>,
        durationSeconds: Int
    ): List<SmartChapter> {
        val out = mutableListOf<SmartChapter>()

        for (candidate in candidates.sortedBy { it.startIndex }) {
            if (candidate.startIndex !in timeline.indices ||
                candidate.endIndex !in timeline.indices
            ) continue

            val rawStart = timeline[candidate.startIndex].timeSec
            val rawEnd = timeline[candidate.endIndex].timeSec
            val midpointSec = rawStart + ((rawEnd - rawStart) / 2)

            if (isGameCategory(markerAt(markers, midpointSec))) continue

            val insideProbes = probes
                .filter { it.timelineIndex in candidate.startIndex..candidate.endIndex }
                .sortedBy { it.timeSec }

            val strongVideoProbes = insideProbes.filter { it.score >= 4 }
            val evidenceRatio = if (insideProbes.isNotEmpty()) {
                strongVideoProbes.size.toDouble() / insideProbes.size.toDouble()
            } else 0.0

            val duration = rawEnd - rawStart
            val accepted =
                strongVideoProbes.any { it.score >= 6 } ||
                (duration >= 70 && candidate.motionRatio >= 0.52 && evidenceRatio >= 0.20) ||
                (duration >= 120 && candidate.motionRatio >= 0.62)

            if (!accepted) continue

            val anchors = strongVideoProbes
                .mapNotNull { probe ->
                    val title = probe.ocr.title?.takeIf(::isStrongTitle)
                        ?: return@mapNotNull null
                    TitleAnchor(probe.timeSec, title, probe.ocr.confidence)
                }
                .sortedBy { it.timeSec }

            val splitPoints = titleChangeSplitPoints(anchors)

            if (splitPoints.isEmpty()) {
                val title = bestTitle(anchors)
                out += SmartChapter(
                    startSeconds = rawStart.coerceAtLeast(0),
                    endSeconds = rawEnd.coerceAtMost(durationSeconds),
                    title = title?.let { "Смотрит: $it" } ?: "Смотрит видео",
                    detail = if (title != null) {
                        "Название найдено по кадрам"
                    } else {
                        "Уверенно определён просмотр видео"
                    },
                    confidence = if (title != null) 91 else 74
                )
                continue
            }

            val boundaries = mutableListOf<Int>()
            boundaries += rawStart
            boundaries += splitPoints
            boundaries += rawEnd

            for (i in 0 until boundaries.lastIndex) {
                val start = boundaries[i]
                val end = boundaries[i + 1]
                if (end - start < 35) continue

                val segmentAnchors = anchors.filter { it.timeSec in start..end }
                val title = bestTitle(segmentAnchors)

                out += SmartChapter(
                    startSeconds = start,
                    endSeconds = end.coerceAtMost(durationSeconds),
                    title = title?.let { "Смотрит: $it" } ?: "Смотрит видео",
                    detail = if (title != null) {
                        "Отдельное видео • название распознано"
                    } else {
                        "Отдельный видео-фрагмент"
                    },
                    confidence = if (title != null) 91 else 72
                )
            }
        }

        return mergeVideoChapters(out)
    }

    private fun titleChangeSplitPoints(anchors: List<TitleAnchor>): List<Int> {
        if (anchors.size < 2) return emptyList()

        val out = mutableListOf<Int>()
        var current = anchors.first()

        for (i in 1 until anchors.size) {
            val next = anchors[i]
            val similar = similarity(current.title, next.title) >= 0.50

            if (!similar && next.timeSec - current.timeSec >= 45) {
                out += current.timeSec + ((next.timeSec - current.timeSec) / 2)
                current = next
            } else if (next.confidence > current.confidence) {
                current = next
            }
        }

        return out.distinct().sorted()
    }

    private fun bestTitle(anchors: List<TitleAnchor>): String? {
        if (anchors.isEmpty()) return null

        var bestTitle: String? = null
        var bestScore = Int.MIN_VALUE

        for (anchor in anchors) {
            var score = anchor.confidence
            for (other in anchors) {
                if (other === anchor) continue
                if (similarity(anchor.title, other.title) >= 0.55) {
                    score += other.confidence / 2
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestTitle = anchor.title
            }
        }

        return bestTitle
    }

    private fun mergeVideoChapters(chapters: List<SmartChapter>): List<SmartChapter> {
        if (chapters.isEmpty()) return emptyList()
        val out = mutableListOf<SmartChapter>()

        for (chapter in chapters.sortedBy { it.startSeconds }) {
            val previous = out.lastOrNull()
            if (previous == null) {
                out += chapter
                continue
            }

            val sameTitle = similarity(previous.title, chapter.title) >= 0.60
            val gap = chapter.startSeconds - previous.endSeconds

            if (sameTitle && gap in 0..30) {
                out[out.lastIndex] = previous.copy(
                    endSeconds = chapter.endSeconds,
                    confidence = max(previous.confidence, chapter.confidence)
                )
            } else {
                out += chapter
            }
        }

        return out
    }

    private fun gameChapters(
        markers: List<Marker>,
        durationSeconds: Int
    ): List<SmartChapter> {
        val out = mutableListOf<SmartChapter>()

        for (i in markers.indices) {
            val marker = markers[i]
            if (!isGameCategory(marker.raw)) continue

            val end = markers.getOrNull(i + 1)?.startSec ?: durationSeconds
            if (end - marker.startSec < 45) continue

            out += SmartChapter(
                startSeconds = marker.startSec,
                endSeconds = end,
                title = "Играет: ${marker.raw}",
                detail = "Игра определена по категории Twitch",
                confidence = 96
            )
        }

        return out
    }

    private fun combineStrict(
        games: List<SmartChapter>,
        videos: List<SmartChapter>,
        durationSeconds: Int
    ): List<SmartChapter> {
        val clippedVideos = mutableListOf<SmartChapter>()

        for (video in videos) {
            var pieces = listOf(video)

            for (game in games) {
                pieces = pieces.flatMap { piece ->
                    when {
                        game.endSeconds <= piece.startSeconds ||
                            game.startSeconds >= piece.endSeconds -> listOf(piece)

                        game.startSeconds <= piece.startSeconds &&
                            game.endSeconds >= piece.endSeconds -> emptyList()

                        game.startSeconds <= piece.startSeconds -> listOf(
                            piece.copy(startSeconds = game.endSeconds)
                        )

                        game.endSeconds >= piece.endSeconds -> listOf(
                            piece.copy(endSeconds = game.startSeconds)
                        )

                        else -> listOf(
                            piece.copy(endSeconds = game.startSeconds),
                            piece.copy(startSeconds = game.endSeconds)
                        )
                    }
                }.filter { it.endSeconds - it.startSeconds >= 35 }
            }

            clippedVideos += pieces
        }

        return (games + clippedVideos)
            .map {
                it.copy(
                    startSeconds = it.startSeconds.coerceIn(0, durationSeconds),
                    endSeconds = it.endSeconds.coerceIn(0, durationSeconds)
                )
            }
            .filter { it.endSeconds > it.startSeconds }
            .sortedBy { it.startSeconds }
            .take(30)
    }

    private fun markerAt(markers: List<Marker>, timeSec: Int): String =
        markers.lastOrNull { it.startSec <= timeSec }?.raw.orEmpty()

    private fun isChatLike(raw: String): Boolean {
        val lower = raw.lowercase()
        if (raw.isBlank()) return true
        return "just chatting" in lower ||
            "общение" in lower ||
            "talk" in lower ||
            "special events" in lower ||
            "irl" in lower ||
            "music" in lower
    }

    private fun isGameCategory(raw: String): Boolean {
        if (raw.isBlank()) return false
        val lower = raw.lowercase()
        return !isChatLike(raw) &&
            !lower.contains("music") &&
            !lower.contains("special events")
    }

    private fun videoEvidenceScore(
        ocr: OcrInfo,
        labels: List<Pair<String, Float>>,
        diff: Int
    ): Int {
        var score = 0
        if (ocr.youtubeLike) score += 5
        if (ocr.playerLike) score += 3
        if (ocr.title != null && ocr.confidence >= 78) score += 2
        if (diff >= 18) score += 1

        val labelText = labels.joinToString(" ") { it.first.lowercase() }
        if (
            listOf("movie", "film", "video", "television", "multimedia", "media")
                .any { it in labelText }
        ) score += 2

        if (
            listOf("video game", "gaming", "computer game", "pc game")
                .any { it in labelText }
        ) score -= 3

        return score
    }

    private suspend fun recognize(bitmap: Bitmap): OcrInfo {
        val result = suspendCancellableCoroutine<Text?> { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener {
                    if (continuation.isActive) continuation.resume(it)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        } ?: return OcrInfo("", null, false, false, 0)

        val fullText = result.text.replace(Regex("\\s+"), " ").trim()
        val lower = fullText.lowercase()

        val youtubeLike = listOf(
            "youtube", "youtu.be", "подписаться", "просмотров",
            "смотреть позже", "comments", "share", "ютуб"
        ).any { it in lower }

        val playerTimeRegex = Regex(
            "(^|\\s)(?:\\d{1,2}:)?\\d{1,2}:\\d{2}(?:\\s|/|$)"
        )
        val playerLike =
            playerTimeRegex.containsMatchIn(fullText) ||
            listOf(
                "pause", "play", "автовоспроизведение", "скорость воспроизведения"
            ).any { it in lower }

        data class Candidate(val text: String, val score: Int)
        val candidates = mutableListOf<Candidate>()

        for (block in result.textBlocks) {
            for (line in block.lines) {
                val text = cleanOcrLine(line.text)
                if (!isUsefulTitleLine(text)) continue

                val box = line.boundingBox
                val letters = text.count { it.isLetter() }
                val words = text.split(' ').count { it.isNotBlank() }

                var score = letters + (words * 4)
                if (text.length in 12..80) score += 12
                if (box != null && box.top < bitmap.height * 0.60f) score += 12
                if (youtubeLike) score += 12

                candidates += Candidate(text, score)
            }
        }

        val best = candidates.maxByOrNull { it.score }
        val confidence = when {
            best == null -> 0
            youtubeLike && best.score >= 60 -> 94
            youtubeLike -> 86
            playerLike && best.score >= 58 -> 84
            best.score >= 72 -> 78
            else -> 62
        }

        return OcrInfo(
            fullText = fullText,
            title = best?.text,
            youtubeLike = youtubeLike,
            playerLike = playerLike,
            confidence = confidence
        )
    }

    private suspend fun recognizeLabels(
        bitmap: Bitmap
    ): List<Pair<String, Float>> {
        return suspendCancellableCoroutine { continuation ->
            labeler.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { labels ->
                    if (continuation.isActive) {
                        continuation.resume(
                            labels
                                .filter { it.confidence >= 0.54f }
                                .sortedByDescending { it.confidence }
                                .take(8)
                                .map { it.text.lowercase() to it.confidence }
                        )
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(emptyList())
                }
        }
    }

    private fun cleanOcrLine(raw: String): String =
        raw.replace(Regex("\\s+"), " ")
            .replace(Regex("[\\u0000-\\u001F]"), "")
            .trim()
            .trim('|', '•', '-', '—', ':', ';')

    private fun isUsefulTitleLine(text: String): Boolean {
        if (text.length !in 9..100) return false
        if (text.count { it.isLetter() } < 6) return false
        if (text.count { it.isDigit() } > text.length * 0.42) return false

        val lower = text.lowercase()
        val noise = listOf(
            "twitch", "t2x2", "t.me/", "telegram",
            "подписаться", "отслеживать", "подарить подпис",
            "контент включает", "просмотров", "комментар",
            "comments", "share", "смотреть позже",
            "rub", "₽", "донат", "чат", "зрителей",
            "онлайн", "авто", "качество", "настройки"
        )

        if (noise.any { it in lower }) return false
        if ("http://" in lower || "https://" in lower || "www." in lower) return false
        return true
    }

    private fun isStrongTitle(text: String): Boolean {
        val clean = cleanOcrLine(text)
        if (!isUsefulTitleLine(clean)) return false
        val words = clean.split(' ').filter { it.length > 1 }
        return words.size >= 2 || clean.length >= 18
    }

    private fun storyboardFrame(
        storyboard: Storyboard,
        index: Int,
        cache: StripCache,
        targetWidth: Int
    ): Bitmap? {
        val perImage = storyboard.cols * storyboard.rows
        if (perImage <= 0 || index !in 0 until storyboard.count) return null

        val imageIndex = index / perImage
        if (imageIndex !in storyboard.imageUrls.indices) return null

        val within = index % perImage
        val col = within % storyboard.cols
        val row = within / storyboard.cols
        val url = storyboard.imageUrls[imageIndex]

        val strip = cache[url]
            ?: downloadBitmap(url)?.also { cache[url] = it }
            ?: return null

        val x = col * storyboard.width
        val y = row * storyboard.height

        if (
            x < 0 || y < 0 ||
            x + storyboard.width > strip.width ||
            y + storyboard.height > strip.height
        ) return null

        val cell = Bitmap.createBitmap(
            strip, x, y, storyboard.width, storyboard.height
        )

        val safeWidth = max(120, targetWidth)
        val safeHeight = max(
            68,
            (storyboard.height * safeWidth.toFloat() / storyboard.width).toInt()
        )

        val scaled = Bitmap.createScaledBitmap(
            cell, safeWidth, safeHeight, true
        )

        if (scaled !== cell && !cell.isRecycled) cell.recycle()
        return scaled
    }

    private fun mainContentHash(bitmap: Bitmap): Long {
        val cropWidth = (bitmap.width * 0.76f).toInt().coerceIn(1, bitmap.width)
        val cropHeight = (bitmap.height * 0.86f).toInt().coerceIn(1, bitmap.height)

        val crop = Bitmap.createBitmap(bitmap, 0, 0, cropWidth, cropHeight)
        val hash = differenceHash(crop)
        if (crop !== bitmap && !crop.isRecycled) crop.recycle()
        return hash
    }

    private fun differenceHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var hash = 0L
        var bit = 0

        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = luminance(scaled.getPixel(x, y))
                val right = luminance(scaled.getPixel(x + 1, y))
                if (left > right) hash = hash or (1L shl bit)
                bit += 1
            }
        }

        if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
        return hash
    }

    private fun luminance(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun hamming(a: Long, b: Long): Int =
        java.lang.Long.bitCount(a xor b)

    private fun normalizeForCompare(value: String): String =
        value.lowercase()
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun similarity(a: String, b: String): Double {
        val first = normalizeForCompare(a)
            .split(' ')
            .filter { it.length > 2 }
            .toSet()
        val second = normalizeForCompare(b)
            .split(' ')
            .filter { it.length > 2 }
            .toSet()

        if (first.isEmpty() || second.isEmpty()) return 0.0
        val intersection = first.intersect(second).size.toDouble()
        val union = first.union(second).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }

    private fun postJson(
        url: String,
        payload: String,
        headers: Map<String, String>
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36"
            )
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }

        return try {
            connection.outputStream.use {
                it.write(payload.toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val body = (
                if (code in 200..299) connection.inputStream else connection.errorStream
            )?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) throw IOException("Twitch: HTTP $code")
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun getText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 12_000
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36"
            )
        }

        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("Storyboard: HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadBitmap(url: String): Bitmap? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }

        return try {
            val code = connection.responseCode
            if (code !in 200..299) return null
            BitmapFactory.decodeStream(connection.inputStream)
        } finally {
            connection.disconnect()
        }
    }
}
