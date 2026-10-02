package com.unknokable.videosohranenki

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import coil.load
import coil.transform.CircleCropTransformation

object YouTubeFeedUi {
    fun buildDirectory(
        activity: Activity,
        settings: AppSettings,
        channels: List<YouTubeChannelSummary>,
        videos: List<YouTubeFeedVideo>,
        loading: Boolean,
        onOpenChannel: (YouTubeChannelSummary, View) -> Unit,
        onOpenVideo: (YouTubeFeedVideo, View) -> Unit,
        onRefresh: () -> Unit
    ): View {
        val palette = settings.palette()
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 28))
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
        }

        val channelHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 2), 0, dp(activity, 2), dp(activity, 8))
        }
        channelHeader.addView(TextView(activity).apply {
            text = "Каналы"
            textSize = 17.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            includeFontPadding = false
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        channelHeader.addView(TextView(activity).apply {
            text = if (loading) "Обновляем…" else "Обновить"
            textSize = 11.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (loading) palette.muted else palette.accent)
            background = rounded(palette.surfaceAlt, 14)
            setPadding(dp(activity, 11), 0, dp(activity, 11), 0)
            isClickable = !loading
            isFocusable = !loading
            setOnClickListener {
                if (!loading) {
                    SohrMotion.press(this, settings.animations)
                    onRefresh()
                }
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 30)))
        root.addView(channelHeader)

        if (loading && channels.isEmpty()) {
            repeat(2) { index ->
                root.addView(
                    channelSkeleton(activity, palette, index),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(activity, 92)
                    ).apply {
                        if (index > 0) topMargin = dp(activity, 10)
                    }
                )
            }
        } else if (channels.isEmpty()) {
            root.addView(TextView(activity).apply {
                text = "Не удалось загрузить YouTube-каналы\nНажмите, чтобы повторить"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(palette.muted)
                background = rounded(palette.surfaceAlt, 18)
                setPadding(dp(activity, 16), dp(activity, 22), dp(activity, 16), dp(activity, 22))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    SohrMotion.press(this, settings.animations)
                    onRefresh()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        } else {
            channels.forEachIndexed { index, channel ->
                val row = channelRow(activity, settings, channel) { pressed ->
                    onOpenChannel(channel, pressed)
                }
                animateEntrance(activity, settings, row, index, 8)
                root.addView(
                    row,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 78)).apply {
                        if (index > 0) topMargin = dp(activity, 6)
                    }
                )
            }
        }

        val newHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 2), dp(activity, 26), dp(activity, 2), dp(activity, 12))
        }
        newHeader.addView(TextView(activity).apply {
            text = "Новые видео"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            includeFontPadding = false
        })
        newHeader.addView(TextView(activity).apply {
            text = if (videos.isEmpty() && loading) {
                "Собираем свежие ролики…"
            } else {
                "Просмотр — в YouTube • список и каналы остаются в SOHR"
            }
            textSize = 11.5f
            setTextColor(palette.muted)
            includeFontPadding = false
            setPadding(0, dp(activity, 4), 0, 0)
        })
        root.addView(newHeader)

        if (videos.isEmpty()) {
            if (!loading) {
                root.addView(TextView(activity).apply {
                    text = "Новых роликов пока нет"
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(palette.muted)
                    background = rounded(palette.surfaceAlt, 18)
                    setPadding(0, dp(activity, 26), 0, dp(activity, 26))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        } else {
            val byChannel = channels.associateBy { it.channelId }
            videos.take(30).forEachIndexed { index, video ->
                val channel = byChannel[video.channelId]
                val card = videoCard(
                    activity,
                    settings,
                    video,
                    channel?.avatarUrl.orEmpty()
                ) { pressed ->
                    onOpenVideo(video, pressed)
                }
                animateEntrance(activity, settings, card, index, 6)
                root.addView(
                    card,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        if (index > 0) topMargin = dp(activity, 14)
                    }
                )
            }
        }
        return root
    }

    fun buildChannel(
        activity: Activity,
        settings: AppSettings,
        channel: YouTubeChannelSummary,
        onBack: () -> Unit,
        onOpenVideo: (YouTubeFeedVideo, View) -> Unit
    ): View {
        val palette = settings.palette()
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8))
        }
        header.addView(ImageButton(activity).apply {
            setImageResource(R.drawable.ic_back)
            imageTintList = ColorStateList.valueOf(palette.text)
            background = rounded(palette.surfaceAlt, 21)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(activity, 11), dp(activity, 11), dp(activity, 11), dp(activity, 11))
            contentDescription = "Назад"
            setOnClickListener {
                SohrMotion.press(this, settings.animations)
                onBack()
            }
        }, LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)).apply {
            marginEnd = dp(activity, 10)
        })

        header.addView(ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = oval(palette.surfaceAlt)
            clipToOutline = true
            if (channel.avatarUrl.isNotBlank()) {
                load(channel.avatarUrl) {
                    transformations(CircleCropTransformation())
                    crossfade(settings.animations)
                }
            }
        }, LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)).apply {
            marginEnd = dp(activity, 10)
        })

        val titleBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(TextView(activity).apply {
            text = channel.title
            textSize = 16.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        })
        titleBox.addView(TextView(activity).apply {
            text = channel.handle
            textSize = 11.5f
            setTextColor(palette.muted)
            includeFontPadding = false
            setPadding(0, dp(activity, 3), 0, 0)
        })
        header.addView(titleBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        page.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 64)))

        val scroll = NestedScrollView(activity).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
        }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 24))
        }
        body.addView(TextView(activity).apply {
            text = "Видео канала"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            includeFontPadding = false
            setPadding(dp(activity, 2), 0, 0, dp(activity, 10))
        })

        if (channel.videos.isEmpty()) {
            body.addView(TextView(activity).apply {
                text = "Видео пока не загрузились"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(palette.muted)
                setPadding(0, dp(activity, 30), 0, dp(activity, 30))
            })
        } else {
            channel.videos.forEachIndexed { index, video ->
                val card = videoCard(
                    activity,
                    settings,
                    video,
                    channel.avatarUrl
                ) { pressed ->
                    onOpenVideo(video, pressed)
                }
                animateEntrance(activity, settings, card, index, 6)
                body.addView(
                    card,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        if (index > 0) topMargin = dp(activity, 14)
                    }
                )
            }
        }
        scroll.addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return page
    }

    private fun channelRow(
        activity: Activity,
        settings: AppSettings,
        channel: YouTubeChannelSummary,
        onClick: (View) -> Unit
    ): View {
        val palette = settings.palette()
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 12), dp(activity, 9), dp(activity, 12), dp(activity, 9))
            background = rounded(palette.surfaceAlt, 19)
            isClickable = true
            isFocusable = true

            addView(ImageView(activity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = oval(palette.surface)
                clipToOutline = true
                if (channel.avatarUrl.isNotBlank()) {
                    load(channel.avatarUrl) {
                        transformations(CircleCropTransformation())
                        crossfade(settings.animations)
                    }
                }
            }, LinearLayout.LayoutParams(dp(activity, 56), dp(activity, 56)))

            val textBox = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(activity, 12), 0, dp(activity, 8), 0)
            }
            textBox.addView(TextView(activity).apply {
                text = channel.title
                textSize = 15.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.text)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                includeFontPadding = false
            })
            textBox.addView(TextView(activity).apply {
                text = channel.handle + " • " + channel.videos.size + " видео"
                textSize = 11.5f
                setTextColor(palette.muted)
                includeFontPadding = false
                setPadding(0, dp(activity, 4), 0, 0)
            })
            addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            addView(ImageView(activity).apply {
                setImageResource(R.drawable.ic_chevron_right)
                imageTintList = ColorStateList.valueOf(palette.muted)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }, LinearLayout.LayoutParams(dp(activity, 24), dp(activity, 24)))

            setOnClickListener {
                SohrMotion.press(this, settings.animations)
                onClick(this)
            }
        }
    }

    private fun videoCard(
        activity: Activity,
        settings: AppSettings,
        video: YouTubeFeedVideo,
        avatarUrl: String,
        onClick: (View) -> Unit
    ): View {
        val palette = settings.palette()
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(palette.surfaceAlt, 22)
            clipToOutline = true
            isClickable = true
            isFocusable = true
            setPadding(dp(activity, 6), dp(activity, 6), dp(activity, 6), dp(activity, 4))
        }

        val width = (activity.resources.displayMetrics.widthPixels - dp(activity, 36))
            .coerceAtLeast(dp(activity, 240))
        val thumbHeight = (width * 9f / 16f).toInt()

        val thumbnailHolder = FrameLayout(activity).apply {
            background = rounded(Color.BLACK, 18)
            clipToOutline = true
        }
        thumbnailHolder.addView(
            ImageView(activity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.BLACK)
                loadVideoThumbnail(
                    image = this,
                    video = video,
                    animate = settings.animations
                )
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        if (video.durationSeconds > 0) {
            thumbnailHolder.addView(
                TextView(activity).apply {
                    text = formatDuration(video.durationSeconds)
                    textSize = 11f
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    setPadding(
                        dp(activity, 7),
                        dp(activity, 3),
                        dp(activity, 7),
                        dp(activity, 3)
                    )
                    background = rounded(Color.parseColor("#C70A0910"), 8)
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.BOTTOM
                ).apply {
                    marginEnd = dp(activity, 8)
                    bottomMargin = dp(activity, 8)
                }
            )
        }

        card.addView(
            thumbnailHolder,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                thumbHeight
            )
        )

        val metaRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(
                dp(activity, 7),
                dp(activity, 10),
                dp(activity, 7),
                dp(activity, 9)
            )
        }
        metaRow.addView(
            ImageView(activity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = oval(palette.surface)
                clipToOutline = true
                if (avatarUrl.isNotBlank()) {
                    load(avatarUrl) {
                        transformations(CircleCropTransformation())
                        crossfade(false)
                    }
                }
            },
            LinearLayout.LayoutParams(dp(activity, 38), dp(activity, 38)).apply {
                marginEnd = dp(activity, 10)
            }
        )

        val textBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        textBox.addView(TextView(activity).apply {
            text = video.title
            textSize = 14.2f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            setLineSpacing(0f, 1.04f)
        })
        textBox.addView(TextView(activity).apply {
            text = video.channelTitle + " • " + publicationText(video)
            textSize = 11.2f
            setTextColor(palette.muted)
            includeFontPadding = false
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(activity, 5), 0, 0)
        })
        metaRow.addView(
            textBox,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        metaRow.addView(
            ImageView(activity).apply {
                setImageResource(R.drawable.ic_action_open)
                imageTintList = ColorStateList.valueOf(palette.accent)
                background = rounded(palette.accentSoft, 13)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(
                    dp(activity, 7),
                    dp(activity, 7),
                    dp(activity, 7),
                    dp(activity, 7)
                )
                contentDescription = "Открывается в YouTube"
            },
            LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)).apply {
                marginStart = dp(activity, 8)
                topMargin = dp(activity, 1)
            }
        )

        card.addView(metaRow)

        card.setOnClickListener {
            SohrMotion.press(card, settings.animations)
            onClick(card)
        }
        return card
    }

    private fun channelSkeleton(activity: Activity, palette: ThemePalette, index: Int): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 12), dp(activity, 9), dp(activity, 12), dp(activity, 9))
            background = rounded(palette.surfaceAlt, 19)
            alpha = 0.60f + index * 0.06f
            addView(View(activity).apply {
                background = oval(palette.surface)
            }, LinearLayout.LayoutParams(dp(activity, 56), dp(activity, 56)))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(activity, 12), 0, 0, 0)
                addView(View(activity).apply { background = rounded(palette.surface, 6) },
                    LinearLayout.LayoutParams(dp(activity, 140), dp(activity, 14)))
                addView(View(activity).apply { background = rounded(palette.surface, 6) },
                    LinearLayout.LayoutParams(dp(activity, 100), dp(activity, 11)).apply { topMargin = dp(activity, 8) })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    private fun loadVideoThumbnail(
        image: ImageView,
        video: YouTubeFeedVideo,
        animate: Boolean
    ) {
        val primary = video.thumbnailUrl
            .takeIf { it.isNotBlank() }
            ?: "https://i.ytimg.com/vi/${video.videoId}/maxresdefault.jpg"
        val sd = "https://i.ytimg.com/vi/${video.videoId}/sddefault.jpg"
        val hq = "https://i.ytimg.com/vi/${video.videoId}/hqdefault.jpg"

        image.load(primary) {
            crossfade(animate)
            listener(
                onError = { _, _ ->
                    if (primary == sd) return@listener
                    image.load(sd) {
                        crossfade(false)
                        listener(
                            onError = { _, _ ->
                                if (sd != hq) {
                                    image.load(hq) { crossfade(false) }
                                }
                            }
                        )
                    }
                }
            )
        }
    }

    private fun animateEntrance(
        activity: Activity,
        settings: AppSettings,
        view: View,
        index: Int,
        maxStagger: Int
    ) {
        if (!settings.animations) return

        view.alpha = 0f
        view.translationY = dp(activity, 8).toFloat()
        view.post {
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index.coerceIn(0, maxStagger) * 24L)
                .setDuration(SohrMotion.NORMAL)
                .setInterpolator(SohrMotion.smooth())
                .start()
        }
    }

    private fun publicationText(video: YouTubeFeedVideo): String {
        if (video.publishedExact && video.publishedAt > 0L) {
            return exactPublicationTime(video.publishedAt)
        }

        val youtubeLabel = video.publishedLabel.trim()
        if (youtubeLabel.isNotBlank()) {
            return youtubeLabel
        }

        if (video.publishedAt > 0L) {
            return exactPublicationTime(video.publishedAt)
        }

        return "Дата не указана"
    }

    private fun exactPublicationTime(epochSeconds: Long): String {
        val zone = java.time.ZoneId.systemDefault()
        val published = java.time.Instant.ofEpochSecond(epochSeconds).atZone(zone)
        val today = java.time.ZonedDateTime.now(zone).toLocalDate()
        val publishedDate = published.toLocalDate()
        val time = published.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

        return when {
            publishedDate == today -> "Сегодня, $time"
            publishedDate == today.minusDays(1) -> "Вчера, $time"
            publishedDate.year == today.year ->
                published.format(
                    java.time.format.DateTimeFormatter.ofPattern(
                        "d MMMM, HH:mm",
                        java.util.Locale("ru", "RU")
                    )
                )
            else ->
                published.format(
                    java.time.format.DateTimeFormatter.ofPattern(
                        "d MMMM yyyy, HH:mm",
                        java.util.Locale("ru", "RU")
                    )
                )
        }
    }

    private fun formatDuration(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val seconds = safe % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp *
                android.content.res.Resources.getSystem().displayMetrics.density
            setColor(color)
        }

    private fun oval(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
