package com.unknokable.videosohranenki

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class TwitchCategorySegment(
    val gameId: String,
    val gameName: String,
    val startedAtMs: Long,
    val endedAtMs: Long?
) {
    fun elapsedMs(nowMs: Long = System.currentTimeMillis()): Long =
        ((endedAtMs ?: nowMs) - startedAtMs).coerceAtLeast(0L)
}

data class TwitchCategoryTimeline(
    val streamStartedAt: String,
    val current: TwitchCategorySegment?,
    val previous: TwitchCategorySegment?
)

class TwitchCategoryTracker(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun observe(live: TwitchLiveStream, nowMs: Long = System.currentTimeMillis()): TwitchCategoryTimeline {
        val streamStartedAt = live.startedAt
        if (streamStartedAt.isBlank()) return timeline(nowMs)

        val storedStart = prefs.getString(KEY_STREAM_STARTED_AT, null)
        val segments = if (storedStart == streamStartedAt) {
            readSegments().toMutableList()
        } else {
            mutableListOf()
        }

        val streamStartMs = runCatching { Instant.parse(streamStartedAt).toEpochMilli() }
            .getOrDefault(nowMs)
        val categoryName = live.gameName.trim().ifBlank { "Без категории" }
        val categoryId = live.gameId.trim()

        val activeIndex = segments.indexOfLast { it.endedAtMs == null }
        val active = activeIndex.takeIf { it >= 0 }?.let(segments::get)

        if (active == null) {
            segments += TwitchCategorySegment(
                gameId = categoryId,
                gameName = categoryName,
                startedAtMs = streamStartMs.coerceAtMost(nowMs),
                endedAtMs = null
            )
        } else if (active.gameId != categoryId || !active.gameName.equals(categoryName, ignoreCase = true)) {
            segments[activeIndex] = active.copy(endedAtMs = nowMs)
            segments += TwitchCategorySegment(
                gameId = categoryId,
                gameName = categoryName,
                startedAtMs = nowMs,
                endedAtMs = null
            )
        }

        write(streamStartedAt, segments.takeLast(MAX_SEGMENTS))
        return buildTimeline(streamStartedAt, segments, nowMs)
    }

    @Synchronized
    fun markOffline(nowMs: Long = System.currentTimeMillis()): TwitchCategoryTimeline {
        val streamStartedAt = prefs.getString(KEY_STREAM_STARTED_AT, null).orEmpty()
        if (streamStartedAt.isBlank()) return TwitchCategoryTimeline("", null, null)

        val segments = readSegments().toMutableList()
        val activeIndex = segments.indexOfLast { it.endedAtMs == null }
        if (activeIndex >= 0) {
            segments[activeIndex] = segments[activeIndex].copy(endedAtMs = nowMs)
            write(streamStartedAt, segments)
        }
        return buildTimeline(streamStartedAt, segments, nowMs)
    }

    @Synchronized
    fun timeline(nowMs: Long = System.currentTimeMillis()): TwitchCategoryTimeline {
        val streamStartedAt = prefs.getString(KEY_STREAM_STARTED_AT, null).orEmpty()
        return buildTimeline(streamStartedAt, readSegments(), nowMs)
    }

    private fun buildTimeline(
        streamStartedAt: String,
        segments: List<TwitchCategorySegment>,
        nowMs: Long
    ): TwitchCategoryTimeline {
        val current = segments.lastOrNull { it.endedAtMs == null }
        val previous = segments
            .asReversed()
            .firstOrNull { segment -> segment.endedAtMs != null && segment !== current }
        return TwitchCategoryTimeline(streamStartedAt, current, previous)
    }

    private fun readSegments(): List<TwitchCategorySegment> {
        val raw = prefs.getString(KEY_SEGMENTS, null).orEmpty()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        TwitchCategorySegment(
                            gameId = item.optString("gameId"),
                            gameName = item.optString("gameName").ifBlank { "Без категории" },
                            startedAtMs = item.optLong("startedAtMs"),
                            endedAtMs = item.takeIf { it.has("endedAtMs") && !it.isNull("endedAtMs") }
                                ?.optLong("endedAtMs")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun write(streamStartedAt: String, segments: List<TwitchCategorySegment>) {
        val array = JSONArray()
        segments.forEach { segment ->
            array.put(
                JSONObject().apply {
                    put("gameId", segment.gameId)
                    put("gameName", segment.gameName)
                    put("startedAtMs", segment.startedAtMs)
                    if (segment.endedAtMs != null) put("endedAtMs", segment.endedAtMs)
                }
            )
        }
        prefs.edit()
            .putString(KEY_STREAM_STARTED_AT, streamStartedAt)
            .putString(KEY_SEGMENTS, array.toString())
            .apply()
    }

    companion object {
        private const val PREFS = "sohr_twitch_category_timeline"
        private const val KEY_STREAM_STARTED_AT = "stream_started_at"
        private const val KEY_SEGMENTS = "segments"
        private const val MAX_SEGMENTS = 24
    }
}
