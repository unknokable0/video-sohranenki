package com.unknokable.sohrai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface ChatEvent {
    data class Status(val text: String) : ChatEvent
    data class Delta(val text: String) : ChatEvent
    data class Done(val responseId: String?) : ChatEvent
}

class ApiClient(
    private val baseUrl: String = BuildConfig.API_BASE_URL
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    suspend fun stream(
        messages: List<ChatMessage>,
        onEvent: suspend (ChatEvent) -> Unit
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("messages", JSONArray().apply {
                messages.takeLast(60).forEach { message ->
                    put(
                        JSONObject()
                            .put("role", message.role)
                            .put("content", message.text)
                    )
                }
            })
        }

        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/chat")
            .post(
                payload.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
            )
            .header("Accept", "application/x-ndjson")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string()?.take(500).orEmpty()
                val suffix = if (detail.isBlank()) "" else ": " + detail
                error("Сервер ответил " + response.code + suffix)
            }

            val source = response.body?.source()
                ?: error("Сервер не вернул поток ответа")

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue

                val event = JSONObject(line)
                when (event.optString("type")) {
                    "status" -> onEvent(ChatEvent.Status(event.optString("text")))
                    "delta" -> onEvent(ChatEvent.Delta(event.optString("text")))
                    "done" -> onEvent(
                        ChatEvent.Done(
                            event.optString("responseId")
                                .takeIf { it.isNotBlank() }
                        )
                    )
                    "error" -> error(
                        event.optString("message")
                            .ifBlank { "Неизвестная ошибка сервера" }
                    )
                }
            }
        }
    }
}
