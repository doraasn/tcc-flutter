package com.tcc.api

import android.content.Context
import com.tcc.TermuxBootstrap
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

// App 内置命令执行器：优先使用内置 Termux，未安装时回退到 Android 系统 sh。
object CommandExecutor {
    private const val DEFAULT_TIMEOUT_MS = 30000L
    private const val MAX_TIMEOUT_MS = 120000L
    private const val MAX_OUTPUT_CHARS = 12000

    data class Result(
        val command: String,
        val cwd: String,
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val timedOut: Boolean,
        val blockedReason: String? = null
    ) {
        fun toToolText(): String {
            return buildString {
                append("$ $command\n")
                append("cwd: $cwd\n")
                if (blockedReason != null) {
                    append("blocked: $blockedReason\n")
                    return@buildString
                }
                append("exit: $exitCode")
                if (timedOut) append(" (timeout)")
                append("\n")
                if (stdout.isNotBlank()) {
                    append("\nstdout:\n")
                    append(truncate(stdout.trim()))
                    append("\n")
                }
                if (stderr.isNotBlank()) {
                    append("\nstderr:\n")
                    append(truncate(stderr.trim()))
                    append("\n")
                }
            }
        }
    }

    fun defaultCwd(context: Context): String {
        return if (TermuxBootstrap.isInstalled(context)) {
            TermuxBootstrap.getHomeDir(context).absolutePath
        } else {
            context.filesDir.absolutePath
        }
    }

    fun run(
        context: Context,
        command: String,
        cwd: String? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Result {
        val cleanCommand = command.trim()
        val workingDir = resolveCwd(context, cwd)
        if (cleanCommand.isEmpty()) {
            return Result(command, workingDir, 126, "", "", false, "empty command")
        }
        blockedReason(cleanCommand)?.let { reason ->
            return Result(cleanCommand, workingDir, 126, "", "", false, reason)
        }

        var proc: Process? = null
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val timeout = timeoutMs.coerceIn(1000L, MAX_TIMEOUT_MS)

        return try {
            proc = if (TermuxBootstrap.isInstalled(context)) {
                TermuxBootstrap.execBash(
                    context,
                    "cd ${shellQuote(workingDir)} && $cleanCommand",
                    TermuxBootstrap.buildEnvironment(context)
                )
            } else {
                ProcessBuilder(
                    "/system/bin/sh",
                    "-c",
                    "cd ${shellQuote(workingDir)} && $cleanCommand"
                ).redirectErrorStream(false).start()
            }

            val running = proc ?: throw IllegalStateException("process not started")
            val outThread = readAsync(running.inputStream.bufferedReader(), stdout)
            val errThread = readAsync(running.errorStream.bufferedReader(), stderr)
            val finished = running.waitFor(timeout, TimeUnit.MILLISECONDS)
            if (!finished) running.destroy()
            outThread.join(1000)
            errThread.join(1000)

            Result(
                command = cleanCommand,
                cwd = workingDir,
                exitCode = if (finished) running.exitValue() else -1,
                stdout = stdout.toString(),
                stderr = stderr.toString(),
                timedOut = !finished
            )
        } catch (e: Exception) {
            Result(cleanCommand, workingDir, 1, stdout.toString(), e.message ?: "", false)
        } finally {
            try { proc?.destroy() } catch (_: Exception) {}
        }
    }

    private fun resolveCwd(context: Context, cwd: String?): String {
        val base = defaultCwd(context)
        val raw = cwd?.trim().orEmpty()
        val file = when {
            raw.isEmpty() -> File(base)
            raw.startsWith("~") && TermuxBootstrap.isInstalled(context) ->
                File(TermuxBootstrap.getHomeDir(context), raw.removePrefix("~").trimStart('/'))
            File(raw).isAbsolute -> File(raw)
            else -> File(base, raw)
        }
        return try {
            if (file.isDirectory) file.canonicalPath else base
        } catch (_: Exception) {
            base
        }
    }

    private fun readAsync(reader: BufferedReader, target: StringBuilder): Thread {
        return Thread {
            try {
                val buf = CharArray(2048)
                while (true) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    synchronized(target) {
                        if (target.length < MAX_OUTPUT_CHARS + 2000) {
                            target.append(buf, 0, n)
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { reader.close() } catch (_: Exception) {}
            }
        }.apply { start() }
    }

    private fun blockedReason(command: String): String? {
        val dangerous = listOf(
            Regex("""(?i)(^|[;&|]\s*)rm\s+-[^\n;]*[rf][^\n;]*\s+/(?:\s|$)"""),
            Regex("""(?i)\bmkfs(?:\.\w+)?\b"""),
            Regex("""(?i)\bdd\b[^\n;]*\bof=/dev/"""),
            Regex("""(?i)\b(reboot|shutdown|poweroff)\b""")
        )
        return if (dangerous.any { it.containsMatchIn(command) }) {
            "dangerous command blocked"
        } else {
            null
        }
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun truncate(value: String): String {
        return if (value.length <= MAX_OUTPUT_CHARS) {
            value
        } else {
            value.take(MAX_OUTPUT_CHARS) + "\n...[truncated ${value.length - MAX_OUTPUT_CHARS} chars]"
        }
    }
}
