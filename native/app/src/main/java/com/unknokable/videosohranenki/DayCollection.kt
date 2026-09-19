package com.unknokable.videosohranenki

import java.time.LocalDate

data class DayCollection(
    val date: LocalDate,
    val videos: List<VideoItem>
) {
    val totalDurationSeconds: Int
        get() = videos.sumOf { it.durationSeconds.coerceAtLeast(0) }
}
