package com.tcc

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

// Termux 环境引导 - 解压内置的完整 Linux 环境（Node.js + Claude Code 等）
object TermuxBootstrap {

    private const val BUNDLE_NAME = "termux-bundle.tar.gz"

    fun getPrefixDir(context: Context): File = File(context.filesDir, "usr")
    fun getHomeDir(context: Context): File = File(context.filesDir, "home")

    // 检查是否已安装
    fun isInstalled(context: Context): Boolean {
        return File(getPrefixDir(context), "bin/bash").canExecute()
    }

    // 检查系统 Termux（未使用内置环境时的备选）
    fun isAvailable(): Boolean {
        return File("/data/data/com.termux/files/usr/bin/bash").canExecute()
    }

    // 解压内置环境到应用私有目录
    fun install(context: Context, onProgress: ((String) -> Unit)? = null): Boolean {
        val prefix = getPrefixDir(context)
        if (isInstalled(context)) {
            onProgress?.invoke("环境已就绪")
            return true
        }

        return try {
            onProgress?.invoke("正在解压运行环境…")

            // 清理之前解压失败的残留文件
            if (prefix.exists()) {
                prefix.deleteRecursively()
            }
            val homeDir = getHomeDir(context)
            if (homeDir.exists()) {
                homeDir.deleteRecursively()
            }

            prefix.mkdirs()
            getHomeDir(context).mkdirs()

            context.assets.open(BUNDLE_NAME).use { assetStream ->
                GZIPInputStream(BufferedInputStream(assetStream)).use { gzIn ->
                    extractTar(gzIn, prefix)
                }
            }

            // 创建 TMPDIR
            File(prefix, "tmp").mkdirs()

            // 用系统 chmod 修复所有文件权限（Android 内部存储上 setExecutable 不可靠）
            try {
                val chmodProc = Runtime.getRuntime().exec(arrayOf("/system/bin/chmod", "-R", "755", prefix.absolutePath))
                val chmodExit = chmodProc.waitFor()
                if (chmodExit != 0) {
                    // chmod 失败，使用 Kotlin API 降级
                    val binDir = File(prefix, "bin")
                    if (binDir.isDirectory) {
                        binDir.listFiles()?.forEach { file ->
                            file.setExecutable(true, false)
                        }
                    }
                }
            } catch (_: Exception) {
                val binDir = File(prefix, "bin")
                if (binDir.isDirectory) {
                    binDir.listFiles()?.forEach { file ->
                        file.setExecutable(true, false)
                    }
                }
            }
            try {
                val bin = File(prefix, "bin")
                // 创建 sh -> bash 符号链接
                Runtime.getRuntime().exec(arrayOf("/system/bin/ln", "-sf", "bash", File(bin, "sh").absolutePath)).waitFor()
            } catch (_: Exception) {}

            if (File(prefix, "bin/bash").canExecute()) {
                onProgress?.invoke("环境就绪")
                true
            } else {
                onProgress?.invoke("解压失败：无法执行 bash")
                false
            }
        } catch (e: Exception) {
            onProgress?.invoke("解压失败: ${e.message}")
            false
        }
    }

    // 纯 Kotlin tar 解压器（避免外部依赖）
    private fun extractTar(input: InputStream, destDir: File) {
        val buffer = ByteArray(8192)
        val header = ByteArray(512)

        while (true) {
            // 读取 512 字节头
            var pos = 0
            while (pos < 512) {
                val n = input.read(header, pos, 512 - pos)
                if (n <= 0) return
                pos += n
            }

            // 检查是否全是零（tar 结束标记）
            if (header.all { it == 0.toByte() }) {
                // 读取第二个零块确认
                pos = 0
                while (pos < 512) {
                    val n = input.read(header, pos, 512 - pos)
                    if (n <= 0) return
                    pos += n
                }
                return
            }

            // 解析文件名（最多 100 字节，找到第一个 \0）
            var nameEnd = 100
            for (j in 0 until 100) {
                if (header[j] == 0.toByte()) { nameEnd = j; break }
            }
            val name = String(header, 0, nameEnd, Charsets.UTF_8).trim()

            // 解析 USTAR 前缀（长路径支持，offset 345，155 字节）
            var prefixEnd = 500
            for (j in 345 until 500) {
                if (header[j] == 0.toByte()) { prefixEnd = j; break }
            }
            val prefixName = if (prefixEnd > 345) String(header, 345, prefixEnd - 345, Charsets.UTF_8).trim() else ""
            val fullName = if (prefixName.isNotEmpty()) "$prefixName/$name" else name

            // 解析文件大小（12 字节八进制，offset 124）
            val sizeStr = String(header, 124, 12, Charsets.UTF_8).trim { it <= ' ' }
            val fileSize = if (sizeStr.isNotEmpty()) sizeStr.toLong(8) else 0L

            // 解析类型标志（offset 156）
            val typeFlag = header[156].toInt() and 0xFF

            if (name.isEmpty()) continue

            when {
                typeFlag == '5'.code || name.endsWith("/") -> {
                    File(destDir, fullName).mkdirs()
                }
                typeFlag == '0'.code || typeFlag == 0 || typeFlag == '7'.code -> {
                    val file = File(destDir, fullName)
                    file.parentFile?.mkdirs()

                    FileOutputStream(file).use { out ->
                        var remaining = fileSize
                        while (remaining > 0) {
                            val toRead = minOf(remaining.toInt(), buffer.size)
                            val n = input.read(buffer, 0, toRead)
                            if (n <= 0) break
                            out.write(buffer, 0, n)
                            remaining -= n
                        }
                    }

                    // 权限由后续 chmod 统一设置
                }
                typeFlag == '2'.code -> {
                    // 符号链接：链接目标在 header offset 157，100 字节
                    val linkEnd = (157 until 257).firstOrNull { header[it] == 0.toByte() } ?: 257
                    val linkTarget = String(header, 157, linkEnd - 157, Charsets.UTF_8).trim()
                    val linkFile = File(destDir, fullName)
                    linkFile.parentFile?.mkdirs()
                    try {
                        java.nio.file.Files.createSymbolicLink(linkFile.toPath(), java.nio.file.Paths.get(linkTarget))
                    } catch (_: Exception) {
                        // 回退：如果符号链接不支持，尝试复制目标文件
                    }
                }
                else -> {
                    // 其他类型跳过
                }
            }

            // 跳过填充到 512 字节边界
            val padding = (512 - (fileSize % 512)) % 512
            if (padding > 0) {
                pos = 0
                while (pos < padding) {
                    val n = input.read(buffer, 0, minOf((padding - pos).toInt(), buffer.size))
                    if (n <= 0) break
                    pos += n
                }
            }
        }
    }

    // 构建 Termux 环境变量
    fun buildEnvironment(context: Context): Map<String, String> {
        val prefix = getPrefixDir(context).absolutePath
        val home = getHomeDir(context).absolutePath
        return mapOf(
            "HOME" to home,
            "PREFIX" to prefix,
            "PATH" to "$prefix/bin:$prefix/bin/applets:/system/bin:/system/xbin",
            "LD_LIBRARY_PATH" to "$prefix/lib",
            "TMPDIR" to "$prefix/tmp",
            "TERM" to "xterm-256color",
            "LANG" to "en_US.UTF-8",
            "SHELL" to "$prefix/bin/bash"
        )
    }
}
