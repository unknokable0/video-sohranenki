package com.unknokable.videosohranenki

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AiResultCache(context: Context) {
    private val prefs = context.getSharedPreferences("video_sohranenki_ai_cache", Context.MODE_PRIVATE)

    fun load(messageId: Long): AiAnalysisResult? {
        val raw = prefs.getString(messageId.toString(), null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val arr = root.getJSONArray("chapters")
            val chapters = buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    add(
                        AiChapter(
                            startSeconds = item.getInt("start"),
                            endSeconds = item.getInt("end"),
                            title = item.getString("title"),
                            detail = item.getString("detail")
                        )
                    )
                }
            }
            AiAnalysisResult(
                chapters = chapters,
                sampledFrames = root.optInt("sampledFrames", chapters.size)
            )
        }.getOrNull()
    }

    fun save(messageId: Long, result: AiAnalysisResult) {
        val arr = JSONArray()
        result.chapters.forEach { chapter ->
            arr.put(
                JSONObject()
                    .put("start", chapter.startSeconds)
                    .put("end", chapter.endSeconds)
                    .put("title", chapter.title)
                    .put("detail", chapter.detail)
            )
        }

        val root = JSONObject()
            .put("sampledFrames", result.sampledFrames)
            .put("chapters", arr)

        prefs.edit()
            .putString(messageId.toString(), root.toString())
            .apply()
    }
}
