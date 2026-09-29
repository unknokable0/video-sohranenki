package com.unknokable.videosohranenki

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
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
    private val onClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<HomeVideoShelfAdapter.Holder>() {

    enum class Mode { CONTINUE, NEW }

    private val animatedIds = hashSetOf<Long>()

    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long = items[position].messageId
    override fun getItemCount(): Int = items.size

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(palette.surface)
                cornerRadius = dp(context, 18).toFloat()
            }
            clipToOutline = true
            layoutParams = RecyclerView.LayoutParams(
                dp(context, 220),
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
            ).apply { cornerRadius = dp(context, 16).toFloat() }
            clipToOutline = true
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

        val play = ImageView(context).apply {
            setImageResource(R.drawable.ic_play)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#9A17131D"))
            }
        }
        preview.addView(
            play,
            FrameLayout.LayoutParams(dp(context, 42), dp(context, 42), Gravity.CENTER)
        )

        val badge = TextView(context).apply {
            text = if (mode == Mode.NEW) "НОВОЕ" else "ПРОДОЛЖИТЬ"
            textSize = 9.5f
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
                dp(context, 25),
                Gravity.START or Gravity.TOP
            ).apply {
                leftMargin = dp(context, 7)
                topMargin = dp(context, 7)
            }
        )

        val duration = TextView(context).apply {
            textSize = 10.5f
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
                dp(context, 25),
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
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 124))
        )

        val title = TextView(context).apply {
            textSize = 14.5f
            maxLines = 2
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            setPadding(dp(context, 11), dp(context, 8), dp(context, 11), 0)
        }
        val meta = TextView(context).apply {
            textSize = 10.8f
            maxLines = 1
            setTextColor(palette.muted)
            setPadding(dp(context, 11), dp(context, 4), dp(context, 11), dp(context, 8))
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
            ageMs <= 48L * 60L * 60L * 1000L

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
            holder.thumbnail.load(model) { crossfade(animationsEnabled) }
        } else {
            holder.thumbnail.setImageDrawable(null)
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
            holder.itemView.translationX = dp(holder.itemView.context, 7).toFloat()
            holder.itemView.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(180L)
                .setStartDelay(position.coerceAtMost(5) * 18L)
                .start()
        } else {
            holder.itemView.alpha = 1f
            holder.itemView.translationX = 0f
        }

        holder.itemView.setOnClickListener {
            if (!holder.itemView.isEnabled) return@setOnClickListener
            holder.itemView.isEnabled = false

            if (animationsEnabled) {
                holder.itemView.animate().cancel()
                holder.itemView.animate()
                    .scaleX(0.985f)
                    .scaleY(0.985f)
                    .setDuration(70L)
                    .withEndAction {
                        holder.itemView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100L)
                            .start()
                        onClick(item)
                    }
                    .start()
            } else {
                onClick(item)
            }

            holder.itemView.postDelayed({ holder.itemView.isEnabled = true }, 420L)
        }
    }

    private fun cleanTitle(raw: String, position: Int): String {
        val looksLikeFileName = raw.matches(
            Regex(
                """\d{4}-\d{2}-\d{2}[_-].*\.(mp4|mkv|mov|webm)""",
                RegexOption.IGNORE_CASE
            )
        )
        return if (looksLikeFileName) "Запись стрима • часть ${position + 1}" else raw
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
