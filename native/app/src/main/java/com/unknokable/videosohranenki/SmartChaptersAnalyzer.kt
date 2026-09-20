package com.unknokable.videosohranenki

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.Build
import com.google.mlkit.vision.common.InputImage
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

data class SmartChapter(
    val startSeconds: Int,
    val endSeconds: Int,
    val title: String,
    val detail: String,
    val confidence: Int = 60
)

data class SmartAnalysisResult(
    val chapters: List<SmartChapter>,
    val scannedFrames: Int,
    val usedOcr: Boolean
)

object SmartChaptersAnalyzer {
    private const val GQL_URL = "https://gql.twitch.tv/gql"
    private const val TWITCH_WEB_CLIENT_ID = "ue6666qo983tsx6so1t0vnawi233wa"

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private const val TWITCH_QUERY =
        "query(\$id: ID!) { video(id: \$id) { id seekPreviewsURL moments(first: 100, momentRequestType: VIDEO_CHAPTER_MARKERS) { edges { node { positionMilliseconds description details { ... on GameChangeMomentDetails { game { displayName } } } } } } } }"

    private data class Marker(val start: Int, val raw: String)
    private data class Boundary(val time: Int, val title: String, val detail: String, val confidence: Int)
    private data class TwitchExtras(val markers: List<Marker>, val storyboardUrl: String?)
    private data class Storyboard(
        val width: Int,
        val height: Int,
        val cols: Int,
        val rows: Int,
        val intervalSec: Double,
        val count: Int,
        val imageUrls: List<String>
    )
    private data class FrameSample(
        val timeSec: Int,
        val hash: Long,
        val bitmap: Bitmap? = null,
        val diffFromPrevious: Int = 0,
        var ocr: OcrSnapshot? = null
    )
    private data class OcrSnapshot(
        val fullText: String,
        val titleCandidate: String?,
        val youtubeLike: Boolean,
        val confidence: Int
    ) {
        val watchTitle: String?
            get() = if (youtubeLike) titleCandidate else null
    }

    suspend fun analyzeTwitch(
        videoId: String,
        durationSeconds: Int,
        onProgress: suspend (Int, String) -> Unit = { _, _ -> }
    ): SmartAnalysisResult = withContext(Dispatchers.IO) {
        require(videoId.isNotBlank() && videoId.all(Char::isDigit)) { "Некорректный Twitch Video ID" }

        onProgress(8, "Читаем структуру Twitch…")
        val extras = fetchTwitchExtras(videoId)
        coroutineContext.ensureActive()

        val baseMarkers = normalizeMarkers(extras.markers, durationSeconds)
        val storyboard = runCatching { extras.storyboardUrl?.let(::fetchStoryboard) }.getOrNull()

        if (storyboard == null) {
            onProgress(88, "Собираем главы…")
            val fallback = buildFromMarkers(baseMarkers, durationSeconds)
            onProgress(100, "Готово")
            return@withContext SmartAnalysisResult(fallback, 0, false)
        }

        onProgress(18, "Берём лёгкие превью вместо всего видео…")
        val samples = loadStoryboardSamples(storyboard, baseMarkers, durationSeconds)
        coroutineContext.ensureActive()

        onProgress(45, "Ищем крупные смены сцены…")
        val ocrIndexes = chooseOcrIndexes(samples, baseMarkers, maxOcr = 16)

        var ocrDone = 0
        for (idx in ocrIndexes) {
            coroutineContext.ensureActive()
            val sample = samples.getOrNull(idx) ?: continue
            val bitmap = sample.bitmap ?: continue
            sample.ocr = recognize(bitmap)
            ocrDone++
            val p = 48 + ((ocrDone * 38) / max(1, ocrIndexes.size))
            onProgress(p.coerceAtMost(86), "Читаем текст на ключевых кадрах…")
        }

        onProgress(90, "Объединяем найденные фрагменты…")
        val chapters = buildTwitchChapters(baseMarkers, samples, durationSeconds)
        samples.forEach { it.bitmap?.takeIf { b -> !b.isRecycled }?.recycle() }

        onProgress(100, "Готово")
        SmartAnalysisResult(chapters, samples.size, ocrDone > 0)
    }

