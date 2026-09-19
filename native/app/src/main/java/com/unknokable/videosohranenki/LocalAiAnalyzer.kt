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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
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
                .setConfidenceThreshold(0.55f)
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

            // Максимум около 40 кадров на ролик. Для 25 минут это примерно 1 кадр каждые 35–40 сек.
            val step = max(15, duration / 40)
            val timestamps = buildList {
                var t = 0
                while (t < duration) {
                    add(t)
                    t += step
                }
                if (isEmpty() || last() != duration - 1) add((duration - 1).coerceAtLeast(0))
            }

            val samples = mutableListOf<SceneSample>()

            timestamps.forEachIndexed { index, second ->
                val bitmap = runCatching {
                    retriever.getFrameAtTime(
                        second * 1_000_000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                }.getOrNull()

                if (bitmap != null) {
                    val sample = analyzeFrame(bitmap, second, labeler, recognizer)
                    bitmap.recycle()
                    samples += sample
                }

                onProgress(((index + 1) * 100 / timestamps.size).coerceIn(0, 100))
            }

            AiAnalysisResult(
                chapters = mergeSamples(samples, duration),
                sampledFrames = samples.size
            )
        } finally {
            runCatching { retriever.release() }
            runCatching { labeler.close() }
            runCatching { recognizer.close() }
        }
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
                .take(6)
                .map { it.text.lowercase() }
        }.getOrDefault(emptyList())

        val text = runCatching {
            recognizer.process(image).awaitResult().text.lowercase()
        }.getOrDefault("")

        return classify(second, text, labels)
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
                .any { it in haystack } ->
                SceneSample(second, "Просмотр видео", "Похоже, стример смотрит ролик или видеосервис")

            listOf("browser", "web page", "website", "chrome", "firefox")
                .any { it in haystack } ->
                SceneSample(second, "Браузер / просмотр", "На экране обнаружен браузер или веб-страница")

            labels.any { "video game" in it || "game" == it } ->
                SceneSample(second, "Игровой момент", "ML-модель определила игровой контент")

            labels.any { "person" in it || "face" in it } ->
                SceneSample(second, "Разговор / стрим", "Похоже на разговорный сегмент")

            else ->
                SceneSample(second, "Другой момент", "Сцена не распознана достаточно уверенно")
        }
    }

    private fun mergeSamples(samples: List<SceneSample>, duration: Int): List<AiChapter> {
        if (samples.isEmpty()) return emptyList()

        val merged = mutableListOf<AiChapter>()
        var currentTitle = samples.first().title
        var currentDetail = samples.first().detail
        var start = samples.first().second

        for (i in 1 until samples.size) {
            val sample = samples[i]
            if (sample.title != currentTitle) {
                val end = sample.second.coerceAtLeast(start + 1)
                merged += AiChapter(start, end, currentTitle, currentDetail)
                start = sample.second
                currentTitle = sample.title
                currentDetail = sample.detail
            }
        }

        merged += AiChapter(
            startSeconds = start,
            endSeconds = duration,
            title = currentTitle,
            detail = currentDetail
        )

        // Короткие одиночные сегменты < 20 сек объединяем с предыдущими, чтобы таймлайн не дробился.
        val cleaned = mutableListOf<AiChapter>()
        for (chapter in merged) {
            if (cleaned.isNotEmpty() && chapter.endSeconds - chapter.startSeconds < 20) {
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
