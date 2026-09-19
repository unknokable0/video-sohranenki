package com.unknokable.videosohranenki

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

data class AiChapter(
    val startSeconds: Int,
    val endSeconds: Int,
    val title: String,
    val detail: String
)

data class AiAnalysisResult(
    val chapters: List<AiChapter>,
    val sampledFrames: Int
)

class LocalAiAnalyzer {

    suspend fun analyze(
        mediaUrl: String,
        durationSeconds: Int,
        onProgress: (Int) -> Unit = {}
    ): AiAnalysisResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.52f)
                .build()
        )
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        try {
            retriever.setDataSource(mediaUrl, emptyMap())

            val duration = if (durationSeconds > 0) {
                durationSeconds
            } else {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.div(1000L)
                    ?.toInt()
                    ?: 0
            }

            if (duration <= 0) {
                return@withContext AiAnalysisResult(emptyList(), 0)
            }

            // Первый проход: достаточно частый, но не убивает телефон.
            // Для 25-минутного ролика это примерно 1 кадр каждые 12 секунд.
            val coarseStep = when {
                duration <= 10 * 60 -> 8
                duration <= 40 * 60 -> 12
                else -> 15
            }

            val timestamps = buildList {
                var t = 0
                while (t < duration) {
                    add(t)
                    t += coarseStep
                }
                if (isEmpty() || last() != duration - 1) {
                    add((duration - 1).coerceAtLeast(0))
                }
            }

            val coarse = mutableListOf<SceneSample>()
            var analyzedFrames = 0

            timestamps.forEachIndexed { index, second ->
                analyzeAt(retriever, second, labeler, recognizer)?.let {
                    coarse += it
                    analyzedFrames++
                }
                onProgress(((index + 1) * 70 / timestamps.size).coerceIn(0, 70))
            }

            if (coarse.isEmpty()) {
                return@withContext AiAnalysisResult(emptyList(), 0)
            }

            val stabilized = stabilize(coarse)
            val boundaries = mutableListOf<Boundary>()
            val transitions = stabilized.zipWithNext()
                .filter { (a, b) -> a.title != b.title }

            transitions.forEachIndexed { index, (left, right) ->
                val refined = refineBoundary(
                    retriever = retriever,
                    from = left.second,
                    to = right.second,
                    targetTitle = right.title,
                    labeler = labeler,
                    recognizer = recognizer
                )
                analyzedFrames += refined.extraFrames
                boundaries += Boundary(
                    second = refined.second,
                    title = right.title,
                    detail = refined.detail.ifBlank { right.detail }
                )

                val progress = 70 + ((index + 1) * 30 / max(1, transitions.size))
                onProgress(progress.coerceIn(70, 100))
            }

            onProgress(100)

            AiAnalysisResult(
                chapters = buildChapters(
                    first = stabilized.first(),
                    boundaries = boundaries,
                    duration = duration
                ),
                sampledFrames = analyzedFrames
            )
        } finally {
            runCatching { retriever.release() }
            runCatching { labeler.close() }
            runCatching { recognizer.close() }
        }
    }

    private suspend fun refineBoundary(
        retriever: MediaMetadataRetriever,
        from: Int,
        to: Int,
        targetTitle: String,
        labeler: com.google.mlkit.vision.label.ImageLabeler,
        recognizer: com.google.mlkit.vision.text.TextRecognizer
    ): RefinedBoundary {
        if (to <= from + 2) {
            return RefinedBoundary(to, "", 0)
        }

        val samples = mutableListOf<SceneSample>()
        var t = (from + 2).coerceAtMost(to)
        while (t <= to) {
            analyzeAt(retriever, t, labeler, recognizer)?.let { samples += it }
            t += 2
        }

        // Берём первый момент, где новый тип сцены подтверждается двумя соседними кадрами.
        for (i in 0 until samples.size - 1) {
            if (samples[i].title == targetTitle && samples[i + 1].title == targetTitle) {
                return RefinedBoundary(
                    second = samples[i].second,
                    detail = samples[i].detail,
                    extraFrames = samples.size
                )
            }
        }

        val firstMatch = samples.firstOrNull { it.title == targetTitle }
        return RefinedBoundary(
            second = firstMatch?.second ?: to,
            detail = firstMatch?.detail.orEmpty(),
            extraFrames = samples.size
        )
    }

    private suspend fun analyzeAt(
        retriever: MediaMetadataRetriever,
        second: Int,
        labeler: com.google.mlkit.vision.label.ImageLabeler,
        recognizer: com.google.mlkit.vision.text.TextRecognizer
    ): SceneSample? {
        val raw = runCatching {
            retriever.getFrameAtTime(
                second * 1_000_000L,
                MediaMetadataRetriever.OPTION_CLOSEST
            )
        }.getOrNull() ?: return null

        val scaled = scaleForMl(raw)
        if (scaled !== raw) raw.recycle()

        return try {
            analyzeFrame(scaled, second, labeler, recognizer)
        } finally {
            scaled.recycle()
        }
    }

    private fun scaleForMl(bitmap: Bitmap): Bitmap {
        val maxWidth = 640
        if (bitmap.width <= maxWidth) return bitmap
        val ratio = maxWidth.toFloat() / bitmap.width.toFloat()
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, maxWidth, height, true)
    }

    private suspend fun analyzeFrame(
        bitmap: Bitmap,
        second: Int,
        labeler: com.google.mlkit.vision.label.ImageLabeler,
        recognizer: com.google.mlkit.vision.text.TextRecognizer
    ): SceneSample {
        val image = InputImage.fromBitmap(bitmap, 0)

        val labels = runCatching {
            labeler.process(image).awaitResult()
                .sortedByDescending { it.confidence }
                .take(8)
                .map { it.text.lowercase() }
        }.getOrDefault(emptyList())

        val text = runCatching {
            recognizer.process(image).awaitResult().text.lowercase()
        }.getOrDefault("")

        return classify(second, text, labels)
    }

    private fun stabilize(samples: List<SceneSample>): List<SceneSample> {
        if (samples.size < 3) return samples

        return samples.mapIndexed { index, sample ->
            val from = (index - 1).coerceAtLeast(0)
            val to = (index + 1).coerceAtMost(samples.lastIndex)
            val window = samples.subList(from, to + 1)
            val winner = window
                .groupingBy { it.title }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key

            if (winner != null && winner != sample.title) {
                window.firstOrNull { it.title == winner }?.copy(second = sample.second) ?: sample
            } else sample
        }
    }

    private fun classify(second: Int, text: String, labels: List<String>): SceneSample {
        val haystack = (text + " " + labels.joinToString(" ")).lowercase()

        return when {
            listOf("minecraft", "mojang", "fabric", "lunar client", "prism launcher")
                .any { it in haystack } ->
                SceneSample(second, "Игра: Minecraft", "На экране распознаны признаки Minecraft")

            listOf("counter-strike", "counter strike", "cs2", "premier", "faceit")
                .any { it in haystack } ->
                SceneSample(second, "Игра: CS2", "На экране распознаны признаки Counter-Strike")

            listOf("youtube", "youtu.be", "подписаться", "subscribe", "просмотры", "views")
                .any { it in haystack } -> {
                val title = extractLikelyVideoTitle(text)
                SceneSample(
                    second,
                    "Просмотр видео",
                    if (title != null) "Видео: $title" else "Стример смотрит видео или видеосервис"
                )
            }

            listOf("browser", "web page", "website", "chrome", "firefox")
                .any { it in haystack } ->
                SceneSample(second, "Браузер / просмотр", "На экране обнаружен браузер или веб-страница")

            labels.any { "video game" in it || "game" == it || "computer game" in it } ->
                SceneSample(second, "Игровой момент", "ML-модель определила игровой контент")

            labels.any { "person" in it || "face" in it || "conversation" in it } ->
                SceneSample(second, "Разговор / стрим", "Похоже на разговорный сегмент")

            else ->
                SceneSample(second, "Другой момент", "Сцена не распознана достаточно уверенно")
        }
    }

    private fun extractLikelyVideoTitle(text: String): String? {
        val banned = listOf(
            "youtube", "подписаться", "subscribe", "просмотров", "views",
            "комментарии", "comments", "главная", "home", "shorts", "поделиться"
        )

        return text.lines()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.length in 12..90 }
            .filter { line -> banned.none { it in line.lowercase() } }
            .filter { line -> line.count { it.isLetter() } >= 6 }
            .maxByOrNull { it.length }
            ?.take(90)
    }

    private fun buildChapters(
        first: SceneSample,
        boundaries: List<Boundary>,
        duration: Int
    ): List<AiChapter> {
        val chapters = mutableListOf<AiChapter>()
        var start = 0
        var title = first.title
        var detail = first.detail

        boundaries.sortedBy { it.second }.forEach { boundary ->
            val boundarySecond = boundary.second.coerceIn(start + 1, duration)
            if (boundarySecond - start >= 4) {
                chapters += AiChapter(
                    startSeconds = start,
                    endSeconds = boundarySecond,
                    title = title,
                    detail = detail
                )
            }
            start = boundarySecond
            title = boundary.title
            detail = boundary.detail
        }

        if (start < duration) {
            chapters += AiChapter(
                startSeconds = start,
                endSeconds = duration,
                title = title,
                detail = detail
            )
        }

        // Убираем короткий ML-шум, но не сдвигаем подтверждённые длинные переходы.
        val cleaned = mutableListOf<AiChapter>()
        chapters.forEach { chapter ->
            if (cleaned.isNotEmpty() && chapter.endSeconds - chapter.startSeconds < 8) {
                val prev = cleaned.removeLast()
                cleaned += prev.copy(endSeconds = chapter.endSeconds)
            } else {
                cleaned += chapter
            }
        }
        return cleaned
    }

    private data class SceneSample(
        val second: Int,
        val title: String,
        val detail: String
    )

    private data class Boundary(
        val second: Int,
        val title: String,
        val detail: String
    )

    private data class RefinedBoundary(
        val second: Int,
        val detail: String,
        val extraFrames: Int
    )

    private suspend fun <T> Task<T>.awaitResult(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result)
            }
            addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            addOnCanceledListener {
                continuation.cancel()
            }
        }
}
