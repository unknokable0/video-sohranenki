package com.unknokable.videosohranenki

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.inspector.frame.FrameExtractor
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume

@OptIn(UnstableApi::class)
class TwitchBoundaryRefiner(
    context: Context,
    hlsUrl: String
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mediaItem = MediaItem.Builder()
        .setUri(hlsUrl)
        .setMimeType(MimeTypes.APPLICATION_M3U8)
        .build()

    private val worker = HandlerThread("sohr-twitch-frame-refiner").apply { start() }
    private val handler = Handler(worker.looper)

    private val callbackExecutor = Executor { command ->
        if (Looper.myLooper() === worker.looper) {
            command.run()
        } else {
            handler.post(command)
        }
    }

    private var extractor: FrameExtractor? = null
    private var closed = false

    suspend fun frameAt(positionMs: Long): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            if (closed) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            handler.post {
                if (closed || !continuation.isActive) return@post

                try {
                    val activeExtractor = extractor ?: FrameExtractor.Builder(
                        appContext,
                        mediaItem
                    ).build().also { extractor = it }

                    val future = activeExtractor.getFrame(positionMs.coerceAtLeast(0L))
                    future.addListener(
                        {
                            try {
                                val frame = future.get()
                                val source = frame.bitmap
                                val safeCopy = runCatching {
                                    source.copy(Bitmap.Config.ARGB_8888, false)
                                }.getOrNull()

                                if (continuation.isActive) {
                                    continuation.resume(safeCopy ?: source)
                                } else {
                                    safeCopy?.takeIf { it !== source && !it.isRecycled }?.recycle()
                                }
                            } catch (_: Throwable) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        },
                        callbackExecutor
                    )
                } catch (_: Throwable) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }

    override fun close() {
        if (closed) return
        closed = true
        handler.post {
            runCatching { extractor?.close() }
            extractor = null
            worker.quitSafely()
        }
    }
}
