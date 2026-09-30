package com.unknokable.videosohranenki

import io.github.tdlibandroid.ktx.TdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File

data class TelegramChannelSummary(
    val username: String,
    val chatId: Long,
    val title: String,
    val verified: Boolean,
    val subscriberCount: Int,
    val avatarPath: String?,
    val unreadCount: Int,
    val lastMessageId: Long,
    val lastMessageDate: Int,
    val lastMessagePreview: String,
    val lastMessagePreviewPath: String? = null,
    val lastMessageKind: String = "text"
)

data class TelegramReactionSnapshot(
    val emoji: String,
    val totalCount: Int,
    val chosen: Boolean
)

data class TelegramChannelPost(
    val message: TdApi.Message,
    val text: String,
    val kind: String,
    val previewPath: String?,
    val fileId: Int?,
    val fileSize: Long,
    val mimeType: String,
    val durationSeconds: Int,
    val viewCount: Int,
    val reactionCount: Int,
    val reactions: List<TelegramReactionSnapshot>,
    val formattedText: TdApi.FormattedText?,
    val mediaWidth: Int,
    val mediaHeight: Int,
    val mediaAlbumId: Long,
    val editDate: Int,
    val isPinned: Boolean
)

class TelegramChannelHub(
    private val client: TdClient
) {
    companion object {
        val USERNAMES = listOf(
            "t2x2_video",
            "beerloga_t2x2",
            "t2xtwitch",
            "wT2x2"
        )
    }

    private val summaries = linkedMapOf<Long, TelegramChannelSummary>()
    private val postsByChatId = linkedMapOf<Long, List<TelegramChannelPost>>()
    private val usernameByChatId = linkedMapOf<Long, String>()

    fun isTracked(chatId: Long): Boolean = usernameByChatId.containsKey(chatId)

    fun cachedSummary(chatId: Long): TelegramChannelSummary? = summaries[chatId]

    fun cachedPosts(chatId: Long): List<TelegramChannelPost> =
        postsByChatId[chatId].orEmpty()

    fun cachedChannels(): List<TelegramChannelSummary> =
        summaries.values.sortedWith(
            compareByDescending<TelegramChannelSummary> { it.lastMessageDate }
                .thenBy { USERNAMES.indexOf(it.username).let { index -> if (index < 0) Int.MAX_VALUE else index } }
        )

    suspend fun refreshChannels(): List<TelegramChannelSummary> = withContext(Dispatchers.IO) {
        val loaded = mutableListOf<TelegramChannelSummary>()
        for (username in USERNAMES) {
            runCatching { resolveChannel(username) }
                .getOrNull()
                ?.let(loaded::add)
        }
        loaded.sortedWith(
            compareByDescending<TelegramChannelSummary> { it.lastMessageDate }
                .thenBy { USERNAMES.indexOf(it.username).let { index -> if (index < 0) Int.MAX_VALUE else index } }
        )
    }

    suspend fun refreshChannel(chatId: Long): TelegramChannelSummary? = withContext(Dispatchers.IO) {
        val username = usernameByChatId[chatId] ?: return@withContext null
        runCatching { resolveChannel(username) }.getOrNull()
    }

    suspend fun warmRecentPosts(limit: Int = 18) = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(6, 30)
        val chatIds = summaries.keys.toList()
        for (chatId in chatIds) {
            if (postsByChatId[chatId].isNullOrEmpty()) {
                runCatching { loadPosts(chatId, limit = safeLimit) }
            }
        }
    }

    private suspend fun resolveChannel(username: String): TelegramChannelSummary {
        val chat = client.send(TdApi.SearchPublicChat(username))
        usernameByChatId[chat.id] = username
        runCatching { client.send(TdApi.OpenChat(chat.id)) }

        val supergroup = if (chat.type is TdApi.ChatTypeSupergroup) {
            val type = chat.type as TdApi.ChatTypeSupergroup
            runCatching {
                client.send(TdApi.GetSupergroup(type.supergroupId))
            }.getOrNull()
        } else {
            null
        }
        val verified = supergroup?.verificationStatus?.isVerified == true
        val subscriberCount = supergroup?.memberCount?.coerceAtLeast(0) ?: 0

        val avatarPath = downloadPreview(chat.photo?.small?.id)

        val last = chat.lastMessage ?: runCatching {
            client.send(TdApi.GetChatHistory(chat.id, 0L, 0, 1, false)).messages.firstOrNull()
        }.getOrNull()

        val summary = TelegramChannelSummary(
            username = username,
            chatId = chat.id,
            title = chat.title.ifBlank { "@$username" },
            verified = verified,
            subscriberCount = subscriberCount,
            avatarPath = avatarPath,
            unreadCount = chat.unreadCount.coerceAtLeast(0),
            lastMessageId = last?.id ?: 0L,
            lastMessageDate = last?.date ?: 0,
            lastMessagePreview = last?.let(::previewText) ?: "Нет сообщений",
            lastMessagePreviewPath = downloadPreview(last?.let(::listPreviewFileId)),
            lastMessageKind = last?.let(::messageKind) ?: "text"
        )
        summaries[chat.id] = summary
        return summary
    }

    suspend fun loadPosts(
        chatId: Long,
        fromMessageId: Long = 0L,
        limit: Int = 60
    ): List<TelegramChannelPost> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, 100)
        val history = client.send(
            TdApi.GetChatHistory(chatId, fromMessageId, 0, safeLimit, false)
        )
        val loaded = history.messages
            .mapNotNull { message ->
                runCatching { toPost(message) }.getOrNull()
            }
            .sortedWith(compareBy<TelegramChannelPost> { it.message.date }.thenBy { it.message.id })

        if (fromMessageId == 0L) {
            postsByChatId[chatId] = loaded
        } else if (loaded.isNotEmpty()) {
            postsByChatId[chatId] = (loaded + postsByChatId[chatId].orEmpty())
                .distinctBy { it.message.id }
                .sortedWith(compareBy<TelegramChannelPost> { it.message.date }.thenBy { it.message.id })
                .takeLast(160)
        }
        loaded
    }

    suspend fun absorbNewMessage(message: TdApi.Message): TelegramChannelPost? = withContext(Dispatchers.IO) {
        if (!isTracked(message.chatId)) return@withContext null

        val post = runCatching { toPost(message) }.getOrNull()
        if (post != null) {
            postsByChatId[message.chatId] = (postsByChatId[message.chatId].orEmpty() + post)
                .distinctBy { it.message.id }
                .sortedWith(compareBy<TelegramChannelPost> { it.message.date }.thenBy { it.message.id })
                .takeLast(160)
        }
        val old = summaries[message.chatId]
        if (old != null) {
            summaries[message.chatId] = old.copy(
                unreadCount = (old.unreadCount + 1).coerceAtLeast(1),
                lastMessageId = message.id,
                lastMessageDate = message.date,
                lastMessagePreview = previewText(message),
                lastMessagePreviewPath = downloadPreview(listPreviewFileId(message)),
                lastMessageKind = messageKind(message)
            )
        }
        post
    }

    suspend fun markViewed(chatId: Long, posts: List<TelegramChannelPost>) {
        val ids = posts.map { it.message.id }.filter { it > 0L }.toLongArray()
        if (ids.isEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching {
                client.send(TdApi.ViewMessages(chatId, ids, null, true))
            }
        }
        summaries[chatId]?.let { summaries[chatId] = it.copy(unreadCount = 0) }
    }

    private suspend fun toPost(message: TdApi.Message): TelegramChannelPost {
        val content = message.content
        var kind = "other"
        var text = previewText(message)
        var previewFileId: Int? = null
        var fileId: Int? = null
        var fileSize = 0L
        var mimeType = "application/octet-stream"
        var duration = 0
        var mediaWidth = 0
        var mediaHeight = 0

        when (content) {
            is TdApi.MessageText -> {
                kind = "text"
                text = content.text.text
            }
            is TdApi.MessagePhoto -> {
                kind = "photo"
                text = content.caption.text
                val photoSize = content.photo.sizes.maxByOrNull { it.width.toLong() * it.height.toLong() }
                mediaWidth = photoSize?.width ?: 0
                mediaHeight = photoSize?.height ?: 0
                previewFileId = photoSize?.photo?.id
                fileId = previewFileId
                fileSize = photoSize?.photo?.let { if (it.size > 0) it.size else it.expectedSize } ?: 0L
                mimeType = "image/jpeg"
            }
            is TdApi.MessageVideo -> {
                kind = "video"
                text = content.caption.text
                previewFileId = content.video.thumbnail?.file?.id
                fileId = content.video.video.id
                fileSize = content.video.video.let { if (it.size > 0) it.size else it.expectedSize }
                mimeType = content.video.mimeType.ifBlank { "video/mp4" }
                duration = content.video.duration
                mediaWidth = content.video.width
                mediaHeight = content.video.height
            }
            is TdApi.MessageVideoNote -> {
                kind = "video_note"
                text = "Видеосообщение"
                previewFileId = content.videoNote.thumbnail?.file?.id
                fileId = content.videoNote.video.id
                fileSize = content.videoNote.video.let { if (it.size > 0) it.size else it.expectedSize }
                mimeType = "video/mp4"
                duration = content.videoNote.duration
                mediaWidth = content.videoNote.length
                mediaHeight = content.videoNote.length
            }
            is TdApi.MessageVoiceNote -> {
                kind = "voice"
                text = content.caption.text
                fileId = content.voiceNote.voice.id
                fileSize = content.voiceNote.voice.let { if (it.size > 0) it.size else it.expectedSize }
                mimeType = content.voiceNote.mimeType.ifBlank { "audio/ogg" }
                duration = content.voiceNote.duration
            }
            is TdApi.MessageAnimation -> {
                kind = "animation"
                text = content.caption.text
                previewFileId = content.animation.thumbnail?.file?.id
                fileId = content.animation.animation.id
                fileSize = content.animation.animation.let { if (it.size > 0) it.size else it.expectedSize }
                mimeType = content.animation.mimeType.ifBlank { "video/mp4" }
                duration = content.animation.duration
                mediaWidth = content.animation.width
                mediaHeight = content.animation.height
            }
            is TdApi.MessageAudio -> {
                kind = "audio"
                text = content.caption.text.ifBlank {
                    listOf(content.audio.performer, content.audio.title)
                        .filter { it.isNotBlank() }
                        .joinToString(" — ")
                }
                fileId = content.audio.audio.id
                fileSize = content.audio.audio.let { if (it.size > 0) it.size else it.expectedSize }
                mimeType = content.audio.mimeType.ifBlank { "audio/mpeg" }
                duration = content.audio.duration
            }
            is TdApi.MessageDocument -> {
                kind = "document"
                text = content.caption.text.ifBlank { content.document.fileName }
                previewFileId = content.document.thumbnail?.file?.id
                fileId = content.document.document.id
                fileSize = content.document.document.let { if (it.size > 0) it.size else it.expectedSize }
                mimeType = content.document.mimeType.ifBlank { "application/octet-stream" }
            }
            is TdApi.MessageSticker -> {
                kind = "sticker"
                text = content.sticker.emoji.ifBlank { "Стикер" }
                previewFileId = content.sticker.thumbnail?.file?.id
            }
            is TdApi.MessagePoll -> {
                kind = "poll"
                text = content.poll.question.text
            }
        }

        val previewPath = downloadPreview(previewFileId)
        val interaction = message.interactionInfo
        val reactionItems = interaction?.reactions?.reactions
            ?.mapNotNull { reaction ->
                val emoji = when (val type = reaction.type) {
                    is TdApi.ReactionTypeEmoji -> type.emoji
                    else -> null
                } ?: return@mapNotNull null
                TelegramReactionSnapshot(
                    emoji = emoji,
                    totalCount = reaction.totalCount.coerceAtLeast(0),
                    chosen = reaction.isChosen
                )
            }
            .orEmpty()
        val reactions = reactionItems.sumOf { it.totalCount }

        return TelegramChannelPost(
            message = message,
            text = text.trim(),
            kind = kind,
            previewPath = previewPath,
            fileId = fileId,
            fileSize = fileSize,
            mimeType = mimeType,
            durationSeconds = duration,
            viewCount = interaction?.viewCount?.coerceAtLeast(0) ?: 0,
            reactionCount = reactions,
            reactions = reactionItems,
            formattedText = formattedTextOf(message),
            mediaWidth = mediaWidth,
            mediaHeight = mediaHeight,
            mediaAlbumId = message.mediaAlbumId,
            editDate = message.editDate,
            isPinned = message.isPinned
        )
    }

    private fun formattedTextOf(message: TdApi.Message): TdApi.FormattedText? =
        when (val content = message.content) {
            is TdApi.MessageText -> content.text
            is TdApi.MessagePhoto -> content.caption
            is TdApi.MessageVideo -> content.caption
            is TdApi.MessageVoiceNote -> content.caption
            is TdApi.MessageAnimation -> content.caption
            is TdApi.MessageAudio -> content.caption
            is TdApi.MessageDocument -> content.caption
            else -> null
        }

    private fun messageKind(message: TdApi.Message): String = when (message.content) {
        is TdApi.MessageText -> "text"
        is TdApi.MessagePhoto -> "photo"
        is TdApi.MessageVideo -> "video"
        is TdApi.MessageVideoNote -> "video_note"
        is TdApi.MessageVoiceNote -> "voice"
        is TdApi.MessageAnimation -> "animation"
        is TdApi.MessageAudio -> "audio"
        is TdApi.MessageDocument -> "document"
        is TdApi.MessageSticker -> "sticker"
        is TdApi.MessagePoll -> "poll"
        else -> "other"
    }

    private fun listPreviewFileId(message: TdApi.Message): Int? = when (val content = message.content) {
        is TdApi.MessagePhoto -> content.photo.sizes
            .filter { it.photo.id > 0 }
            .minByOrNull { size ->
                kotlin.math.abs(size.width - 160) + kotlin.math.abs(size.height - 160)
            }
            ?.photo
            ?.id
        is TdApi.MessageVideo -> content.video.thumbnail?.file?.id
        is TdApi.MessageVideoNote -> content.videoNote.thumbnail?.file?.id
        is TdApi.MessageAnimation -> content.animation.thumbnail?.file?.id
        is TdApi.MessageDocument -> content.document.thumbnail?.file?.id
        is TdApi.MessageSticker -> content.sticker.thumbnail?.file?.id
        else -> null
    }

    private fun previewText(message: TdApi.Message): String {
        val content = message.content
        val raw = when (content) {
            is TdApi.MessageText -> content.text.text
            is TdApi.MessagePhoto -> content.caption.text.ifBlank { "Фото" }
            is TdApi.MessageVideo -> content.caption.text.ifBlank { "Видео" }
            is TdApi.MessageVideoNote -> "Видеосообщение"
            is TdApi.MessageVoiceNote -> content.caption.text.ifBlank { "Голосовое сообщение" }
            is TdApi.MessageAnimation -> content.caption.text.ifBlank { "GIF / анимация" }
            is TdApi.MessageAudio -> content.caption.text.ifBlank { "Аудио" }
            is TdApi.MessageDocument -> content.caption.text.ifBlank {
                content.document.fileName.ifBlank { "Файл" }
            }
            is TdApi.MessageSticker -> content.sticker.emoji.ifBlank { "Стикер" }
            is TdApi.MessagePoll -> content.poll.question.text
            else -> "Сообщение"
        }
        return raw.replace('\n', ' ').replace(Regex("\\s+"), " ").trim().take(120)
    }

    private suspend fun downloadPreview(fileId: Int?): String? {
        if (fileId == null || fileId <= 0) return null
        return runCatching {
            val known = client.send(TdApi.GetFile(fileId))
            val existing = known.local.path
                .takeIf { known.local.isDownloadingCompleted && it.isNotBlank() && File(it).exists() }
            if (existing != null) return@runCatching existing

            val loaded = client.send(TdApi.DownloadFile(fileId, 6, 0, 0, true))
            loaded.local.path
                .takeIf { loaded.local.isDownloadingCompleted && it.isNotBlank() && File(it).exists() }
        }.getOrNull()
    }
}