    suspend fun analyzeTelegram(
        durationSeconds: Int,
        sourceFactory: (() -> MediaDataSource)?,
        mediaUrl: String,
        videoTitle: String,
        onProgress: suspend (Int, String) -> Unit = { _, _ -> }
    ): SmartAnalysisResult = withContext(Dispatchers.IO) {
        if (durationSeconds <= 0) {
            return@withContext SmartAnalysisResult(
                chapters = listOf(SmartChapter(0, 0, "Начало видео", "Видео Telegram", 50)),
                scannedFrames = 0,
                usedOcr = false
            )
        }

        onProgress(8, "Готовим быстрый анализ Telegram…")
        val retriever = MediaMetadataRetriever()
        var dataSource: MediaDataSource? = null

        try {
            if (sourceFactory != null) {
                dataSource = sourceFactory()
                retriever.setDataSource(dataSource)
            } else {
                retriever.setDataSource(mediaUrl, emptyMap())
            }

            val times = sampleTimes(durationSeconds, maxSamples = 30)
            val samples = ArrayList<FrameSample>(times.size)

            for ((index, sec) in times.withIndex()) {
                coroutineContext.ensureActive()
                val frame = getScaledFrame(retriever, sec)
                if (frame != null) {
                    val hash = differenceHash(frame)
                    val previous = samples.lastOrNull()
                    val diff = if (previous == null) 0 else hamming(previous.hash, hash)
                    samples += FrameSample(sec, hash, frame, diff)
                }
                val p = 10 + (((index + 1) * 40) / max(1, times.size))
                onProgress(p.coerceAtMost(50), "Ищем крупные смены в видео…")
            }

            val ocrIndexes = chooseTelegramOcrIndexes(samples, maxOcr = 14)
            var ocrDone = 0
            for (idx in ocrIndexes) {
                coroutineContext.ensureActive()
                val sample = samples.getOrNull(idx) ?: continue
                val bitmap = sample.bitmap ?: continue
                sample.ocr = recognize(bitmap)
                ocrDone++
                val p = 52 + ((ocrDone * 34) / max(1, ocrIndexes.size))
                onProgress(p.coerceAtMost(86), "Распознаём названия на экране…")
            }

            onProgress(90, "Собираем понятные таймкоды…")
            val chapters = buildTelegramChapters(samples, durationSeconds, videoTitle)
            samples.forEach { it.bitmap?.takeIf { b -> !b.isRecycled }?.recycle() }

            onProgress(100, "Готово")
            SmartAnalysisResult(chapters, samples.size, ocrDone > 0)
        } finally {
            runCatching { retriever.release() }
            runCatching { dataSource?.close() }
        }
    }

