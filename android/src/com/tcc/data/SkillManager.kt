package com.tcc.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Skills 和 MCP 服务器管理器
class SkillManager(private val context: Context) {

    companion object {
        private const val PREFS_SKILLS = "skills_config"
        private const val PREFS_MCP = "mcp_servers"
    }

    // ---- Skills 开关 ----

    data class SkillEntry(
        val name: String,
        val enabled: Boolean = true
    )

    fun getSkills(): List<SkillEntry> {
        val prefs = context.getSharedPreferences(PREFS_SKILLS, Context.MODE_PRIVATE)
        val json = prefs.getString("list", "") ?: ""
        if (json.isEmpty()) return defaultSkills()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<SkillEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) {
                    list.add(SkillEntry(
                        name = obj.optString("name", ""),
                        enabled = obj.optBoolean("enabled", true)
                    ))
                }
            }
            list
        } catch (_: Exception) { defaultSkills() }
    }

    fun setSkillEnabled(name: String, enabled: Boolean) {
        val list = getSkills().toMutableList()
        val idx = list.indexOfFirst { it.name == name }
        if (idx >= 0) list[idx] = list[idx].copy(enabled = enabled)
        else list.add(SkillEntry(name, enabled))
        saveSkills(list)
    }

    private fun saveSkills(list: List<SkillEntry>) {
        val arr = JSONArray()
        for (s in list) {
            arr.put(JSONObject().apply {
                put("name", s.name)
                put("enabled", s.enabled)
            })
        }
        context.getSharedPreferences(PREFS_SKILLS, Context.MODE_PRIVATE)
            .edit().putString("list", arr.toString()).apply()
    }

    private fun defaultSkills(): List<SkillEntry> = listOf(
        SkillEntry("lark-cli", true),
        SkillEntry("apk-build", true),
        SkillEntry("tcc-git", false),
        SkillEntry("verify", true),
        SkillEntry("code-review", true),
        SkillEntry("loop", true)
    )

    // ---- MCP 服务器 ----

    data class McpServer(
        val name: String,
        val command: String = "",
        val args: String = "",
        val enabled: Boolean = true
    )

    fun getMcpServers(): List<McpServer> {
        val prefs = context.getSharedPreferences(PREFS_MCP, Context.MODE_PRIVATE)
        val json = prefs.getString("list", "") ?: ""
        if (json.isEmpty()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<McpServer>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) {
                    list.add(McpServer(
                        name = obj.optString("name", ""),
                        command = obj.optString("command", ""),
                        args = obj.optString("args", ""),
                        enabled = obj.optBoolean("enabled", true)
                    ))
                }
            }
            list
        } catch (_: Exception) { emptyList() }
    }

    fun saveMcpServer(server: McpServer) {
        val list = getMcpServers().toMutableList()
        val idx = list.indexOfFirst { it.name == server.name }
        if (idx >= 0) list[idx] = server
        else list.add(server)
        saveMcpServers(list)
    }

    fun deleteMcpServer(name: String) {
        saveMcpServers(getMcpServers().filter { it.name != name })
    }

    private fun saveMcpServers(list: List<McpServer>) {
        val arr = JSONArray()
        for (s in list) {
            arr.put(JSONObject().apply {
                put("name", s.name)
                put("command", s.command)
                put("args", s.args)
                put("enabled", s.enabled)
            })
        }
        context.getSharedPreferences(PREFS_MCP, Context.MODE_PRIVATE)
            .edit().putString("list", arr.toString()).apply()
    }

    // 构建 --settings JSON（传给 claude）
    fun buildSettingsJson(): String {
        val settings = JSONObject()

        // Skills：只传启用的
        val enabledSkills = getSkills().filter { it.enabled }
        if (enabledSkills.isNotEmpty()) {
            settings.put("skills", JSONArray(enabledSkills.map {
                it.name
            }))
        }

        // MCP servers：只传启用的
        val enabledMcp = getMcpServers().filter { it.enabled }
        if (enabledMcp.isNotEmpty()) {
            val mcpObj = JSONObject()
            for (m in enabledMcp) {
                mcpObj.put(m.name, JSONObject().apply {
                    put("command", m.command)
                    put("args", m.args.split(" ").filter { it.isNotEmpty() })
                })
            }
            settings.put("mcpServers", mcpObj)
        }

        return if (settings.length() > 0) settings.toString() else ""
    }
}
