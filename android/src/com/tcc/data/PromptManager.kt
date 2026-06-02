package com.tcc.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// 提示词管理器 - 全局 + 项目级别
class PromptManager(private val context: Context) {

    companion object {
        private const val PREFS_KEY = "prompt_templates"
        private const val PROJECT_PROMPTS_FILE = ".tcc/prompts.json"
    }

    // ---- 全局提示词模板 ----

    fun getGlobalTemplates(): MutableList<PromptTemplate> {
        val raw = ConfigManager.getInstance(context).getPromptTemplatesRaw()
        val list = mutableListOf<PromptTemplate>()
        for (i in 0 until raw.length()) {
            val obj = raw.optJSONObject(i)
            if (obj != null) {
                list.add(PromptTemplate(
                    name = obj.optString("name", ""),
                    content = obj.optString("content", "")
                ))
            }
        }
        return list
    }

    fun saveGlobalTemplate(name: String, content: String) {
        val config = ConfigManager.getInstance(context)
        val raw = config.getPromptTemplatesRaw()
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
        config.savePromptTemplatesRaw(raw)
    }

    fun deleteGlobalTemplate(name: String) {
        val config = ConfigManager.getInstance(context)
        val raw = config.getPromptTemplatesRaw()
        val updated = JSONArray()
        for (i in 0 until raw.length()) {
            val obj = raw.optJSONObject(i)
            if (obj != null && obj.optString("name", "") != name) {
                updated.put(obj)
            }
        }
        config.savePromptTemplatesRaw(updated)
    }

    // ---- 项目提示词 ----

    private fun getProjectPromptsFile(): java.io.File {
        // 尝试在 filesDir 下找项目目录，或用默认
        val home = java.io.File(context.filesDir, "home")
        val projectDir = java.io.File(home, ".tcc")
        projectDir.mkdirs()
        return java.io.File(projectDir, "prompts.json")
    }

    fun getProjectTemplates(): MutableList<PromptTemplate> {
        val file = getProjectPromptsFile()
        if (!file.isFile) return mutableListOf()
        return try {
            val json = JSONArray(file.readText())
            val list = mutableListOf<PromptTemplate>()
            for (i in 0 until json.length()) {
                val obj = json.optJSONObject(i)
                if (obj != null) {
                    list.add(PromptTemplate(
                        name = obj.optString("name", ""),
                        content = obj.optString("content", "")
                    ))
                }
            }
            list
        } catch (_: Exception) { mutableListOf() }
    }

    fun saveProjectTemplate(name: String, content: String) {
        val list = getProjectTemplates()
        val existing = list.indexOfFirst { it.name == name }
        if (existing >= 0) {
            list[existing] = list[existing].copy(content = content)
        } else {
            list.add(PromptTemplate(name, content))
        }
        writeProjectTemplates(list)
    }

    fun deleteProjectTemplate(name: String) {
        val list = getProjectTemplates().filter { it.name != name }.toMutableList()
        writeProjectTemplates(list)
    }

    private fun writeProjectTemplates(list: List<PromptTemplate>) {
        val json = JSONArray()
        for (t in list) {
            json.put(JSONObject().apply {
                put("name", t.name)
                put("content", t.content)
            })
        }
        getProjectPromptsFile().writeText(json.toString(2))
    }
}

data class PromptTemplate(
    val name: String,
    val content: String
)
