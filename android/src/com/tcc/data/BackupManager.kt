package com.tcc.data

import android.content.Context
import com.tcc.api.WebDavClient
import com.tcc.model.Conversation
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(private val context: Context) {

    private val backupDir = File(context.filesDir, "backups")

    init { backupDir.mkdirs() }

    fun getWebDavClient(): WebDavClient? {
        val config = ConfigManager.getInstance(context)
        val url = config.getWebDavUrl()
        val user = config.getWebDavUser()
        val pass = config.getWebDavPass()
        if (url.isBlank() || user.isBlank() || pass.isBlank()) return null
        return WebDavClient(url, user, pass)
    }

    fun testConnection(callback: (Boolean, String) -> Unit) {
        val client = getWebDavClient()
        if (client == null) {
            callback(false, "请先配置 WebDAV")
            return
        }
        Thread {
            val result = client.testConnection()
            callback(result.ok, result.message)
        }.start()
    }

    fun backupToWebDav(callback: (Boolean, String) -> Unit) {
        val client = getWebDavClient()
        if (client == null) {
            callback(false, "请先配置 WebDAV")
            return
        }
        Thread {
            try {
                // Create backup directory on server
                client.mkdir("MCC")

                // Collect all conversation data
                val data = collectAllData()
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val filename = "MCC/backup_$timestamp.json"
                val result = client.upload(filename, data.toByteArray(Charsets.UTF_8))
                callback(result.ok, if (result.ok) "备份成功: $filename" else result.message)
            } catch (e: Exception) {
                callback(false, "备份失败: ${e.message ?: "未知错误"}")
            }
        }.start()
    }

    fun restoreFromWebDav(callback: (Boolean, String) -> Unit) {
        val client = getWebDavClient()
        if (client == null) {
            callback(false, "请先配置 WebDAV")
            return
        }
        Thread {
            try {
                // List backups
                val listResult = client.list("MCC")
                if (!listResult.ok) {
                    callback(false, "无法获取备份列表: ${listResult.message}")
                    return@Thread
                }

                val files = listResult.data?.let { String(it, Charsets.UTF_8) } ?: ""
                val backupFiles = files.lines()
                    .filter { it.startsWith("backup_") && it.endsWith(".json") }
                    .sorted()

                if (backupFiles.isEmpty()) {
                    callback(false, "未找到备份文件")
                    return@Thread
                }

                // Download the latest backup
                val latest = backupFiles.last()
                val downloadResult = client.download("MCC/$latest")
                if (!downloadResult.ok || downloadResult.data == null) {
                    callback(false, "下载备份失败: ${downloadResult.message}")
                    return@Thread
                }

                // Restore data
                val json = String(downloadResult.data, Charsets.UTF_8)
                restoreAllData(json)
                callback(true, "恢复成功: $latest")
            } catch (e: Exception) {
                callback(false, "恢复失败: ${e.message ?: "未知错误"}")
            }
        }.start()
    }

    fun exportToLocal(callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                val data = collectAllData()
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val localFile = File(backupDir, "backup_$timestamp.json")
                localFile.writeText(data)
                callback(true, "已导出到: ${localFile.absolutePath}")
            } catch (e: Exception) {
                callback(false, "导出失败: ${e.message ?: "未知错误"}")
            }
        }.start()
    }

    private fun collectAllData(): String {
        val convManager = ConversationManager.getInstance(context)
        val config = ConfigManager.getInstance(context)

        val data = JSONObject().apply {
            put("version", 1)
            put("timestamp", System.currentTimeMillis())
            put("app", "MCC")

            // Export config (without API key for security)
            val safeConfig = config.getAll()
            safeConfig.remove("api_key")
            put("config", safeConfig)

            // Export conversations
            val convs = convManager.listConversations()
            val convsArray = JSONArray()
            for (conv in convs) {
                val fullConv = convManager.getConversation(conv.id)
                if (fullConv != null) {
                    convsArray.put(fullConv.toJson())
                }
            }
            put("conversations", convsArray)
        }
        return data.toString(2)
    }

    private fun restoreAllData(json: String) {
        val data = JSONObject(json)
        val convManager = ConversationManager.getInstance(context)

        // Restore conversations
        if (data.has("conversations")) {
            val convsArray = data.getJSONArray("conversations")
            for (i in 0 until convsArray.length()) {
                val convJson = convsArray.getJSONObject(i)
                val conv = Conversation.fromJson(convJson)
                convManager.saveConversation(conv)
            }
        }

        // Restore config (non-sensitive)
        if (data.has("config")) {
            val config = ConfigManager.getInstance(context)
            config.updateFromJson(data.getJSONObject("config"))
        }
    }
}
