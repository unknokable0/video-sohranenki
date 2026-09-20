package com.unknokable.videosohranenki

import android.media.MediaDataSource
import fi.iki.elonen.NanoHTTPD
import io.github.tdlibandroid.ktx.TdClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.drinkless.tdlib.TdApi
import java.io.InputStream
import java.io.RandomAccessFile
import kotlin.math.min

class TelegramStreamServer(
    private val client: TdClient,
    port: Int = 8765
) : NanoHTTPD("127.0.0.1", port) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun url(item: VideoItem): String =
        "http://127.0.0.1:$listeningPort/video/${item.fileId}?size=${item.fileSize}&mime=${item.mimeType}"

    fun mediaDataSource(item: VideoItem): MediaDataSource =
        TelegramMediaDataSource(
            client = client,
            fileId = item.fileId,
            fileSize = item.fileSize
        )

    fun prefetch(item: VideoItem) {
        if (item.fileSize <= 0L) return
        scope.launch {
            runCatching {
                val limit = minOf(12L * 1024L * 1024L, item.fileSize)
                client.send(TdApi.DownloadFile(item.fileId, 30, 0, limit, false))
            }
        }
    }

    override fun stop() {
        scope.cancel()
        super.stop()
    }

    override fun serve(session: IHTTPSession): Response {
        if (!session.uri.startsWith("/video/")) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }

        val fileId = session.uri.substringAfterLast("/").toIntOrNull()
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Bad file id")
        val size = session.parameters["size"]?.firstOrNull()?.toLongOrNull() ?: 0L
        val mime = session.parameters["mime"]?.firstOrNull()?.ifBlank { "video/mp4" } ?: "video/mp4"

        if (size <= 0L) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Unknown file size")
        }

        val range = session.headers["range"]
        var start = 0L
        var end = size - 1
        var status = Response.Status.OK

        if (!range.isNullOrBlank() && range.startsWith("bytes=")) {
            val value = range.removePrefix("bytes=").substringBefore(",")
            val parts = value.split("-", limit = 2)
            start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            end = parts.getOrNull(1)?.toLongOrNull() ?: (size - 1)
            end = min(end, size - 1)
            if (start > end || start >= size) {
                return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "")
                    .also { it.addHeader("Content-Range", "bytes */$size") }
            }
            status = Response.Status.PARTIAL_CONTENT
        }

        val length = end - start + 1
        val stream = TelegramFileInputStream(client, fileId, start, end)
        val response = newFixedLengthResponse(status, mime, stream, length)
        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("Cache-Control", "private, max-age=3600")
        response.addHeader("Content-Length", length.toString())
        if (status == Response.Status.PARTIAL_CONTENT) {
            response.addHeader("Content-Range", "bytes $start-$end/$size")
        }
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
    private var prefetchedStart = -1L
    private var prefetchedLimit = 0L

    private val firstWindow = 2L * 1024L * 1024L
    private val steadyWindow = 12L * 1024L * 1024L
    private val prefetchThreshold = 4L * 1024L * 1024L

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) == 1) one[0].toInt() and 0xff else -1
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (position > endInclusive) return -1
        val wanted = min(length.toLong(), endInclusive - position + 1).toInt()
        if (wanted <= 0) return -1

        ensureRange(position, wanted.toLong())

        val file = raf ?: return -1
        file.seek(position)
        val maxReadable = minOf(
            wanted.toLong(),
            (bufferedEndExclusive - position).coerceAtLeast(0L)
        ).toInt()
        if (maxReadable <= 0) return -1

        val count = file.read(buffer, offset, maxReadable)
        if (count > 0) {
            position += count
            maybePrefetchNext()
        }
        return count
    }

    private fun ensureRange(offset: Long, requested: Long) {
        val requestedEnd = min(endInclusive + 1, offset + requested)
        if (offset >= bufferedStart && requestedEnd <= bufferedEndExclusive) return

        val usePrefetch = offset == prefetchedStart && prefetchedLimit > 0L
        val windowStart = if (usePrefetch) prefetchedStart else offset
        val windowLimit = if (usePrefetch) {
            prefetchedLimit
        } else {
            val target = if (offset < firstWindow) firstWindow else steadyWindow
            min(target, endInclusive - offset + 1)
        }

        val result = runBlocking {
            client.send(TdApi.DownloadFile(fileId, 32, windowStart, windowLimit, true))
        }
        applyLocalRange(result.local)

        if (bufferedEndExclusive < requestedEnd) {
            val retryLimit = minOf(maxOf(requested, firstWindow), endInclusive - offset + 1)
            val retry = runBlocking {
                    client.send(TdApi.DownloadFile(fileId, 32, offset, retryLimit, true))
            }
            applyLocalRange(retry.local)
        }

        if (usePrefetch) {
            prefetchedStart = -1L
            prefetchedLimit = 0L
        }
    }

    private fun applyLocalRange(local: TdApi.LocalFile) {
        val path = local.path
        if (path.isBlank()) throw IllegalStateException("TDLib returned no local path")

        if (path != filePath) {
            raf?.close()
            filePath = path
            raf = RandomAccessFile(path, "r")
        }

        bufferedStart = local.downloadOffset
        bufferedEndExclusive = if (local.isDownloadingCompleted) {
            endInclusive + 1
        } else {
            local.downloadOffset + local.downloadedPrefixSize
        }
    }

    private fun maybePrefetchNext() {
        if (bufferedEndExclusive <= 0L || bufferedEndExclusive > endInclusive) return
        if (bufferedEndExclusive - position > prefetchThreshold) return
        if (prefetchedStart == bufferedEndExclusive) return

        val nextStart = bufferedEndExclusive
        val nextLimit = min(steadyWindow, endInclusive - nextStart + 1)
        if (nextLimit <= 0L) return

        prefetchedStart = nextStart
        prefetchedLimit = nextLimit

        runBlocking {
            runCatching {
                client.send(TdApi.DownloadFile(fileId, 24, nextStart, nextLimit, false))
            }
        }
    }

    override fun close() {
        raf?.close()
        raf = null
        super.close()
    }
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
    private val chunkSize = 2L * 1024L * 1024L

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position < 0 || position >= fileSize) return -1
        if (size <= 0) return 0

        val wanted = minOf(size.toLong(), fileSize - position).toInt()
        ensureRange(position, maxOf(wanted.toLong(), chunkSize))

        val file = raf ?: return -1
        file.seek(position)
        return file.read(buffer, offset, wanted)
    }

    @Synchronized
    private fun ensureRange(position: Long, requested: Long) {
        val wantedEnd = minOf(fileSize, position + requested)
        if (cachedStart >= 0 && position >= cachedStart && wantedEnd <= cachedEndExclusive) {
            return
        }

        val limit = minOf(maxOf(requested, chunkSize), fileSize - position)
        val result = runBlocking {
            client.send(TdApi.DownloadFile(fileId, 24, position, limit, true))
        }

        val path = result.local.path
        if (path.isBlank()) {
            throw IllegalStateException("Telegram file is not ready")
        }

        if (path != filePath) {
            raf?.close()
            filePath = path
            raf = RandomAccessFile(path, "r")
        }

        cachedStart = result.local.downloadOffset
        cachedEndExclusive = if (result.local.isDownloadingCompleted) {
            fileSize
        } else {
            result.local.downloadOffset + result.local.downloadedPrefixSize
        }
    }

    override fun getSize(): Long = fileSize

    override fun close() {
        raf?.close()
        raf = null
    }
}
