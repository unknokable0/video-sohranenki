package com.unknokable.videosohranenki

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth
import java.time.ZoneId

data class SohrMonthlyRecap(
    val month: YearMonth,
    val watchedMs: Long,
    val playStarts: Int,
    val uniqueVideos: Int,
    val longestSessionMs: Long
)

class SohrExperienceStore(context: Context) {
    private val prefs = context.getSharedPreferences("sohr_experience", Context.MODE_PRIVATE)

    fun beginVisit(nowMs: Long = System.currentTimeMillis()): Long {
        val previous = prefs.getLong(KEY_LAST_VISIT, 0L)
        prefs.edit().putLong(KEY_LAST_VISIT, nowMs).apply()
        return previous
    }

    fun lastVisitMs(): Long = prefs.getLong(KEY_LAST_VISIT, 0L)

    fun queueIds(): List<Long> {
        val raw = prefs.getString(KEY_QUEUE, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val id = array.optLong(i, Long.MIN_VALUE)
                    if (id != Long.MIN_VALUE && id !in this) add(id)
                }
            }
        }.getOrDefault(emptyList())
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
        prefs.edit().putString(KEY_QUEUE, "[]").apply()
    }

    private fun saveQueue(ids: List<Long>) {
        val array = JSONArray()
        ids.distinct().take(40).forEach(array::put)
        prefs.edit().putString(KEY_QUEUE, array.toString()).apply()
    }

    fun recordPlayStart(
        messageId: Long,
        atMs: Long = System.currentTimeMillis()
    ) {
        mutateMonth(atMs) { json ->
            json.put("playStarts", json.optInt("playStarts", 0) + 1)
            val unique = json.optJSONArray("unique") ?: JSONArray()
            val exists = (0 until unique.length()).any { unique.optLong(it) == messageId }
            if (!exists) unique.put(messageId)
            json.put("unique", unique)
        }
    }

    fun recordWatchTime(
        messageId: Long,
        watchedMs: Long,
        sessionMs: Long = watchedMs,
        atMs: Long = System.currentTimeMillis()
    ) {
        if (watchedMs <= 0L) return
        mutateMonth(atMs) { json ->
            json.put("watchedMs", json.optLong("watchedMs", 0L) + watchedMs.coerceAtMost(10 * 60_000L))
            json.put(
                "longestSessionMs",
                maxOf(json.optLong("longestSessionMs", 0L), sessionMs.coerceAtMost(12 * 60 * 60_000L))
            )
            val unique = json.optJSONArray("unique") ?: JSONArray()
            val exists = (0 until unique.length()).any { unique.optLong(it) == messageId }
            if (!exists) unique.put(messageId)
            json.put("unique", unique)
        }
    }

    fun recap(month: YearMonth = YearMonth.now()): SohrMonthlyRecap {
        val json = readMonth(month)
        return SohrMonthlyRecap(
            month = month,
            watchedMs = json.optLong("watchedMs", 0L),
            playStarts = json.optInt("playStarts", 0),
            uniqueVideos = json.optJSONArray("unique")?.length() ?: 0,
            longestSessionMs = json.optLong("longestSessionMs", 0L)
        )
    }

    private fun mutateMonth(atMs: Long, block: (JSONObject) -> Unit) {
        val month = YearMonth.from(
            java.time.Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault())
        )
        val json = readMonth(month)
        block(json)
        prefs.edit().putString(monthKey(month), json.toString()).apply()
    }

    private fun readMonth(month: YearMonth): JSONObject =
        runCatching {
            JSONObject(prefs.getString(monthKey(month), "{}").orEmpty())
        }.getOrElse { JSONObject() }

    private fun monthKey(month: YearMonth): String = "month_" + month

    companion object {
        private const val KEY_LAST_VISIT = "last_visit_ms"
        private const val KEY_QUEUE = "playback_queue"
    }
}
