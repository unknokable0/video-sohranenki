package com.unknokable.videosohranenki

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class PlaybackSessionAdapter(
    private val palette: ThemePalette,
    source: List<VideoItem>,
    private val onPlay: (VideoItem) -> Unit,
    private val onRemove: (VideoItem) -> Unit
) : RecyclerView.Adapter<PlaybackSessionAdapter.Holder>() {

    private val items = source.toMutableList()

    class Holder(
        root: View,
        val handle: TextView,
        val title: TextView,
        val meta: TextView,
        val remove: TextView
    ) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), dp(10), dp(9), dp(10))
            background = rounded(palette.surfaceAlt, 18)
            isClickable = true
            isFocusable = true
        }

        val handle = TextView(context).apply {
            text = "≡"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(palette.muted)
            contentDescription = "Перетащить"
        }

        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(7), 0)
        }

        val title = TextView(context).apply {
            textSize = 13f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        }

        val meta = TextView(context).apply {
            textSize = 11f
            includeFontPadding = false
            setTextColor(palette.muted)
            setPadding(0, dp(3), 0, 0)
        }

        labels.addView(title)
        labels.addView(meta)

        val remove = TextView(context).apply {
            text = "×"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(palette.muted)
            background = rounded(palette.surface, 14)
            isClickable = true
            isFocusable = true
            contentDescription = "Убрать из сессии"
        }

        root.addView(handle, LinearLayout.LayoutParams(dp(30), dp(42)))
        root.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(remove, LinearLayout.LayoutParams(dp(36), dp(36)))

        return Holder(root, handle, title, meta, remove)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .ifBlank { "Видео" }

        val minutes = item.durationSeconds.coerceAtLeast(0) / 60
        val hours = minutes / 60
        val rest = minutes % 60
        holder.meta.text =
            if (hours > 0) {
                hours.toString() + " ч " + rest + " мин • Сессия"
            } else {
                rest.toString() + " мин • Сессия"
            }

        holder.itemView.setOnClickListener { onPlay(item) }
        holder.remove.setOnClickListener { onRemove(item) }
    }

    override fun getItemCount(): Int = items.size

    fun move(from: Int, to: Int): Boolean {
        if (from !in items.indices || to !in items.indices || from == to) return false
        Collections.swap(items, from, to)
        notifyItemMoved(from, to)
        return true
    }

    fun remove(messageId: Long) {
        val index = items.indexOfFirst { it.messageId == messageId }
        if (index < 0) return
        items.removeAt(index)
        notifyItemRemoved(index)
    }

    fun ids(): List<Long> = items.map { it.messageId }
}
