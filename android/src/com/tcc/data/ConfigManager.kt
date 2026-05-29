package com.tcc.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ConfigManager private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("mcc_config", Context.MODE_PRIVATE)

    fun getApiKey(): String = prefs.getString("api_key", "") ?: ""
    fun setApiKey(value: String) { prefs.edit().putString("api_key", value).apply() }

    fun getBaseUrl(): String = prefs.getString("base_url", "https://api.deepseek.com/anthropic") ?: "https://api.deepseek.com/anthropic"
    fun setBaseUrl(value: String) { prefs.edit().putString("base_url", value).apply() }

    fun getModel(): String = prefs.getString("model", "deepseek-v4-flash") ?: "deepseek-v4-flash"
    fun setModel(value: String) { prefs.edit().putString("model", value).apply() }

    fun getSystemPrompt(): String = prefs.getString("system_prompt", "") ?: ""
    fun setSystemPrompt(value: String) { prefs.edit().putString("system_prompt", value).apply() }

    fun getMaxTokens(): Int = prefs.getInt("max_tokens", 4096)
    fun setMaxTokens(value: Int) { prefs.edit().putInt("max_tokens", value).apply() }

    fun getFontSize(): Int = prefs.getInt("font_size", 14)
    fun setFontSize(value: Int) { prefs.edit().putInt("font_size", value).apply() }

    // WebDAV
    fun getWebDavUrl(): String = prefs.getString("webdav_url", "") ?: ""
    fun setWebDavUrl(value: String) { prefs.edit().putString("webdav_url", value).apply() }

    fun getWebDavUser(): String = prefs.getString("webdav_user", "") ?: ""
    fun setWebDavUser(value: String) { prefs.edit().putString("webdav_user", value).apply() }

    fun getWebDavPass(): String = prefs.getString("webdav_pass", "") ?: ""
    fun setWebDavPass(value: String) { prefs.edit().putString("webdav_pass", value).apply() }

    fun getAll(): JSONObject {
        return JSONObject().apply {
            put("api_key", getApiKey())
            put("base_url", getBaseUrl())
            put("model", getModel())
            put("system_prompt", getSystemPrompt())
            put("max_tokens", getMaxTokens())
            put("font_size", getFontSize())
            put("webdav_url", getWebDavUrl())
            put("webdav_user", getWebDavUser())
            put("prompt_templates", getPromptTemplatesRaw())
        }
    }

    fun updateFromJson(json: JSONObject) {
        val editor = prefs.edit()
        if (json.has("api_key")) editor.putString("api_key", json.optString("api_key", ""))
        if (json.has("base_url")) editor.putString("base_url", json.optString("base_url", "https://api.deepseek.com/anthropic"))
        if (json.has("model")) editor.putString("model", json.optString("model", "deepseek-v4-flash"))
        if (json.has("system_prompt")) editor.putString("system_prompt", json.optString("system_prompt", ""))
        if (json.has("max_tokens")) editor.putInt("max_tokens", json.optInt("max_tokens", 4096))
        if (json.has("font_size")) editor.putInt("font_size", json.optInt("font_size", 14))
        if (json.has("webdav_url")) editor.putString("webdav_url", json.optString("webdav_url", ""))
        if (json.has("webdav_user")) editor.putString("webdav_user", json.optString("webdav_user", ""))
        if (json.has("webdav_pass")) editor.putString("webdav_pass", json.optString("webdav_pass", ""))
        editor.apply()

        if (json.has("prompt_templates")) {
            val templates = json.optJSONArray("prompt_templates")
            if (templates != null) {
                prefs.edit().putString("prompt_templates", templates.toString()).apply()
            }
        }
    }

    fun getPromptTemplates(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val raw = getPromptTemplatesRaw()
        for (i in 0 until raw.length()) {
            val obj = raw.optJSONObject(i)
            if (obj != null) {
                val name = obj.optString("name", "")
                val content = obj.optString("content", "")
                if (name.isNotEmpty()) {
                    result.add(Pair(name, content))
                }
            }
        }
        return result
    }

    fun savePromptTemplate(name: String, content: String) {
        val raw = getPromptTemplatesRaw()
        // Replace existing entry with same name, or append
        var found = false
        for (i in 0 until raw.length()) {
            val obj = raw.optJSONObject(i)
            if (obj != null && obj.optString("name", "") == name) {
                obj.put("content", content)
                found = true
                break
            }
        }
        if (!found) {
            raw.put(JSONObject().apply {
                put("name", name)
                put("content", content)
            })
        }
        prefs.edit().putString("prompt_templates", raw.toString()).apply()
    }

    fun deletePromptTemplate(name: String) {
        val raw = getPromptTemplatesRaw()
        val updated = JSONArray()
        for (i in 0 until raw.length()) {
            val obj = raw.optJSONObject(i)
            if (obj != null && obj.optString("name", "") != name) {
                updated.put(obj)
            }
        }
        prefs.edit().putString("prompt_templates", updated.toString()).apply()
    }

    private fun getPromptTemplatesRaw(): JSONArray {
        val json = prefs.getString("prompt_templates", "[]") ?: "[]"
        return try {
            JSONArray(json)
        } catch (e: Exception) {
            JSONArray()
        }
    }

    companion object {
        @Volatile
        private var instance: ConfigManager? = null

        @JvmStatic
        fun getInstance(context: Context): ConfigManager {
            return instance ?: synchronized(this) {
                instance ?: ConfigManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
