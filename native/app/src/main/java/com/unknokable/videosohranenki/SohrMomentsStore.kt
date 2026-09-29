package com.unknokable.videosohranenki

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SohrMoment(
    val id: String,
    val messageId: Long,
    val positionMs: Long,
    val videoTitle: String,
    val label: String,
    val createdAtMs: Long
)

class SohrMomentsStore(context: Context) {
    private val prefs = context.getSharedPreferences("sohr_moments", Context.MODE_PRIVATE)
    @Volatile private var cache: List<SohrMoment>? = null

    fun all(): List<SohrMoment> {
        cache?.let { return it }
        val parsed = runCatching {
            val array = JSONArray(prefs.getString(KEY_ITEMS, "[]").orEmpty())
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val messageId = obj.optLong("messageId", Long.MIN_VALUE)
                    if (id.isBlank() || messageId == Long.MIN_VALUE) continue
                    add(
                        SohrMoment(
                            id = id,
                            messageId = messageId,
                            positionMs = obj.optLong("positionMs", 0L).coerceAtLeast(0L),
                            videoTitle = obj.optString("videoTitle", "Видео"),
                            label = obj.optString("label", ""),
                            createdAtMs = obj.optLong("createdAtMs", 0L)
                        )
                    )
                }
            }.sortedByDescending { it.createdAtMs }
        }.getOrDefault(emptyList())
        cache = parsed
        return parsed
    }

    fun forVideo(messageId: Long): List<SohrMoment> =
        all().filter { it.messageId == messageId }.sortedBy { it.positionMs }

    fun countForVideo(messageId: Long): Int = all().count { it.messageId == messageId }

    fun add(item: VideoItem, positionMs: Long, label: String = ""): SohrMoment {
        val now = System.currentTimeMillis()
        val normalizedPosition = positionMs.coerceAtLeast(0L)
        val cleanTitle = item.title
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .ifBlank { "Видео" }
            .take(100)

        val current = all().toMutableList()
        val nearDuplicate = current.indexOfFirst {
            it.messageId == item.messageId &&
                kotlin.math.abs(it.positionMs - normalizedPosition) <= 1_500L
        }

        val moment = SohrMoment(
            id = if (nearDuplicate >= 0) current[nearDuplicate].id else "${item.messageId}_$now",
            messageId = item.messageId,
            positionMs = normalizedPosition,
            videoTitle = cleanTitle,
            label = label.trim().take(48),
            createdAtMs = now
        )

        if (nearDuplicate >= 0) current[nearDuplicate] = moment else current.add(moment)
        save(current.sortedByDescending { it.createdAtMs })
        return moment
    }

    fun rename(id: String, label: String) {
        save(
            all().map {
                if (it.id == id) it.copy(label = label.trim().take(48)) else it
            }
        )
    }

    fun remove(id: String) {
        save(all().filterNot { it.id == id })
    }

    fun removeForVideo(messageId: Long) {
        save(all().filterNot { it.messageId == messageId })
    }

    private fun save(items: List<SohrMoment>) {
        val normalized = items.distinctBy { it.id }.take(300)
        cache = normalized
        val array = JSONArray()
        normalized.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("messageId", item.messageId)
                    .put("positionMs", item.positionMs)
                    .put("videoTitle", item.videoTitle)
                    .put("label", item.label)
                    .put("createdAtMs", item.createdAtMs)
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    companion object {
        private const val KEY_ITEMS = "items"
    }
}
