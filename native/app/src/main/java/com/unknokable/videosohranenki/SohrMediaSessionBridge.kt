package com.unknokable.videosohranenki

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaSession

class SohrMediaSessionBridge(
    context: Context,
    private val player: Player,
    private val item: VideoItem,
    private val mediaUrl: String
) {
    val mediaItem: MediaItem = MediaItem.Builder()
        .setMediaId(item.messageId.toString())
        .setUri(mediaUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(cleanTitle(item.title))
                .setArtist("T2x2 • SOHR")
                .setAlbumTitle("SOHR")
                .build()
        )
        .build()

    private val session = MediaSession.Builder(context, player)
        .setId("sohr_playback")
        .build()

    fun release() {
        session.release()
    }

    private fun cleanTitle(raw: String): String {
        val first = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        return first.ifBlank { "Видео" }
    }
}
