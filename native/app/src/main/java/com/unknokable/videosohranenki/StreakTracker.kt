package com.unknokable.videosohranenki

import android.content.Context
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class StreakTracker(context: Context) {
    private val prefs = context.getSharedPreferences("sohr_streak", Context.MODE_PRIVATE)

    fun markWatched(date: LocalDate = LocalDate.now()): Boolean {
        val set = prefs.getStringSet(KEY_DAYS, emptySet()).orEmpty().toMutableSet()
        val value = date.toString()
        if (!set.add(value)) return false
        prefs.edit()
            .putStringSet(KEY_DAYS, set)
            .putString(KEY_PENDING_IGNITION_DAY, value)
            .apply()
        return true
    }

    fun watchedDays(): Set<LocalDate> =
        prefs.getStringSet(KEY_DAYS, emptySet()).orEmpty()
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()

    fun currentStreak(today: LocalDate = LocalDate.now()): Int {
        val days = watchedDays()
        if (days.isEmpty()) return 0
        var cursor = when {
            today in days -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> return 0
        }
        var count = 0
        while (cursor in days) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    fun longestStreak(): Int {
        val sorted = watchedDays().sorted()
        if (sorted.isEmpty()) return 0
        var best = 1
        var current = 1
        for (index in 1 until sorted.size) {
            current = if (ChronoUnit.DAYS.between(sorted[index - 1], sorted[index]) == 1L) {
                current + 1
            } else {
                1
            }
            if (current > best) best = current
        }
        return best
    }

    fun recentDays(
        count: Int = 7,
        today: LocalDate = LocalDate.now()
    ): List<Pair<LocalDate, Boolean>> {
        val days = watchedDays()
        return (count - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            date to (date in days)
        }
    }

    fun totalWatchedDays(): Int = watchedDays().size
    fun watchedToday(today: LocalDate = LocalDate.now()): Boolean = today in watchedDays()
    fun lastWatchedDay(): LocalDate? = watchedDays().maxOrNull()

    fun consumeIgnition(date: LocalDate = LocalDate.now()): Boolean {
        val expected = date.toString()
        if (prefs.getString(KEY_PENDING_IGNITION_DAY, null) != expected) return false
        prefs.edit().remove(KEY_PENDING_IGNITION_DAY).apply()
        return true
    }

    companion object {
        private const val KEY_DAYS = "watched_days"
        private const val KEY_PENDING_IGNITION_DAY = "pending_ignition_day"
    }
}
