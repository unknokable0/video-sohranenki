package com.unknokable.videosohranenki

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoAdapter(
    private val items: List<VideoItem>,
    private val onClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.Holder>() {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 14))
            setBackgroundColor(Color.parseColor("#0B0911"))
        }

        val preview = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#1B1528"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 205)
            )
        }

        val play = TextView(context).apply {
            text = "▶"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#553B82F6"))
        }
        preview.addView(play, FrameLayout.LayoutParams(dp(context, 72), dp(context, 72), Gravity.CENTER))

        val duration = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4))
            setBackgroundColor(Color.parseColor("#CC000000"))
        }
        preview.addView(duration, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.BOTTOM
        ).apply {
            marginEnd = dp(context, 8)
            bottomMargin = dp(context, 8)
        })

        val title = TextView(context).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#F7F5FF"))
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
            setPadding(0, dp(context, 10), 0, 0)
        }

        val meta = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#9E96AD"))
            setPadding(0, dp(context, 5), 0, 0)
        }

        root.addView(preview)
        root.addView(title)
        root.addView(meta)

        return Holder(root, title, meta, duration)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.duration.text = formatDuration(item.durationSeconds)
        holder.meta.text = formatMeta(item)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    private fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return "Видео"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun formatMeta(item: VideoItem): String {
        val date = SimpleDateFormat("dd.MM.yyyy • HH:mm", Locale.getDefault())
            .format(Date(item.date.toLong() * 1000L))
        val size = when {
            item.fileSize >= 1024L * 1024L * 1024L -> "%.1f ГБ".format(item.fileSize / 1073741824.0)
            item.fileSize >= 1024L * 1024L -> "%.0f МБ".format(item.fileSize / 1048576.0)
            else -> ""
        }
        return listOf(date, size).filter { it.isNotBlank() }.joinToString(" • ")
    }

    class Holder(
        view: View,
        val title: TextView,
        val meta: TextView,
        val duration: TextView
    ) : RecyclerView.ViewHolder(view)
}
