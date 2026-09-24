package com.unknokable.sohrai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
        .connectTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.MINUTES)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val activeCalls = Collections.synchronizedSet(mutableSetOf<Call>())
    @Volatile private var activeWebSocket: WebSocket? = null

    fun cancel() {
        synchronized(activeCalls) {
            activeCalls.toList().forEach { runCatching { it.cancel() } }
            activeCalls.clear()
        }
        activeWebSocket?.cancel()
        activeWebSocket = null
    }

    suspend fun verifyGemini(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(key, StandardCharsets.UTF_8.name())
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?key=" + encoded)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error(readApiError(response, "Google AI отклонил ключ"))
                }
            }
        }
    }

    suspend fun verifyGroq(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/models")
                .get()
                .header("Authorization", "Bearer " + key)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error(readApiError(response, "Groq отклонил ключ"))
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
    ): SmartResult = coroutineScope {
        val latest = messages.lastOrNull { it.role == "user" }?.text.orEmpty()
        if (latest.isBlank()) error("Пустой запрос")

        when {
            needsFreshWeb(latest) -> {
                onStage("Проверяю свежую информацию в интернете…")
                val searched = runCatching {
                    withTimeout(120_000L) {
                        geminiLiveSearch(geminiKey, messages, onDelta)
                    }
                }
                if (searched.isSuccess && !searched.getOrNull().isNullOrBlank()) {
                    SmartResult(searched.getOrThrow(), "Web · Gemini Live")
                } else {
                    onStage("Веб-поиск недоступен — включаю перекрёстную проверку…")
                    smartFusion(geminiKey, groqKey, messages, onStage, onDelta)
                }
            }

            isComplex(latest) -> {
                smartFusion(geminiKey, groqKey, messages, onStage, onDelta)
            }

            else -> {
                onStage("Думаю…")
                val answer = runCatching {
                    geminiStream(
                        key = geminiKey,
                        contents = normalGeminiContents(messages),
                        system = baseSystem(),
                        onDelta = onDelta
                    )
                }.getOrElse {
                    onStage("Gemini недоступен — переключаюсь на резерв…")
                    val fallback = runCatching {
                        groqCall(
                            key = groqKey,
                            model = "openai/gpt-oss-120b",
                            messages = normalOpenAiMessages(messages)
                        )
                    }.getOrElse {
                        groqCall(
                            key = groqKey,
                            model = "qwen/qwen3.8-27b",
                            messages = normalOpenAiMessages(messages)
                        )
                    }
                    onDelta(fallback)
                    fallback
                }
                SmartResult(answer, "Smart")
            }
        }
    }

    private suspend fun smartFusion(
        geminiKey: String,
        groqKey: String,
        messages: List<ChatMessage>,
        onStage: (String) -> Unit,
        onDelta: (String) -> Unit
    ): SmartResult = coroutineScope {
        onStage("Сверяю решение тремя моделями…")

        val opinions = listOf(
            async(Dispatchers.IO) {
                runCatching {
                    Opinion(
                        "Gemini 3.8 Flash",
                        geminiGenerate(
                            geminiKey,
                            reviewerGeminiContents(messages),
                            reviewerSystem("Gemini")
                        )
                    )
                }.getOrNull()
            },
            async(Dispatchers.IO) {
                runCatching {
                    Opinion(
                        "GPT-OSS 120B",
                        groqCall(
                            groqKey,
                            "openai/gpt-oss-120b",
                            reviewerOpenAiMessages(messages, "GPT-OSS")
                        )
                    )
                }.getOrNull()
            },
            async(Dispatchers.IO) {
                runCatching {
                    Opinion(
                        "Qwen 3.8 27B",
                        groqCall(
                            groqKey,
                            "qwen/qwen3.8-27b",
                            reviewerOpenAiMessages(messages, "Qwen")
                        )
                    )
                }.getOrNull()
            }
        ).awaitAll().filterNotNull()

        if (opinions.isEmpty()) {
            error("Все подключённые бесплатные модели временно недоступны.")
        }

        if (opinions.size == 1) {
            onDelta(opinions.first().text)
            return@coroutineScope SmartResult(
                opinions.first().text,
                opinions.first().label + " · fallback"
            )
        }

        onStage("Собираю один итоговый ответ…")

        val finalAnswer = runCatching {
            geminiStream(
                key = geminiKey,
                contents = synthesisGeminiContents(messages, opinions),
                system = synthesisSystem(),
                onDelta = onDelta
            )
        }.getOrElse {
            val fallback = opinions.maxByOrNull { it.text.length }?.text.orEmpty()
            onDelta(fallback)
            fallback
        }

        SmartResult(
            finalAnswer,
            "Smart 3×"
        )
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
        if (q.length >= 320) return true

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
            "Пиши естественно, ясно и аккуратно. Не выдумывай факты, версии, цены, даты или источники. " +
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
            messages.filter { it.text.isNotBlank() }.takeLast(30).forEach { message ->
                put(
                    JSONObject()
                        .put("role", if (message.role == "assistant") "model" else "user")
                        .put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", message.text))
                        )
                )
            }
        }

    private fun reviewerGeminiContents(messages: List<ChatMessage>): JSONArray =
        JSONArray().apply {
            messages.filter { it.text.isNotBlank() }.takeLast(18).forEach { message ->
                put(
                    JSONObject()
                        .put("role", if (message.role == "assistant") "model" else "user")
                        .put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", message.text))
                        )
                )
            }
        }

    private fun synthesisGeminiContents(
        messages: List<ChatMessage>,
        opinions: List<Opinion>
    ): JSONArray {
        val recent = messages.filter { it.text.isNotBlank() }.takeLast(10)
        val latest = messages.lastOrNull { it.role == "user" }?.text.orEmpty()

        val context = buildString {
            recent.forEach {
                append(if (it.role == "user") "Пользователь: " else "Ассистент: ")
                append(it.text.take(3500))
                append("\\n")
            }
        }

        val panel = buildString {
            opinions.forEachIndexed { index, opinion ->
                append("\\n--- Эксперт ")
                append(index + 1)
                append(" · ")
                append(opinion.label)
                append(" ---\\n")
                append(opinion.text.take(7000))
                append("\\n")
            }
        }

        val prompt =
            "Последний запрос пользователя:\\n" + latest +
                "\\n\\nНедавний контекст:\\n" + context +
                "\\nНезависимые ответы:\\n" + panel +
                "\\nСобери финальный ответ."

        return JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", prompt))
                )
        )
    }

    private fun normalOpenAiMessages(messages: List<ChatMessage>): JSONArray =
        JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", baseSystem()))
            messages.filter { it.text.isNotBlank() }.takeLast(28).forEach {
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
            messages.filter { it.text.isNotBlank() }.takeLast(18).forEach {
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
        system: String
    ): String = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(key, StandardCharsets.UTF_8.name())
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
                    .put("temperature", 0.35)
                    .put("maxOutputTokens", 4096)
            )

        val request = Request.Builder()
            .url(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "gemini-3.8-flash:generateContent?key=" + encoded
            )
            .post(
                payload.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
            )
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error(readJsonError(body, "Gemini ответил " + response.code))
            }

            val text = extractGeminiText(JSONObject(body))
            if (text.isBlank()) error("Gemini вернул пустой ответ")
            text
        }
    }

    private suspend fun geminiStream(
        key: String,
        contents: JSONArray,
        system: String,
        onDelta: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(key, StandardCharsets.UTF_8.name())
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
                    .put("temperature", 0.35)
                    .put("maxOutputTokens", 8192)
            )

        val request = Request.Builder()
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

        val call = client.newCall(request)
        activeCalls.add(call)

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    error(readJsonError(body, "Gemini ответил " + response.code))
                }

                val source = response.body?.source()
                    ?: error("Gemini не вернул поток")
                val out = StringBuilder()

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
                }

                if (out.isEmpty()) error("Gemini вернул пустой поток")
                out.toString()
            }
        } finally {
            activeCalls.remove(call)
        }
    }

    private suspend fun groqCall(
        key: String,
        model: String,
        messages: JSONArray
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0.35)
            .put("max_completion_tokens", 4096)

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .post(
                payload.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
            )
            .header("Authorization", "Bearer " + key)
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
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
    }

    private suspend fun geminiLiveSearch(
        key: String,
        messages: List<ChatMessage>,
        onDelta: (String) -> Unit
    ): String = suspendCancellableCoroutine { continuation ->
        val encoded = URLEncoder.encode(key, StandardCharsets.UTF_8.name())
        val request = Request.Builder()
            .url(
                "wss://generativelanguage.googleapis.com/ws/" +
                    "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent" +
                    "?key=" + encoded
            )
            .build()

        val answer = StringBuilder()
        val sources = linkedMapOf<String, String>()
        var promptSent = false
        var finished = false

        fun finishSuccess() {
            if (finished) return
            finished = true
            activeWebSocket = null

            val finalText = buildString {
                append(answer.toString().trim())
                if (sources.isNotEmpty()) {
                    append("\\n\\n**Источники**")
                    sources.entries.take(8).forEach { entry ->
                        append("\\n- [")
                        append(entry.value.replace("[", "").replace("]", ""))
                        append("](")
                        append(entry.key)
                        append(")")
                    }
                }
            }

            if (finalText.isBlank()) {
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        IllegalStateException("Веб-поиск не вернул текст")
                    )
                }
            } else if (continuation.isActive) {
                continuation.resume(finalText)
            }
        }

        fun finishFailure(t: Throwable) {
            if (finished) return
            finished = true
            activeWebSocket = null
            if (continuation.isActive) continuation.resumeWithException(t)
        }

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                activeWebSocket = webSocket

                val setup = JSONObject().put(
                    "setup",
                    JSONObject()
                        .put("model", "models/gemini-3.8-live")
                        .put(
                            "generationConfig",
                            JSONObject().put(
                                "responseModalities",
                                JSONArray().put("AUDIO")
                            )
                        )
                        .put(
                            "systemInstruction",
                            JSONObject().put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().put(
                                        "text",
                                        baseSystem() +
                                            " Для этого запроса используй Google Search grounding. " +
                                            "Учитывай текущую дату и не придумывай источники."
                                    )
                                )
                            )
                        )
                        .put(
                            "tools",
                            JSONArray().put(
                                JSONObject().put("googleSearch", JSONObject())
                            )
                        )
                        .put("outputAudioTranscription", JSONObject())
                )

                webSocket.send(setup.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return

                if (json.has("setupComplete") && !promptSent) {
                    promptSent = true

                    val context = buildString {
                        messages.filter { it.text.isNotBlank() }.takeLast(12).forEach {
                            append(if (it.role == "user") "Пользователь: " else "Ассистент: ")
                            append(it.text.take(2500))
                            append("\\n")
                        }
                    }

                    val clientContent = JSONObject().put(
                        "clientContent",
                        JSONObject()
                            .put(
                                "turns",
                                JSONArray().put(
                                    JSONObject()
                                        .put("role", "user")
                                        .put(
                                            "parts",
                                            JSONArray().put(
                                                JSONObject().put(
                                                    "text",
                                                    "Контекст диалога:\\n" + context +
                                                        "\\nОтветь на последний запрос. " +
                                                        "Проверь свежие факты через Google Search."
                                                )
                                            )
                                        )
                                )
                            )
                            .put("turnComplete", true)
                    )

                    webSocket.send(clientContent.toString())
                    return
                }

                val server = json.optJSONObject("serverContent")
                if (server != null) {
                    val transcript = server
                        .optJSONObject("outputTranscription")
                        ?.optString("text")
                        .orEmpty()

                    if (transcript.isNotEmpty()) {
                        answer.append(transcript)
                        onDelta(transcript)
                    } else {
                        val parts = server
                            .optJSONObject("modelTurn")
                            ?.optJSONArray("parts")

                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                val part = parts.optJSONObject(i) ?: continue
                                val piece = part.optString("text")
                                if (piece.isNotEmpty()) {
                                    answer.append(piece)
                                    onDelta(piece)
                                }
                            }
                        }
                    }

                    collectGrounding(
                        server.optJSONObject("groundingMetadata"),
                        sources
                    )

                    if (server.optBoolean("turnComplete", false)) {
                        webSocket.close(1000, "done")
                        finishSuccess()
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Audio bytes are ignored; the app uses the text transcript.
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?
            ) {
                finishFailure(t)
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String
            ) {
                if (!finished) {
                    if (answer.isNotEmpty()) {
                        finishSuccess()
                    } else {
                        finishFailure(
                            IllegalStateException(
                                if (reason.isBlank()) "Gemini Live закрыл соединение" else reason
                            )
                        )
                    }
                }
            }
        }

        val socket = client.newWebSocket(request, listener)
        activeWebSocket = socket

        continuation.invokeOnCancellation {
            runCatching { socket.cancel() }
            activeWebSocket = null
        }
    }

    private fun collectGrounding(
        metadata: JSONObject?,
        out: MutableMap<String, String>
    ) {
        if (metadata == null) return
        val chunks = metadata.optJSONArray("groundingChunks") ?: return

        for (i in 0 until chunks.length()) {
            val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
            val url = web.optString("uri")
            if (url.isBlank()) continue
            val title = web.optString("title").ifBlank { url }
            out.putIfAbsent(url, title)
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

    private fun readApiError(response: Response, fallback: String): String {
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
