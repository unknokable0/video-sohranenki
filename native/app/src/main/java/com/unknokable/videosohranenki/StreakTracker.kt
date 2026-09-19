package com.unknokable.videosohranenki

import android.content.Context
import java.time.LocalDate

class StreakTracker(context: Context) {
    private val prefs = context.getSharedPreferences("sohr_streak", Context.MODE_PRIVATE)

    fun markWatched(date: LocalDate = LocalDate.now()): Boolean {
        val set = prefs.getStringSet(KEY_DAYS, emptySet()).orEmpty().toMutableSet()
        val value = date.toString()
        if (!set.add(value)) return false
        prefs.edit().putStringSet(KEY_DAYS, set).apply()
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

    fun totalWatchedDays(): Int = watchedDays().size

    fun watchedToday(today: LocalDate = LocalDate.now()): Boolean = today in watchedDays()

    companion object {
        private const val KEY_DAYS = "watched_days"
    }
}
