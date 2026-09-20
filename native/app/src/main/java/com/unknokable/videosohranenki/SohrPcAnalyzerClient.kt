package com.unknokable.videosohranenki

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.coroutines.coroutineContext

data class PcAnalyzerEndpoint(
    val host: String,
    val port: Int,
    val token: String,
    val version: String
)

object SohrPcAnalyzerClient {
    private const val DISCOVERY_PORT = 8764
    private const val MAGIC = "SOHR_DISCOVER_V1"

    suspend fun discover(timeoutMs: Int = 1_800): PcAnalyzerEndpoint? =
        withContext(Dispatchers.IO) {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 220

                val payload = MAGIC.toByteArray(Charsets.UTF_8)
                val targets = linkedSetOf<InetAddress>()
                runCatching { targets += InetAddress.getByName("255.255.255.255") }

                runCatching {
                    val interfaces = NetworkInterface.getNetworkInterfaces()
                    while (interfaces.hasMoreElements()) {
                        val iface = interfaces.nextElement()
                        if (!iface.isUp || iface.isLoopback) continue
                        iface.interfaceAddresses.forEach { address ->
                            val broadcast = address.broadcast
                            if (broadcast is Inet4Address) targets += broadcast
                        }
                    }
                }

                targets.forEach { target ->
                    runCatching {
                        socket.send(
                            DatagramPacket(
                                payload,
                                payload.size,
                                target,
                                DISCOVERY_PORT
                            )
                        )
                    }
                }

                val deadline = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(4_096)

                while (System.currentTimeMillis() < deadline) {
                    coroutineContext.ensureActive()
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val json = JSONObject(
                            String(
                                packet.data,
                                packet.offset,
                                packet.length,
                                Charsets.UTF_8
                            )
                        )

                        if (json.optString("type") != "SOHR_ANALYZER_V1") continue

                        val host = json.optString("ip")
                            .takeIf { it.isNotBlank() }
                            ?: packet.address.hostAddress
                            ?: continue
                        val port = json.optInt("port", 8765)
                        val token = json.optString("token")
                        val version = json.optString("version", "?")

                        if (token.isBlank()) continue

                        return@withContext PcAnalyzerEndpoint(
                            host = host,
                            port = port,
                            token = token,
                            version = version
                        )
                    } catch (_: SocketTimeoutException) {
                    }
                }

                null
            }
        }

    suspend fun analyze(
        videoId: String,
        durationSeconds: Int,
        force: Boolean = false,
        onProgress: suspend (Int, String) -> Unit = { _, _ -> }
    ): SmartAnalysisResult = withContext(Dispatchers.IO) {
        onProgress(1, "Ищем SOHR Analyzer на ПК…")
        val endpoint = discover()
            ?: throw IOException(
                "SOHR Analyzer на ПК не найден. Запусти SOHR-Analyzer.exe и подключи телефон и ПК к одной сети."
            )

        onProgress(3, "ПК найден • Analyzer " + endpoint.version)

        val request = JSONObject().apply {
            put("video_id", videoId)
            put("duration_seconds", durationSeconds)
            put("force", force)
        }

        val first = requestJson(
            endpoint = endpoint,
            method = "POST",
            path = "/v1/analyze",
            body = request.toString(),
            connectTimeoutMs = 3_000,
            readTimeoutMs = 5_000
        )

        if (first.optBoolean("cached", false)) {
            val chapters = parseResult(first.optJSONObject("result"))
            onProgress(100, "Готово • результат уже был на ПК")
            return@withContext SmartAnalysisResult(
                chapters = chapters,
                scannedFrames = 0,
                usedOcr = true
            )
        }

        val jobId = first.optString("job_id")
            .takeIf { it.isNotBlank() }
            ?: throw IOException("Analyzer не вернул ID задания")

        var lastProgress = -1
        while (true) {
            coroutineContext.ensureActive()
            delay(650L)

            val job = requestJson(
                endpoint = endpoint,
                method = "GET",
                path = "/v1/jobs/" + jobId,
                body = null,
                connectTimeoutMs = 2_500,
                readTimeoutMs = 4_000
            )

            val status = job.optString("status")
            val progress = job.optInt("progress", 0).coerceIn(0, 100)
            val message = job.optString("message").ifBlank { "Анализируем на ПК…" }

            if (progress != lastProgress || status == "done") {
                lastProgress = progress
                onProgress(progress, message)
            }

            when (status) {
                "done" -> {
                    val chapters = parseChapters(job.optJSONArray("chapters"))
                    onProgress(100, "Готово • найдено видео: " + chapters.size)
                    return@withContext SmartAnalysisResult(
                        chapters = chapters,
                        scannedFrames = 0,
                        usedOcr = true
                    )
                }

                "error" -> {
                    val error = job.optString("error")
                    throw IOException(
                        error.ifBlank { "SOHR Analyzer не смог обработать VOD" }
                    )
                }

                "cancelled" -> {
                    throw IOException("Анализ на ПК был остановлен")
                }
            }
        }
    }

    private fun parseResult(result: JSONObject?): List<SmartChapter> {
        return parseChapters(result?.optJSONArray("chapters"))
    }

    private fun parseChapters(array: JSONArray?): List<SmartChapter> {
        if (array == null) return emptyList()

        val out = ArrayList<SmartChapter>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val start = item.optInt("start_seconds", -1)
            val end = item.optInt("end_seconds", -1)
            if (start < 0 || end <= start) continue

            out += SmartChapter(
                startSeconds = start,
                endSeconds = end,
                title = item.optString("title").ifBlank { "Смотрит видео" },
                detail = item.optString("detail").ifBlank {
                    "SOHR PC Analyzer"
                },
                confidence = item.optInt("confidence", 80)
            )
        }

        return out.sortedBy { it.startSeconds }
    }

    private fun requestJson(
        endpoint: PcAnalyzerEndpoint,
        method: String,
        path: String,
        body: String?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): JSONObject {
        val url = URL("http://" + endpoint.host + ":" + endpoint.port + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-SOHR-Token", endpoint.token)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        return try {
            if (body != null) {
                connection.outputStream.use {
                    it.write(body.toByteArray(Charsets.UTF_8))
                }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val text = stream?.use {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText()
            }.orEmpty()

            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(text).optString("error")
                }.getOrNull()
                throw IOException(
                    message?.takeIf { it.isNotBlank() }
                        ?: "Analyzer HTTP " + code
                )
            }

            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}
