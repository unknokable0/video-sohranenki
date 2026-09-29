package com.unknokable.videosohranenki

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class SohrRecapSnapshot(
    val month: YearMonth,
    val watchedMs: Long,
    val starts: Int,
    val uniqueVideos: Int,
    val longestSessionMs: Long,
    val activeDay: String?,
    val activeDayMs: Long
)

class SohrRecapStore(context: Context) {
    private val prefs = context.getSharedPreferences("sohr_recap_v2", Context.MODE_PRIVATE)

    fun markSessionStart(messageId: Long, atMs: Long = System.currentTimeMillis()) {
        mutateMonth(atMs) { json ->
            json.put("starts", json.optInt("starts", 0) + 1)
            addUnique(json, messageId)
        }
    }

    fun addWatchSlice(
        messageId: Long,
        watchedMs: Long,
        sessionMs: Long,
        atMs: Long = System.currentTimeMillis()
    ) {
        if (watchedMs <= 0L) return
        val safeSlice = watchedMs.coerceAtMost(10 * 60_000L)
        mutateMonth(atMs) { json ->
            json.put("watchedMs", json.optLong("watchedMs", 0L) + safeSlice)
            json.put(
                "longestSessionMs",
                maxOf(
                    json.optLong("longestSessionMs", 0L),
                    sessionMs.coerceIn(0L, 12 * 60 * 60_000L)
                )
            )
            addUnique(json, messageId)

            val day = Instant.ofEpochMilli(atMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString()
            val days = json.optJSONObject("days") ?: JSONObject()
            days.put(day, days.optLong(day, 0L) + safeSlice)
            json.put("days", days)
        }
    }

    fun snapshot(month: YearMonth = YearMonth.now()): SohrRecapSnapshot {
        val json = readMonth(month)
        val days = json.optJSONObject("days") ?: JSONObject()
        var bestDay: String? = null
        var bestMs = 0L
        val keys = days.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = days.optLong(key, 0L)
            if (value > bestMs) {
                bestDay = key
                bestMs = value
            }
        }

        return SohrRecapSnapshot(
            month = month,
            watchedMs = json.optLong("watchedMs", 0L),
            starts = json.optInt("starts", 0),
            uniqueVideos = json.optJSONArray("unique")?.length() ?: 0,
            longestSessionMs = json.optLong("longestSessionMs", 0L),
            activeDay = bestDay,
            activeDayMs = bestMs
        )
    }

    private fun addUnique(json: JSONObject, messageId: Long) {
        val unique = json.optJSONArray("unique") ?: JSONArray()
        var exists = false
        for (i in 0 until unique.length()) {
            if (unique.optLong(i, Long.MIN_VALUE) == messageId) {
                exists = true
                break
            }
        }
        if (!exists) unique.put(messageId)
        json.put("unique", unique)
    }

    private fun mutateMonth(atMs: Long, block: (JSONObject) -> Unit) {
        val month = YearMonth.from(
            Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault())
        )
        val json = readMonth(month)
        block(json)
        prefs.edit().putString(monthKey(month), json.toString()).apply()
    }

    private fun readMonth(month: YearMonth): JSONObject =
        runCatching {
            JSONObject(prefs.getString(monthKey(month), "{}").orEmpty())
        }.getOrElse { JSONObject() }

    private fun monthKey(month: YearMonth): String = "month_$month"
}
