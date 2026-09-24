package com.unknokable.sohrai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    val sources: List<Pair<String, String>> = emptyList()
)

private data class FreeOpinion(
    val label: String,
    val model: String,
    val text: String
)

class FusionApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    private val analysts = listOf(
        "Nemotron" to "nvidia/nemotron-3-ultra-550b-a55b:free",
        "Laguna" to "poolside/laguna-s-2.1:free",
        "Ling" to "inclusionai/ling-3.0-flash:free"
    )

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
        messages: List<ChatMessage>,
        onStage: suspend (String) -> Unit = {}
    ): FusionResult = coroutineScope {
        onStage("Три бесплатные модели анализируют запрос…")

        val opinions = analysts.map { (label, model) ->
            async(Dispatchers.IO) {
                runCatching {
                    FreeOpinion(
                        label = label,
                        model = model,
                        text = callModel(
                            key = key,
                            model = model,
                            messages = analystMessages(messages, label)
                        )
                    )
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()

        if (opinions.isEmpty()) {
            error("Все бесплатные модели сейчас недоступны. Попробуй повторить запрос позже.")
        }

        onStage("Сверяю ${opinions.size} независимых ответа…")

        val finalText = runCatching {
            callModel(
                key = key,
                model = "nvidia/nemotron-3-ultra-550b-a55b:free",
                messages = judgeMessages(messages, opinions),
                maxTokens = 3200
            )
        }.getOrElse {
            // Если финальный бесплатный вызов временно недоступен, не теряем уже полученный результат.
            bestEffortFallback(opinions)
        }

        FusionResult(
            text = finalText.trim(),
            concreteModel = "Free Fusion · " + opinions.joinToString(" + ") { it.label }
        )
    }

    private fun analystMessages(messages: List<ChatMessage>, label: String): JSONArray =
        JSONArray().apply {
            put(
                JSONObject()
                    .put("role", "system")
                    .put(
                        "content",
                        "Ты один из независимых экспертов Free Fusion ($label). " +
                            "Дай точный, полезный и компактный ответ. Проверяй внутреннюю логику и не выдумывай факты. " +
                            "Если вопрос требует свежей информации из интернета, а у тебя нет подтверждённых свежих данных, прямо обозначь это. " +
                            "Не описывай скрытые рассуждения; дай только вывод и важные основания. Отвечай на языке пользователя."
                    )
            )
            messages.filter { it.text.isNotBlank() }.takeLast(14).forEach { message ->
                put(JSONObject().put("role", message.role).put("content", message.text))
            }
        }

    private fun judgeMessages(messages: List<ChatMessage>, opinions: List<FreeOpinion>): JSONArray {
        val context = buildString {
            messages.filter { it.text.isNotBlank() }.takeLast(10).forEach {
                append(if (it.role == "user") "Пользователь: " else "Ассистент: ")
                append(it.text.take(5000))
                append("\n")
            }
        }

        val panel = buildString {
            opinions.forEachIndexed { index, opinion ->
                append("\n--- Ответ ")
                append(index + 1)
                append(" · ")
                append(opinion.label)
                append(" ---\n")
                append(opinion.text.take(7000))
                append("\n")
            }
        }

        return JSONArray().apply {
            put(
                JSONObject()
                    .put("role", "system")
                    .put(
                        "content",
                        "Ты финальный редактор SOHR AI Free Fusion. Ниже есть несколько независимых ответов бесплатных моделей. " +
                            "Сверь их и выдай ОДИН цельный ответ пользователю. Не говори, что ты 'смешал модели', если это не нужно. " +
                            "Исправляй явные противоречия, не усиливай непроверенные утверждения и не придумывай источники. " +
                            "Если модели расходятся или актуальность нельзя подтвердить без веб-поиска, коротко укажи неопределённость. " +
                            "Пиши красиво и компактно, используй Markdown только там, где он улучшает читаемость. " +
                            "Не раскрывай скрытые цепочки рассуждений."
                    )
            )
            put(
                JSONObject()
                    .put("role", "user")
                    .put(
                        "content",
                        "Контекст диалога:\n$context\nНезависимые ответы моделей:$panel\nСобери лучший финальный ответ."
                    )
            )
        }
    }

    private fun bestEffortFallback(opinions: List<FreeOpinion>): String {
        val first = opinions.first()
        return buildString {
            append(first.text.trim())
            if (opinions.size == 1) {
                append("\n\n_Остальные бесплатные модели временно не ответили._")
            } else {
                append("\n\n_Финальная сверка временно недоступна; показан наиболее полный из полученных ответов._")
            }
        }
    }

    private fun callModel(
        key: String,
        model: String,
        messages: JSONArray,
        maxTokens: Int = 2400
    ): String {
        val payload = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("max_tokens", maxTokens)
            .put("temperature", 0.35)

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "https://github.com/unknokable0/video-sohranenki")
            .header("X-OpenRouter-Title", "SOHR AI Free Fusion")
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
            val message = json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?: error("Модель не вернула сообщение")
            return extractText(message.opt("content")).ifBlank {
                error("Модель вернула пустой ответ")
            }
        }
    }

    private fun extractText(content: Any?): String = when (content) {
        is String -> content
        is JSONArray -> buildString {
            for (i in 0 until content.length()) {
                val item = content.optJSONObject(i) ?: continue
                val text = item.optString("text")
                if (text.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(text)
                }
            }
        }
        else -> ""
    }
}
