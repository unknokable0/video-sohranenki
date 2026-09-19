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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DayCollectionAdapter(
    private val items: List<DayCollection>,
    private val onClick: (DayCollection) -> Unit
) : RecyclerView.Adapter<DayCollectionAdapter.Holder>() {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context

        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8))
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14))
            background = rounded("#151120", 18f)
        }

        val hero = FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#2D1B52"),
                    Color.parseColor("#171126"),
                    Color.parseColor("#0F0C18")
                )
            ).apply { cornerRadius = dp(context, 16).toFloat() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 132)
            )
        }

        val bigDate = TextView(context).apply {
            textSize = 34f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        hero.addView(bigDate, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val badge = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dp(context, 9), dp(context, 5), dp(context, 9), dp(context, 5))
            background = rounded("#251A45", 10f)
        }
        hero.addView(badge, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.TOP
        ).apply {
            marginEnd = dp(context, 10)
            topMargin = dp(context, 10)
        })

        val title = TextView(context).apply {
            textSize = 18f
            setTextColor(Color.parseColor("#F7F5FF"))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(context, 12), 0, 0)
        }

        val meta = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#9E96AD"))
            setPadding(0, dp(context, 5), 0, 0)
        }

        val action = TextView(context).apply {
            text = "Смотреть сборник  ›"
            textSize = 14f
            setTextColor(Color.parseColor("#B89AFF"))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(context, 10), 0, 0)
        }

        card.addView(hero)
        card.addView(title)
        card.addView(meta)
        card.addView(action)
        outer.addView(card)

        return Holder(outer, bigDate, badge, title, meta)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.bigDate.text = item.date.format(DateTimeFormatter.ofPattern("dd.MM"))
        holder.badge.text = "${item.videos.size} видео"
        holder.title.text = dayTitle(item.date)
        holder.meta.text = buildMeta(item)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    private fun dayTitle(date: LocalDate): String {
        val today = LocalDate.now()
        val formatted = date.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))
        return when (date) {
            today -> "Сегодня • $formatted"
            today.minusDays(1) -> "Вчера • $formatted"
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

        return listOfNotNull(
            "${item.videos.size} видео",
            durationText,
            "@t2x2_video"
        ).joinToString(" • ")
    }

    private fun rounded(color: String, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = radiusDp * 2.5f
        }

    class Holder(
        view: View,
        val bigDate: TextView,
        val badge: TextView,
        val title: TextView,
        val meta: TextView
    ) : RecyclerView.ViewHolder(view)
}
