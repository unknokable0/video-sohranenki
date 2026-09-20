package com.unknokable.videosohranenki

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class SmartChapter(
    val startSeconds: Int,
    val endSeconds: Int,
    val title: String,
    val detail: String
)

object SmartChaptersAnalyzer {
    private const val GQL_URL = "https://gql.twitch.tv/gql"
    private const val TWITCH_WEB_CLIENT_ID = "ue6666qo983tsx6so1t0vnawi233wa"

    private const val QUERY =
        "query(\$id: ID!) { video(id: \$id) { id moments(first: 100, momentRequestType: VIDEO_CHAPTER_MARKERS) { edges { node { positionMilliseconds description details { ... on GameChangeMomentDetails { game { displayName } } } } } } } }"

    suspend fun analyze(videoId: String, durationSeconds: Int): List<SmartChapter> = withContext(Dispatchers.IO) {
        require(videoId.isNotBlank() && videoId.all(Char::isDigit)) { "Некорректный Twitch Video ID" }

        val payload = JSONObject().apply {
            put("query", QUERY)
            put("variables", JSONObject().apply { put("id", videoId) })
        }.toString()

        val connection = (URL(GQL_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Client-ID", TWITCH_WEB_CLIENT_ID)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
        }

        val body = try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("Twitch analysis: HTTP $code")
            text
        } finally {
            connection.disconnect()
        }

        val root = JSONObject(body)
        if (root.has("errors")) {
            val msg = root.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
            throw IOException(msg?.takeIf { it.isNotBlank() } ?: "Twitch не вернул структуру стрима")
        }

        val edges = root.optJSONObject("data")
            ?.optJSONObject("video")
            ?.optJSONObject("moments")
            ?.optJSONArray("edges")

        data class Marker(val start: Int, val raw: String)

        val markers = mutableListOf<Marker>()
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
                val raw = description.ifBlank { game }
                markers += Marker((startMs / 1000L).toInt().coerceAtLeast(0), raw)
            }
        }

        markers.sortBy { it.start }

        val normalized = mutableListOf<Marker>()
        for (marker in markers) {
            val previous = normalized.lastOrNull()
            if (previous != null && previous.raw.equals(marker.raw, ignoreCase = true)) continue
            normalized += marker
        }

        if (normalized.isEmpty() || normalized.first().start > 30) {
            normalized.add(0, Marker(0, normalized.firstOrNull()?.raw.orEmpty()))
        }

        val safeDuration = durationSeconds.coerceAtLeast(normalized.lastOrNull()?.start ?: 0)

        val out = normalized.mapIndexed { index, marker ->
            val end = normalized.getOrNull(index + 1)?.start ?: safeDuration
            val raw = marker.raw.ifBlank { "Стрим" }
            val title = titleFor(raw, index, marker.start)
            val detail = detailFor(raw)
            SmartChapter(
                startSeconds = marker.start,
                endSeconds = end.coerceAtLeast(marker.start),
                title = title,
                detail = detail
            )
        }.filter { it.endSeconds > it.startSeconds || it.startSeconds == 0 }

        if (out.isEmpty()) {
            listOf(
                SmartChapter(
                    startSeconds = 0,
                    endSeconds = durationSeconds.coerceAtLeast(0),
                    title = "Начало стрима",
                    detail = "Twitch не нашёл смен категорий"
                )
            )
        } else out
    }

    private fun titleFor(raw: String, index: Int, start: Int): String {
        val value = raw.trim()
        val lower = value.lowercase()
        if (index == 0 && start <= 30) return "Начало стрима"
        return when {
            "just chatting" in lower -> "Общение / реакции"
            lower == "irl" || "irl" in lower -> "IRL / общение"
            "music" in lower -> "Музыка / общение"
            "special events" in lower -> "Событие / просмотр"
            value.equals("Стрим", ignoreCase = true) -> "Фрагмент стрима"
            else -> "Играет: $value"
        }
    }

    private fun detailFor(raw: String): String {
        val value = raw.trim()
        return if (value.isBlank() || value.equals("Стрим", ignoreCase = true)) {
            "Раздел стрима"
        } else {
            "Категория Twitch: $value"
        }
    }

    fun encode(chapters: List<SmartChapter>): String {
        val array = JSONArray()
        chapters.forEach { chapter ->
            array.put(JSONObject().apply {
                put("start", chapter.startSeconds)
                put("end", chapter.endSeconds)
                put("title", chapter.title)
                put("detail", chapter.detail)
            })
        }
        return array.toString()
    }

    fun decode(json: String): List<SmartChapter>? = runCatching {
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    SmartChapter(
                        startSeconds = obj.getInt("start"),
                        endSeconds = obj.getInt("end"),
                        title = obj.getString("title"),
                        detail = obj.getString("detail")
                    )
                )
            }
        }
    }.getOrNull()
}
