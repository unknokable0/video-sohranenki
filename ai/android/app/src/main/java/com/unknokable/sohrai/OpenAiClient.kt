package com.unknokable.sohrai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.TimeUnit

data class OpenAiResult(
    val text: String,
    val responseId: String?,
    val model: String,
    val sources: List<Pair<String, String>>
)

class OpenAiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.MINUTES)
        .callTimeout(4, TimeUnit.MINUTES)
        .build()

    private val activeCalls =
        Collections.synchronizedSet(mutableSetOf<Call>())

    fun cancel() {
        synchronized(activeCalls) {
            activeCalls.toList().forEach { runCatching { it.cancel() } }
            activeCalls.clear()
        }
    }

    suspend fun verifyKey(key: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                withTimeout(20_000L) {
                    val call = http.newCall(
                        Request.Builder()
                            .url("https://api.openai.com/v1/models/gpt-5.6-sol")
                            .get()
                            .header("Authorization", "Bearer " + key)
                            .build()
                    )

                    activeCalls.add(call)

                    try {
                        call.execute().use { response ->
                            if (!response.isSuccessful) {
                                val body =
                                    response.body?.string().orEmpty()

                                error(
                                    readApiError(
                                        body,
                                        "OpenAI отклонил ключ (" +
                                            response.code +
                                            ")"
                                    )
                                )
                            }
                        }
                    } finally {
                        activeCalls.remove(call)
                    }
                }
            }
        }

    suspend fun streamResponse(
        key: String,
        model: String,
        previousResponseId: String?,
        history: List<ChatMessage>,
        userText: String,
        onStage: (String) -> Unit,
        onDelta: (String) -> Unit
    ): OpenAiResult =
        withTimeout(230_000L) {
            val first = runCatching {
                executeStream(
                    key = key,
                    model = model,
                    previousResponseId = previousResponseId,
                    history = history,
                    userText = userText,
                    onStage = onStage,
                    onDelta = onDelta
                )
            }

            if (first.isSuccess) {
                first.getOrThrow()
            } else {
                val error = first.exceptionOrNull()
                val message = error?.message.orEmpty()

                if (
                    previousResponseId != null &&
                    (
                        message.contains("previous_response_id", true) ||
                        message.contains("not found", true) ||
                        message.contains("expired", true)
                    )
                ) {
                    onStage("Восстанавливаю контекст чата…")

                    executeStream(
                        key = key,
                        model = model,
                        previousResponseId = null,
                        history = history,
                        userText = userText,
                        onStage = onStage,
                        onDelta = onDelta
                    )
                } else {
                    throw error ?: IllegalStateException(
                        "Не удалось получить ответ."
                    )
                }
            }
        }

    private suspend fun executeStream(
        key: String,
        model: String,
        previousResponseId: String?,
        history: List<ChatMessage>,
        userText: String,
        onStage: (String) -> Unit,
        onDelta: (String) -> Unit
    ): OpenAiResult =
        withContext(Dispatchers.IO) {
            val effort =
                if (isComplex(userText)) "high" else "medium"

            val payload = JSONObject()
                .put("model", model)
                .put("stream", true)
                .put("store", true)
                .put("instructions", instructions())
                .put(
                    "reasoning",
                    JSONObject()
                        .put("effort", effort)
                        .put("context", "all_turns")
                )
                .put(
                    "text",
                    JSONObject()
                        .put("verbosity", "medium")
                )
                .put(
                    "tools",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "web_search")
                            .put("search_context_size", "medium")
                    )
                )
                .put("tool_choice", "auto")
                .put("parallel_tool_calls", true)
                .put("max_output_tokens", 12000)

            if (!previousResponseId.isNullOrBlank()) {
                payload.put(
                    "previous_response_id",
                    previousResponseId
                )
                payload.put("input", userText)
            } else {
                payload.put(
                    "input",
                    fallbackInput(history, userText)
                )
            }

            val request = Request.Builder()
                .url("https://api.openai.com/v1/responses")
                .post(
                    payload.toString()
                        .toRequestBody(
                            "application/json; charset=utf-8"
                                .toMediaType()
                        )
                )
                .header(
                    "Authorization",
                    "Bearer " + key
                )
                .header(
                    "Content-Type",
                    "application/json"
                )
                .header(
                    "Accept",
                    "text/event-stream"
                )
                .build()

            val call = http.newCall(request)
            activeCalls.add(call)

            val out = StringBuilder()
            val sources =
                linkedMapOf<String, String>()

            var responseId: String? = null
            var actualModel = model

            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val body =
                            response.body?.string().orEmpty()

                        error(
                            readApiError(
                                body,
                                "OpenAI API: " +
                                    response.code
                            )
                        )
                    }

                    val source =
                        response.body?.source()
                            ?: error(
                                "OpenAI не вернул поток."
                            )

                    onStage(
                        if (effort == "high")
                            "Думаю глубже…"
                        else
                            "Думаю…"
                    )

                    while (!source.exhausted()) {
                        val line =
                            source.readUtf8Line()
                                ?: break

                        if (!line.startsWith("data:")) {
                            continue
                        }

                        val raw =
                            line.removePrefix("data:")
                                .trim()

                        if (
                            raw.isBlank() ||
                            raw == "[DONE]"
                        ) {
                            continue
                        }

                        val event =
                            runCatching {
                                JSONObject(raw)
                            }.getOrNull()
                                ?: continue

                        when (
                            event.optString("type")
                        ) {
                            "response.created" -> {
                                val created =
                                    event.optJSONObject(
                                        "response"
                                    )

                                responseId =
                                    created?.optString("id")
                                        ?.takeIf {
                                            it.isNotBlank()
                                        }

                                actualModel =
                                    created?.optString(
                                        "model"
                                    )
                                        ?.takeIf {
                                            it.isNotBlank()
                                        }
                                        ?: actualModel
                            }

                            "response.web_search_call.in_progress",
                            "response.web_search_call.searching" -> {
                                onStage(
                                    "Проверяю интернет…"
                                )
                            }

                            "response.output_text.delta" -> {
                                val delta =
                                    event.optString(
                                        "delta"
                                    )

                                if (delta.isNotEmpty()) {
                                    out.append(delta)
                                    onDelta(delta)
                                }
                            }

                            "response.completed" -> {
                                val completed =
                                    event.optJSONObject(
                                        "response"
                                    )

                                if (completed != null) {
                                    responseId =
                                        completed.optString(
                                            "id"
                                        )
                                            .takeIf {
                                                it.isNotBlank()
                                            }
                                            ?: responseId

                                    actualModel =
                                        completed.optString(
                                            "model"
                                        )
                                            .takeIf {
                                                it.isNotBlank()
                                            }
                                            ?: actualModel

                                    collectSources(
                                        completed,
                                        sources
                                    )
                                }
                            }

                            "response.failed" -> {
                                val failed =
                                    event.optJSONObject(
                                        "response"
                                    )

                                val message =
                                    failed
                                        ?.optJSONObject(
                                            "error"
                                        )
                                        ?.optString(
                                            "message"
                                        )
                                        .orEmpty()

                                error(
                                    message.ifBlank {
                                        "OpenAI не смог завершить ответ."
                                    }
                                )
                            }

                            "error" -> {
                                val message =
                                    event.optString(
                                        "message"
                                    ).ifBlank {
                                        event
                                            .optJSONObject(
                                                "error"
                                            )
                                            ?.optString(
                                                "message"
                                            )
                                            .orEmpty()
                                    }

                                error(
                                    message.ifBlank {
                                        "Ошибка OpenAI API."
                                    }
                                )
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                if (out.isEmpty()) throw t
            } finally {
                activeCalls.remove(call)
            }

            if (out.isEmpty()) {
                error("OpenAI вернул пустой ответ.")
            }

            OpenAiResult(
                text = out.toString(),
                responseId = responseId,
                model = actualModel,
                sources =
                    sources.entries.map {
                        it.value to it.key
                    }
            )
        }

    private fun fallbackInput(
        history: List<ChatMessage>,
        userText: String
    ): JSONArray {
        val input = JSONArray()

        history
            .filter { it.text.isNotBlank() }
            .takeLast(18)
            .forEach { message ->
                input.put(
                    JSONObject()
                        .put(
                            "role",
                            if (
                                message.role ==
                                "assistant"
                            )
                                "assistant"
                            else
                                "user"
                        )
                        .put(
                            "content",
                            message.text
                        )
                )
            }

        val lastHistoryUser =
            history.lastOrNull()?.let {
                it.role == "user" &&
                    it.text.trim() ==
                    userText.trim()
            } ?: false

        if (!lastHistoryUser) {
            input.put(
                JSONObject()
                    .put("role", "user")
                    .put("content", userText)
            )
        }

        return input
    }

    private fun collectSources(
        response: JSONObject,
        out: MutableMap<String, String>
    ) {
        val output =
            response.optJSONArray("output")
                ?: return

        for (i in 0 until output.length()) {
            val item =
                output.optJSONObject(i)
                    ?: continue

            val content =
                item.optJSONArray("content")
                    ?: continue

            for (
                j in 0 until content.length()
            ) {
                val part =
                    content.optJSONObject(j)
                        ?: continue

                val annotations =
                    part.optJSONArray(
                        "annotations"
                    )
                        ?: continue

                for (
                    k in 0 until annotations.length()
                ) {
                    val annotation =
                        annotations.optJSONObject(k)
                            ?: continue

                    if (
                        annotation.optString(
                            "type"
                        ) != "url_citation"
                    ) {
                        continue
                    }

                    var url =
                        annotation.optString("url")

                    var title =
                        annotation.optString("title")

                    val nested =
                        annotation.optJSONObject(
                            "url_citation"
                        )

                    if (
                        url.isBlank() &&
                        nested != null
                    ) {
                        url =
                            nested.optString("url")

                        title =
                            nested.optString("title")
                    }

                    if (url.isNotBlank()) {
                        out.putIfAbsent(
                            url,
                            title.ifBlank { url }
                        )
                    }
                }
            }
        }
    }

    private fun isComplex(
        text: String
    ): Boolean {
        val q = text.lowercase()

        if (q.length >= 300) {
            return true
        }

        val triggers = listOf(
            "подумай",
            "проанализ",
            "сравни",
            "проверь",
            "разбери",
            "почему",
            "реши",
            "посчитай",
            "архитект",
            "код",
            "ошибка",
            "баг",
            "оптимиз",
            "докажи",
            "подробно",
            "analyze",
            "compare",
            "debug",
            "architecture",
            "reason"
        )

        return triggers.any(q::contains)
    }

    private fun instructions(): String =
        "Formatting re-enabled\n" +
            "Ты — SOHR AI, один постоянный персональный чат пользователя. " +
            "По качеству ответа веди себя как сильный современный ChatGPT: " +
            "думай перед ответом, проверяй важные факты и не выдумывай. " +
            "Если информация может быть свежей, изменившейся или пользователь просит поиск, " +
            "используй web_search. " +
            "Отвечай на языке пользователя. " +
            "Пиши чисто и естественно: короткие абзацы, Markdown только когда он реально помогает. " +
            "Не рассказывай пользователю о скрытой цепочке рассуждений. " +
            "Если есть неопределённость, скажи об этом прямо. " +
            "Не называй себя ChatGPT и не утверждай, что это официальное приложение OpenAI."

    private fun readApiError(
        body: String,
        fallback: String
    ): String =
        runCatching {
            val json =
                JSONObject(body)

            json.optJSONObject("error")
                ?.optString("message")
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: fallback
        }.getOrDefault(fallback)
}
