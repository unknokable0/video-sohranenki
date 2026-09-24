package com.unknokable.sohrai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ChatStore(context: Context) {
    private val prefs =
        context.getSharedPreferences(
            "sohr_ai_chat_v6",
            Context.MODE_PRIVATE
        )

    fun load(): MutableList<ChatMessage> {
        val raw =
            prefs.getString(
                "messages",
                null
            ) ?: return mutableListOf()

        return runCatching {
            val array = JSONArray(raw)

            MutableList(array.length()) {
                index ->
                val item =
                    array.getJSONObject(index)

                ChatMessage(
                    role =
                        item.optString(
                            "role",
                            "user"
                        ),
                    text =
                        item.optString(
                            "text",
                            ""
                        )
                )
            }
                .filter {
                    it.text.isNotBlank()
                }
                .toMutableList()
        }.getOrDefault(
            mutableListOf()
        )
    }

    fun save(
        messages: List<ChatMessage>
    ) {
        val kept =
            messages
                .filter {
                    it.text.isNotBlank()
                }
                .takeLast(400)

        val array = JSONArray()

        kept.forEach { message ->
            array.put(
                JSONObject()
                    .put(
                        "role",
                        message.role
                    )
                    .put(
                        "text",
                        message.text
                    )
            )
        }

        prefs.edit()
            .putString(
                "messages",
                array.toString()
            )
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove("messages")
            .apply()
    }
}
