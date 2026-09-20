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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DayCollectionAdapter(
    private val items: List<DayCollection>,
    private val palette: ThemePalette,
    private val animationsEnabled: Boolean,
    private val onClick: (DayCollection) -> Unit
) : RecyclerView.Adapter<DayCollectionAdapter.Holder>() {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10))
            background = GradientDrawable().apply {
                setColor(palette.surface)
                cornerRadius = dp(context, 18).toFloat()
            }
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(context, 16), dp(context, 5), dp(context, 16), dp(context, 5))
            }
        }

        val preview = FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    if (palette === AppThemes.Light) Color.parseColor("#EDE5FF") else Color.parseColor("#35205F"),
                    if (palette === AppThemes.Light) Color.parseColor("#F8F5FF") else Color.parseColor("#171220")
                )
            ).apply { cornerRadius = dp(context, 14).toFloat() }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(dp(context, 144), dp(context, 82))
        }

        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.72f
        }
        preview.addView(image, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val date = TextView(context).apply {
            textSize = 27f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setShadowLayer(10f, 0f, 2f, Color.BLACK)
        }
        preview.addView(date, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val badge = TextView(context).apply {
            textSize = 10.5f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(context, 7), dp(context, 3), dp(context, 7), dp(context, 3))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D9000000"))
                cornerRadius = dp(context, 7).toFloat()
            }
        }
        preview.addView(badge, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.BOTTOM
        ).apply {
            marginEnd = dp(context, 6)
            bottomMargin = dp(context, 6)
        })

        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 12), 0, dp(context, 6), 0)
        }

        val title = TextView(context).apply {
            textSize = 16f
            setTextColor(palette.text)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
        }

        val meta = TextView(context).apply {
            textSize = 12.5f
            setTextColor(palette.muted)
            setPadding(0, dp(context, 6), 0, 0)
            maxLines = 2
        }

        val arrow = TextView(context).apply {
            text = "›"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(palette.accent)
        }

        info.addView(title)
        info.addView(meta)

        root.addView(preview)
        root.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(arrow, LinearLayout.LayoutParams(dp(context, 28), dp(context, 44)))

        return Holder(root, image, date, badge, title, meta)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.date.text = item.date.format(DateTimeFormatter.ofPattern("dd.MM"))
        holder.badge.text = "${item.videos.size} видео"
        holder.title.text = dayTitle(item.date)
        holder.meta.text = buildMeta(item)

        val firstWithThumb = item.videos.firstOrNull { !it.thumbnailPath.isNullOrBlank() || !it.thumbnailUrl.isNullOrBlank() }
        val thumbPath = firstWithThumb?.thumbnailPath
        val thumbModel: Any? = when {
            !thumbPath.isNullOrBlank() && File(thumbPath).exists() -> File(thumbPath)
            !firstWithThumb?.thumbnailUrl.isNullOrBlank() -> firstWithThumb?.thumbnailUrl
            else -> null
        }
        if (thumbModel != null) holder.image.load(thumbModel) { crossfade(animationsEnabled) }
        else holder.image.setImageDrawable(null)

        if (animationsEnabled) {
            holder.itemView.alpha = 0f
            holder.itemView.translationY = dp(holder.itemView.context, 10).toFloat()
            holder.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(190)
                .setStartDelay((position.coerceAtMost(8) * 22L))
                .start()
        }

        holder.itemView.setOnClickListener {
            if (!holder.itemView.isEnabled) return@setOnClickListener
            holder.itemView.isEnabled = false
            onClick(item)
            holder.itemView.postDelayed({ holder.itemView.isEnabled = true }, 450)
        }
    }

    private fun dayTitle(date: LocalDate): String {
        val today = LocalDate.now()
        val formatted = date.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))
        return when (date) {
            today -> "Сегодня · $formatted"
            today.minusDays(1) -> "Вчера · $formatted"
            else -> formatted.replaceFirstChar { it.titlecase(Locale("ru")) }
        }
    }

    private fun buildMeta(item: DayCollection): String {
        val duration = item.totalDurationSeconds
        val durationText = if (duration > 0) {
            val h = duration / 3600
            val m = (duration % 3600) / 60
            if (h > 0) "$h ч ${m} мин" else "$m мин"
        } else null
        val source = if (item.videos.firstOrNull()?.source == "twitch") "Twitch • @t2x2" else "@t2x2_video"
        return listOfNotNull("${item.videos.size} видео", durationText, source).joinToString(" · ")
    }

    class Holder(
        view: View,
        val image: ImageView,
        val date: TextView,
        val badge: TextView,
        val title: TextView,
        val meta: TextView
    ) : RecyclerView.ViewHolder(view)
}
