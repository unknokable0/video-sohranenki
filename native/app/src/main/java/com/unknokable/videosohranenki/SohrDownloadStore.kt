package com.unknokable.videosohranenki

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SohrDownloadEntry(
    val messageId: Long,
    val title: String,
    val path: String,
    val sizeBytes: Long,
    val downloadedAtMs: Long
)

class SohrDownloadStore(context: Context) {
    private val prefs = context.getSharedPreferences("sohr_download_center", Context.MODE_PRIVATE)

    fun entries(): List<SohrDownloadEntry> {
        val raw = prefs.getString(KEY_ENTRIES, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val path = obj.optString("path")
                    if (path.isBlank()) continue
                    val file = File(path)
                    if (!file.exists()) continue
                    add(
                        SohrDownloadEntry(
                            messageId = obj.optLong("messageId"),
                            title = obj.optString("title", "Видео"),
                            path = path,
                            sizeBytes = file.length().takeIf { it > 0L } ?: obj.optLong("sizeBytes", 0L),
                            downloadedAtMs = obj.optLong("downloadedAtMs", 0L)
                        )
                    )
                }
            }.sortedByDescending { it.downloadedAtMs }
        }.getOrDefault(emptyList())
    }

    fun register(item: VideoItem, file: File) {
        val list = entries().filterNot { it.messageId == item.messageId }.toMutableList()
        list.add(
            0,
            SohrDownloadEntry(
                messageId = item.messageId,
                title = item.title.ifBlank { "Видео" },
                path = file.absolutePath,
                sizeBytes = file.length(),
                downloadedAtMs = System.currentTimeMillis()
            )
        )
        save(list)
    }

    fun remove(messageId: Long): Boolean {
        val list = entries()
        val target = list.firstOrNull { it.messageId == messageId } ?: return false
        runCatching { File(target.path).delete() }
        save(list.filterNot { it.messageId == messageId })
        return true
    }

    fun contains(messageId: Long): Boolean = entries().any { it.messageId == messageId }

    fun totalBytes(): Long = entries().sumOf { it.sizeBytes }

    private fun save(entries: List<SohrDownloadEntry>) {
        val array = JSONArray()
        entries.take(100).forEach { entry ->
            array.put(
                JSONObject()
                    .put("messageId", entry.messageId)
                    .put("title", entry.title)
                    .put("path", entry.path)
                    .put("sizeBytes", entry.sizeBytes)
                    .put("downloadedAtMs", entry.downloadedAtMs)
            )
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    companion object {
        private const val KEY_ENTRIES = "entries"
    }
}
