package com.tcc.api

import android.content.Context
import android.util.Log
import com.tcc.TermuxBootstrap
import com.tcc.data.ConfigManager
import com.tcc.model.Message
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

// Claude Code CLI 流式聊天客户端 - 通过 ProcessBuilder 调用 claude 命令行
// 替代 AnthropicClient，使用本地 Termux 环境中的 Claude Code CLI
class ClaudeCli(private val context: Context) {

    companion object {
        private const val TAG = "TCC"
    }

    @Volatile
    private var aborted = false

    private var process: Process? = null
    private var doneSent = false

    // 会话 ID - 用于 Claude CLI 会话管理（预留未来使用）
    // 设置此值后，下次请求将使用 --resume 恢复该会话
    @Volatile
    var sessionId: String? = null

    // 流式响应回调接口
    fun interface StreamCallback {
        fun onEvent(event: StreamEvent)
    }

    // 流式事件密封类
    sealed class StreamEvent {
        data class Chunk(val text: String) : StreamEvent()
        data class Done(val stopReason: String) : StreamEvent()
        data class Error(val message: String) : StreamEvent()
    }

    // 发起流式 CLI 聊天请求
    // 在后台线程中启动 claude 进程，按字符读取输出实现真流式
    fun streamChat(
        messages: List<Message>,
        systemPrompt: String?,
        config: ConfigManager,
        callback: StreamCallback
    ) {
        aborted = false
        doneSent = false

        if (!TermuxBootstrap.isInstalled(context)) {
            callback.onEvent(StreamEvent.Error("Termux 环境未安装或未就绪"))
            return
        }

        Thread {
            try {
                // 获取 Termux 环境变量（PATH、HOME、LD_LIBRARY_PATH 等）
                val env = HashMap(TermuxBootstrap.buildEnvironment(context))

                // 注入 API Key 作为环境变量，供 claude 命令使用
                val apiKey = config.getApiKey()
                if (apiKey.isEmpty()) {
                    callback.onEvent(StreamEvent.Error("API Key 未配置"))
                    return@Thread
                }
                env["ANTHROPIC_API_KEY"] = apiKey

                // 从最后一条用户消息构建提示词
                // Claude CLI 通过会话管理自己的上下文，无需发送完整历史
                val lastUserMsg = messages.lastOrNull { it.role == "user" }
                    ?: run {
                        callback.onEvent(StreamEvent.Error("没有用户消息"))
                        return@Thread
                    }

                // 构建 prompt 和命令
                val prompt = buildPrompt(lastUserMsg.content, systemPrompt)
                val cmd = buildCommand(prompt)

                // 获取 Termux 中的 bash 路径
                val prefix = TermuxBootstrap.getPrefixDir(context)
                val bash = File(prefix, "bin/bash").absolutePath
                Log.d(TAG, "启动 Claude CLI: bash -c $cmd")

                // 启动进程
                val pb = ProcessBuilder(bash, "-c", cmd)
                pb.environment().putAll(env)
                // 合并 stderr 到 stdout，以便捕获所有输出（包括会话信息）
                pb.redirectErrorStream(true)
                process = pb.start()

                // 按缓冲区读取 stdout，每~80ms 或每 64+ 字符刷新一次（避免逐字 UI 更新）
                val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                val outputBuilder = StringBuilder()
                val buffer = StringBuilder()
                var lastFlush = System.currentTimeMillis()
                var charCode: Int
                while (reader.read().also { charCode = it } != -1) {
                    if (aborted) {
                        process!!.destroy()
                        reader.close()
                        return@Thread
                    }
                    val ch = charCode.toChar()
                    outputBuilder.append(ch)
                    buffer.append(ch)
                    val now = System.currentTimeMillis()
                    if (buffer.length >= 64 || (now - lastFlush) > 80) {
                        callback.onEvent(StreamEvent.Chunk(buffer.toString()))
                        buffer.clear()
                        lastFlush = now
                    }
                }
                reader.close()
                // 刷新剩余字符
                if (buffer.isNotEmpty()) {
                    callback.onEvent(StreamEvent.Chunk(buffer.toString()))
                }

                // 等待进程自然结束
                val exitCode = process!!.waitFor()

                if (aborted) return@Thread

                if (exitCode == 0) {
                    // 处理完成，从输出中解析会话 ID 供下次使用
                    parseAndStoreSessionId(outputBuilder.toString())
                    if (!doneSent) {
                        doneSent = true
                        callback.onEvent(StreamEvent.Done("end_turn"))
                    }
                } else {
                    // 非零退出码表示错误
                    if (!doneSent) {
                        doneSent = true
                        Log.e(TAG, "Claude CLI 异常退出: exit code $exitCode")
                        callback.onEvent(StreamEvent.Error("CLI 进程退出码: $exitCode"))
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Claude CLI 异常: ${e.javaClass.name}: ${e.message}", e)
                if (!aborted && !doneSent) {
                    doneSent = true
                    callback.onEvent(StreamEvent.Error(e.message ?: "未知错误"))
                }
            } finally {
                process?.destroy()
                process = null
            }
        }.start()
    }

    // 中止当前请求 - 销毁进程
    fun abort() {
        aborted = true
        process?.destroy()
    }

    // 构建提示词文本
    // 如果设置了 systemPrompt，以 "System: ..." 作为前缀
    private fun buildPrompt(userContent: String, systemPrompt: String?): String {
        return if (systemPrompt != null && systemPrompt.isNotEmpty()) {
            "System: $systemPrompt\n\n$userContent"
        } else {
            userContent
        }
    }

    // 构建 CLI 命令
    // 如果 sessionId 已设置，使用 --resume 恢复会话
    private fun buildCommand(prompt: String): String {
        val escapedPrompt = escapeSingleQuotes(prompt)
        val claudeCmd = if (sessionId != null) {
            "claude --resume $sessionId -p '$escapedPrompt'"
        } else {
            "claude -p '$escapedPrompt'"
        }
        // 先 cd 到 home 目录确保 claude 配置文件可访问
        return "cd ~ && $claudeCmd"
    }

    // 转义单引号用于 bash 安全
    // 在 bash 中，单引号字符串内的单引号无法直接转义
    // 使用 '"'"' 模式：结束单引号 + 转义单引号 + 重新开始单引号
    private fun escapeSingleQuotes(s: String): String {
        return s.replace("'", "'\\''")
    }

    // 从输出中解析并存储会话 ID
    // Claude CLI 在输出中可能包含会话标识符
    // 解析成功后存储到 sessionId，供后续 --resume 使用
    private fun parseAndStoreSessionId(output: String) {
        val sessionRegex = Regex("""[Ss]ession[:\-]?\s*([a-zA-Z0-9_\-]{8,64})""")
        val match = sessionRegex.find(output)
        if (match != null) {
            sessionId = match.groupValues[1]
            Log.d(TAG, "检测到会话 ID: $sessionId")
        }
        // 不匹配时不重置 sessionId（保留现有值，以支持 --resume 场景）
    }
}
