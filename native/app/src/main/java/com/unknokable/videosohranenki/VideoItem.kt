package com.unknokable.videosohranenki

data class VideoItem(
    val messageId: Long,
    val title: String,
    val date: Int,
    val durationSeconds: Int,
    val fileId: Int,
    val fileSize: Long,
    val mimeType: String,
    val thumbnailFileId: Int? = null,
    val thumbnailPath: String? = null,
    val localPath: String? = null,
    val source: String = "telegram",
    val thumbnailUrl: String? = null,
    val externalUrl: String? = null
)
