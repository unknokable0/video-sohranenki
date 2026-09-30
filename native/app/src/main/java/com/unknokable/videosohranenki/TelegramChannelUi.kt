package com.unknokable.videosohranenki

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.text.util.Linkify
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import coil.load
import coil.transform.CircleCropTransformation
import org.drinkless.tdlib.TdApi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern

data class TelegramChannelRender(
    val root: View,
    val messagesHost: LinearLayout,
    val scroll: NestedScrollView
)

object TelegramChannelUi {

    fun buildDirectory(
        activity: Activity,
        settings: AppSettings,
        channels: List<TelegramChannelSummary>,
        loading: Boolean,
        onOpen: (TelegramChannelSummary, View) -> Unit
    ): View {
        val palette = settings.palette()
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(activity, 4), 0, dp(activity, 14))
            setBackgroundColor(Color.TRANSPARENT)
        }

        if (loading && channels.isEmpty()) {
            repeat(4) { index ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(activity, 14), dp(activity, 9), dp(activity, 12), dp(activity, 9))
                    alpha = 0.58f + index * 0.05f
                }
                row.addView(
                    View(activity).apply { background = oval(palette.surfaceAlt) },
                    LinearLayout.LayoutParams(dp(activity, 60), dp(activity, 60))
                )
                row.addView(
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(activity, 12), 0, 0, 0)
                        addView(
                            View(activity).apply { background = rounded(palette.surfaceAlt, 6) },
                            LinearLayout.LayoutParams(dp(activity, 150), dp(activity, 15))
                        )
                        addView(
                            View(activity).apply { background = rounded(palette.surfaceAlt, 6) },
                            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 12)).apply {
                                topMargin = dp(activity, 10)
                            }
                        )
                    },
                    LinearLayout.LayoutParams(0, dp(activity, 60), 1f)
                )
                root.addView(
                    row,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 78))
                )
            }
            return root
        }

        if (channels.isEmpty()) {
            root.addView(TextView(activity).apply {
                text = "Не удалось получить каналы. Проверьте подключение к Telegram."
                textSize = 13.5f
                gravity = Gravity.CENTER
                setTextColor(palette.muted)
                setPadding(dp(activity, 18), dp(activity, 36), dp(activity, 18), dp(activity, 36))
            })
            return root
        }

        channels.forEachIndexed { index, channel ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 12), dp(activity, 8))
                background = null
                isClickable = true
                isFocusable = true
            }

            val avatar = ImageView(activity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = oval(palette.surfaceAlt)
                clipToOutline = true
                if (!channel.avatarPath.isNullOrBlank()) {
                    load(channel.avatarPath) {
                        transformations(CircleCropTransformation())
                        crossfade(true)
                    }
                }
            }
            row.addView(
                avatar,
                LinearLayout.LayoutParams(dp(activity, 60), dp(activity, 60))
            )

            val center = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(activity, 12), 0, dp(activity, 8), 0)
            }

            val nameLine = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val channelTitle = TextView(activity).apply {
                text = channel.title
                textSize = 16.2f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.text)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                includeFontPadding = false
            }
            nameLine.addView(
                channelTitle,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            if (channel.verified) {
                val badge = verifiedBadge(activity, palette.accent, 18)
                nameLine.addView(
                    badge,
                    LinearLayout.LayoutParams(dp(activity, 18), dp(activity, 18)).apply {
                        marginStart = dp(activity, 6)
                    }
                )
                nameLine.post {
                    val available = (nameLine.width - dp(activity, 24)).coerceAtLeast(dp(activity, 72))
                    if (channelTitle.maxWidth != available) channelTitle.maxWidth = available
                }
            } else {
                nameLine.post {
                    val available = nameLine.width.coerceAtLeast(dp(activity, 72))
                    if (channelTitle.maxWidth != available) channelTitle.maxWidth = available
                }
            }
            center.addView(nameLine)

            val previewLine = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(activity, 6), 0, 0)
            }
            if (!channel.lastMessagePreviewPath.isNullOrBlank()) {
                previewLine.addView(
                    ImageView(activity).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        background = rounded(palette.surfaceAlt, if (channel.lastMessageKind == "video_note") 13 else 6)
                        clipToOutline = true
                        load(channel.lastMessagePreviewPath) { crossfade(true) }
                    },
                    LinearLayout.LayoutParams(dp(activity, 27), dp(activity, 27)).apply {
                        marginEnd = dp(activity, 7)
                    }
                )
            }
            previewLine.addView(
                TextView(activity).apply {
                    text = channel.lastMessagePreview
                    textSize = 13.4f
                    setTextColor(palette.muted)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            center.addView(previewLine)
            row.addView(
                center,
                LinearLayout.LayoutParams(0, dp(activity, 60), 1f)
            )

            val end = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
                setPadding(0, dp(activity, 2), 0, dp(activity, 2))
            }
            end.addView(
                TextView(activity).apply {
                    text = channelTime(channel.lastMessageDate)
                    textSize = 11.4f
                    setTextColor(if (channel.unreadCount > 0) palette.accent else palette.muted)
                    includeFontPadding = false
                }
            )
            if (channel.unreadCount > 0) {
                end.addView(
                    TextView(activity).apply {
                        text = if (channel.unreadCount > 99) "99+" else channel.unreadCount.toString()
                        textSize = 10.5f
                        gravity = Gravity.CENTER
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(Color.WHITE)
                        background = rounded(palette.accent, 11)
                        includeFontPadding = false
                        setPadding(dp(activity, 7), 0, dp(activity, 7), 0)
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 22)).apply {
                        topMargin = dp(activity, 11)
                    }
                )
            }
            row.addView(
                end,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 60))
            )

            row.setOnClickListener {
                SohrMotion.press(row, settings.animations)
                onOpen(channel, row)
            }

            if (settings.animations) {
                row.alpha = 0f
                row.translationY = dp(activity, 6).toFloat()
                row.postDelayed({
                    if (!row.isAttachedToWindow) return@postDelayed
                    row.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(SohrMotion.NORMAL)
                        .setInterpolator(SohrMotion.smooth())
                        .start()
                }, (index * 28L).coerceAtMost(110L))
            }

            root.addView(
                row,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 78))
            )

            if (index != channels.lastIndex) {
                root.addView(
                    View(activity).apply {
                        setBackgroundColor(palette.surfaceAlt)
                        alpha = 0.52f
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(activity, 1)
                    ).apply {
                        marginStart = dp(activity, 86)
                        marginEnd = dp(activity, 12)
                    }
                )
            }
        }

        return root
    }

    fun buildChannel(
        activity: Activity,
        settings: AppSettings,
        channel: TelegramChannelSummary,
        posts: List<TelegramChannelPost>,
        loading: Boolean,
        canLoadOlder: Boolean,
        unreadCountAtOpen: Int = 0,
        onBack: () -> Unit,
        onLoadOlder: () -> Unit,
        onVideo: (TelegramChannelPost, View) -> Unit,
        onPhoto: (TelegramChannelPost, View) -> Unit,
        onVoice: (TelegramChannelPost, View) -> Unit
    ): TelegramChannelRender {
        val palette = settings.palette()
        val page = FrameLayout(activity).apply {
            setBackgroundColor(palette.background)
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 14), dp(activity, 8))
            setBackgroundColor(palette.background)
        }
        val back = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_back)
            imageTintList = ColorStateList.valueOf(palette.text)
            background = rounded(palette.surfaceAlt, 20)
            setPadding(dp(activity, 11), dp(activity, 11), dp(activity, 11), dp(activity, 11))
            contentDescription = "Назад"
            setOnClickListener {
                SohrMotion.press(this, settings.animations)
                onBack()
            }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)).apply {
            marginEnd = dp(activity, 10)
        })

        val avatar = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = oval(palette.surfaceAlt)
            clipToOutline = true
            if (!channel.avatarPath.isNullOrBlank()) {
                load(channel.avatarPath) {
                    transformations(CircleCropTransformation())
                    crossfade(true)
                }
            }
        }
        header.addView(avatar, LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)).apply {
            marginEnd = dp(activity, 10)
        })

        val titleBlock = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val titleLine = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleLine.addView(TextView(activity).apply {
            text = channel.title
            textSize = 15.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (channel.verified) {
            titleLine.addView(
                verifiedBadge(activity, palette.accent, 17),
                LinearLayout.LayoutParams(dp(activity, 17), dp(activity, 17)).apply {
                    marginStart = dp(activity, 5)
                }
            )
        }
        titleBlock.addView(titleLine)
        titleBlock.addView(TextView(activity).apply {
            text = if (channel.subscriberCount > 0) {
                subscriberLabel(channel.subscriberCount)
            } else {
                "@${channel.username}"
            }
            textSize = 11f
            setTextColor(palette.muted)
            includeFontPadding = false
            setPadding(0, dp(activity, 3), 0, 0)
        })
        header.addView(titleBlock, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 60)))

        val scroll = NestedScrollView(activity).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(palette.background)
        }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 20))
        }
        scroll.addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        if (canLoadOlder && posts.isNotEmpty()) {
            body.addView(TextView(activity).apply {
                text = "Показать более ранние сообщения"
                textSize = 12.5f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.accent)
                background = rounded(palette.surfaceAlt, 16)
                setOnClickListener {
                    SohrMotion.press(this, settings.animations)
                    onLoadOlder()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 42)).apply {
                bottomMargin = dp(activity, 10)
            })
        }

        val messagesHost = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        body.addView(messagesHost)

        if (loading && posts.isEmpty()) {
            repeat(4) {
                messagesHost.addView(
                    View(activity).apply {
                        background = rounded(palette.surface, 18)
                        alpha = 0.68f
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 108)).apply {
                        bottomMargin = dp(activity, 9)
                    }
                )
            }
        } else if (posts.isEmpty()) {
            messagesHost.addView(TextView(activity).apply {
                text = "Сообщений пока нет"
                textSize = 13.5f
                gravity = Gravity.CENTER
                setTextColor(palette.muted)
                setPadding(0, dp(activity, 42), 0, dp(activity, 42))
            })
        } else {
            var lastDayKey = ""
            val unreadStartIndex =
                if (unreadCountAtOpen > 0) {
                    (posts.size - unreadCountAtOpen).coerceIn(0, posts.size)
                } else {
                    -1
                }

            var index = 0
            while (index < posts.size) {
                val first = posts[index]
                val albumId = first.mediaAlbumId
                var endExclusive = index + 1
                if (albumId != 0L && first.kind in setOf("photo", "video")) {
                    while (
                        endExclusive < posts.size &&
                        posts[endExclusive].mediaAlbumId == albumId &&
                        posts[endExclusive].kind in setOf("photo", "video")
                    ) {
                        endExclusive++
                    }
                }
                val group = posts.subList(index, endExclusive)
                val currentDayKey = dayKey(first.message.date)

                if (currentDayKey != lastDayKey) {
                    messagesHost.addView(
                        TextView(activity).apply {
                            text = dayLabel(first.message.date)
                            textSize = 12f
                            gravity = Gravity.CENTER
                            setTypeface(typeface, Typeface.BOLD)
                            setTextColor(palette.text)
                            background = rounded(palette.surfaceAlt, 13)
                            includeFontPadding = false
                            setPadding(dp(activity, 12), dp(activity, 5), dp(activity, 12), dp(activity, 5))
                        },
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            gravity = Gravity.CENTER_HORIZONTAL
                            topMargin = if (index == 0) 0 else dp(activity, 5)
                            bottomMargin = dp(activity, 8)
                        }
                    )
                    lastDayKey = currentDayKey
                }

                if (
                    unreadStartIndex >= 0 &&
                    unreadStartIndex in index until endExclusive
                ) {
                    messagesHost.addView(
                        newMessagesDivider(activity, palette.accent),
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = dp(activity, 2)
                            bottomMargin = dp(activity, 8)
                        }
                    )
                }

                val card =
                    if (group.size > 1 && albumId != 0L) {
                        buildAlbumCard(activity, settings, group, onVideo, onPhoto)
                    } else {
                        buildPostCard(activity, settings, first, onVideo, onPhoto, onVoice)
                    }

                if (settings.animations) {
                    card.alpha = 0f
                    card.translationY = dp(activity, 7).toFloat()
                    card.postDelayed({
                        if (!card.isAttachedToWindow) return@postDelayed
                        card.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(SohrMotion.NORMAL)
                            .setInterpolator(SohrMotion.smooth())
                            .start()
                    }, (index * 22L).coerceAtMost(140L))
                }
                messagesHost.addView(
                    card,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(activity, 9)
                    }
                )
                index = endExclusive
            }
        }

        content.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        page.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val jumpToLatest = TextView(activity).apply {
            text = "↓"
            textSize = 22f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = oval(palette.accent)
            visibility = View.GONE
            alpha = 0f
            scaleX = 0.84f
            scaleY = 0.84f
            elevation = dp(activity, 8).toFloat()
            contentDescription = "К последним сообщениям"
            setOnClickListener {
                SohrMotion.press(this, settings.animations)
                scroll.smoothScrollTo(0, scroll.getChildAt(0)?.height ?: 0)
            }
        }
        page.addView(
            jumpToLatest,
            FrameLayout.LayoutParams(dp(activity, 48), dp(activity, 48), Gravity.END or Gravity.BOTTOM).apply {
                marginEnd = dp(activity, 16)
                bottomMargin = dp(activity, 18)
            }
        )

        scroll.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
                val child = scroll.getChildAt(0)
                val remaining = ((child?.height ?: 0) - (scrollY + scroll.height)).coerceAtLeast(0)
                val shouldShow = remaining > dp(activity, 420)
                if (shouldShow && jumpToLatest.visibility != View.VISIBLE) {
                    jumpToLatest.visibility = View.VISIBLE
                    if (settings.animations) {
                        jumpToLatest.animate().cancel()
                        jumpToLatest.alpha = 0f
                        jumpToLatest.scaleX = 0.82f
                        jumpToLatest.scaleY = 0.82f
                        jumpToLatest.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(SohrMotion.NORMAL)
                            .setInterpolator(SohrMotion.smooth())
                            .start()
                    } else {
                        jumpToLatest.alpha = 1f
                        jumpToLatest.scaleX = 1f
                        jumpToLatest.scaleY = 1f
                    }
                } else if (!shouldShow && jumpToLatest.visibility == View.VISIBLE) {
                    if (settings.animations) {
                        jumpToLatest.animate().cancel()
                        jumpToLatest.animate()
                            .alpha(0f)
                            .scaleX(0.82f)
                            .scaleY(0.82f)
                            .setDuration(SohrMotion.FAST)
                            .withEndAction { jumpToLatest.visibility = View.GONE }
                            .start()
                    } else {
                        jumpToLatest.visibility = View.GONE
                    }
                }
            }
        )

        return TelegramChannelRender(page, messagesHost, scroll)
    }

    private fun buildAlbumCard(
        activity: Activity,
        settings: AppSettings,
        posts: List<TelegramChannelPost>,
        onVideo: (TelegramChannelPost, View) -> Unit,
        onPhoto: (TelegramChannelPost, View) -> Unit
    ): View {
        val palette = settings.palette()
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 10))
            background = rounded(palette.surface, 18)
        }

        if (posts.any { it.isPinned }) {
            card.addView(TextView(activity).apply {
                text = "Закреплено"
                textSize = 10.8f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.accent)
                includeFontPadding = false
                setPadding(dp(activity, 4), 0, 0, dp(activity, 7))
            })
        }

        var i = 0
        while (i < posts.size) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            repeat(2) { column ->
                val item = posts.getOrNull(i + column)
                if (item == null) {
                    row.addView(
                        View(activity),
                        LinearLayout.LayoutParams(0, dp(activity, 156), 1f).apply {
                            if (column == 0) marginEnd = dp(activity, 3)
                        }
                    )
                } else {
                    val media = FrameLayout(activity).apply {
                        background = rounded(palette.surfaceAlt, 12)
                        clipToOutline = true
                        isClickable = true
                        isFocusable = true
                    }
                    media.addView(
                        ImageView(activity).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            if (!item.previewPath.isNullOrBlank()) {
                                load(item.previewPath) { crossfade(true) }
                            }
                        },
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    if (item.kind == "video") {
                        media.addView(TextView(activity).apply {
                            text = "▶"
                            textSize = 21f
                            gravity = Gravity.CENTER
                            setTextColor(Color.WHITE)
                            background = oval(Color.argb(150, 0, 0, 0))
                        }, FrameLayout.LayoutParams(dp(activity, 44), dp(activity, 44), Gravity.CENTER))
                        if (item.durationSeconds > 0) {
                            media.addView(TextView(activity).apply {
                                text = formatDuration(item.durationSeconds)
                                textSize = 10f
                                gravity = Gravity.CENTER
                                setTextColor(Color.WHITE)
                                background = rounded(Color.argb(160, 0, 0, 0), 8)
                                setPadding(dp(activity, 6), 0, dp(activity, 6), 0)
                            }, FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                dp(activity, 22),
                                Gravity.END or Gravity.BOTTOM
                            ).apply {
                                marginEnd = dp(activity, 7)
                                bottomMargin = dp(activity, 7)
                            })
                        }
                    }
                    media.setOnClickListener {
                        SohrMotion.press(media, settings.animations)
                        if (item.kind == "photo") onPhoto(item, media) else onVideo(item, media)
                    }
                    row.addView(
                        media,
                        LinearLayout.LayoutParams(0, dp(activity, 156), 1f).apply {
                            if (column == 0) marginEnd = dp(activity, 3)
                        }
                    )
                }
            }
            card.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(activity, 156)
                ).apply {
                    if (i + 2 < posts.size) bottomMargin = dp(activity, 3)
                }
            )
            i += 2
        }

        val captionPost = posts.firstOrNull { it.text.isNotBlank() }
        val caption = captionPost?.text.orEmpty()
        if (caption.isNotBlank()) {
            card.addView(TextView(activity).apply {
                textSize = 14.4f
                setTextColor(palette.text)
                setLineSpacing(0f, 1.08f)
                setTextIsSelectable(false)
                includeFontPadding = false
                setPadding(dp(activity, 4), dp(activity, 9), dp(activity, 4), 0)
                applyTelegramFormattedText(
                    this,
                    captionPost?.formattedText,
                    caption,
                    palette.accent
                )
                setOnLongClickListener {
                    copyText(activity, caption)
                    true
                }
            })
        }

        val reactionCount = posts.sumOf { it.reactionCount.coerceAtLeast(0) }
        if (reactionCount > 0) {
            card.addView(TextView(activity).apply {
                text = "♥ " + compact(reactionCount)
                textSize = 12f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.text)
                background = rounded(palette.surfaceAlt, 14)
                includeFontPadding = false
                setPadding(dp(activity, 10), dp(activity, 5), dp(activity, 10), dp(activity, 5))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(activity, 9)
                marginStart = dp(activity, 4)
            })
        }

        val last = posts.last()
        val footer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 4), dp(activity, 9), dp(activity, 4), 0)
        }
        val views = posts.maxOfOrNull { it.viewCount } ?: 0
        footer.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (views > 0) {
                addView(ImageView(activity).apply {
                    setImageResource(R.drawable.ic_visibility)
                    imageTintList = ColorStateList.valueOf(palette.muted)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }, LinearLayout.LayoutParams(dp(activity, 16), dp(activity, 16)).apply {
                    marginEnd = dp(activity, 4)
                })
                addView(TextView(activity).apply {
                    text = compact(views)
                    textSize = 10.8f
                    setTextColor(palette.muted)
                    includeFontPadding = false
                })
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        footer.addView(TextView(activity).apply {
            text = buildString {
                if (posts.any { it.editDate > 0 }) append("изменено • ")
                append(messageTime(last.message.date))
            }
            textSize = 10.8f
            setTextColor(palette.muted)
            includeFontPadding = false
        })
        card.addView(footer)
        return card
    }

    fun buildPostCard(
        activity: Activity,
        settings: AppSettings,
        post: TelegramChannelPost,
        onVideo: (TelegramChannelPost, View) -> Unit,
        onPhoto: (TelegramChannelPost, View) -> Unit,
        onVoice: (TelegramChannelPost, View) -> Unit
    ): View {
        val palette = settings.palette()
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 12), dp(activity, 11), dp(activity, 12), dp(activity, 10))
            background = rounded(palette.surface, 18)
        }

        val time = messageTime(post.message.date)

        if (post.isPinned) {
            card.addView(TextView(activity).apply {
                text = "Закреплено"
                textSize = 10.8f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.accent)
                includeFontPadding = false
                setPadding(0, 0, 0, dp(activity, 7))
            })
        }

        val mediaTitle = when (post.kind) {
            "video" -> "Видео"
            "video_note" -> "Видеосообщение"
            "voice" -> "Голосовое сообщение"
            "animation" -> "GIF / анимация"
            "audio" -> "Аудио"
            "document" -> "Файл"
            "sticker" -> "Стикер"
            "poll" -> "Опрос"
            else -> null
        }

        if (mediaTitle != null && post.kind != "photo") {
            card.addView(TextView(activity).apply {
                text = mediaTitle
                textSize = 10.8f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.accent)
                includeFontPadding = false
                setPadding(0, 0, 0, dp(activity, 7))
            })
        }

        when (post.kind) {
            "photo", "video", "animation", "video_note" -> {
                val isCircle = post.kind == "video_note"
                val preview = FrameLayout(activity).apply {
                    background = if (isCircle) oval(palette.surfaceAlt) else rounded(palette.surfaceAlt, 15)
                    clipToOutline = true
                    isClickable = true
                    isFocusable = true
                }
                val image = ImageView(activity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    if (!post.previewPath.isNullOrBlank()) {
                        load(post.previewPath) { crossfade(true) }
                    }
                }
                preview.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                if (post.kind != "photo") {
                    preview.addView(TextView(activity).apply {
                        text = "▶"
                        textSize = if (isCircle) 25f else 30f
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        background = oval(Color.argb(150, 0, 0, 0))
                    }, FrameLayout.LayoutParams(dp(activity, 52), dp(activity, 52), Gravity.CENTER))
                    if (post.durationSeconds > 0) {
                        preview.addView(TextView(activity).apply {
                            text = formatDuration(post.durationSeconds)
                            textSize = 10.5f
                            gravity = Gravity.CENTER
                            setTextColor(Color.WHITE)
                            background = rounded(Color.argb(155, 0, 0, 0), 9)
                            setPadding(dp(activity, 6), 0, dp(activity, 6), 0)
                        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 24), Gravity.END or Gravity.BOTTOM).apply {
                            marginEnd = dp(activity, 8)
                            bottomMargin = dp(activity, 8)
                        })
                    }
                }
                preview.setOnClickListener {
                    SohrMotion.press(preview, settings.animations)
                    if (post.kind == "photo") onPhoto(post, preview) else onVideo(post, preview)
                }
                val size = if (isCircle) dp(activity, 188) else ViewGroup.LayoutParams.MATCH_PARENT
                card.addView(preview, LinearLayout.LayoutParams(size, if (isCircle) dp(activity, 188) else dp(activity, 268)).apply {
                    gravity = if (isCircle) Gravity.CENTER_HORIZONTAL else Gravity.NO_GRAVITY
                    bottomMargin = if (post.text.isBlank()) 0 else dp(activity, 9)
                })
            }
            "voice", "audio" -> {
                val voice = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8))
                    background = rounded(palette.surfaceAlt, 15)
                    isClickable = true
                    isFocusable = true
                }
                voice.addView(TextView(activity).apply {
                    text = "▶"
                    textSize = 17f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    background = oval(palette.accent)
                }, LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 40)))
                voice.addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(activity, 10), 0, 0, 0)
                    addView(TextView(activity).apply {
                        text = if (post.kind == "voice") "Голосовое" else "Аудио"
                        textSize = 13.2f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(palette.text)
                        includeFontPadding = false
                    })
                    addView(TextView(activity).apply {
                        text = formatDuration(post.durationSeconds)
                        textSize = 10.8f
                        setTextColor(palette.muted)
                        includeFontPadding = false
                        setPadding(0, dp(activity, 3), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                voice.setOnClickListener {
                    SohrMotion.press(voice, settings.animations)
                    onVoice(post, voice)
                }
                card.addView(voice, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 58)).apply {
                    bottomMargin = if (post.text.isBlank()) 0 else dp(activity, 9)
                })
            }
        }

        val showText = post.text.isNotBlank() && !(post.kind == "video_note" && post.text == "Видеосообщение")
        if (showText) {
            card.addView(TextView(activity).apply {
                textSize = 14.4f
                setTextColor(palette.text)
                setLinkTextColor(palette.accent)
                setLineSpacing(0f, 1.08f)
                setTextIsSelectable(false)
                includeFontPadding = false
                applyTelegramFormattedText(
                    this,
                    post.formattedText,
                    post.text,
                    palette.accent
                )
                setOnLongClickListener {
                    copyText(activity, post.text)
                    true
                }
            })
        }

        if (post.reactionCount > 0) {
            card.addView(TextView(activity).apply {
                text = "♥ " + compact(post.reactionCount)
                textSize = 12f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.text)
                background = rounded(palette.surfaceAlt, 14)
                includeFontPadding = false
                setPadding(dp(activity, 10), dp(activity, 5), dp(activity, 10), dp(activity, 5))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(activity, 9)
            })
        }

        val footer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(activity, 9), 0, 0)
        }
        val viewsLine = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (post.viewCount > 0) {
            viewsLine.addView(ImageView(activity).apply {
                setImageResource(R.drawable.ic_visibility)
                imageTintList = ColorStateList.valueOf(palette.muted)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }, LinearLayout.LayoutParams(dp(activity, 16), dp(activity, 16)).apply {
                marginEnd = dp(activity, 4)
            })
            viewsLine.addView(TextView(activity).apply {
                text = compact(post.viewCount)
                textSize = 10.8f
                setTextColor(palette.muted)
                includeFontPadding = false
            })
        }
        footer.addView(viewsLine, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        footer.addView(TextView(activity).apply {
            text = if (post.editDate > 0) "изменено • $time" else time
            textSize = 10.8f
            setTextColor(palette.muted)
            includeFontPadding = false
        })
        card.addView(footer)

        return card
    }

    private fun newMessagesDivider(activity: Activity, accent: Int): View =
        TextView(activity).apply {
            text = "Новые сообщения"
            textSize = 11.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(accent)
            includeFontPadding = false
            setPadding(0, dp(activity, 5), 0, dp(activity, 5))
            background = rounded(Color.argb(24, Color.red(accent), Color.green(accent), Color.blue(accent)), 12)
        }

    private fun copyText(activity: Activity, value: String) {
        if (value.isBlank()) return
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SOHR", value))
        Toast.makeText(activity, "Текст скопирован", Toast.LENGTH_SHORT).show()
    }

    private fun verifiedBadge(activity: Activity, accent: Int, sizeDp: Int): ImageView =
        ImageView(activity).apply {
            setImageResource(R.drawable.ic_check)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = oval(accent)
            val padding = dp(activity, if (sizeDp >= 18) 4 else 3)
            setPadding(padding, padding, padding, padding)
            contentDescription = "Подтверждённый канал"
        }

    private fun applyTelegramFormattedText(
        textView: TextView,
        formatted: TdApi.FormattedText?,
        fallback: String,
        accent: Int
    ) {
        val raw = formatted?.text?.takeIf { it.isNotBlank() } ?: fallback
        textView.text = raw
        textView.setTextIsSelectable(false)
        enableTelegramLinks(textView, accent)

        val spannable = SpannableString(textView.text)
        formatted?.entities.orEmpty().forEach { entity ->
            val start = entity.offset.coerceIn(0, raw.length)
            val end = (entity.offset + entity.length).coerceIn(start, raw.length)
            if (start >= end) return@forEach

            val span: Any? = when (val type = entity.type) {
                is TdApi.TextEntityTypeTextUrl -> URLSpan(type.url)
                is TdApi.TextEntityTypeUrl -> URLSpan(raw.substring(start, end))
                is TdApi.TextEntityTypeEmailAddress -> URLSpan("mailto:" + raw.substring(start, end))
                is TdApi.TextEntityTypePhoneNumber -> URLSpan("tel:" + raw.substring(start, end))
                is TdApi.TextEntityTypeMention -> {
                    val username = raw.substring(start, end).removePrefix("@")
                    URLSpan("https://t.me/$username")
                }
                is TdApi.TextEntityTypeBold -> StyleSpan(Typeface.BOLD)
                is TdApi.TextEntityTypeItalic -> StyleSpan(Typeface.ITALIC)
                is TdApi.TextEntityTypeUnderline -> UnderlineSpan()
                is TdApi.TextEntityTypeStrikethrough -> StrikethroughSpan()
                is TdApi.TextEntityTypeCode,
                is TdApi.TextEntityTypePre,
                is TdApi.TextEntityTypePreCode -> TypefaceSpan("monospace")
                else -> null
            }

            if (span != null) {
                if (span is URLSpan) {
                    spannable.getSpans(start, end, URLSpan::class.java).forEach(spannable::removeSpan)
                }
                spannable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        textView.text = spannable
        textView.linksClickable = true
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.highlightColor = Color.TRANSPARENT
        textView.setLinkTextColor(accent)
    }

    @Suppress("DEPRECATION")
    private fun enableTelegramLinks(textView: TextView, accent: Int) {
        Linkify.addLinks(textView, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
        Linkify.addLinks(
            textView,
            Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9_]{5,32})"),
            "https://t.me/",
            null,
            Linkify.TransformFilter { matcher, _ -> matcher.group(1).orEmpty() }
        )
        textView.linksClickable = true
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.highlightColor = Color.TRANSPARENT
        textView.setLinkTextColor(accent)
    }

    private fun dayKey(timestamp: Int): String {
        if (timestamp <= 0) return ""
        return Instant.ofEpochSecond(timestamp.toLong())
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()
    }

    private fun dayLabel(timestamp: Int): String {
        if (timestamp <= 0) return ""
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochSecond(timestamp.toLong()).atZone(zone).toLocalDate()
        val today = java.time.LocalDate.now(zone)
        return when (date) {
            today -> "Сегодня"
            today.minusDays(1) -> "Вчера"
            else -> date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault()))
        }
    }

    private fun subscriberLabel(count: Int): String {
        val value = String.format(Locale.US, "%,d", count).replace(',', ' ')
        val mod100 = count % 100
        val mod10 = count % 10
        val suffix = when {
            mod100 in 11..14 -> "подписчиков"
            mod10 == 1 -> "подписчик"
            mod10 in 2..4 -> "подписчика"
            else -> "подписчиков"
        }
        return "$value $suffix"
    }

    private fun messageTime(timestamp: Int): String {
        if (timestamp <= 0) return ""
        return Instant.ofEpochSecond(timestamp.toLong())
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
    }

    private fun channelTime(timestamp: Int): String {
        if (timestamp <= 0) return ""
        val date = Instant.ofEpochSecond(timestamp.toLong()).atZone(ZoneId.systemDefault())
        val today = java.time.LocalDate.now(ZoneId.systemDefault())
        return if (date.toLocalDate() == today) {
            date.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        } else {
            date.format(DateTimeFormatter.ofPattern("dd.MM", Locale.getDefault()))
        }
    }

    private fun formatDuration(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        return "%d:%02d".format(Locale.US, safe / 60, safe % 60)
    }

    private fun compact(value: Int): String = when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000f)
        value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000f)
        else -> value.toString()
    }.replace(".0", "")

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp.toFloat() * android.content.res.Resources.getSystem().displayMetrics.density
        }

    private fun oval(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
