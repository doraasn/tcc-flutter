package com.tcc.api

import java.io.BufferedReader
import java.io.InputStreamReader

object LarkClient {

    private val termuxBin = "/data/data/com.termux/files/usr/bin"
    private val larkCliPath = if (java.io.File("$termuxBin/lark-cli").canExecute())
        "$termuxBin/lark-cli" else "lark-cli"

    fun isAvailable(): Boolean {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf(larkCliPath, "--help"))
            val exitCode = proc.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    fun execute(vararg args: String): String {
        val cmd = arrayOf(larkCliPath, *args)
        val pb = ProcessBuilder(*cmd)
        // Use Termux environment if available
        if (larkCliPath.startsWith(termuxBin)) {
            pb.environment().apply {
                put("PATH", "$termuxBin:/system/bin:/system/xbin")
                put("HOME", "/data/data/com.termux/files/home")
            }
        }
        val proc = pb.start()

        // Read stdout
        val stdoutReader = BufferedReader(InputStreamReader(proc.inputStream))
        val stdout = stdoutReader.readText()
        stdoutReader.close()

        // Read stderr (for diagnostics)
        val stderrReader = BufferedReader(InputStreamReader(proc.errorStream))
        val stderr = stderrReader.readText()
        stderrReader.close()

        val exitCode = proc.waitFor()

        return if (exitCode == 0) {
            stdout.trim()
        } else {
            val errorMsg = stderr.trim().ifEmpty { stdout.trim() }
            "Error (exit $exitCode): $errorMsg"
        }
    }

    fun authStatus(): String {
        return try {
            execute("auth", "status")
        } catch (e: Exception) {
            "Error: ${e.message ?: "Unknown error"}"
        }
    }
}
