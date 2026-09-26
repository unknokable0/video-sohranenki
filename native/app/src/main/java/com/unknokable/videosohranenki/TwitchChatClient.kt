package com.unknokable.videosohranenki

import android.graphics.Color
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class TwitchChatMessage(
    val displayName: String,
    val text: String,
    val color: Int
)

class TwitchChatClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @Volatile
    private var socket: SSLSocket? = null

    fun connect(
        accessToken: String,
        accountLogin: String,
        channelLogin: String,
        onStatus: (String) -> Unit,
        onMessage: (TwitchChatMessage) -> Unit
    ) {
        closeConnection()
        job?.cancel()

        job = scope.launch {
            var reconnectAttempt = 0

            while (isActive) {
                var authFailed = false
                var twitchRequestedReconnect = false

                try {
                    onStatus(
                        if (reconnectAttempt == 0) "Подключаем чат…"
                        else "Переподключаемся…"
                    )

                    val ssl = (SSLSocketFactory.getDefault()
                        .createSocket("irc.chat.twitch.tv", 6697) as SSLSocket).apply {
                        soTimeout = 0
                        startHandshake()
                    }
                    socket = ssl

                    val reader = BufferedReader(
                        InputStreamReader(ssl.inputStream, Charsets.UTF_8)
                    )
                    val writer = BufferedWriter(
                        OutputStreamWriter(ssl.outputStream, Charsets.UTF_8)
                    )

                    fun send(line: String) {
                        writer.write(line)
                        writer.write("\r\n")
                        writer.flush()
                    }

                    send("PASS oauth:" + accessToken)
                    send("NICK " + accountLogin.lowercase())
                    send("CAP REQ :twitch.tv/tags twitch.tv/commands")
                    send("JOIN #" + channelLogin.lowercase())

                    var connected = false

                    while (isActive && !ssl.isClosed) {
                        val line = reader.readLine() ?: break

                        when {
                            line.startsWith("PING ") -> {
                                send(line.replaceFirst("PING", "PONG"))
                            }

                            line.contains(
                                "Login authentication failed",
                                ignoreCase = true
                            ) -> {
                                authFailed = true
                                onStatus("Переподключи Twitch")
                                break
                            }

                            line.startsWith(":tmi.twitch.tv RECONNECT") ||
                                line.contains(" RECONNECT ") -> {
                                twitchRequestedReconnect = true
                                break
                            }

                            !connected &&
                                (line.contains(" 001 ") ||
                                    line.contains(" ROOMSTATE #")) -> {
                                connected = true
                                reconnectAttempt = 0
                                onStatus("Чат подключён")
                            }

                            line.contains(" PRIVMSG #") -> {
                                if (!connected) {
                                    connected = true
                                    reconnectAttempt = 0
                                    onStatus("Чат подключён")
                                }
                                parsePrivMsg(line)?.let(onMessage)
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A short network/TLS break is recoverable.
                } finally {
                    closeConnection()
                }

                if (!isActive || authFailed) break

                reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
                onStatus("Переподключаемся…")

                val delayMs = if (twitchRequestedReconnect) {
                    350L
                } else {
                    when (reconnectAttempt) {
                        1 -> 700L
                        2 -> 1_200L
                        3 -> 2_000L
                        4 -> 3_500L
                        else -> 6_000L
                    }
                }
                delay(delayMs)
            }
        }
    }

    private fun parsePrivMsg(line: String): TwitchChatMessage? {
        val priv = line.indexOf(" PRIVMSG #")
        if (priv < 0) return null

        val messageStart = line.indexOf(" :", priv)
        if (messageStart < 0 || messageStart + 2 > line.length) return null

        val messageText = line.substring(messageStart + 2).trim()
        if (messageText.isBlank()) return null

        val tags = if (line.startsWith("@")) {
            val firstSpace = line.indexOf(' ')
            if (firstSpace > 1) {
                line.substring(1, firstSpace)
                    .split(';')
                    .mapNotNull { entry ->
                        val idx = entry.indexOf('=')
                        if (idx <= 0) null
                        else entry.substring(0, idx) to unescape(entry.substring(idx + 1))
                    }
                    .toMap()
            } else {
                emptyMap()
            }
        } else {
            emptyMap()
        }

        val prefixStart = if (line.startsWith("@")) line.indexOf(' ') + 1 else 0
        val prefixEnd = line.indexOf('!', prefixStart)
        val fallbackName = if (prefixEnd > prefixStart) {
            line.substring(prefixStart, prefixEnd).removePrefix(":")
        } else {
            "user"
        }

        val displayName = tags["display-name"]
            .orEmpty()
            .ifBlank { fallbackName }

        val parsedColor = tags["color"]
            .orEmpty()
            .takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) }
            ?.let { runCatching { Color.parseColor(it) }.getOrNull() }

        return TwitchChatMessage(
            displayName = displayName,
            text = messageText,
            color = parsedColor ?: Color.parseColor("#B69CFF")
        )
    }

    private fun unescape(value: String): String =
        value.replace("\\s", " ")
            .replace("\\:", ";")
            .replace("\\\\", "\\")
            .replace("\\r", "\r")
            .replace("\\n", "\n")

    private fun closeConnection() {
        runCatching { socket?.close() }
        socket = null
    }

    fun close() {
        job?.cancel()
        job = null
        closeConnection()
        scope.cancel()
    }
}
