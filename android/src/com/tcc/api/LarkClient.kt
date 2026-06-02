package com.tcc.api

import android.content.Context
import com.tcc.TermuxBootstrap
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

// lark-cli 命令行包装器
object LarkClient {

    // 检查 lark-cli 是否可用
    fun isAvailable(context: Context): Boolean {
        return try {
            val prefix = TermuxBootstrap.getPrefixDir(context)
            val binPath = File(prefix, "bin/lark-cli").absolutePath
            val proc = TermuxBootstrap.execInTermux(context, binPath, "--help")
            val exitCode = proc.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    // 执行 lark-cli 命令
    fun execute(context: Context, vararg args: String): String {
        return try {
            val prefix = TermuxBootstrap.getPrefixDir(context)
            val binPath = File(prefix, "bin/lark-cli").absolutePath
            val pb = TermuxBootstrap.execInTermux(context, binPath, *args)

            // Read stdout
            val stdoutReader = BufferedReader(InputStreamReader(pb.inputStream))
            val stdout = stdoutReader.readText()
            stdoutReader.close()

            // Read stderr (for diagnostics)
            val stderrReader = BufferedReader(InputStreamReader(pb.errorStream))
            val stderr = stderrReader.readText()
            stderrReader.close()

            val exitCode = pb.waitFor()

            if (exitCode == 0) {
                stdout.trim()
            } else {
                val errorMsg = stderr.trim().ifEmpty { stdout.trim() }
                "Error (exit $exitCode): $errorMsg"
            }
        } catch (e: Exception) {
            "Error: ${e.message ?: "Unknown error"}"
        }
    }

    // 检查 lark-cli 认证状态
    fun authStatus(context: Context): String {
        return try {
            execute(context, "auth", "status")
        } catch (e: Exception) {
            "Error: ${e.message ?: "Unknown error"}"
        }
    }
}
