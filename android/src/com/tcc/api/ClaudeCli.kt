package com.tcc.api

import android.content.Context
import android.util.Log
import com.tcc.TermuxBootstrap
import com.tcc.data.ConfigManager
import com.tcc.model.Message
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

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

                if (provider.env["TCC_USE_CLAUDE_CLI"] != "1") {
                    log("Using HTTP API with app command executor")
                    streamHttpChat(messages.filter { !it.isStreaming }, systemPrompt, config, provider, callback)
                    return@Thread
                }

                val lastUserMsg = messages.lastOrNull { it.role == "user" }
                    ?: run { callback.onEvent(StreamEvent.Error("没有用户消息")); return@Thread }
                val prompt = buildPrompt(lastUserMsg.content, systemPrompt)
                log("prompt len=${prompt.length}")

                val prefix = TermuxBootstrap.getPrefixDir(context)
                val homeDir = TermuxBootstrap.getHomeDir(context)
                val claudeExe = File(prefix, "bin/claude.exe")
                val claudeScript = File(prefix, "bin/claude")
                val claudeBin = if (claudeExe.isFile) claudeExe else claudeScript
                log("claude cmd=${claudeBin.absolutePath} exists=${claudeBin.exists()} size=${claudeBin.length()}")
                if (!claudeBin.isFile) {
                    log("Claude CLI missing, falling back to HTTP API")
                    streamHttpChat(messages.filter { !it.isStreaming }, systemPrompt, config, provider, callback)
                    return@Thread
                }

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
                        parseSessionIdFromText(line)?.let {
                            sessionId = it
                            log("session=$it (text)")
                        }
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

    private data class HttpToolCall(
        val id: String,
        val name: String,
        val input: JSONObject
    )

    private data class HttpAssistantResponse(
        val text: String,
        val toolCalls: List<HttpToolCall>,
        val content: JSONArray
    )

    private fun streamHttpChat(
        messages: List<Message>,
        systemPrompt: String?,
        config: ConfigManager,
        provider: com.tcc.model.Provider,
        callback: StreamCallback
    ) {
        val apiKey = provider.env["ANTHROPIC_AUTH_TOKEN"]
            ?: provider.env["ANTHROPIC_API_KEY"]
            ?: ""
        if (apiKey.isEmpty()) {
            callback.onEvent(StreamEvent.Error("API Key 未配置"))
            return
        }

        val base = (provider.env["ANTHROPIC_BASE_URL"] ?: "https://api.anthropic.com")
            .trim()
            .trimEnd('/')
            .ifEmpty { "https://api.anthropic.com" }
        val url = if (base.endsWith("/v1/messages")) base else "$base/v1/messages"
        val slot = config.getActiveModelSlot()
        val model = provider.env["ANTHROPIC_MODEL"] ?: slot?.model ?: ""
        if (model.isEmpty()) {
            callback.onEvent(StreamEvent.Error("模型未配置"))
            return
        }

        val timeoutMs = provider.env["API_TIMEOUT_MS"]?.toIntOrNull() ?: 300000
        val conversation = buildHttpMessages(messages)
        var inputTokens = 0L
        var outputTokens = 0L
        var toolsEnabled = true
        var emittedText = false
        try {
            log("HTTP fallback url=$url model=$model")

            for (round in 0 until 6) {
                if (aborted) return
                val body = buildHttpBody(model, slot?.maxTokens ?: 4096, systemPrompt, conversation, toolsEnabled)
                val response = try {
                    postJson(url, apiKey, body, timeoutMs)
                } catch (e: Exception) {
                    if (toolsEnabled && round == 0) {
                        toolsEnabled = false
                        loge("tool request failed, retry without tools: ${e.message}")
                        postJson(url, apiKey, buildHttpBody(model, slot?.maxTokens ?: 4096, systemPrompt, conversation, false), timeoutMs)
                    } else {
                        throw e
                    }
                }

                extractSessionId(response)?.let { sessionId = it }
                val usage = response.optJSONObject("usage")
                    ?: response.optJSONObject("message")?.optJSONObject("usage")
                if (usage != null) {
                    inputTokens += usage.optLong("input_tokens", 0)
                    outputTokens += usage.optLong("output_tokens", 0)
                }

                val errorObj = response.optJSONObject("error")
                if (errorObj != null) {
                    callback.onEvent(StreamEvent.Error(errorObj.optString("message", errorObj.toString())))
                    return
                }

                val parsed = parseAssistantResponse(response)
                if (parsed.text.isNotBlank()) {
                    callback.onEvent(StreamEvent.Chunk(parsed.text))
                    emittedText = true
                }

                if (parsed.toolCalls.isEmpty()) {
                    if (!emittedText) {
                        val text = extractHttpText(response)
                        if (text.isNotBlank()) callback.onEvent(StreamEvent.Chunk(text))
                    }
                    break
                }

                conversation.put(JSONObject().apply {
                    put("role", "assistant")
                    put("content", parsed.content)
                })

                val toolResults = JSONArray()
                for (toolCall in parsed.toolCalls) {
                    if (aborted) return
                    val resultText = runToolCall(toolCall, callback)
                    toolResults.put(JSONObject().apply {
                        put("type", "tool_result")
                        put("tool_use_id", toolCall.id)
                        put("content", resultText)
                    })
                }
                conversation.put(JSONObject().apply {
                    put("role", "user")
                    put("content", toolResults)
                })
            }

            if (!aborted) {
                if (inputTokens > 0 || outputTokens > 0) {
                    val finalCost = inputTokens / 1e6 * (slot?.pricePer1mInput ?: 0.0) +
                        outputTokens / 1e6 * (slot?.pricePer1mOutput ?: 0.0)
                    config.addUsageTokens(provider.id, model, inputTokens, outputTokens, finalCost)
                }
                callback.onEvent(StreamEvent.Done("end_turn"))
            }
        } catch (e: Exception) {
            loge("HTTP fallback failed: ${e.message}")
            if (!aborted) callback.onEvent(StreamEvent.Error(e.message ?: "HTTP 请求失败"))
        }
    }

    private fun buildHttpMessages(messages: List<Message>): JSONArray {
        val arr = JSONArray()
        for (message in messages) {
            if (message.role != "user" && message.role != "assistant") continue
            if (message.content.isBlank()) continue
            arr.put(JSONObject().apply {
                put("role", message.role)
                put("content", message.content)
            })
        }
        return arr
    }

    private fun buildHttpBody(
        model: String,
        maxTokens: Int,
        systemPrompt: String?,
        messages: JSONArray,
        includeTools: Boolean
    ): JSONObject {
        return JSONObject().apply {
            put("model", model)
            put("max_tokens", maxTokens)
            put("stream", false)
            put("system", buildHttpSystemPrompt(systemPrompt))
            put("messages", messages)
            if (includeTools) put("tools", JSONArray().put(buildRunCommandTool()))
        }
    }

    private fun buildHttpSystemPrompt(systemPrompt: String?): String {
        val userPrompt = systemPrompt?.trim().orEmpty()
        val toolPrompt = """
你运行在 TCC Android App 内。你可以使用 run_command 工具在本机执行 shell 命令。
使用规则：
- 当用户要求查看文件、检查系统、运行脚本、诊断错误或执行命令时，优先调用 run_command。
- 命令会在 App 沙盒中执行；如果内置 Termux 已安装，则使用 Termux bash，否则使用 Android /system/bin/sh。
- 优先执行只读或诊断命令。不要运行破坏性命令；危险命令会被 App 拦截。
- 执行命令后，根据真实 stdout/stderr/exit code 给用户简洁说明。
        """.trimIndent()
        return if (userPrompt.isEmpty()) toolPrompt else "$userPrompt\n\n$toolPrompt"
    }

    private fun buildRunCommandTool(): JSONObject {
        return JSONObject().apply {
            put("name", "run_command")
            put("description", "Run a local shell command inside the Android app. Use it to inspect files, run diagnostics, and execute simple commands requested by the user.")
            put("input_schema", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("command", JSONObject().apply {
                        put("type", "string")
                        put("description", "Shell command to execute.")
                    })
                    put("cwd", JSONObject().apply {
                        put("type", "string")
                        put("description", "Optional working directory. Defaults to the app home directory.")
                    })
                    put("timeout_ms", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Optional timeout in milliseconds. Defaults to 30000 and is capped by the app.")
                    })
                })
                put("required", JSONArray().put("command"))
            })
        }
    }

    private fun postJson(url: String, apiKey: String, body: JSONObject, timeoutMs: Int): JSONObject {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30000
                readTimeout = timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("anthropic-version", "2023-06-01")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.readText()?.trim().orEmpty()
            if (code !in 200..299) throw RuntimeException("HTTP $code: ${text.take(800)}")
            JSONObject(text)
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseAssistantResponse(response: JSONObject): HttpAssistantResponse {
        val content = response.optJSONArray("content") ?: JSONArray()
        val text = StringBuilder()
        val toolCalls = mutableListOf<HttpToolCall>()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            when (block.optString("type")) {
                "text" -> text.append(block.optString("text", ""))
                "tool_use" -> {
                    val input = block.optJSONObject("input") ?: try {
                        JSONObject(block.optString("input", "{}"))
                    } catch (_: Exception) {
                        JSONObject()
                    }
                    toolCalls.add(HttpToolCall(
                        id = block.optString("id", "tool_$i"),
                        name = block.optString("name", ""),
                        input = input
                    ))
                }
            }
        }
        if (text.isEmpty()) text.append(extractHttpText(response))
        return HttpAssistantResponse(text.toString(), toolCalls, content)
    }

    private fun runToolCall(toolCall: HttpToolCall, callback: StreamCallback): String {
        if (toolCall.name != "run_command") {
            return "Unsupported tool: ${toolCall.name}"
        }
        val command = toolCall.input.optString("command", "").trim()
        val cwd = toolCall.input.optString("cwd", "").ifBlank { null }
        val timeoutMs = toolCall.input.optLong("timeout_ms", 30000L)
        callback.onEvent(StreamEvent.Chunk("\n\n[运行命令] `$command`\n"))
        val result = CommandExecutor.run(context, command, cwd, timeoutMs)
        val resultText = result.toToolText()
        callback.onEvent(StreamEvent.Chunk("```text\n${resultText.take(2000)}\n```\n"))
        return resultText
    }

    private fun extractHttpText(json: JSONObject): String {
        val type = json.optString("type", "")
        if (type == "content_block_delta") {
            val delta = json.optJSONObject("delta")
            return delta?.optString("text", "").orEmpty()
        }
        if (type == "content_block_start") {
            val block = json.optJSONObject("content_block")
            return block?.optString("text", "").orEmpty()
        }
        val content = json.optJSONArray("content")
        if (content != null) {
            val sb = StringBuilder()
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                if (block.optString("type", "text") == "text") {
                    sb.append(block.optString("text", ""))
                }
            }
            if (sb.isNotEmpty()) return sb.toString()
        }
        val choices = json.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val choice = choices.optJSONObject(0)
            val delta = choice?.optJSONObject("delta")
            val msg = choice?.optJSONObject("message")
            return delta?.optString("content", "")
                ?: msg?.optString("content", "")
                ?: ""
        }
        return ""
    }

    private fun handleJsonEvent(json: org.json.JSONObject, textBuffer: StringBuilder, callback: StreamCallback) {
        extractSessionId(json)?.let {
            sessionId = it
            log("session=$it")
        }

        when (json.optString("type", "")) {
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

    // Claude CLI 的 session 字段在不同版本里位置不完全一致，这里递归扫描 JSON，检测到才更新。
    private fun extractSessionId(value: Any?): String? {
        return when (value) {
            is org.json.JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (isSessionKey(key)) {
                        val sid = normalizeSessionId(value.optString(key, ""))
                        if (sid != null) return sid
                    }
                    extractSessionId(value.opt(key))?.let { return it }
                }
                null
            }
            is org.json.JSONArray -> {
                for (i in 0 until value.length()) {
                    extractSessionId(value.opt(i))?.let { return it }
                }
                null
            }
            else -> null
        }
    }

    private fun isSessionKey(key: String): Boolean {
        val normalized = key.toLowerCase(java.util.Locale.US).replace("-", "_")
        return normalized == "session_id" ||
            normalized == "sessionid" ||
            normalized == "conversation_id" ||
            normalized == "conversationid"
    }

    private fun parseSessionIdFromText(text: String): String? {
        val patterns = listOf(
            Regex("""(?i)\bsession[_ -]?id["']?\s*[:=]\s*["']?([A-Za-z0-9._:-]{8,})"""),
            Regex("""(?i)\bconversation[_ -]?id["']?\s*[:=]\s*["']?([A-Za-z0-9._:-]{8,})""")
        )
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            normalizeSessionId(match.groupValues[1])?.let { return it }
        }
        return null
    }

    private fun normalizeSessionId(raw: String): String? {
        val sid = raw.trim().trim('"', '\'', ',', ';')
        if (sid.length < 8) return null
        if (!sid.any { it.isLetterOrDigit() }) return null
        return sid
    }

    fun abort() { aborted = true; process?.destroy() }

    private fun buildPrompt(userContent: String, systemPrompt: String?): String {
        val sp = systemPrompt?.trim() ?: ""
        return if (sp.isNotEmpty()) "System: $sp\n\n$userContent" else userContent
    }
}
