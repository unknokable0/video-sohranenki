package com.unknokable.sohrai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.TimeUnit

data class SmartResult(
    val text: String,
    val mode: String
)

private data class Opinion(
    val label: String,
    val text: String
)

class SmartAiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    private val activeCalls = Collections.synchronizedSet(mutableSetOf<Call>())

    fun cancel() {
        synchronized(activeCalls) {
            activeCalls.toList().forEach { runCatching { it.cancel() } }
            activeCalls.clear()
        }
    }

    suspend fun verifyGemini(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(15_000L) {
                val encoded = URLEncoder.encode(key, StandardCharsets.UTF_8.name())
                val call = client.newCall(
                    Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models?key=" + encoded)
                        .get()
                        .build()
                )
                activeCalls.add(call)
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) {
                            error(readApiError(response, "Google AI отклонил ключ"))
                        }
                    }
                } finally {
                    activeCalls.remove(call)
                }
            }
        }
    }

    suspend fun verifyGroq(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(15_000L) {
                val call = client.newCall(
                    Request.Builder()
                        .url("https://api.groq.com/openai/v1/models")
                        .get()
                        .header("Authorization", "Bearer " + key)
                        .build()
                )
                activeCalls.add(call)
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) {
                            error(readApiError(response, "Groq отклонил ключ"))
                        }
                    }
                } finally {
                    activeCalls.remove(call)
                }
            }
        }
    }

    suspend fun reply(
        geminiKey: String,
        groqKey: String,
        messages: List<ChatMessage>,
        onStage: (String) -> Unit,
        onDelta: (String) -> Unit
    ): SmartResult = withTimeout(95_000L) {
        val latest = messages.lastOrNull { it.role == "user" }?.text.orEmpty()
        if (latest.isBlank()) error("Пустой запрос")

        when {
            needsFreshWeb(latest) -> {
                onStage("Ищу и проверяю свежую информацию…")
                val web = runCatching {
                    withTimeout(65_000L) {
                        geminiStream(
                            key = geminiKey,
                            contents = normalGeminiContents(messages),
                            system = baseSystem() + " Для этого запроса обязательно используй Google Search grounding и приложи проверяемые источники.",
                            thinkingLevel = "medium",
                            useSearch = true,
                            onDelta = onDelta
                        )
                    }
                }

                if (web.isSuccess) {
                    SmartResult(web.getOrThrow(), "Web · Gemini 3.8 Flash")
                } else {
                    onStage("Поиск недоступен — отвечаю без веба через резерв…")
                    val fallback = fallbackAnswer(
                        geminiKey = geminiKey,
                        groqKey = groqKey,
                        messages = messages,
                        onDelta = onDelta,
                        thinkingLevel = "high"
                    )
                    SmartResult(fallback, "Fallback · без веба")
                }
            }

            isComplex(latest) -> {
                smartFusion(
                    geminiKey = geminiKey,
                    groqKey = groqKey,
                    messages = messages,
                    onStage = onStage,
                    onDelta = onDelta
                )
            }

            else -> {
                onStage("Думаю…")
                val answer = fallbackAnswer(
                    geminiKey = geminiKey,
                    groqKey = groqKey,
                    messages = messages,
                    onDelta = onDelta,
                    thinkingLevel = "low"
                )
                SmartResult(answer, "Gemini 3.8 Flash")
            }
        }
    }

    private suspend fun fallbackAnswer(
        geminiKey: String,
        groqKey: String,
        messages: List<ChatMessage>,
        onDelta: (String) -> Unit,
        thinkingLevel: String
    ): String {
        val gemini = runCatching {
            withTimeout(55_000L) {
                geminiStream(
                    key = geminiKey,
                    contents = normalGeminiContents(messages),
                    system = baseSystem(),
                    thinkingLevel = thinkingLevel,
                    useSearch = false,
                    onDelta = onDelta
                )
            }
        }
        if (gemini.isSuccess) return gemini.getOrThrow()

        val gptOss = runCatching {
            withTimeout(28_000L) {
                groqCall(
                    key = groqKey,
                    model = "openai/gpt-oss-120b",
                    messages = normalOpenAiMessages(messages)
                )
            }
        }
        if (gptOss.isSuccess) {
            val text = gptOss.getOrThrow()
            onDelta(text)
            return text
        }

        val qwen = withTimeout(28_000L) {
            groqCall(
                key = groqKey,
                model = "qwen/qwen3.8-27b",
                messages = normalOpenAiMessages(messages)
            )
        }
        onDelta(qwen)
        return qwen
    }

    private suspend fun smartFusion(
        geminiKey: String,
        groqKey: String,
        messages: List<ChatMessage>,
        onStage: (String) -> Unit,
        onDelta: (String) -> Unit
    ): SmartResult = coroutineScope {
        onStage("Сверяю ответ тремя моделями…")

        val opinions = listOf(
            async(Dispatchers.IO) {
                runCatching {
                    withTimeout(32_000L) {
                        Opinion(
                            "Gemini",
                            geminiGenerate(
                                key = geminiKey,
                                contents = reviewerGeminiContents(messages),
                                system = reviewerSystem("Gemini"),
                                thinkingLevel = "high"
                            )
                        )
                    }
                }.getOrNull()
            },
            async(Dispatchers.IO) {
                runCatching {
                    withTimeout(24_000L) {
                        Opinion(
                            "GPT-OSS",
                            groqCall(
                                key = groqKey,
                                model = "openai/gpt-oss-120b",
                                messages = reviewerOpenAiMessages(messages, "GPT-OSS")
                            )
                        )
                    }
                }.getOrNull()
            },
            async(Dispatchers.IO) {
                runCatching {
                    withTimeout(24_000L) {
                        Opinion(
                            "Qwen",
                            groqCall(
                                key = groqKey,
                                model = "qwen/qwen3.8-27b",
                                messages = reviewerOpenAiMessages(messages, "Qwen")
                            )
                        )
                    }
                }.getOrNull()
            }
        ).awaitAll().filterNotNull()

        if (opinions.isEmpty()) {
            error("Все модели временно недоступны. Повтори запрос.")
        }

        if (opinions.size == 1) {
            onDelta(opinions.first().text)
            return@coroutineScope SmartResult(
                opinions.first().text,
                opinions.first().label + " · fallback"
            )
        }

        onStage("Собираю лучший итог…")

        val final = runCatching {
            withTimeout(45_000L) {
                geminiStream(
                    key = geminiKey,
                    contents = synthesisGeminiContents(messages, opinions),
                    system = synthesisSystem(),
                    thinkingLevel = "high",
                    useSearch = false,
                    onDelta = onDelta
                )
            }
        }.getOrElse {
            val fallback = opinions.maxByOrNull { it.text.length }?.text.orEmpty()
            onDelta(fallback)
            fallback
        }

        SmartResult(final, "Smart 3×")
    }

    private fun needsFreshWeb(text: String): Boolean {
        val q = text.lowercase()
        val triggers = listOf(
            "сегодня", "сейчас", "актуальн", "последн", "новост", "вышел",
            "обновлен", "обновлён", "релиз", "поищи", "найди в интернете",
            "цена сейчас", "курс сейчас", "кто сейчас", "latest", "today",
            "current", "news", "right now", "this week", "this month"
        )
        return triggers.any(q::contains)
    }

    private fun isComplex(text: String): Boolean {
        val q = text.lowercase()
        if (q.length >= 280) return true

        val triggers = listOf(
            "подумай", "проанализ", "сравни", "проверь точно", "разбери",
            "почему", "реши", "посчитай", "архитект", "код", "ошибка",
            "баг", "лучший вариант", "что лучше", "план", "оптимиз",
            "докажи", "объясни подробно", "analyze", "compare", "debug",
            "best approach", "architecture", "reason carefully"
        )
        return triggers.any(q::contains)
    }

    private fun baseSystem(): String =
        "Ты — SOHR AI, один постоянный умный чат. Отвечай на языке пользователя. " +
            "Пиши естественно, ясно и аккуратно. Проверяй логику перед ответом. " +
            "Не выдумывай факты, версии, цены, даты или источники. " +
            "Если чего-то не знаешь или не можешь подтвердить — скажи это прямо. " +
            "Не раскрывай скрытую цепочку рассуждений. Для простых вопросов отвечай компактно, " +
            "для сложных — структурировано, но без лишней воды."

    private fun reviewerSystem(name: String): String =
        "Ты независимый эксперт " + name + " внутри SOHR AI. Проверь запрос и предложи максимально точный ответ. " +
            "Ищи ошибки в предположениях, арифметике, логике и технических деталях. " +
            "Не раскрывай скрытую цепочку рассуждений; дай только вывод и нужные основания. " +
            "Отвечай на языке пользователя."

    private fun synthesisSystem(): String =
        "Ты финальный редактор SOHR AI. Тебе даны независимые ответы нескольких моделей. " +
            "Сверь их и выдай ОДИН цельный лучший ответ пользователю. " +
            "Не упоминай внутреннее голосование моделей без необходимости. " +
            "Исправляй явные ошибки и не усиливай непроверенные утверждения. " +
            "Если уверенность ограничена, обозначь это коротко. " +
            "Не раскрывай скрытые цепочки рассуждений."

    private fun normalGeminiContents(messages: List<ChatMessage>): JSONArray =
        JSONArray().apply {
            messages.filter { it.text.isNotBlank() }.takeLast(24).forEach { message ->
                put(
                    JSONObject()
                        .put("role", if (message.role == "assistant") "model" else "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", message.text)))
                )
            }
        }

    private fun reviewerGeminiContents(messages: List<ChatMessage>): JSONArray =
        JSONArray().apply {
            messages.filter { it.text.isNotBlank() }.takeLast(14).forEach { message ->
                put(
                    JSONObject()
                        .put("role", if (message.role == "assistant") "model" else "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", message.text)))
                )
            }
        }

    private fun synthesisGeminiContents(
        messages: List<ChatMessage>,
        opinions: List<Opinion>
    ): JSONArray {
        val latest = messages.lastOrNull { it.role == "user" }?.text.orEmpty()
        val panel = buildString {
            opinions.forEachIndexed { index, opinion ->
                append("\n--- Ответ ")
                append(index + 1)
                append(" · ")
                append(opinion.label)
                append(" ---\n")
                append(opinion.text.take(6000))
                append("\n")
            }
        }

        return JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put(
                    "parts",
                    JSONArray().put(
                        JSONObject().put(
                            "text",
                            "Последний запрос:\n" + latest +
                                "\n\nНезависимые ответы:" + panel +
                                "\nСобери один лучший итоговый ответ."
                        )
                    )
                )
        )
    }

    private fun normalOpenAiMessages(messages: List<ChatMessage>): JSONArray =
        JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", baseSystem()))
            messages.filter { it.text.isNotBlank() }.takeLast(22).forEach {
                put(
                    JSONObject()
                        .put("role", if (it.role == "assistant") "assistant" else "user")
                        .put("content", it.text)
                )
            }
        }

    private fun reviewerOpenAiMessages(
        messages: List<ChatMessage>,
        name: String
    ): JSONArray =
        JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", reviewerSystem(name)))
            messages.filter { it.text.isNotBlank() }.takeLast(14).forEach {
                put(
                    JSONObject()
                        .put("role", if (it.role == "assistant") "assistant" else "user")
                        .put("content", it.text)
                )
            }
        }

    private suspend fun geminiGenerate(
        key: String,
        contents: JSONArray,
        system: String,
        thinkingLevel: String
    ): String = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(key, StandardCharsets.UTF_8.name())
        val payload = geminiPayload(
            contents = contents,
            system = system,
            thinkingLevel = thinkingLevel,
            useSearch = false
        )

        val call = client.newCall(
            Request.Builder()
                .url(
                    "https://generativelanguage.googleapis.com/v1beta/models/" +
                        "gemini-3.8-flash:generateContent?key=" + encoded
                )
                .post(
                    payload.toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .build()
        )

        activeCalls.add(call)
        try {
            call.execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error(readJsonError(body, "Gemini ответил " + response.code))
                }

                val text = extractGeminiText(JSONObject(body))
                if (text.isBlank()) error("Gemini вернул пустой ответ")
                text
            }
        } finally {
            activeCalls.remove(call)
        }
    }

    private suspend fun geminiStream(
        key: String,
        contents: JSONArray,
        system: String,
        thinkingLevel: String,
        useSearch: Boolean,
        onDelta: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(key, StandardCharsets.UTF_8.name())
        val payload = geminiPayload(
            contents = contents,
            system = system,
            thinkingLevel = thinkingLevel,
            useSearch = useSearch
        )

        val call = client.newCall(
            Request.Builder()
                .url(
                    "https://generativelanguage.googleapis.com/v1beta/models/" +
                        "gemini-3.8-flash:streamGenerateContent?alt=sse&key=" + encoded
                )
                .post(
                    payload.toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .header("Accept", "text/event-stream")
                .build()
        )

        activeCalls.add(call)

        val out = StringBuilder()
        val sources = linkedMapOf<String, String>()

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    error(readJsonError(body, "Gemini ответил " + response.code))
                }

                val source = response.body?.source()
                    ?: error("Gemini не вернул поток")

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue

                    val raw = line.removePrefix("data:").trim()
                    if (raw.isBlank() || raw == "[DONE]") continue

                    val chunk = runCatching { JSONObject(raw) }.getOrNull() ?: continue
                    val text = extractGeminiText(chunk)

                    if (text.isNotEmpty()) {
                        out.append(text)
                        onDelta(text)
                    }

                    collectGrounding(chunk, sources)
                }
            }
        } catch (t: Throwable) {
            if (out.isEmpty()) throw t
        } finally {
            activeCalls.remove(call)
        }

        if (out.isEmpty()) error("Gemini вернул пустой поток")

        if (sources.isNotEmpty()) {
            out.append("\n\n**Источники**")
            sources.entries.take(8).forEach { entry ->
                out.append("\n- [")
                out.append(entry.value.replace("[", "").replace("]", ""))
                out.append("](")
                out.append(entry.key)
                out.append(")")
            }
        }

        out.toString()
    }

    private fun geminiPayload(
        contents: JSONArray,
        system: String,
        thinkingLevel: String,
        useSearch: Boolean
    ): JSONObject {
        val payload = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", system))
                )
            )
            .put("contents", contents)
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.3)
                    .put("maxOutputTokens", 8192)
                    .put(
                        "thinkingConfig",
                        JSONObject().put("thinkingLevel", thinkingLevel)
                    )
            )

        if (useSearch) {
            payload.put(
                "tools",
                JSONArray().put(
                    JSONObject().put("google_search", JSONObject())
                )
            )
        }

        return payload
    }

    private suspend fun groqCall(
        key: String,
        model: String,
        messages: JSONArray
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0.3)
            .put("max_completion_tokens", 3072)

        val call = client.newCall(
            Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .post(
                    payload.toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .build()
        )

        activeCalls.add(call)
        try {
            call.execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error(readJsonError(body, "Groq ответил " + response.code))
                }

                val text = JSONObject(body)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
                    .orEmpty()

                if (text.isBlank()) error("Groq вернул пустой ответ")
                text
            }
        } finally {
            activeCalls.remove(call)
        }
    }

    private fun collectGrounding(
        chunk: JSONObject,
        out: MutableMap<String, String>
    ) {
        val candidates = chunk.optJSONArray("candidates") ?: return
        for (i in 0 until candidates.length()) {
            val metadata = candidates
                .optJSONObject(i)
                ?.optJSONObject("groundingMetadata")
                ?: continue

            val chunks = metadata.optJSONArray("groundingChunks") ?: continue
            for (j in 0 until chunks.length()) {
                val web = chunks.optJSONObject(j)?.optJSONObject("web") ?: continue
                val url = web.optString("uri")
                if (url.isBlank()) continue
                val title = web.optString("title").ifBlank { url }
                out.putIfAbsent(url, title)
            }
        }
    }

    private fun extractGeminiText(json: JSONObject): String {
        val candidates = json.optJSONArray("candidates") ?: return ""
        val content = candidates.optJSONObject(0)?.optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""

        return buildString {
            for (i in 0 until parts.length()) {
                val text = parts.optJSONObject(i)?.optString("text").orEmpty()
                if (text.isNotEmpty()) append(text)
            }
        }
    }

    private fun readApiError(response: okhttp3.Response, fallback: String): String {
        val body = response.body?.string().orEmpty()
        return readJsonError(body, fallback + " (" + response.code + ")")
    }

    private fun readJsonError(body: String, fallback: String): String =
        runCatching {
            val json = JSONObject(body)
            json.optJSONObject("error")
                ?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: fallback
        }.getOrDefault(fallback)
}
