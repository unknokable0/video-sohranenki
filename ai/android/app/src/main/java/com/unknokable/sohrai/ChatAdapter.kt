package com.unknokable.sohrai

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon

class ChatAdapter(
    private val messages: List<ChatMessage>,
    private var palette: ThemePalette
) : RecyclerView.Adapter<ChatAdapter.MessageHolder>() {

    companion object {
        private const val USER = 1
        private const val ASSISTANT = 2
    }

    private val markwonCache = mutableMapOf<Int, Markwon>()
    private val animated = mutableSetOf<Int>()

    class MessageHolder(
        val shell: FrameLayout,
        val text: TextView
    ) : RecyclerView.ViewHolder(shell)

    fun updatePalette(value: ThemePalette) {
        palette = value
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (messages[position].role == "user") USER else ASSISTANT

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MessageHolder {
        val context = parent.context

        val shell = FrameLayout(context).apply {
            setPadding(dp(14), dp(4), dp(14), dp(4))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val text = TextView(context).apply {
            textSize = 15.8f
            setLineSpacing(dp(3).toFloat(), 1.04f)
            includeFontPadding = false
            setTextIsSelectable(true)
            movementMethod = LinkMovementMethod.getInstance()
        }

        val params = FrameLayout.LayoutParams(
            if (viewType == USER) dp(320)
            else ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = if (viewType == USER) Gravity.END else Gravity.START
        }

        shell.addView(text, params)
        return MessageHolder(shell, text)
    }

    override fun onBindViewHolder(
        holder: MessageHolder,
        position: Int
    ) {
        val message = messages[position]
        val user = message.role == "user"

        holder.text.setTextColor(palette.text)
        holder.text.typeface = Typeface.create("sans", Typeface.NORMAL)

        if (user) {
            holder.text.setPadding(dp(14), dp(10), dp(14), dp(10))
            holder.text.background = rounded(palette.surface, 20f)
            holder.text.text = message.text
        } else {
            holder.text.setPadding(dp(3), dp(10), dp(3), dp(10))
            holder.text.background = null
            val markwon = markwonCache.getOrPut(holder.text.context.hashCode()) {
                Markwon.create(holder.text.context)
            }
            markwon.setMarkdown(holder.text, message.text.ifBlank { " " })
        }

        if (animated.add(position)) {
            holder.shell.alpha = 0f
            holder.shell.translationY = dp(6).toFloat()
            holder.shell.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180L)
                .start()
        }
    }

    override fun getItemCount(): Int = messages.size

    private fun rounded(
        color: Int,
        radiusDp: Float
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp *
                android.content.res.Resources
                    .getSystem()
                    .displayMetrics
                    .density
        }

    private fun dp(value: Int): Int =
        (value *
            android.content.res.Resources
                .getSystem()
                .displayMetrics
                .density
        ).toInt()
}
