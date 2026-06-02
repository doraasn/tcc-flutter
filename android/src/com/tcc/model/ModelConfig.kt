package com.tcc.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// 供应商配置（一组完整的环境变量 + 多个模型槽位）
data class Provider(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val preset: String = "",           // "deepseek" | "anthropic" | ""
    val env: MutableMap<String, String> = mutableMapOf(),
    val models: MutableMap<String, ModelSlot> = mutableMapOf()
) {
    companion object {
        // 内置 DeepSeek 预设
        fun deepSeekPreset(): Provider = Provider(
            name = "DeepSeek",
            preset = "deepseek",
            env = mutableMapOf(
                "ANTHROPIC_BASE_URL" to "https://api.deepseek.com/anthropic",
                "ANTHROPIC_AUTH_TOKEN" to "",
                "ANTHROPIC_MODEL" to "deepseek-v4-pro[1m]",
                "ANTHROPIC_DEFAULT_OPUS_MODEL" to "deepseek-v4-pro[1m]",
                "ANTHROPIC_DEFAULT_SONNET_MODEL" to "deepseek-v4-pro[1m]",
                "ANTHROPIC_DEFAULT_HAIKU_MODEL" to "deepseek-v4-flash",
                "CLAUDE_CODE_SUBAGENT_MODEL" to "deepseek-v4-flash",
                "CLAUDE_CODE_EFFORT_LEVEL" to "max"
            ),
            models = mutableMapOf(
                "ANTHROPIC_MODEL" to ModelSlot("默认", "deepseek-v4-pro[1m]", 8192),
                "ANTHROPIC_DEFAULT_OPUS_MODEL" to ModelSlot("Opus", "deepseek-v4-pro[1m]", 8192),
                "ANTHROPIC_DEFAULT_SONNET_MODEL" to ModelSlot("Sonnet", "deepseek-v4-pro[1m]", 8192),
                "ANTHROPIC_DEFAULT_HAIKU_MODEL" to ModelSlot("Haiku", "deepseek-v4-flash", 4096)
            )
        )

        // 内置 Mimo 预设
        fun mimoPreset(): Provider = Provider(
            name = "Mimo",
            preset = "mimo",
            env = mutableMapOf(
                "ANTHROPIC_BASE_URL" to "https://token-plan-cn.xiaomimimo.com/anthropic",
                "ANTHROPIC_AUTH_TOKEN" to "",
                "ANTHROPIC_MODEL" to "mimo-v2.5",
                "ANTHROPIC_DEFAULT_OPUS_MODEL" to "mimo-v2.5",
                "ANTHROPIC_DEFAULT_SONNET_MODEL" to "mimo-v2.5",
                "ANTHROPIC_DEFAULT_HAIKU_MODEL" to "mimo-v2.5",
                "API_TIMEOUT_MS" to "3000000"
            ),
            models = mutableMapOf(
                "ANTHROPIC_MODEL" to ModelSlot("默认", "mimo-v2.5", 8192),
                "ANTHROPIC_DEFAULT_OPUS_MODEL" to ModelSlot("Opus", "mimo-v2.5", 8192),
                "ANTHROPIC_DEFAULT_SONNET_MODEL" to ModelSlot("Sonnet", "mimo-v2.5", 8192),
                "ANTHROPIC_DEFAULT_HAIKU_MODEL" to ModelSlot("Haiku", "mimo-v2.5", 4096)
            )
        )

        // 内置 Claude 官方预设
        fun anthropicPreset(): Provider = Provider(
            name = "Claude 官方",
            preset = "anthropic",
            env = mutableMapOf(
                "ANTHROPIC_BASE_URL" to "https://api.anthropic.com",
                "ANTHROPIC_AUTH_TOKEN" to "",
                "ANTHROPIC_MODEL" to "claude-sonnet-4-20250514",
                "ANTHROPIC_DEFAULT_OPUS_MODEL" to "claude-opus-4-20250514",
                "ANTHROPIC_DEFAULT_SONNET_MODEL" to "claude-sonnet-4-20250514",
                "ANTHROPIC_DEFAULT_HAIKU_MODEL" to "claude-haiku-4-20250514",
                "CLAUDE_CODE_SUBAGENT_MODEL" to "claude-haiku-4-20250514",
                "CLAUDE_CODE_EFFORT_LEVEL" to "max"
            ),
            models = mutableMapOf(
                "ANTHROPIC_MODEL" to ModelSlot("默认", "claude-sonnet-4-20250514", 8192),
                "ANTHROPIC_DEFAULT_OPUS_MODEL" to ModelSlot("Opus", "claude-opus-4-20250514", 8192),
                "ANTHROPIC_DEFAULT_SONNET_MODEL" to ModelSlot("Sonnet", "claude-sonnet-4-20250514", 8192),
                "ANTHROPIC_DEFAULT_HAIKU_MODEL" to ModelSlot("Haiku", "claude-haiku-4-20250514", 4096)
            )
        )

        fun fromJson(json: JSONObject): Provider {
            val env = mutableMapOf<String, String>()
            val envJson = json.optJSONObject("env")
            if (envJson != null) {
                for (k in envJson.keys()) env[k] = envJson.optString(k, "")
            }
            val models = mutableMapOf<String, ModelSlot>()
            val modelsJson = json.optJSONObject("models")
            if (modelsJson != null) {
                for (k in modelsJson.keys()) {
                    models[k] = ModelSlot.fromJson(modelsJson.getJSONObject(k))
                }
            }
            return Provider(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", ""),
                preset = json.optString("preset", ""),
                env = env,
                models = models
            )
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("preset", preset)
        put("env", JSONObject(env))
        put("models", JSONObject(models.mapValues { it.value.toJson() }))
    }
}

// 单个模型槽位配置
data class ModelSlot(
    val displayName: String,
    val model: String,
    val maxTokens: Int = 4096,
    val pricePer1mInput: Double = 0.0,
    val pricePer1mOutput: Double = 0.0
) {
    companion object {
        fun fromJson(json: JSONObject): ModelSlot = ModelSlot(
            displayName = json.optString("displayName", ""),
            model = json.optString("model", ""),
            maxTokens = json.optInt("maxTokens", 4096),
            pricePer1mInput = json.optDouble("pricePer1mInput", 0.0),
            pricePer1mOutput = json.optDouble("pricePer1mOutput", 0.0)
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("displayName", displayName)
        put("model", model)
        put("maxTokens", maxTokens)
        put("pricePer1mInput", pricePer1mInput)
        put("pricePer1mOutput", pricePer1mOutput)
    }
}

// 使用统计（每个 Provider + 模型名）
data class UsageStats(
    val providerId: String = "",
    val modelName: String = "",
    var totalInputTokens: Long = 0,
    var totalOutputTokens: Long = 0,
    var totalCostUsd: Double = 0.0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("providerId", providerId)
        put("modelName", modelName)
        put("totalInputTokens", totalInputTokens)
        put("totalOutputTokens", totalOutputTokens)
        put("totalCostUsd", totalCostUsd)
    }

    companion object {
        fun fromJson(json: JSONObject): UsageStats = UsageStats(
            providerId = json.optString("providerId", ""),
            modelName = json.optString("modelName", ""),
            totalInputTokens = json.optLong("totalInputTokens", 0),
            totalOutputTokens = json.optLong("totalOutputTokens", 0),
            totalCostUsd = json.optDouble("totalCostUsd", 0.0)
        )
    }
}
