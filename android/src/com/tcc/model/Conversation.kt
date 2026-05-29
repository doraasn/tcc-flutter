package com.tcc.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "新对话",
    val model: String = "deepseek-v4-flash",
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    val messages: MutableList<Message> = mutableListOf()

    fun toJson(): JSONObject {
        val msgArray = JSONArray()
        for (msg in messages) {
            msgArray.put(msg.toJson())
        }
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("model", model)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
            put("messages", msgArray)
        }
    }

    fun addMessage(msg: Message) {
        messages.add(msg)
        updatedAt = System.currentTimeMillis()
    }

    companion object {
        @JvmStatic
        fun fromJson(json: JSONObject): Conversation {
            val conv = Conversation(
                id = json.optString("id", UUID.randomUUID().toString()),
                title = json.optString("title", "新对话"),
                model = json.optString("model", "deepseek-v4-flash"),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
            )
            val msgArray = json.optJSONArray("messages")
            if (msgArray != null) {
                for (i in 0 until msgArray.length()) {
                    val msgObj = msgArray.optJSONObject(i)
                    if (msgObj != null) {
                        conv.messages.add(Message.fromJson(msgObj))
                    }
                }
            }
            return conv
        }

        @JvmStatic
        fun generateTitle(messages: List<Message>): String {
            for (msg in messages) {
                if (msg.role == "user") {
                    val text = msg.content.trim()
                    return if (text.length > 30) text.substring(0, 30) + "…" else text
                }
            }
            return "新对话"
        }
    }
}
