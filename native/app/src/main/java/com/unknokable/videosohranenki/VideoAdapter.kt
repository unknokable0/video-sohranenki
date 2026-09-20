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

class VideoAdapter(
    private val items: List<VideoItem>,
    private val palette: ThemePalette,
    private val animationsEnabled: Boolean,
    private val onClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.Holder>() {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
            background = GradientDrawable().apply {
                setColor(palette.surface)
                cornerRadius = dp(context, 16).toFloat()
            }
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(context, 16), dp(context, 6), dp(context, 16), dp(context, 6))
            }
        }

        val preview = FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    if (palette === AppThemes.Light) Color.parseColor("#E9E0FF") else Color.parseColor("#2B1D47"),
                    if (palette === AppThemes.Light) Color.parseColor("#F7F3FF") else Color.parseColor("#171120")
                )
            ).apply { cornerRadius = dp(context, 13).toFloat() }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                dp(context, 148),
                dp(context, 84)
            )
        }

        val thumbnail = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0f
        }
        preview.addView(
            thumbnail,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val play = TextView(context).apply {
            text = "▶"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#8A000000"))
                shape = GradientDrawable.OVAL
            }
        }
        preview.addView(
            play,
            FrameLayout.LayoutParams(
                dp(context, 44),
                dp(context, 44),
                Gravity.CENTER
            )
        )

        val duration = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(dp(context, 6), dp(context, 3), dp(context, 6), dp(context, 3))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D9000000"))
                cornerRadius = dp(context, 6).toFloat()
            }
        }
        preview.addView(
            duration,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.BOTTOM
            ).apply {
                marginEnd = dp(context, 6)
                bottomMargin = dp(context, 6)
            }
        )

        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 12), 0, 0, 0)
        }

        val title = TextView(context).apply {
            textSize = 15f
            setTextColor(palette.text)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
        }

        val meta = TextView(context).apply {
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(0, dp(context, 6), 0, 0)
            maxLines = 2
        }

        info.addView(title)
        info.addView(meta)

        root.addView(preview)
        root.addView(
            info,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        return Holder(root, thumbnail, title, meta, duration)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.title.text = cleanTitle(item.title, position)
        holder.duration.text = formatDuration(item.durationSeconds)
        holder.meta.text = formatMeta(item)

        val thumb = item.thumbnailPath
        val remoteThumb = item.thumbnailUrl
        val model: Any? = when {
            !thumb.isNullOrBlank() && File(thumb).exists() -> File(thumb)
            !remoteThumb.isNullOrBlank() -> remoteThumb
            else -> null
        }
        if (model != null) {
            holder.thumbnail.alpha = 0f
            holder.thumbnail.load(model) {
                crossfade(animationsEnabled)
                listener(onSuccess = { _, _ ->
                    holder.thumbnail.animate().alpha(1f).setDuration(if (animationsEnabled) 180 else 0).start()
                })
            }
        } else {
            holder.thumbnail.setImageDrawable(null)
            holder.thumbnail.alpha = 0f
        }

        if (animationsEnabled) {
            holder.itemView.alpha = 0f
            holder.itemView.translationY = dp(holder.itemView.context, 10).toFloat()
            holder.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180)
                .setStartDelay((position.coerceAtMost(8) * 18).toLong())
                .start()
        } else {
            holder.itemView.alpha = 1f
            holder.itemView.translationY = 0f
        }

        holder.itemView.setOnClickListener {
            if (!holder.itemView.isEnabled) return@setOnClickListener
            holder.itemView.isEnabled = false
            onClick(item)
            holder.itemView.postDelayed({ holder.itemView.isEnabled = true }, 450)
        }
    }

    private fun cleanTitle(raw: String, position: Int): String {
        val looksLikeFileName = raw.matches(Regex("""\d{4}-\d{2}-\d{2}[_-].*\.(mp4|mkv|mov|webm)""", RegexOption.IGNORE_CASE))
        return if (looksLikeFileName) "Запись стрима • часть ${position + 1}" else raw
    }

    private fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return "Видео"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun formatMeta(item: VideoItem): String {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(Date(item.date.toLong() * 1000L))
        val size = when {
            item.fileSize >= 1024L * 1024L * 1024L -> "%.1f ГБ".format(item.fileSize / 1073741824.0)
            item.fileSize >= 1024L * 1024L -> "%.0f МБ".format(item.fileSize / 1048576.0)
            else -> ""
        }
        return listOf(time, size)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
    }

    class Holder(
        view: View,
        val thumbnail: ImageView,
        val title: TextView,
        val meta: TextView,
        val duration: TextView
    ) : RecyclerView.ViewHolder(view)
}
