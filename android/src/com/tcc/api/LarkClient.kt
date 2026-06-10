package com.tcc.api

import android.content.Context
import com.tcc.TermuxBootstrap
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

// lark-cli 命令行包装器
object LarkClient {

    fun hasNode(context: Context): Boolean {
        val prefix = TermuxBootstrap.getPrefixDir(context)
        return File(prefix, "bin/node").isFile
    }

    fun isInstalled(context: Context): Boolean {
        val prefix = TermuxBootstrap.getPrefixDir(context)
        return File(prefix, "bin/lark-cli").isFile
    }

    // 检查 lark-cli 是否可用
    fun isAvailable(context: Context): Boolean {
        if (!hasNode(context) || !isInstalled(context)) return false
        return !execute(context, "--help").startsWith("Error")
    }

    // 执行 lark-cli 命令
    fun execute(context: Context, vararg args: String): String {
        return try {
            val prefix = TermuxBootstrap.getPrefixDir(context)
            val nodePath = File(prefix, "bin/node")
            val cliPath = File(prefix, "bin/lark-cli")
            if (!nodePath.isFile) {
                return "Error: Node.js 未安装，请先安装/修复 Claude 环境"
            }
            if (!cliPath.isFile) {
                return "Error: lark-cli 未安装，请先安装/修复 Claude 环境"
            }
            val pb = TermuxBootstrap.execInTermux(
                context,
                nodePath.absolutePath,
                cliPath.canonicalPath,
                *args
            )

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
