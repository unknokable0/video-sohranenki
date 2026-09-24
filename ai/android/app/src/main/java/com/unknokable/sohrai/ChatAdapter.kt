package com.unknokable.sohrai

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon

class ChatAdapter(
    private val messages: List<ChatMessage>
) : RecyclerView.Adapter<ChatAdapter.MessageHolder>() {

    companion object {
        private const val USER = 1
        private const val ASSISTANT = 2
    }

    private val markwonCache = mutableMapOf<Int, Markwon>()

    class MessageHolder(
        val shell: FrameLayout,
        val text: TextView
    ) : RecyclerView.ViewHolder(shell)

    override fun getItemViewType(position: Int): Int =
        if (messages[position].role == "user") USER else ASSISTANT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageHolder {
        val context = parent.context
        val shell = FrameLayout(context).apply {
            setPadding(dp(14), dp(5), dp(14), dp(5))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val text = TextView(context).apply {
            textSize = 15.5f
            setLineSpacing(dp(3).toFloat(), 1.04f)
            includeFontPadding = false
            setTextIsSelectable(viewType == ASSISTANT)
            setPadding(dp(15), dp(12), dp(15), dp(12))
        }

        val params = FrameLayout.LayoutParams(
            if (viewType == USER) dp(320) else ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = if (viewType == USER) Gravity.END else Gravity.START
        }
        shell.addView(text, params)

        return MessageHolder(shell, text)
    }

    override fun onBindViewHolder(holder: MessageHolder, position: Int) {
        val message = messages[position]
        val user = message.role == "user"

        holder.text.setTextColor(Color.parseColor(if (user) "#FFFFFF" else "#F4F0FA"))
        holder.text.typeface = Typeface.create("sans", Typeface.NORMAL)
        holder.text.background = rounded(
            if (user) "#7C5CFC" else "#17131F",
            if (user) 20f else 18f
        )

        if (user) {
            holder.text.text = message.text
        } else {
            val markwon = markwonCache.getOrPut(holder.text.context.hashCode()) {
                Markwon.create(holder.text.context)
            }
            markwon.setMarkdown(holder.text, message.text.ifBlank { " " })
        }
    }

    override fun getItemCount(): Int = messages.size

    private fun rounded(color: String, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = radiusDp * 3f
        }

    private fun dp(value: Int): Int =
        (value * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
