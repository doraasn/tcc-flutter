package com.tcc.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// 对话数据模型
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "新对话",
    val model: String = "deepseek-v4-flash",
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var claudeSessionId: String? = null,
    var usageStats: UsageStats = UsageStats()
) {
    val messages: MutableList<Message> = mutableListOf()

    // 转为 JSON 对象
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
            put("claudeSessionId", claudeSessionId ?: "")
            put("messages", msgArray)
            put("usageStats", usageStats.toJson())
        }
    }

    // 添加消息并更新时间戳
    fun addMessage(msg: Message) {
        messages.add(msg)
        updatedAt = System.currentTimeMillis()
    }

    companion object {
        // 从 JSON 解析对话
        @JvmStatic
        fun fromJson(json: JSONObject): Conversation {
            val conv = Conversation(
                id = json.optString("id", UUID.randomUUID().toString()),
                title = json.optString("title", "新对话"),
                model = json.optString("model", "deepseek-v4-flash"),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                claudeSessionId = json.optString("claudeSessionId", "").ifEmpty { null },
                usageStats = UsageStats.fromJson(json.optJSONObject("usageStats") ?: JSONObject())
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

        // 从用户消息生成对话标题
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
