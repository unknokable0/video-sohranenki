package com.unknokable.videosohranenki

enum class CollectionSort(val label: String) {
    NEWEST("Сначала новые"),
    OLDEST("Сначала старые"),
    MOST_VIDEOS("Больше видео"),
    LONGEST("Больше по времени")
}

enum class VideoSort(val label: String) {
    NEWEST("Сначала новые"),
    OLDEST("Сначала старые"),
    LONGEST("Самые длинные"),
    SHORTEST("Самые короткие"),
    LARGEST("Самые большие"),
    SMALLEST("Самые маленькие"),
    TITLE("По названию")
}
