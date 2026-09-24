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

data class FusionResult(
    val text: String,
    val concreteModel: String?,
    val sources: List<Pair<String, String>>
)

class FusionApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.MINUTES)
        .build()

    suspend fun verifyKey(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/key")
                .get()
                .header("Authorization", "Bearer $key")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = response.body?.string()?.take(500).orEmpty()
                    error(if (detail.isBlank()) "OpenRouter отклонил ключ (" + response.code + ")" else detail)
                }
            }
        }
    }

    suspend fun fusion(
        key: String,
        messages: List<ChatMessage>
    ): FusionResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("model", "openrouter/fusion")
            put("tool_choice", "required")
            put("plugins", JSONArray().put(
                JSONObject()
                    .put("id", "fusion")
                    .put("analysis_models", JSONArray()
                        .put("~openai/gpt-latest")
                        .put("~anthropic/claude-opus-latest")
                        .put("~google/gemini-pro-latest")
                        .put("x-ai/grok-4.7")
                    )
                    .put("model", "~openai/gpt-latest")
                    .put("max_tool_calls", 4)
                    .put("max_completion_tokens", 9000)
                    .put("reasoning", JSONObject().put("effort", "high"))
            ))
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content",
                    "Ты — единый финальный ассистент SOHR AI. Пользователь видит один чат, но ответ должен опираться на независимый анализ нескольких сильных моделей. " +
                    "Дай один цельный ответ, без перечисления внутренних рассуждений моделей. Проверяй актуальные факты через доступный веб-поиск. " +
                    "Если источники расходятся, укажи это коротко. Не придумывай данные, версии, цены, даты и источники. " +
                    "Отвечай на языке пользователя. Форматируй аккуратно: короткие абзацы, ясные заголовки только когда они реально помогают."
                ))
                messages.filter { it.text.isNotBlank() }.takeLast(22).forEach { message ->
                    put(JSONObject().put("role", message.role).put("content", message.text))
                }
            })
        }

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "https://github.com/unknokable0/video-sohranenki")
            .header("X-OpenRouter-Title", "SOHR AI Fusion")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                error(detail.ifBlank { "OpenRouter ответил " + response.code })
            }

            val json = JSONObject(body)
            val choice = json.optJSONArray("choices")?.optJSONObject(0)
                ?: error("OpenRouter не вернул ответ")
            val message = choice.optJSONObject("message")
                ?: error("OpenRouter вернул ответ без message")

            val text = extractText(message.opt("content"))
                .ifBlank { error("Ответ модели пустой") }

            val sources = linkedMapOf<String, String>()
            collectAnnotations(message, sources)
            val citations = message.optJSONArray("citations")
            if (citations != null) {
                for (i in 0 until citations.length()) {
                    val item = citations.opt(i)
                    if (item is String && item.startsWith("http")) {
                        sources.putIfAbsent(item, item)
                    } else if (item is JSONObject) {
                        val url = item.optString("url")
                        if (url.isNotBlank()) {
                            sources.putIfAbsent(url, item.optString("title").ifBlank { url })
                        }
                    }
                }
            }

            val withSources = if (sources.isEmpty()) text else buildString {
                append(text.trim())
                append("\n\n**Источники**")
                sources.entries.take(8).forEach { (url, title) ->
                    append("\n- [")
                    append(title.replace("[", "").replace("]", ""))
                    append("](")
                    append(url)
                    append(")")
                }
            }

            FusionResult(
                text = withSources,
                concreteModel = json.optString("model").takeIf { it.isNotBlank() },
                sources = sources.entries.map { it.value to it.key }
            )
        }
    }

    private fun extractText(content: Any?): String {
        return when (content) {
            is String -> content
            is JSONArray -> buildString {
                for (i in 0 until content.length()) {
                    val item = content.optJSONObject(i) ?: continue
                    val t = item.optString("text")
                    if (t.isNotBlank()) {
                        if (isNotEmpty()) append("\n")
                        append(t)
                    }
                }
            }
            else -> ""
        }
    }

    private fun collectAnnotations(node: Any?, out: MutableMap<String, String>) {
        when (node) {
            is JSONObject -> {
                val type = node.optString("type")
                if (type == "url_citation") {
                    val url = node.optString("url")
                    val title = node.optString("title").ifBlank { url }
                    if (url.isNotBlank()) out.putIfAbsent(url, title)
                }
                val citation = node.optJSONObject("url_citation")
                if (citation != null) {
                    val url = citation.optString("url")
                    val title = citation.optString("title").ifBlank { url }
                    if (url.isNotBlank()) out.putIfAbsent(url, title)
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val childKey = keys.next()
                    collectAnnotations(node.opt(childKey), out)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) collectAnnotations(node.opt(i), out)
        }
    }
}
