package com.unknokable.videosohranenki

import android.media.MediaDataSource
import fi.iki.elonen.NanoHTTPD
import io.github.tdlibandroid.ktx.TdClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import org.drinkless.tdlib.TdApi
import java.io.IOException
import java.io.InputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

private object TelegramDownloadCoordinator {
    private val locks = ConcurrentHashMap<Int, Mutex>()

    suspend fun <T> withFile(fileId: Int, block: suspend () -> T): T =
        locks.getOrPut(fileId) { Mutex() }.withLock { block() }
}

class TelegramStreamServer(
    private val client: TdClient,
    port: Int = 8765
) : NanoHTTPD("127.0.0.1", port) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun url(item: VideoItem): String =
        "http://127.0.0.1:$listeningPort/video/${item.fileId}?size=${item.fileSize}&mime=${item.mimeType}"

    fun mediaDataSource(item: VideoItem): MediaDataSource = TelegramMediaDataSource(client, item.fileId, item.fileSize)

    fun release(item: VideoItem) {
        // Intentionally do not cancel TDLib downloads here.
        // A manual/offline download may be using the same file and cancelling it
        // causes "Canceled by another downloadFile request".
        if (item.fileId <= 0) return
    }

    fun prefetch(item: VideoItem) {
        if (item.fileId <= 0 || item.fileSize <= 0L) return
        scope.launch {
            runCatching {
                // Prime only a small startup range. A large 12 MB request could make
                // a following synchronous read wait several seconds before first frame.
                val limit = minOf(768L * 1024L, item.fileSize)
                TelegramDownloadCoordinator.withFile(item.fileId) {
                    client.send(TdApi.DownloadFile(item.fileId, 32, 0, limit, true))
                }
            }
        }
    }

    fun prefetchFraction(
        item: VideoItem,
        fraction: Float = 0.15f,
        maxBytes: Long = 24L * 1024L * 1024L
    ) {
        if (item.fileId <= 0 || item.fileSize <= 0L) return
        scope.launch {
            runCatching {
                val target = (item.fileSize * fraction.coerceIn(0.01f, 0.25f))
                    .toLong()
                    .coerceAtLeast(768L * 1024L)
                val limit = minOf(item.fileSize, target, maxBytes.coerceAtLeast(768L * 1024L))
                TelegramDownloadCoordinator.withFile(item.fileId) {
                    client.send(TdApi.DownloadFile(item.fileId, 20, 0, limit, true))
                }
            }
        }
    }

    suspend fun downloadFully(item: VideoItem): File = withContext(Dispatchers.IO) {
        require(item.fileId > 0 && item.fileSize > 0L) { "Видео недоступно для скачивания" }

        var finalFile: TdApi.File? = null
        var addedToDownloads = false

        try {
            if (item.chatId != 0L && item.messageId > 0L) {
                // TDLib's persistent download list is independent of partial
                // downloadFile() range requests used by the player. This prevents
                // "Canceled by another downloadFile request" while watching.
                client.send(
                    TdApi.AddFileToDownloads(
                        item.fileId,
                        item.chatId,
                        item.messageId,
                        32
                    )
                )
                addedToDownloads = true

                while (true) {
                    val state = client.send(TdApi.GetFile(item.fileId))
                    finalFile = state
                    if (state.local.isDownloadingCompleted) break

                    check(
                        state.local.isDownloadingActive || state.local.canBeDownloaded
                    ) { "Telegram остановил загрузку файла" }

                    delay(250L)
                }
            } else {
                // Fallback for old cached items which don't have chatId yet.
                // One unlimited synchronous request is safer than a chain of
                // offset/limit requests, because changing offset/limit cancels
                // an existing synchronous TDLib download.
                finalFile = TelegramDownloadCoordinator.withFile(item.fileId) {
                    client.send(TdApi.DownloadFile(item.fileId, 32, 0, 0, true))
                }
            }

            val completed = finalFile ?: client.send(TdApi.GetFile(item.fileId))
            val path = completed.local.path
            require(
                completed.local.isDownloadingCompleted && path.isNotBlank()
            ) { "Telegram не завершил загрузку файла" }

            File(path).takeIf { it.exists() }
                ?: error("Загруженный файл не найден")
        } finally {
            if (addedToDownloads) {
                runCatching {
                    client.send(TdApi.RemoveFileFromDownloads(item.fileId, false))
                }
            }
        }
    }

    override fun stop() { scope.cancel(); super.stop() }

    override fun serve(session: IHTTPSession): Response {
        if (!session.uri.startsWith("/video/")) return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        val fileId = session.uri.substringAfterLast("/").toIntOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Bad file id")
        val size = session.parameters["size"]?.firstOrNull()?.toLongOrNull() ?: 0L
        val mime = session.parameters["mime"]?.firstOrNull()?.ifBlank { "video/mp4" } ?: "video/mp4"
        if (size <= 0L) return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Unknown file size")

        val range = session.headers["range"]
        var start = 0L
        var end = size - 1
        var status = Response.Status.OK
        if (!range.isNullOrBlank() && range.startsWith("bytes=")) {
            val parts = range.removePrefix("bytes=").substringBefore(",").split("-", limit = 2)
            start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            end = min(parts.getOrNull(1)?.toLongOrNull() ?: (size - 1), size - 1)
            if (start > end || start >= size) return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "").also { it.addHeader("Content-Range", "bytes */$size") }
            status = Response.Status.PARTIAL_CONTENT
        }
        val length = end - start + 1
        val response = newFixedLengthResponse(status, mime, TelegramFileInputStream(client, fileId, start, end), length)
        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("Cache-Control", "no-store")
        response.addHeader("Content-Length", length.toString())
        if (status == Response.Status.PARTIAL_CONTENT) response.addHeader("Content-Range", "bytes $start-$end/$size")
        return response
    }
}

