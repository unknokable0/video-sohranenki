package com.unknokable.videosohranenki

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeVideoShelfAdapter(
    private val items: List<VideoItem>,
    private val palette: ThemePalette,
    private val animationsEnabled: Boolean,
    private val mode: Mode,
    private val progressFor: (VideoItem) -> Float,
    private val lastPlayedAtFor: (VideoItem) -> Long,
    private val onClick: (VideoItem, View) -> Unit,
    private val onLongClick: (VideoItem, View) -> Unit
) : RecyclerView.Adapter<HomeVideoShelfAdapter.Holder>() {

    enum class Mode { CONTINUE, NEW }

    private val animatedIds = hashSetOf<Long>()
    private var lastClickAtMs = 0L

    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long = items[position].messageId
    override fun getItemCount(): Int = items.size

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val screenWidth = context.resources.displayMetrics.widthPixels
        val cardWidth = (screenWidth * 0.60f).toInt()
            .coerceIn(dp(context, 154), dp(context, 205))
        val previewHeight = (cardWidth * 9f / 16f).toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(palette.surface)
                cornerRadius = dp(context, 18).toFloat()
            }
            clipToOutline = true
            elevation = dp(context, 0).toFloat()
            layoutParams = RecyclerView.LayoutParams(
                cardWidth,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(dp(context, 6), dp(context, 2), dp(context, 6), dp(context, 6))
            }
        }

        val preview = FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    if (palette === AppThemes.Light) Color.parseColor("#ECE4FF") else Color.parseColor("#2E1E4E"),
                    if (palette === AppThemes.Light) Color.parseColor("#F8F5FF") else Color.parseColor("#171120")
                )
            )
            clipToOutline = false
        }

        val thumbnail = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        preview.addView(
            thumbnail,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val blend = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, palette.surface)
            )
            alpha = 0.92f
        }
        preview.addView(
            blend,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 22),
                Gravity.BOTTOM
            )
        )

        val play = ImageView(context).apply {
            setImageResource(R.drawable.ic_play)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#9A17131D"))
            }
        }
        preview.addView(
            play,
            FrameLayout.LayoutParams(dp(context, 36), dp(context, 36), Gravity.CENTER)
        )

        val badge = TextView(context).apply {
            text = if (mode == Mode.NEW) "НОВОЕ" else "ПРОДОЛЖИТЬ"
            textSize = 9.2f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(context, 7), 0, dp(context, 7), 0)
            background = GradientDrawable().apply {
                setColor(if (mode == Mode.NEW) palette.accent else Color.parseColor("#B516131D"))
                cornerRadius = dp(context, 8).toFloat()
            }
        }
        preview.addView(
            badge,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(context, 23),
                Gravity.START or Gravity.TOP
            ).apply {
                leftMargin = dp(context, 7)
                topMargin = dp(context, 7)
            }
        )

        val duration = TextView(context).apply {
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(context, 6), 0, dp(context, 6), 0)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D9000000"))
                cornerRadius = dp(context, 6).toFloat()
            }
        }
        preview.addView(
            duration,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(context, 23),
                Gravity.END or Gravity.BOTTOM
            ).apply {
                rightMargin = dp(context, 7)
                bottomMargin = dp(context, 7)
            }
        )

        val progressTrack = FrameLayout(context).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#66000000")) }
            visibility = View.GONE
        }
        val progressFill = View(context).apply {
            background = GradientDrawable().apply { setColor(palette.accent) }
        }
        progressTrack.addView(
            progressFill,
            FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START)
        )
        preview.addView(
            progressTrack,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 3),
                Gravity.BOTTOM
            )
        )

        root.addView(
            preview,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, previewHeight)
        )

        val title = TextView(context).apply {
            textSize = 13f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            setPadding(dp(context, 10), dp(context, 7), dp(context, 10), 0)
        }
        val meta = TextView(context).apply {
            textSize = 10f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            setTextColor(palette.muted)
            setPadding(dp(context, 10), dp(context, 3), dp(context, 10), dp(context, 9))
        }
        root.addView(title)
        root.addView(meta)

        return Holder(root, thumbnail, badge, duration, progressTrack, progressFill, title, meta)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val progress = progressFor(item).coerceIn(0f, 1f)
        val ageMs = (System.currentTimeMillis() - item.date.toLong() * 1000L).coerceAtLeast(0L)
        val trulyNew = mode == Mode.NEW &&
            progress <= 0.005f &&
            lastPlayedAtFor(item) <= 0L &&
            ageMs <= 24L * 60L * 60L * 1000L

        holder.badge.visibility =
            if (mode == Mode.CONTINUE || trulyNew) View.VISIBLE else View.GONE
        holder.badge.text = if (mode == Mode.CONTINUE) "ПРОДОЛЖИТЬ" else "НОВОЕ"

        holder.title.text = cleanTitle(item.title, position)
        holder.duration.text = formatDuration(item.durationSeconds)
        holder.meta.text =
            if (mode == Mode.CONTINUE && progress > 0f && item.durationSeconds > 0) {
                val remainingSeconds =
                    (item.durationSeconds * (1f - progress)).toInt().coerceAtLeast(0)
                "Осталось " + formatRemaining(remainingSeconds)
            } else {
                formatDate(item.date)
            }

        val thumbPath = item.thumbnailPath
        val model: Any? = when {
            !thumbPath.isNullOrBlank() && File(thumbPath).exists() -> File(thumbPath)
            !item.thumbnailUrl.isNullOrBlank() -> item.thumbnailUrl
            else -> null
        }

        holder.thumbnail.animate().cancel()
        if (model != null) {
            holder.thumbnail.scaleX = 1.015f
            holder.thumbnail.scaleY = 1.015f
            holder.thumbnail.load(model) {
                crossfade(animationsEnabled)
                listener(onSuccess = { _, _ ->
                    holder.thumbnail.animate().cancel()
                    holder.thumbnail.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(if (animationsEnabled) 240L else 0L)
                        .start()
                })
            }
        } else {
            holder.thumbnail.setImageDrawable(null)
            holder.thumbnail.scaleX = 1f
            holder.thumbnail.scaleY = 1f
        }

        if (progress > 0.005f && progress < 0.995f) {
            holder.progressTrack.visibility = View.VISIBLE
            holder.progressTrack.post {
                val width = holder.progressTrack.width.coerceAtLeast(dp(holder.itemView.context, 2))
                holder.progressFill.layoutParams = FrameLayout.LayoutParams(
                    (width * progress).toInt().coerceIn(dp(holder.itemView.context, 2), width),
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.START
                )
            }
        } else {
            holder.progressTrack.visibility = View.GONE
            holder.progressFill.layoutParams = FrameLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START
            )
        }

        holder.itemView.animate().cancel()
        if (animationsEnabled && animatedIds.add(item.messageId)) {
            holder.itemView.alpha = 0f
            holder.itemView.translationX = dp(holder.itemView.context, 10).toFloat()
            holder.itemView.scaleX = 0.97f
            holder.itemView.scaleY = 0.97f
            holder.itemView.animate()
                .alpha(1f)
                .translationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(240L)
                .setStartDelay(position.coerceAtMost(5) * 22L)
                .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                .start()
        } else {
            holder.itemView.alpha = 1f
            holder.itemView.translationX = 0f
            holder.itemView.scaleX = 1f
            holder.itemView.scaleY = 1f
        }

        holder.itemView.setOnClickListener {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastClickAtMs < 320L) return@setOnClickListener
            lastClickAtMs = now

            if (animationsEnabled) {
                holder.itemView.animate().cancel()
                holder.itemView.animate()
                    .scaleX(0.965f)
                    .scaleY(0.965f)
                    .setDuration(SohrMotion.FAST / 2)
                    .setInterpolator(SohrMotion.smooth())
                    .withEndAction {
                        holder.itemView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(SohrMotion.FAST)
                            .setInterpolator(SohrMotion.smooth())
                            .start()
                        onClick(item, holder.thumbnail)
                    }
                    .start()
            } else {
                onClick(item, holder.thumbnail)
            }
        }

        holder.itemView.setOnLongClickListener {
            holder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            if (animationsEnabled) {
                holder.itemView.animate().cancel()
                holder.itemView.animate()
                    .scaleX(1.018f)
                    .scaleY(1.018f)
                    .translationY(-dp(holder.itemView.context, 2).toFloat())
                    .setDuration(120L)
                    .withEndAction {
                        holder.itemView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .translationY(0f)
                            .setDuration(150L)
                            .start()
                    }
                    .start()
            }
            onLongClick(item, holder.itemView)
            true
        }
    }

    private fun cleanTitle(raw: String, position: Int): String {
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
        val firstLine = normalized
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        val looksLikeFileName = firstLine.matches(
            Regex(
                """\d{4}-\d{2}-\d{2}[_-].*\.(mp4|mkv|mov|webm)""",
                RegexOption.IGNORE_CASE
            )
        )
        return when {
            looksLikeFileName -> "Запись стрима • часть ${position + 1}"
            firstLine.isNotBlank() -> firstLine
            else -> "Видео"
        }
    }

    private fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return "Видео"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%d:%02d".format(m, s)
    }

    private fun formatRemaining(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "$h ч ${m.coerceAtLeast(1)} мин"
        else "${m.coerceAtLeast(1)} мин"
    }

    private fun formatDate(epochSeconds: Int): String {
        val now = System.currentTimeMillis()
        val timeMs = epochSeconds.toLong() * 1000L
        val ageMs = (now - timeMs).coerceAtLeast(0L)
        val dayMs = 24L * 60L * 60L * 1000L

        return when {
            ageMs < dayMs -> "Сегодня • " +
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMs))
            ageMs < 2L * dayMs -> "Вчера • " +
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMs))
            else -> SimpleDateFormat("d MMMM", Locale("ru")).format(Date(timeMs))
        }
    }

    class Holder(
        view: View,
        val thumbnail: ImageView,
        val badge: TextView,
        val duration: TextView,
        val progressTrack: FrameLayout,
        val progressFill: View,
        val title: TextView,
        val meta: TextView
    ) : RecyclerView.ViewHolder(view)
}
