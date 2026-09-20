package com.unknokable.videosohranenki

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SohrVideoCache(private val context: Context) {
    private val catalogFile = File(context.filesDir, "sohr_video_catalog.json")
    private val importsDir = File(context.filesDir, "sohr_imports").apply { mkdirs() }
    private val thumbsDir = File(context.filesDir, "sohr_thumbs").apply { mkdirs() }

    fun load(): List<VideoItem> = runCatching {
        if (!catalogFile.exists()) return emptyList()
        val array = JSONArray(catalogFile.readText())
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val localPath = o.optString("localPath").takeIf { it.isNotBlank() }
                if (localPath != null && !File(localPath).exists()) continue
                add(VideoItem(
                    messageId = o.getLong("messageId"),
                    title = o.optString("title", "Видео"),
                    date = o.getInt("date"),
                    durationSeconds = o.optInt("durationSeconds", 0),
                    fileId = o.optInt("fileId", 0),
                    fileSize = o.optLong("fileSize", 0L),
                    mimeType = o.optString("mimeType", "video/mp4"),
                    thumbnailFileId = if (o.has("thumbnailFileId") && !o.isNull("thumbnailFileId")) o.getInt("thumbnailFileId") else null,
                    thumbnailPath = o.optString("thumbnailPath").takeIf { it.isNotBlank() && File(it).exists() },
                    localPath = localPath
                ))
            }
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun save(items: List<VideoItem>) {
        runCatching {
            val array = JSONArray()
            items.distinctBy { it.messageId }.forEach { item ->
                array.put(JSONObject().apply {
                    put("messageId", item.messageId)
                    put("title", item.title)
                    put("date", item.date)
                    put("durationSeconds", item.durationSeconds)
                    put("fileId", item.fileId)
                    put("fileSize", item.fileSize)
                    put("mimeType", item.mimeType)
                    put("thumbnailFileId", item.thumbnailFileId ?: JSONObject.NULL)
                    put("thumbnailPath", item.thumbnailPath ?: "")
                    put("localPath", item.localPath ?: "")
                })
            }
            val tmp = File(catalogFile.parentFile, catalogFile.name + ".tmp")
            tmp.writeText(array.toString())
            if (catalogFile.exists()) catalogFile.delete()
            tmp.renameTo(catalogFile)
        }
    }

    fun importSharedVideo(uri: Uri): VideoItem {
        val resolver = context.contentResolver
        var displayName = "Видео"
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) displayName = c.getString(0)?.takeIf { it.isNotBlank() } ?: displayName
            }
        }
        val mime = resolver.getType(uri)?.takeIf { it.startsWith("video/") } ?: "video/mp4"
        val stamp = System.currentTimeMillis()
        val ext = when {
            mime.contains("webm") -> "webm"
            mime.contains("quicktime") -> "mov"
            else -> "mp4"
        }
        val target = File(importsDir, "video_$stamp.$ext")
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Не удалось открыть видео" }
            target.outputStream().buffered().use { output -> input.copyTo(output, 256 * 1024) }
        }

        var durationMs = 0L
        var thumbPath: String? = null
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(target.absolutePath)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val frameUs = if (durationMs > 4_000L) durationMs * 350L else 1_000_000L
                val frame = retriever.getFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val thumb = File(thumbsDir, "thumb_$stamp.jpg")
                    thumb.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                    frame.recycle()
                    thumbPath = thumb.absolutePath
                }
            } finally {
                retriever.release()
            }
        }

        return VideoItem(
            messageId = -stamp,
            title = displayName.substringBeforeLast('.').ifBlank { "Видео" },
            date = (System.currentTimeMillis() / 1000L).toInt(),
            durationSeconds = (durationMs / 1000L).toInt(),
            fileId = 0,
            fileSize = target.length(),
            mimeType = mime,
            thumbnailPath = thumbPath,
            localPath = target.absolutePath
        )
    }
}
