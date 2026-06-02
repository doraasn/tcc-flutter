package com.tcc.data

import android.content.Context
import com.tcc.model.ModelSlot
import com.tcc.model.Provider
import com.tcc.model.UsageStats
import org.json.JSONArray
import org.json.JSONObject

// 配置管理器 — 基于 Provider 的模型配置体系
class ConfigManager private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("mcc_config", Context.MODE_PRIVATE)

    // ---- 通用设置 ----
    fun getFontSize(): Int = prefs.getInt("font_size", 14)
    fun setFontSize(value: Int) { prefs.edit().putInt("font_size", value).apply() }
    fun getSystemPrompt(): String = prefs.getString("system_prompt", "") ?: ""
    fun setSystemPrompt(value: String) { prefs.edit().putString("system_prompt", value).apply() }

    fun getWebDavUrl(): String = prefs.getString("webdav_url", "") ?: ""
    fun setWebDavUrl(value: String) { prefs.edit().putString("webdav_url", value).apply() }
    fun getWebDavUser(): String = prefs.getString("webdav_user", "") ?: ""
    fun setWebDavUser(value: String) { prefs.edit().putString("webdav_user", value).apply() }
    fun getWebDavPass(): String = prefs.getString("webdav_pass", "") ?: ""
    fun setWebDavPass(value: String) { prefs.edit().putString("webdav_pass", value).apply() }

    // ---- Provider 管理 ----

    private val providersKey = "providers"
    private val activeProviderIdKey = "active_provider_id"
    private val activeModelKeyKey = "active_model_key"

    fun getProviders(): MutableList<Provider> {
        val json = prefs.getString(providersKey, null) ?: return defaultProviders()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<Provider>()
            for (i in 0 until arr.length()) list.add(Provider.fromJson(arr.getJSONObject(i)))
            list
        } catch (_: Exception) { defaultProviders() }
    }

    fun getActiveProvider(): Provider? {
        val id = prefs.getString(activeProviderIdKey, null)
        return getProviders().find { it.id == id } ?: getProviders().firstOrNull()
    }

    fun getActiveModelKey(): String =
        prefs.getString(activeModelKeyKey, "ANTHROPIC_MODEL") ?: "ANTHROPIC_MODEL"

    fun getActiveModelSlot(): ModelSlot? {
        val provider = getActiveProvider() ?: return null
        return provider.models[getActiveModelKey()] ?: provider.models["ANTHROPIC_MODEL"]
    }

    fun setActiveModelKey(key: String) { prefs.edit().putString(activeModelKeyKey, key).apply() }

    fun switchProvider(providerId: String) {
        prefs.edit().putString(activeProviderIdKey, providerId).apply()
        prefs.edit().putString(activeModelKeyKey, "ANTHROPIC_MODEL").apply()
    }

    fun switchModel(providerId: String, modelKey: String) {
        prefs.edit().putString(activeProviderIdKey, providerId).apply()
        prefs.edit().putString(activeModelKeyKey, modelKey).apply()
    }

    fun saveProvider(provider: Provider) {
        val list = getProviders().toMutableList()
        val idx = list.indexOfFirst { it.id == provider.id }
        if (idx >= 0) list[idx] = provider else list.add(provider)
        saveProviders(list)
    }

    fun deleteProvider(id: String) {
        saveProviders(getProviders().filter { it.id != id })
    }

    private fun saveProviders(list: List<Provider>) {
        val arr = JSONArray()
        for (p in list) arr.put(p.toJson())
        prefs.edit().putString(providersKey, arr.toString()).apply()
    }

    private fun defaultProviders(): MutableList<Provider> {
        val mm = Provider.mimoPreset()
        val ds = Provider.deepSeekPreset()
        val list = mutableListOf(mm, ds, Provider.anthropicPreset())
        saveProviders(list)
        prefs.edit().putString(activeProviderIdKey, mm.id).apply()
        return list
    }

    // ---- 使用统计 ----

    fun getUsageStats(): MutableList<UsageStats> {
        val json = prefs.getString("usage_stats", null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<UsageStats>()
            for (i in 0 until arr.length()) list.add(UsageStats.fromJson(arr.getJSONObject(i)))
            list
        } catch (_: Exception) { mutableListOf() }
    }

    fun addUsageTokens(providerId: String, modelName: String, inputTokens: Long, outputTokens: Long, costUsd: Double) {
        val list = getUsageStats().toMutableList()
        val idx = list.indexOfFirst { it.providerId == providerId && it.modelName == modelName }
        if (idx >= 0) {
            list[idx].totalInputTokens += inputTokens
            list[idx].totalOutputTokens += outputTokens
            list[idx].totalCostUsd += costUsd
        } else {
            list.add(UsageStats(providerId, modelName, inputTokens, outputTokens, costUsd))
        }
        val arr = JSONArray()
        for (s in list) arr.put(s.toJson())
        prefs.edit().putString("usage_stats", arr.toString()).apply()
    }

    fun getTotalCost(): Double = getUsageStats().sumOf { it.totalCostUsd }

    // ---- 提示词模板 ----
    fun getPromptTemplates(): List<Pair<String, String>> {
        val raw = getPromptTemplatesRaw()
        val result = mutableListOf<Pair<String, String>>()
        for (i in 0 until raw.length()) {
            val obj = raw.optJSONObject(i)
            if (obj != null) {
                val n = obj.optString("name", ""); val c = obj.optString("content", "")
                if (n.isNotEmpty()) result.add(Pair(n, c))
            }
        }
        return result
    }

    fun getPromptTemplatesRaw(): JSONArray {
        val json = prefs.getString("prompt_templates", "[]") ?: "[]"
        return try { JSONArray(json) } catch (_: Exception) { JSONArray() }
    }

    fun savePromptTemplatesRaw(raw: JSONArray) {
        prefs.edit().putString("prompt_templates", raw.toString()).apply()
    }

    // ---- 导出/导入（WebDAV 备份用） ----
    fun getExportData(): JSONObject = JSONObject().apply {
        put("font_size", getFontSize())
        put("system_prompt", getSystemPrompt())
        put("webdav_url", getWebDavUrl())
        put("webdav_user", getWebDavUser())
        put("prompt_templates", getPromptTemplatesRaw())
        put("providers", JSONArray(getProviders().map { it.toJson() }))
        put("active_provider_id", prefs.getString(activeProviderIdKey, ""))
        put("active_model_key", prefs.getString(activeModelKeyKey, ""))
    }

    fun importFromJson(json: JSONObject) {
        if (json.has("font_size")) setFontSize(json.optInt("font_size", 14))
        if (json.has("system_prompt")) setSystemPrompt(json.optString("system_prompt", ""))
        if (json.has("webdav_url")) setWebDavUrl(json.optString("webdav_url", ""))
        if (json.has("webdav_user")) setWebDavUser(json.optString("webdav_user", ""))
        if (json.has("prompt_templates")) {
            val t = json.optJSONArray("prompt_templates")
            if (t != null) savePromptTemplatesRaw(t)
        }
        if (json.has("providers")) {
            val arr = json.optJSONArray("providers")
            if (arr != null) {
                val list = mutableListOf<Provider>()
                for (i in 0 until arr.length()) list.add(Provider.fromJson(arr.getJSONObject(i)))
                saveProviders(list)
            }
        }
        if (json.has("active_provider_id")) {
            prefs.edit().putString(activeProviderIdKey, json.optString("active_provider_id", "")).apply()
        }
    }

    companion object {
        @Volatile
        private var instance: ConfigManager? = null

        @JvmStatic
        fun getInstance(context: Context): ConfigManager =
            instance ?: synchronized(this) {
                instance ?: ConfigManager(context.applicationContext).also { instance = it }
            }
    }
}