    private fun fetchTwitchExtras(videoId: String): TwitchExtras {
        val payload = JSONObject().apply {
            put("query", TWITCH_QUERY)
            put("variables", JSONObject().apply { put("id", videoId) })
        }.toString()

        val root = JSONObject(postJson(GQL_URL, payload, mapOf("Client-ID" to TWITCH_WEB_CLIENT_ID)))
        if (root.has("errors")) {
            val msg = root.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
            throw IOException(msg?.takeIf { it.isNotBlank() } ?: "Twitch не вернул структуру стрима")
        }

        val video = root.optJSONObject("data")?.optJSONObject("video")
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
                markers += Marker((startMs / 1000L).toInt().coerceAtLeast(0), description.ifBlank { game })
            }
        }

        return TwitchExtras(
            markers = markers.sortedBy { it.start },
            storyboardUrl = video.optString("seekPreviewsURL").takeIf { it.startsWith("http") }
        )
    }

    private fun normalizeMarkers(markers: List<Marker>, durationSeconds: Int): List<Marker> {
        val out = mutableListOf<Marker>()
        for (marker in markers.sortedBy { it.start }) {
            if (marker.start > durationSeconds && durationSeconds > 0) continue
            val previous = out.lastOrNull()
            if (previous != null && previous.raw.equals(marker.raw, ignoreCase = true)) continue
            out += marker
        }
        if (out.isEmpty() || out.first().start > 30) {
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
            if (width <= 0 || height <= 0 || cols <= 0 || rows <= 0 || count <= 0 || interval <= 0.0) continue

            val images = buildList {
                for (j in 0 until imagesJson.length()) {
                    val file = imagesJson.optString(j)
                    if (file.isNotBlank()) add(URL(URL(url), file).toString())
                }
            }
            if (images.isEmpty()) continue

            val candidate = Storyboard(width, height, cols, rows, interval, count, images)
            if (best == null || candidate.width > best.width) best = candidate
        }
        return best ?: throw IOException("Twitch не отдал storyboard")
    }

    private fun loadStoryboardSamples(
        sb: Storyboard,
        markers: List<Marker>,
        durationSeconds: Int
    ): MutableList<FrameSample> {
        val wanted = linkedSetOf<Int>()
        val target = 40
        val step = max(1, ceil(sb.count / target.toDouble()).toInt())
        var i = 0
        while (i < sb.count) {
            wanted += i
            i += step
        }
        wanted += 0
        wanted += (sb.count - 1).coerceAtLeast(0)
        for (marker in markers) {
            val idx = (marker.start / sb.intervalSec).toInt().coerceIn(0, sb.count - 1)
            wanted += idx
            if (idx + 1 < sb.count) wanted += idx + 1
        }

        val stripCache = HashMap<String, Bitmap>()
        val samples = mutableListOf<FrameSample>()
        var previousHash: Long? = null

        for (idx in wanted.sorted().take(52)) {
            val frame = storyboardFrame(sb, idx, stripCache) ?: continue
            val time = (idx * sb.intervalSec).toInt().coerceIn(0, durationSeconds.coerceAtLeast(0))
            val hash = differenceHash(frame)
            val diff = previousHash?.let { hamming(it, hash) } ?: 0
            samples += FrameSample(time, hash, frame, diff)
            previousHash = hash
        }

        stripCache.values.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        return samples
    }

    private fun storyboardFrame(
        sb: Storyboard,
        index: Int,
        stripCache: MutableMap<String, Bitmap>
    ): Bitmap? {
        val perImage = sb.cols * sb.rows
        if (perImage <= 0) return null
        val imageIndex = index / perImage
        if (imageIndex !in sb.imageUrls.indices) return null
        val within = index % perImage
        val col = within % sb.cols
        val row = within / sb.cols
        val url = sb.imageUrls[imageIndex]

        val strip = stripCache[url] ?: downloadBitmap(url)?.also { stripCache[url] = it } ?: return null
        val x = col * sb.width
        val y = row * sb.height
        if (x + sb.width > strip.width || y + sb.height > strip.height) return null

        val cell = Bitmap.createBitmap(strip, x, y, sb.width, sb.height)
        val targetWidth = max(440, sb.width)
        val targetHeight = max(248, (sb.height * targetWidth.toFloat() / sb.width).toInt())
        val scaled = Bitmap.createScaledBitmap(cell, targetWidth, targetHeight, true)
        if (scaled !== cell) cell.recycle()
        return scaled
    }

    private fun chooseOcrIndexes(samples: List<FrameSample>, markers: List<Marker>, maxOcr: Int): List<Int> {
        if (samples.isEmpty()) return emptyList()
        val scores = HashMap<Int, Int>()
        scores[0] = 100

        for (marker in markers) {
            val idx = samples.indices.minByOrNull { kotlin.math.abs(samples[it].timeSec - marker.start) } ?: continue
            scores[idx] = max(scores[idx] ?: 0, 90)
            if (idx + 1 < samples.size) scores[idx + 1] = max(scores[idx + 1] ?: 0, 82)
        }

        for (i in 1 until samples.size) {
            if (samples[i].diffFromPrevious >= 20) {
                scores[i] = max(scores[i] ?: 0, samples[i].diffFromPrevious + 35)
            }
        }

        return scores.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .take(maxOcr)
            .map { it.key }
            .sorted()
    }

    private fun chooseTelegramOcrIndexes(samples: List<FrameSample>, maxOcr: Int): List<Int> {
        if (samples.isEmpty()) return emptyList()
        val scores = HashMap<Int, Int>()
        scores[0] = 100
        if (samples.size > 1) scores[1] = 80

        for (i in 1 until samples.size) {
            val diff = samples[i].diffFromPrevious
            if (diff >= 16) {
                scores[i] = max(scores[i] ?: 0, diff + 35)
                if (i + 1 < samples.size) scores[i + 1] = max(scores[i + 1] ?: 0, diff + 18)
            }
        }

        return scores.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .take(maxOcr)
            .map { it.key }
            .sorted()
    }

    private suspend fun recognize(bitmap: Bitmap): OcrSnapshot {
        val result = suspendCancellableCoroutine<Text?> { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
        } ?: return OcrSnapshot("", null, false, 0)

        val full = result.text.replace(Regex("\\s+"), " ").trim()
        val lower = full.lowercase()
        val youtubeLike = listOf(
            "youtube", "youtu.be", "подписаться", "просмотров", "comments",
            "share", "смотреть позже", "видео", "ютуб"
        ).count { it in lower } >= 1

        data class Candidate(val text: String, val score: Int)
        val candidates = mutableListOf<Candidate>()

        for (block in result.textBlocks) {
            for (line in block.lines) {
                val text = cleanOcrLine(line.text)
                if (!isUsefulLine(text)) continue
                val box = line.boundingBox
                val letters = text.count { it.isLetter() }
                val words = text.split(' ').count { it.isNotBlank() }
                var score = letters + words * 3
                if (youtubeLike) score += 14
                if (box != null && box.top < bitmap.height * 0.72f) score += 5
                if (text.length in 14..72) score += 8
                if (text.any { it in "—-:|•" }) score += 2
                candidates += Candidate(text, score)
            }
        }

        val best = candidates.maxByOrNull { it.score }
        val confidence = when {
            best == null -> 0
            youtubeLike && best.score >= 55 -> 90
            youtubeLike -> 78
            best.score >= 55 -> 72
            else -> 58
        }

        return OcrSnapshot(full, best?.text, youtubeLike, confidence)
    }

    private fun cleanOcrLine(raw: String): String =
        raw.replace(Regex("\\s+"), " ")
            .replace(Regex("[\\u0000-\\u001F]"), "")
            .trim()
            .trim('|', '•', '-', '—', ':', ';')

    private fun isUsefulLine(text: String): Boolean {
        if (text.length !in 7..100) return false
        if (text.count { it.isLetter() } < 5) return false
        if (text.count { it.isDigit() } > text.length * 0.45) return false

        val lower = text.lowercase()
        val banned = listOf(
            "twitch", "t2x2", "t.me/", "telegram", "подписаться", "отслеживать",
            "подарить подпис", "контент включает", "rub", "₽", "донат", "чат",
            "зрителей", "онлайн", "авто", "качество", "настройки"
        )
        if (banned.any { it in lower }) return false
        if ("http://" in lower || "https://" in lower || "www." in lower) return false
        return true
    }

    private fun buildTwitchChapters(
        markers: List<Marker>,
        samples: List<FrameSample>,
        durationSeconds: Int
    ): List<SmartChapter> {
        val boundaries = mutableListOf<Boundary>()

        for ((index, marker) in markers.withIndex()) {
            val nearest = samples.minByOrNull { kotlin.math.abs(it.timeSec - marker.start) }
            val ocr = nearest?.ocr
            val baseline = markerTitle(marker.raw, index, marker.start)
            val useOcr = ocr?.watchTitle?.takeIf { marker.raw.isBlank() || isChatCategory(marker.raw) }

            boundaries += if (!useOcr.isNullOrBlank()) {
                Boundary(marker.start, "Смотрит: " + useOcr, "Распознано по кадру • Twitch", ocr.confidence)
            } else {
                Boundary(marker.start, baseline, markerDetail(marker.raw), 84)
            }
        }

        var lastWatch: String? = null
        var lastWatchTime = -10_000
        for (sample in samples) {
            val title = sample.ocr?.watchTitle ?: continue
            val normalized = normalizeForCompare(title)
            if (normalized.isBlank()) continue
            if (lastWatch == null || similarity(lastWatch, normalized) < 0.55) {
                val insideMarker = markers.lastOrNull { it.start <= sample.timeSec }
                if (insideMarker == null || isChatCategory(insideMarker.raw) || insideMarker.raw.isBlank()) {
                    if (sample.timeSec - lastWatchTime >= 90) {
                        boundaries += Boundary(
                            sample.timeSec,
                            "Смотрит: " + title,
                            "Название найдено OCR по превью",
                            sample.ocr?.confidence ?: 76
                        )
                        lastWatch = normalized
                        lastWatchTime = sample.timeSec
                    }
                }
            }
        }

        return finalizeBoundaries(boundaries, durationSeconds, "Начало стрима")
    }

    private fun buildTelegramChapters(
        samples: List<FrameSample>,
        durationSeconds: Int,
        videoTitle: String
    ): List<SmartChapter> {
        val boundaries = mutableListOf<Boundary>()
        boundaries += Boundary(0, "Начало видео", "Telegram • быстрый анализ", 82)

        var lastMeaningful = ""
        var lastBoundaryTime = 0

        for (sample in samples) {
            if (sample.timeSec == 0) continue
            val ocr = sample.ocr
            val watch = ocr?.watchTitle
            if (!watch.isNullOrBlank()) {
                val normalized = normalizeForCompare(watch)
                if (lastMeaningful.isBlank() || similarity(lastMeaningful, normalized) < 0.55) {
                    if (sample.timeSec - lastBoundaryTime >= 75) {
                        boundaries += Boundary(
                            sample.timeSec,
                            "Смотрит: " + watch,
                            "Название распознано на экране",
                            ocr.confidence
                        )
                        lastMeaningful = normalized
                        lastBoundaryTime = sample.timeSec
                        continue
                    }
                }
            }

            if (sample.diffFromPrevious >= 32 && sample.timeSec - lastBoundaryTime >= 300) {
                boundaries += Boundary(
                    sample.timeSec,
                    "Новый фрагмент",
                    "Обнаружена крупная смена сцены",
                    58
                )
                lastBoundaryTime = sample.timeSec
            }
        }

        if (boundaries.size == 1 && videoTitle.isNotBlank() && !videoTitle.equals("Запись стрима", true)) {
            boundaries[0] = Boundary(0, videoTitle.take(72), "Telegram • название видео", 72)
        }

        return finalizeBoundaries(boundaries, durationSeconds, "Начало видео")
    }

    private fun finalizeBoundaries(
        input: List<Boundary>,
        durationSeconds: Int,
        fallbackTitle: String
    ): List<SmartChapter> {
        val sorted = input.sortedBy { it.time }
        val merged = mutableListOf<Boundary>()

        for (boundary in sorted) {
            val time = boundary.time.coerceIn(0, durationSeconds.coerceAtLeast(0))
            val b = boundary.copy(time = time)
            val previous = merged.lastOrNull()

            if (previous != null && time - previous.time < 35) {
                if (b.confidence > previous.confidence) {
                    merged[merged.lastIndex] = b.copy(time = previous.time)
                }
                continue
            }

            if (previous != null &&
                normalizeForCompare(previous.title) == normalizeForCompare(b.title)
            ) {
                continue
            }

            merged += b
        }

        if (merged.isEmpty() || merged.first().time > 0) {
            merged.add(0, Boundary(0, fallbackTitle, "Автоматический анализ", 60))
        }

        return merged.mapIndexed { index, b ->
            val end = merged.getOrNull(index + 1)?.time ?: durationSeconds.coerceAtLeast(b.time)
            SmartChapter(
                startSeconds = b.time,
                endSeconds = end.coerceAtLeast(b.time),
                title = b.title,
                detail = b.detail,
                confidence = b.confidence
            )
        }
    }

    private fun buildFromMarkers(markers: List<Marker>, durationSeconds: Int): List<SmartChapter> {
        if (markers.isEmpty()) {
            return listOf(SmartChapter(0, durationSeconds.coerceAtLeast(0), "Начало стрима", "Twitch", 60))
        }
        return markers.mapIndexed { index, marker ->
            val end = markers.getOrNull(index + 1)?.start ?: durationSeconds.coerceAtLeast(marker.start)
            SmartChapter(
                marker.start,
                end,
                markerTitle(marker.raw, index, marker.start),
                markerDetail(marker.raw),
                82
            )
        }
    }

    private fun markerTitle(raw: String, index: Int, start: Int): String {
        val value = raw.trim()
        val lower = value.lowercase()
        if (index == 0 && start <= 30) return "Начало стрима"
        return when {
            isChatCategory(value) -> "Общение / реакции"
            lower == "irl" || "irl" in lower -> "IRL / общение"
            "music" in lower -> "Музыка / общение"
            "special events" in lower -> "Событие / просмотр"
            value.isBlank() -> "Фрагмент стрима"
            else -> "Играет: " + value
        }
    }

    private fun markerDetail(raw: String): String =
        raw.takeIf { it.isNotBlank() }?.let { "Категория Twitch: " + it } ?: "Раздел стрима"

    private fun isChatCategory(raw: String): Boolean {
        val lower = raw.lowercase()
        return "just chatting" in lower || "общение" in lower || "talk" in lower
    }

    private fun sampleTimes(durationSeconds: Int, maxSamples: Int): List<Int> {
        if (durationSeconds <= 0) return listOf(0)
        val count = max(2, minOf(maxSamples, (durationSeconds / 60) + 2))
        val step = durationSeconds.toDouble() / (count - 1)
        return (0 until count).map { (it * step).toInt().coerceIn(0, durationSeconds) }.distinct()
    }

    private fun getScaledFrame(retriever: MediaMetadataRetriever, seconds: Int): Bitmap? {
        val timeUs = seconds.coerceAtLeast(0) * 1_000_000L
        return runCatching {
            if (Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    480,
                    270
                )
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        }.getOrNull()
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
                bit++
            }
        }
        if (scaled !== bitmap) scaled.recycle()
        return hash
    }

    private fun luminance(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    private fun normalizeForCompare(value: String): String =
        value.lowercase()
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun similarity(a: String, b: String): Double {
        val aa = normalizeForCompare(a).split(' ').filter { it.length > 2 }.toSet()
        val bb = normalizeForCompare(b).split(' ').filter { it.length > 2 }.toSet()
        if (aa.isEmpty() || bb.isEmpty()) return 0.0
        val intersection = aa.intersect(bb).size.toDouble()
        val union = aa.union(bb).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }

    private fun postJson(url: String, payload: String, headers: Map<String, String>): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        return try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP " + code)
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun getText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP " + code)
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadBitmap(url: String): Bitmap? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 10_000
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

    fun encode(chapters: List<SmartChapter>): String {
        val root = JSONObject()
        root.put("version", 2)
        val array = JSONArray()
        chapters.forEach { chapter ->
            array.put(JSONObject().apply {
                put("start", chapter.startSeconds)
                put("end", chapter.endSeconds)
                put("title", chapter.title)
                put("detail", chapter.detail)
                put("confidence", chapter.confidence)
            })
        }
        root.put("chapters", array)
        return root.toString()
    }

    fun decode(json: String): List<SmartChapter>? = runCatching {
        val root = JSONObject(json)
        if (root.optInt("version", 0) != 2) return@runCatching null
        val array = root.getJSONArray("chapters")
        val chapters = mutableListOf<SmartChapter>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            chapters += SmartChapter(
                startSeconds = obj.getInt("start"),
                endSeconds = obj.getInt("end"),
                title = obj.getString("title"),
                detail = obj.getString("detail"),
                confidence = obj.optInt("confidence", 60)
            )
        }
        chapters
    }.getOrNull()
}