private class TelegramFileInputStream(
    private val client: TdClient,
    private val fileId: Int,
    start: Long,
    private val endInclusive: Long
) : InputStream() {
    private var position = start
    private var filePath: String? = null
    private var raf: RandomAccessFile? = null
    private var bufferedStart = -1L
    private var bufferedEndExclusive = -1L
    private val startupWindow = 256L * 1024L
    private val steadyWindow = 1L * 1024L * 1024L
    private val backgroundWindow = 4L * 1024L * 1024L
    private val prefetchThreshold = 512L * 1024L

    override fun read(): Int { val one = ByteArray(1); return if (read(one, 0, 1) == 1) one[0].toInt() and 0xff else -1 }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (position > endInclusive) return -1
        val wanted = min(length.toLong(), endInclusive - position + 1).toInt()
        if (wanted <= 0) return -1
        ensureRange(position, wanted.toLong())
        val file = raf ?: throw IOException("Telegram stream file is unavailable")
        file.seek(position)
        val maxReadable = minOf(
            wanted.toLong(),
            (bufferedEndExclusive - position).coerceAtLeast(0L)
        ).toInt()
        if (maxReadable <= 0) {
            // Returning -1 here means real EOF to ExoPlayer and can turn a temporary
            // Telegram range delay into a fatal/truncated-media error.
            throw IOException("Telegram range is not ready at byte $position")
        }
        val count = file.read(buffer, offset, maxReadable)
        if (count <= 0 && position <= endInclusive) {
            throw IOException("Telegram stream returned no data at byte $position")
        }
        if (count > 0) {
            position += count
            maybePrefetchNext()
        }
        return count
    }

    private fun ensureRange(offset: Long, requested: Long) {
        val requestedEnd = min(endInclusive + 1, offset + requested)
        if (covers(offset, requestedEnd)) return

        val baseWindow = if (offset < 1024L * 1024L) startupWindow else steadyWindow
        var attempts = 0
        var lastError: Throwable? = null

        while (!covers(offset, requestedEnd) && attempts < 5) {
            val windowLimit = minOf(
                maxOf(requested, baseWindow),
                endInclusive - offset + 1
            )
            try {
                val result = runBlocking {
                    TelegramDownloadCoordinator.withFile(fileId) {
                        client.send(TdApi.DownloadFile(fileId, 32, offset, windowLimit, true))
                    }
                }
                applyLocalRange(result.local)
            } catch (error: Throwable) {
                lastError = error
            }

            if (!covers(offset, requestedEnd) && attempts < 4) {
                try {
                    Thread.sleep(35L * (attempts + 1))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            attempts++
        }

        if (!covers(offset, requestedEnd)) {
            throw IOException(
                "Telegram did not provide requested bytes $offset..${requestedEnd - 1}",
                lastError
            )
        }
    }

    private fun covers(start: Long, endExclusive: Long): Boolean =
        bufferedStart >= 0L &&
            start >= bufferedStart &&
            endExclusive <= bufferedEndExclusive

    private fun applyLocalRange(local: TdApi.LocalFile) {
        val path = local.path
        if (path.isBlank()) throw IllegalStateException("TDLib returned no local path")
        if (path != filePath) { raf?.close(); filePath = path; raf = RandomAccessFile(path, "r") }
        bufferedStart = local.downloadOffset
        bufferedEndExclusive = if (local.isDownloadingCompleted) endInclusive + 1 else local.downloadOffset + local.downloadedPrefixSize
    }

    private fun maybePrefetchNext() {
        if (
            bufferedEndExclusive <= 0L ||
            bufferedEndExclusive > endInclusive ||
            bufferedEndExclusive - position > prefetchThreshold
        ) return

        val nextStart = bufferedEndExclusive.coerceAtLeast(position)
        val nextLimit = min(backgroundWindow, endInclusive - nextStart + 1)
        if (nextLimit <= 0L) return

        // Fire-and-forget read-ahead. Blocking reads above still request only a small
        // window, so first frame and seeks don't wait for a multi-megabyte download.
        runBlocking {
            runCatching {
                TelegramDownloadCoordinator.withFile(fileId) {
                    client.send(TdApi.DownloadFile(fileId, 24, nextStart, nextLimit, true))
                }
            }
        }
    }

    override fun close() { raf?.close(); raf = null; super.close() }
}

private class TelegramMediaDataSource(
    private val client: TdClient,
    private val fileId: Int,
    private val fileSize: Long
) : MediaDataSource() {
    private var raf: RandomAccessFile? = null
    private var filePath: String? = null
    private var cachedStart = -1L
    private var cachedEndExclusive = -1L
    private val chunkSize = 512L * 1024L

    @Synchronized override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position < 0 || position >= fileSize) return -1
        if (size <= 0) return 0
        val wanted = minOf(size.toLong(), fileSize - position).toInt()
        ensureRange(position, maxOf(wanted.toLong(), chunkSize))
        val file = raf ?: throw IOException("Telegram preview file is unavailable")
        file.seek(position)
        val readable = minOf(
            wanted.toLong(),
            (cachedEndExclusive - position).coerceAtLeast(0L)
        ).toInt()
        if (readable <= 0) throw IOException("Telegram preview range is not ready")
        return file.read(buffer, offset, readable)
    }

    @Synchronized private fun ensureRange(position: Long, requested: Long) {
        val wantedEnd = minOf(fileSize, position + requested)
        if (cachedStart >= 0 && position >= cachedStart && wantedEnd <= cachedEndExclusive) return
        val limit = minOf(maxOf(requested, chunkSize), fileSize - position)
        val result = runBlocking {
            TelegramDownloadCoordinator.withFile(fileId) {
                client.send(TdApi.DownloadFile(fileId, 32, position, limit, true))
            }
        }
        val path = result.local.path
        if (path.isBlank()) throw IllegalStateException("Telegram file is not ready")
        if (path != filePath) { raf?.close(); filePath = path; raf = RandomAccessFile(path, "r") }
        cachedStart = result.local.downloadOffset
        cachedEndExclusive = if (result.local.isDownloadingCompleted) fileSize else result.local.downloadOffset + result.local.downloadedPrefixSize
    }

    override fun getSize(): Long = fileSize
    override fun close() { raf?.close(); raf = null }
}
