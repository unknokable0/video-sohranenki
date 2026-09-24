package com.unknokable.sohrai

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var store: ChatStore
    private lateinit var adapter: ChatAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var input: EditText
    private lateinit var send: TextView
    private lateinit var status: TextView

    private val api = ApiClient()
    private var streaming = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.parseColor("#0B0910")
        window.navigationBarColor = Color.parseColor("#0B0910")

        store = ChatStore(this)
        messages += store.load()

        if (messages.isEmpty()) {
            messages += ChatMessage(
                role = "assistant",
                text = "Привет. Это один постоянный чат. Пиши что угодно — я буду отвечать коротко, аккуратно и проверять свежую информацию, когда это нужно."
            )
        }

        setContentView(buildUi())
        recycler.post { scrollToBottom() }
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0910"))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(11))
        }

        header.addView(TextView(this).apply {
            text = "SOHR AI"
            textSize = 20f
            setTextColor(Color.parseColor("#F7F2FF"))
            setTypeface(typeface, Typeface.BOLD)
        })

        status = TextView(this).apply {
            text = "Готов"
            textSize = 12f
            setTextColor(Color.parseColor("#968AA8"))
            setPadding(0, dp(3), 0, 0)
        }
        header.addView(status)
        root.addView(header)

        recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, dp(4), 0, dp(8))
        }
        adapter = ChatAdapter(messages)
        recycler.adapter = adapter

        root.addView(
            recycler,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(dp(12), dp(8), dp(12), dp(14))
        }

        val inputShell = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(3), dp(5), dp(3))
            background = rounded("#17131F", 24f)
        }

        input = EditText(this).apply {
            hint = "Сообщение…"
            setHintTextColor(Color.parseColor("#786E86"))
            setTextColor(Color.parseColor("#F7F2FF"))
            textSize = 15.5f
            minLines = 1
            maxLines = 6
            isSingleLine = false
            background = null
            setPadding(dp(10), dp(8), dp(8), dp(8))
            imeOptions = EditorInfo.IME_ACTION_SEND
            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE

            setOnEditorActionListener { _, actionId, event ->
                val sendAction =
                    actionId == EditorInfo.IME_ACTION_SEND ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)

                if (sendAction && !eventHasShift(event)) {
                    sendMessage()
                    true
                } else {
                    false
                }
            }
        }

        send = TextView(this).apply {
            text = "↑"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded("#8B6CFF", 22f)
            setOnClickListener { sendMessage() }
        }

        inputShell.addView(
            input,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        inputShell.addView(send, LinearLayout.LayoutParams(dp(44), dp(44)))

        composer.addView(
            inputShell,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(composer)
        return root
    }

    private fun sendMessage() {
        if (streaming) return
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return

        input.setText("")
        streaming = true
        send.alpha = 0.45f
        status.text = "Думаю…"

        messages += ChatMessage("user", text)
        val assistantIndex = messages.size
        messages += ChatMessage("assistant", "")
        adapter.notifyItemRangeInserted(assistantIndex - 1, 2)
        scrollToBottom()

        store.save(messages)
        val requestHistory = messages.dropLast(1).map { it.copy() }

        lifecycleScope.launch {
            try {
                api.stream(requestHistory) { event ->
                    withContext(Dispatchers.Main) {
                        when (event) {
                            is ChatEvent.Status -> {
                                if (event.text.isNotBlank()) status.text = event.text
                            }
                            is ChatEvent.Delta -> {
                                messages[assistantIndex].text += event.text
                                adapter.notifyItemChanged(assistantIndex)
                                scrollToBottom()
                            }
                            is ChatEvent.Done -> {
                                status.text = "Готов"
                            }
                        }
                    }
                }

                if (messages[assistantIndex].text.isBlank()) {
                    messages[assistantIndex].text = "Ответ не пришёл. Попробуй отправить сообщение ещё раз."
                    adapter.notifyItemChanged(assistantIndex)
                }
                store.save(messages)
            } catch (error: Throwable) {
                messages[assistantIndex].text =
                    "Не удалось подключиться к AI-серверу. " +
                    (error.message ?: "Неизвестная ошибка.")
                adapter.notifyItemChanged(assistantIndex)
                status.text = "Ошибка подключения"
                store.save(messages)
            } finally {
                streaming = false
                send.alpha = 1f
                if (status.text == "Думаю…") status.text = "Готов"
            }
        }
    }

    private fun scrollToBottom() {
        if (messages.isNotEmpty()) {
            recycler.scrollToPosition(messages.lastIndex)
        }
    }

    private fun eventHasShift(event: KeyEvent?): Boolean =
        event?.isShiftPressed == true

    private fun rounded(color: String, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = radiusDp * resources.displayMetrics.density
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
