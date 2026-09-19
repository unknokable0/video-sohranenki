package com.unknokable.videosohranenki

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
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
    private val palette: ThemePalette,
    private val animationsEnabled: Boolean,
    private val onClick: (DayCollection) -> Unit
) : RecyclerView.Adapter<DayCollectionAdapter.Holder>() {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context

        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 7), dp(context, 16), dp(context, 7))
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14))
            background = rounded(palette.surface, dp(context, 20).toFloat())
        }

        val hero = FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    if (palette === AppThemes.Light) Color.parseColor("#EEE7FF") else Color.parseColor("#2D1B52"),
                    if (palette === AppThemes.Light) Color.parseColor("#F8F5FF") else Color.parseColor("#171126")
                )
            ).apply { cornerRadius = dp(context, 17).toFloat() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 128)
            )
        }

        val bigDate = TextView(context).apply {
            textSize = 34f
            setTextColor(palette.text)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val badge = TextView(context).apply {
            textSize = 12f
            setTextColor(if (palette === AppThemes.Light) palette.accent else Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5))
            background = rounded(
                if (palette === AppThemes.Light) Color.parseColor("#F0E9FF") else Color.parseColor("#251A45"),
                dp(context, 11).toFloat()
            )
        }

        hero.addView(bigDate, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
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
            setTextColor(palette.text)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(context, 12), 0, 0)
        }

        val meta = TextView(context).apply {
            textSize = 13f
            setTextColor(palette.muted)
            setPadding(0, dp(context, 5), 0, 0)
        }

        val action = TextView(context).apply {
            text = "Смотреть сборник  ›"
            textSize = 14f
            setTextColor(palette.accent)
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

        if (animationsEnabled) {
            holder.itemView.alpha = 0f
            holder.itemView.translationY = dp(holder.itemView.context, 10).toFloat()
            holder.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180)
                .setStartDelay((position.coerceAtMost(7) * 18L))
                .start()

            holder.itemView.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.988f).scaleY(0.988f).setDuration(70).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                }
                false
            }
        }

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

        return listOfNotNull("${item.videos.size} видео", durationText, "@t2x2_video")
            .joinToString(" • ")
    }

    private fun rounded(color: Int, radiusPx: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx
        }

    class Holder(
        view: View,
        val bigDate: TextView,
        val badge: TextView,
        val title: TextView,
        val meta: TextView
    ) : RecyclerView.ViewHolder(view)
}
