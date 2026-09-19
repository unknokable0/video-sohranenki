package com.unknokable.videosohranenki

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
            setBackgroundColor(Color.parseColor("#0B0911"))
        }

        val preview = FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#2B1D47"),
                    Color.parseColor("#171120")
                )
            ).apply { cornerRadius = dp(context, 12).toFloat() }
            layoutParams = LinearLayout.LayoutParams(
                dp(context, 148),
                dp(context, 84)
            )
        }

        val play = TextView(context).apply {
            text = "▶"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        preview.addView(
            play,
            FrameLayout.LayoutParams(
                dp(context, 48),
                dp(context, 48),
                Gravity.CENTER
            )
        )

        val duration = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(dp(context, 6), dp(context, 3), dp(context, 6), dp(context, 3))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC000000"))
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
            setTextColor(Color.parseColor("#F7F5FF"))
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
        }

        val meta = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#9E96AD"))
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
        val time = SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(Date(item.date.toLong() * 1000L))
        val size = when {
            item.fileSize >= 1024L * 1024L * 1024L -> "%.1f ГБ".format(item.fileSize / 1073741824.0)
            item.fileSize >= 1024L * 1024L -> "%.0f МБ".format(item.fileSize / 1048576.0)
            else -> ""
        }
        return listOf(time, size, "@t2x2_video")
            .filter { it.isNotBlank() }
            .joinToString(" • ")
    }

    class Holder(
        view: View,
        val title: TextView,
        val meta: TextView,
        val duration: TextView
    ) : RecyclerView.ViewHolder(view)
}
