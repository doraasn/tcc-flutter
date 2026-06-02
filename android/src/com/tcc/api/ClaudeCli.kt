package com.tcc.api

import android.content.Context
import android.util.Log
import com.tcc.TermuxBootstrap
import com.tcc.data.ConfigManager
import com.tcc.model.Message
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class ClaudeCli(private val context: Context) {

    companion object {
        private const val TAG = "TCC"
        private const val MAX_LOG_SIZE = 500 * 1024L // 500KB
        private val logBuffer = StringBuilder()
        private var logFile: File? = null

        fun initLog(context: Context) {
            logBuffer.clear()
            val logsDir = File(context.filesDir, "logs")
            if (!logsDir.exists()) logsDir.mkdirs()
            logFile = File(logsDir, "tcc.log")
            // 轮转：超过 500KB 时清空重建
            try {
                val f = logFile ?: return
                if (f.exists() && f.length() > MAX_LOG_SIZE) {
                    f.writeText("")
                }
            } catch (_: Exception) {}
        }

        fun getLog(): String = synchronized(logBuffer) { logBuffer.toString() }

        fun getLogFile(): File? = logFile

        private fun log(msg: String) {
            Log.d(TAG, msg)
            val ts = System.currentTimeMillis() / 1000
            synchronized(logBuffer) { logBuffer.appendLine("$ts $msg") }
            writeToFile("INFO", msg)
        }

        private fun loge(msg: String) {
            Log.e(TAG, msg)
            val ts = System.currentTimeMillis() / 1000
            synchronized(logBuffer) { logBuffer.appendLine("$ts ERR: $msg") }
            writeToFile("ERROR", msg)
        }

        private fun writeToFile(level: String, msg: String) {
            try {
                val f = logFile ?: return
                if (f.exists() && f.length() > MAX_LOG_SIZE) {
                    f.writeText("")
                }
                val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date())
                f.appendText("$time [$level] $msg\n")
            } catch (_: Exception) {}
        }
    }

    @Volatile var sessionId: String? = null
    @Volatile private var aborted = false
    private var process: Process? = null

    fun interface StreamCallback { fun onEvent(event: StreamEvent) }

    sealed class StreamEvent {
        data class Chunk(val text: String) : StreamEvent()
        data class Done(val stopReason: String) : StreamEvent()
        data class Error(val message: String) : StreamEvent()
    }

    fun streamChat(
        messages: List<Message>,
        systemPrompt: String?,
        config: ConfigManager,
        callback: StreamCallback
    ) {
        aborted = false

        if (!TermuxBootstrap.isInstalled(context)) {
            callback.onEvent(StreamEvent.Error("Termux 环境未安装")) ; return
        }

        Thread {
            try {
                val provider = config.getActiveProvider() ?: run {
                    callback.onEvent(StreamEvent.Error("未配置供应商")); return@Thread
                }

                val realApiKey = provider.env["ANTHROPIC_AUTH_TOKEN"] ?: provider.env["ANTHROPIC_API_KEY"] ?: ""
                val realBaseUrl = provider.env["ANTHROPIC_BASE_URL"] ?: ""
                val model = provider.env["ANTHROPIC_MODEL"] ?: ""

                log("provider=${provider.name} base=$realBaseUrl model=$model key=${realApiKey.take(8)}...")

                if (realApiKey.isEmpty()) {
                    callback.onEvent(StreamEvent.Error("API Key 未配置")); return@Thread
                }

                val lastUserMsg = messages.lastOrNull { it.role == "user" }
                    ?: run { callback.onEvent(StreamEvent.Error("没有用户消息")); return@Thread }
                val prompt = buildPrompt(lastUserMsg.content, systemPrompt)
                log("prompt len=${prompt.length}")

                val prefix = TermuxBootstrap.getPrefixDir(context)
                val homeDir = TermuxBootstrap.getHomeDir(context)
                val claudeBin = File(prefix, "bin/claude.exe")
                log("claude.exe exists=${claudeBin.exists()} size=${claudeBin.length()}")

                val promptFile = File(homeDir, ".tcc_prompt.txt")
                promptFile.writeText(prompt)

                val args = mutableListOf("--dangerously-skip-permissions", "--output-format", "stream-json", "--verbose")
                if (sessionId != null) { args.add("--resume"); args.add(sessionId!!) }

                val stderrFile = File(homeDir, ".tcc_stderr.txt")
                val shellCmd = "cd '${homeDir.absolutePath}' && cat '${promptFile.absolutePath}' | '${claudeBin.absolutePath}' ${args.joinToString(" ")} 2>'${stderrFile.absolutePath}'"
                log("cmd: $shellCmd")

                val env = HashMap(TermuxBootstrap.buildEnvironment(context))
                env["ANTHROPIC_BASE_URL"] = realBaseUrl
                env["ANTHROPIC_API_KEY"] = realApiKey
                env["ANTHROPIC_AUTH_TOKEN"] = realApiKey
                env["ANTHROPIC_MODEL"] = model

                for ((key, value) in provider.env) {
                    if (value.isNotEmpty()) {
                        env[key] = value
                    }
                }
                env["LD_PRELOAD"] = ""
                env["NODE_TLS_REJECT_UNAUTHORIZED"] = "0"

                // 诊断日志：显示关键环境变量
                log("ENV ANTHROPIC_BASE_URL=${env["ANTHROPIC_BASE_URL"]}")
                log("ENV ANTHROPIC_AUTH_TOKEN=${env["ANTHROPIC_AUTH_TOKEN"]?.take(20)}...")
                log("ENV ANTHROPIC_MODEL=${env["ANTHROPIC_MODEL"]}")

                process = TermuxBootstrap.execBash(context, shellCmd, extraEnv = env)
                log("process started")

                val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                val allOutput = StringBuilder()
                val textBuffer = StringBuilder()
                var lastFlush = System.currentTimeMillis()
                val startTime = System.currentTimeMillis()
                var lineCount = 0

                while (true) {
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000
                    if (elapsed > 120) {
                        loge("TIMEOUT 120s lines=$lineCount")
                        process!!.destroy()
                        callback.onEvent(StreamEvent.Error("超时 120s"))
                        return@Thread
                    }
                    if (!reader.ready()) {
                        try { process!!.exitValue(); loge("EXITED lines=$lineCount"); break }
                        catch (_: IllegalThreadStateException) {}
                        Thread.sleep(200)
                        continue
                    }
                    val line = reader.readLine() ?: break
                    lineCount++
                    allOutput.append(line).append('\n')
                    if (lineCount <= 20) log("[$lineCount] ${line.take(200)}")
                    if (line.isEmpty()) continue

                    try {
                        val json = org.json.JSONObject(line)
                        handleJsonEvent(json, textBuffer, callback)
                    } catch (_: Exception) {
                        textBuffer.append(line).append('\n')
                    }

                    val now = System.currentTimeMillis()
                    if (textBuffer.length >= 64 || (now - lastFlush) > 80) {
                        if (textBuffer.isNotEmpty()) {
                            callback.onEvent(StreamEvent.Chunk(textBuffer.toString()))
                            textBuffer.clear(); lastFlush = now
                        }
                    }
                }
                reader.close()
                if (textBuffer.isNotEmpty()) callback.onEvent(StreamEvent.Chunk(textBuffer.toString()))

                // 读 stderr
                try {
                    if (stderrFile.exists()) {
                        val stderrContent = stderrFile.readText().trim()
                        if (stderrContent.isNotEmpty()) loge("STDERR: ${stderrContent.take(1000)}")
                        stderrFile.delete()
                    }
                } catch (_: Exception) {}

                val exitCode = process!!.waitFor()
                log("=== END exit=$exitCode lines=$lineCount ===")

                if (exitCode != 0 && !aborted) {
                    val output = allOutput.toString().trim()
                    loge("FAIL exit=$exitCode output=${output.take(500)}")
                    callback.onEvent(StreamEvent.Error("退出码: $exitCode\n${output.take(300)}"))
                } else if (lineCount == 0 && !aborted) {
                    callback.onEvent(StreamEvent.Error("无输出，退出码=$exitCode"))
                }

            } catch (e: Exception) {
                loge("EXCEPTION: ${e.message}")
                if (!aborted) callback.onEvent(StreamEvent.Error(e.message ?: "未知错误"))
            } finally {
                process?.destroy(); process = null
            }
        }.start()
    }

    private fun handleJsonEvent(json: org.json.JSONObject, textBuffer: StringBuilder, callback: StreamCallback) {
        when (json.optString("type", "")) {
            "system" -> {
                val sid = json.optString("session_id", "")
                if (sid.isNotEmpty()) { sessionId = sid; log("session=$sid") }
            }
            "assistant" -> {
                val msg = json.optJSONObject("message") ?: return
                val content = msg.optJSONArray("content") ?: return
                for (i in 0 until content.length()) {
                    val block = content.optJSONObject(i) ?: continue
                    if (block.optString("type") == "text") {
                        val text = block.optString("text", "")
                        if (text.isNotEmpty()) textBuffer.append(text)
                    }
                }
            }
            "result" -> {
                val sid = json.optString("session_id", "")
                if (sid.isNotEmpty()) sessionId = sid
                val usage = json.optJSONObject("usage")
                if (usage != null) {
                    val inT = usage.optLong("input_tokens", 0)
                    val outT = usage.optLong("output_tokens", 0)
                    val cost = json.optDouble("total_cost_usd", -1.0)
                    if (inT > 0 || outT > 0) {
                        val cfg = ConfigManager.getInstance(context)
                        val p = cfg.getActiveProvider()
                        val s = cfg.getActiveModelSlot()
                        val finalCost = if (cost >= 0) cost else (inT / 1e6 * (s?.pricePer1mInput ?: 0.0) + outT / 1e6 * (s?.pricePer1mOutput ?: 0.0))
                        cfg.addUsageTokens(p?.id ?: "", s?.model ?: "", inT, outT, finalCost)
                    }
                }
                if (!json.optBoolean("is_error", false)) {
                    callback.onEvent(StreamEvent.Done(json.optString("stop_reason", "end_turn")))
                }
            }
        }
    }

    fun abort() { aborted = true; process?.destroy() }

    private fun buildPrompt(userContent: String, systemPrompt: String?): String {
        val sp = systemPrompt?.trim() ?: ""
        return if (sp.isNotEmpty()) "System: $sp\n\n$userContent" else userContent
    }
}
