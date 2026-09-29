package com.unknokable.videosohranenki

import android.content.Context
import org.json.JSONArray

class SohrExperienceStore(context: Context) {
    private val prefs = context.getSharedPreferences("sohr_experience", Context.MODE_PRIVATE)
    @Volatile private var queueCache: List<Long>? = null

    fun beginVisit(nowMs: Long = System.currentTimeMillis()): Long {
        val previous = prefs.getLong(KEY_LAST_VISIT, 0L)
        prefs.edit().putLong(KEY_LAST_VISIT, nowMs).apply()
        return previous
    }

    fun lastVisitMs(): Long = prefs.getLong(KEY_LAST_VISIT, 0L)

    fun queueIds(): List<Long> {
        queueCache?.let { return it }
        val raw = prefs.getString(KEY_QUEUE, "[]").orEmpty()
        val parsed = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val id = array.optLong(i, Long.MIN_VALUE)
                    if (id != Long.MIN_VALUE && id !in this) add(id)
                }
            }
        }.getOrDefault(emptyList())
        queueCache = parsed
        return parsed
    }

    fun addNext(messageId: Long) {
        val current = queueIds().filterNot { it == messageId }.toMutableList()
        current.add(0, messageId)
        saveQueue(current)
    }

    fun addLast(messageId: Long) {
        val current = queueIds().filterNot { it == messageId }.toMutableList()
        current.add(messageId)
        saveQueue(current)
    }

    fun removeFromQueue(messageId: Long) {
        saveQueue(queueIds().filterNot { it == messageId })
    }

    fun moveInQueue(messageId: Long, delta: Int) {
        val list = queueIds().toMutableList()
        val index = list.indexOf(messageId)
        if (index < 0) return
        val target = (index + delta).coerceIn(0, list.lastIndex)
        if (target == index) return
        val value = list.removeAt(index)
        list.add(target, value)
        saveQueue(list)
    }

    fun clearQueue() {
        queueCache = emptyList()
        prefs.edit().putString(KEY_QUEUE, "[]").apply()
    }

    private fun saveQueue(ids: List<Long>) {
        val normalized = ids.distinct().take(40)
        queueCache = normalized
        val array = JSONArray()
        normalized.forEach(array::put)
        prefs.edit().putString(KEY_QUEUE, array.toString()).apply()
    }

    companion object {
        private const val KEY_LAST_VISIT = "last_visit_ms"
        private const val KEY_QUEUE = "playback_queue"
    }
}
