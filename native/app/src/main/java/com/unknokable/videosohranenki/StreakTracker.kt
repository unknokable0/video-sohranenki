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

        val total = set.size
        val lastRewardTotal = prefs.getInt(KEY_LAST_REWARD_TOTAL, 0)
        val shouldReward = total >= 7 && total / 7 > lastRewardTotal / 7
        val tokens = prefs.getInt(KEY_PROTECTION_TOKENS, 0).coerceIn(0, MAX_PROTECTION_TOKENS)

        prefs.edit()
            .putStringSet(KEY_DAYS, set)
            .putString(KEY_PENDING_IGNITION_DAY, value)
            .apply {
                if (shouldReward) {
                    putInt(KEY_PROTECTION_TOKENS, (tokens + 1).coerceAtMost(MAX_PROTECTION_TOKENS))
                    putInt(KEY_LAST_REWARD_TOTAL, total)
                }
            }
            .apply()
        return true
    }

    fun watchedDays(): Set<LocalDate> =
        prefs.getStringSet(KEY_DAYS, emptySet()).orEmpty()
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()

    fun protectedDays(): Set<LocalDate> =
        prefs.getStringSet(KEY_PROTECTED_DAYS, emptySet()).orEmpty()
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()

    fun protectionTokens(): Int =
        prefs.getInt(KEY_PROTECTION_TOKENS, 0).coerceIn(0, MAX_PROTECTION_TOKENS)

    fun lastProtectedDay(): LocalDate? = protectedDays().maxOrNull()

    private fun ensureProtection(today: LocalDate) {
        val tokens = protectionTokens()
        if (tokens <= 0) return

        val watched = watchedDays()
        val protected = protectedDays().toMutableSet()
        val yesterday = today.minusDays(1)
        val before = today.minusDays(2)
        val active = watched + protected

        if (yesterday !in active && before in active) {
            protected += yesterday
            prefs.edit()
                .putStringSet(KEY_PROTECTED_DAYS, protected.map { it.toString() }.toSet())
                .putInt(KEY_PROTECTION_TOKENS, (tokens - 1).coerceAtLeast(0))
                .apply()
        }
    }

    private fun activeDays(today: LocalDate = LocalDate.now()): Set<LocalDate> {
        ensureProtection(today)
        return watchedDays() + protectedDays()
    }

    fun currentStreak(today: LocalDate = LocalDate.now()): Int {
        val days = activeDays(today)
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
        val sorted = activeDays().sorted()
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
        val days = activeDays(today)
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
        private const val KEY_PROTECTED_DAYS = "protected_days"
        private const val KEY_PROTECTION_TOKENS = "protection_tokens"
        private const val KEY_LAST_REWARD_TOTAL = "last_protection_reward_total"
        private const val MAX_PROTECTION_TOKENS = 2
    }
}
