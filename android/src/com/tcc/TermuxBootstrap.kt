package com.tcc

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import org.tukaani.xz.XZInputStream

// Termux 环境引导 - 解压内置的完整 Linux 环境（gzip 压缩，Java GZipInputStream 解压）
object TermuxBootstrap {

    private const val BUNDLE_NAME = "termux-bundle.tar.xz"
    private const val TAG = "TCC.Bootstrap"

    private var installed = false
    @Volatile var lastError: String? = null

    fun getPrefixDir(context: Context): File = File(context.filesDir, "usr")
    fun getHomeDir(context: Context): File = File(context.filesDir, "home")

    fun getBashExecutable(context: Context): String =
        File(getPrefixDir(context), "bin/bash").absolutePath

    fun isInstalled(context: Context): Boolean {
        if (installed) return true
        val bashFile = File(getPrefixDir(context), "bin/bash")
        val bash = bashFile.absolutePath
        Log.e(TAG, "isInstalled: checking $bash exists=${bashFile.exists()}")
        if (!bashFile.isFile) {
            lastError = "bash 不存在（$bash）"
            return false
        }
        // 每次启动都修复权限（旧版本可能权限不对）
        fixPermissions(context)
        // 通过动态链接器执行 bash（直接 exec 会被 MIUI SELinux 拦截）
        return try {
            val proc = execInTermux(context, bash, "-c", "echo ok")
            val code = proc.waitFor()
            installed = code == 0
            if (!installed) lastError = "bash 退出码=$code"
            else Log.e(TAG, "isInstalled: SUCCESS")
            installed
        } catch (e: Exception) {
            lastError = "bash 无法执行: ${e.message}"
            Log.e(TAG, "isInstalled: failed", e)
            false
        }
    }

    // 修复文件权限（旧版本可能权限不对，每次启动都执行）
    private fun fixPermissions(context: Context) {
        try {
            val prefix = getPrefixDir(context)
            val dirs = listOf(
                prefix.absolutePath,
                File(prefix, "bin").absolutePath,
                File(prefix, "lib").absolutePath,
                File(prefix, "glibc/lib").absolutePath,
                File(prefix, "tmp").absolutePath
            )
            for (dir in dirs) {
                val d = File(dir)
                if (!d.exists()) continue
                val pb = ProcessBuilder("/system/bin/chmod", "-R", "755", dir)
                pb.redirectErrorStream(true)
                val p = pb.start()
                p.inputStream.bufferedReader().readText()
                p.waitFor()
            }
        } catch (_: Exception) {}
    }

    // 解压内置环境到应用私有目录
    fun install(context: Context, onProgress: ((String) -> Unit)? = null): Boolean {
        val prefix = getPrefixDir(context)
        if (isInstalled(context)) { onProgress?.invoke("环境已就绪"); return true }

        return try {
            Log.e(TAG, "install: starting")
            onProgress?.invoke("准备环境…")

            // 清理旧文件
            if (prefix.exists()) {
                Log.e(TAG, "install: removing old prefix")
                prefix.deleteRecursively()
            }
            val homeDir = getHomeDir(context)
            if (homeDir.exists()) homeDir.deleteRecursively()
            prefix.mkdirs(); getHomeDir(context).mkdirs()
            Log.e(TAG, "install: prefix=${prefix.absolutePath}")

            // 把 gz 包从 APK assets 复制到临时文件
            onProgress?.invoke("正在复制压缩包…")
            val tmpDir = File(context.filesDir, ".tcc_install").also { it.mkdirs() }
            val bundleFile = File(tmpDir, BUNDLE_NAME)
            context.assets.open(BUNDLE_NAME).use { src ->
                bundleFile.outputStream().use { dst -> src.copyTo(dst) }
            }
            Log.e(TAG, "install: bundle copied, size=${bundleFile.length()}")

            // 解压 xz → tar 临时文件
            onProgress?.invoke("正在解压 xz 包…")
            val tmpTar = File(tmpDir, "bundle.tar")
            try {
                val fis = bundleFile.inputStream()
                val bis = BufferedInputStream(fis, 65536)
                try {
                    val xz = XZInputStream(bis, 65536) // 64 MiB 内存上限，实际只需要 8 MiB
                    try {
                        val dst = tmpTar.outputStream()
                        try {
                            val buf = ByteArray(65536)
                            while (true) {
                                val n = xz.read(buf, 0, buf.size)
                                if (n < 0) break
                                dst.write(buf, 0, n)
                            }
                        } finally { dst.close() }
                    } finally { xz.close() }
                } finally { bis.close() }
            } catch (e: Throwable) {
                Log.e(TAG, "install: XZ decompress FAILED: ${e.javaClass.name}: ${e.message}", e)
                throw RuntimeException("xz 解压失败[${e.javaClass.simpleName}]: ${e.message}")
            }
            Log.e(TAG, "install: tar decompressed, size=${tmpTar.length()}")

            // 提取 tar
            onProgress?.invoke("正在解包文件…")
            try {
                tmpTar.inputStream().use { fis ->
                    BufferedInputStream(fis, 65536).use { bis ->
                        extractTar(bis, prefix)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "install: tar extract FAILED: ${e.message}", e)
                throw RuntimeException("tar 解包失败: ${e.message}")
            }
            Log.e(TAG, "install: tar extract done")

            // 清理临时文件
            tmpDir.deleteRecursively()

            // 创建 TMPDIR
            File(prefix, "tmp").mkdirs()

            // 设置执行权限
            onProgress?.invoke("设置权限…")
            try {
                val bashFile = File(prefix, "bin/bash")
                val ok1 = bashFile.setExecutable(true, false)
                val ok2 = bashFile.setReadable(true, false)
                Log.e(TAG, "install: setExecutable=$ok1 setReadable=$ok2")
                // 用 ProcessBuilder 确保 stdout/stderr 被消费，避免挂起
                val chmodDirs = listOf(prefix.absolutePath,
                    File(prefix, "bin").absolutePath,
                    File(prefix, "lib").absolutePath,
                    File(prefix, "glibc/lib").absolutePath,
                    File(prefix, "tmp").absolutePath)
                for (dir in chmodDirs) {
                    val pb = ProcessBuilder("/system/bin/chmod", "-R", "755", dir)
                    pb.redirectErrorStream(true)
                    val p = pb.start()
                    p.inputStream.bufferedReader().readText() // 消费输出
                    val code = p.waitFor()
                    Log.e(TAG, "install: chmod $dir exit=$code")
                }
                // 检查文件权限
                val canExec = bashFile.canExecute()
                Log.e(TAG, "install: bash.canExecute()=$canExec file=${bashFile.absolutePath}")
            } catch (e: Exception) { Log.w(TAG, "install: chmod failed", e) }

            // sh → bash 符号链接
            try {
                val shFile = File(prefix, "bin/sh")
                if (!shFile.exists()) {
                    Runtime.getRuntime().exec(arrayOf("/system/bin/ln", "-sf", "bash",
                        shFile.absolutePath)).waitFor()
                }
            } catch (e: Exception) { Log.w(TAG, "install: ln failed", e) }

            // 复制 exec_glibc 转发器（用于非 PIE 二进制如 claude）
            try {
                val execWrapper = File(prefix, "bin/exec_glibc")
                if (!execWrapper.isFile) {
                    context.assets.open("exec_glibc").use { src ->
                        execWrapper.outputStream().use { dst -> src.copyTo(dst) }
                    }
                    execWrapper.setExecutable(true, false)
                    Log.e(TAG, "install: exec_glibc deployed")
                }
            } catch (e: Exception) { Log.w(TAG, "install: exec_glibc failed", e) }

            // patchelf 修复 claude 二进制
            onProgress?.invoke("配置 Claude CLI…")
            val claudeOk = installClaudeBinary(context, prefix)
            Log.e(TAG, "install: claude binary patched=$claudeOk")

            // 创建命令路径链接
            onProgress?.invoke("配置命令路径…")
            try {
                // claude 命令链接
                val claudeBin = File(prefix, "bin/claude")
                val claudeExe = File(prefix, "bin/claude.exe")
                if (!claudeBin.exists() && claudeExe.isFile) {
                    Runtime.getRuntime().exec(arrayOf("/system/bin/ln", "-sf", "claude.exe", claudeBin.absolutePath)).waitFor()
                }
                // lark-cli 命令链接
                val larkCliBin = File(prefix, "bin/lark-cli")
                val larkCliSrc = File(prefix, "lib/node_modules/@larksuite/cli/bin/lark-cli")
                if (!larkCliBin.exists() && larkCliSrc.isFile) {
                    Runtime.getRuntime().exec(arrayOf("/system/bin/ln", "-sf",
                        larkCliSrc.absolutePath, larkCliBin.absolutePath)).waitFor()
                }
            } catch (e: Exception) { Log.w(TAG, "install: symlink failed", e) }

            // 验证
            val ok = isInstalled(context)
            if (ok) {
                onProgress?.invoke("环境就绪")
                Log.e(TAG, "install: SUCCESS")
            } else {
                // isInstalled 已经设置了 lastError，不要覆盖它
                if (lastError == null) {
                    lastError = "bash 无法执行（${getPrefixDir(context).absolutePath}/bin/bash）"
                }
                onProgress?.invoke("安装失败: ${lastError}")
                Log.e(TAG, "install: FAILED - ${lastError}")
            }
            ok

        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            Log.e(TAG, "install: FAILED: ${lastError}", e)
            onProgress?.invoke("异常: ${lastError}")
            false
        }
    }

    // 复制 claude 原生二进制 + patchelf 修复 ELF interpreter → glibc linker
    private fun installClaudeBinary(context: Context, prefix: File): Boolean {
        val src = File(prefix,
            "lib/node_modules/@anthropic-ai/claude-code-linux-arm64/claude")
        if (!src.isFile) {
            Log.w(TAG, "installClaudeBinary: claude binary not found at ${src.absolutePath}")
            return false
        }

        val dst = File(prefix, "bin/claude.exe")
        src.inputStream().use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
        dst.setExecutable(true, false)

        val linker = File(prefix, "glibc/lib/ld-linux-aarch64.so.1")
        val patchelf = File(prefix, "bin/patchelf")
        if (!linker.isFile) { Log.w(TAG, "installClaudeBinary: linker not found at ${linker.absolutePath}"); return false }
        if (!patchelf.isFile) { Log.w(TAG, "installClaudeBinary: patchelf not found at ${patchelf.absolutePath}"); return false }

        return try {
            val p = execInTermux(context, patchelf.absolutePath,
                "--set-interpreter", linker.absolutePath, dst.absolutePath)
            val code = p.waitFor()
            val err = p.errorStream.bufferedReader().readText()
            if (code != 0) Log.w(TAG, "installClaudeBinary: patchelf exit=$code err=$err")
            else Log.e(TAG, "installClaudeBinary: success")
            code == 0
        } catch (e: Exception) {
            Log.e(TAG, "installClaudeBinary: exception", e)
            false
        }
    }

    // 纯 Kotlin tar 解压器（支持 GNU long name / PAX 扩展头）
    private fun extractTar(input: InputStream, destDir: File) {
        val buffer = ByteArray(8192)
        val header = ByteArray(512)
        var pendingLongName: String? = null

        while (true) {
            var pos = 0
            while (pos < 512) {
                val n = input.read(header, pos, 512 - pos)
                if (n <= 0) return
                pos += n
            }

            // 全零标记 = tar 结束
            if (header.all { it == 0.toByte() }) {
                pos = 0
                while (pos < 512) {
                    val n = input.read(header, pos, 512 - pos)
                    if (n <= 0) return
                    pos += n
                }
                return
            }

            // 解析 header
            var nameEnd = 100
            for (j in 0 until 100) { if (header[j] == 0.toByte()) { nameEnd = j; break } }
            val name = String(header, 0, nameEnd, Charsets.UTF_8).trim()

            var prefixEnd = 500
            for (j in 345 until 500) { if (header[j] == 0.toByte()) { prefixEnd = j; break } }
            val prefixName = if (prefixEnd > 345) String(header, 345, prefixEnd - 345, Charsets.UTF_8).trim() else ""
            val fullName = if (prefixName.isNotEmpty()) "$prefixName/$name" else name

            val sizeStr = String(header, 124, 12, Charsets.UTF_8).trim { it <= ' ' }
            val fileSize = if (sizeStr.isNotEmpty()) sizeStr.toLong(8) else 0L
            val typeFlag = header[156].toInt() and 0xFF
            // tar header 偏移 100: 文件模式 (8 字节八进制)
            val modeStr = String(header, 100, 8, Charsets.UTF_8).trim { it <= ' ' }
            val fileMode = if (modeStr.isNotEmpty()) modeStr.toLong(8) else 0L
            if (name.isEmpty()) continue

            // 处理 GNU long name 扩展（路径文本很小，可以完整读入）
            if (typeFlag == 'L'.code) {
                val data = ByteArray(fileSize.toInt())
                var read = 0
                while (read < fileSize) {
                    val n = input.read(data, read, data.size - read)
                    if (n <= 0) break
                    read += n
                }
                pendingLongName = data.toString(Charsets.UTF_8).trimEnd(' ')
                skipPadding(input, buffer, fileSize)
                continue
            }

            // 实际的文件名 = pendingLongName ?? fullName
            val actualName = pendingLongName ?: fullName
            pendingLongName = null

            when {
                // 跳过 PAX 扩展头 / GNU long link — 跳到数据末尾，padding 由后面的 skipPadding 处理
                typeFlag == 'x'.code || typeFlag == 'g'.code || typeFlag == 'K'.code -> {
                    skipData(input, buffer, fileSize)
                }
                typeFlag == '5'.code || actualName.endsWith("/") -> {
                    File(destDir, actualName).mkdirs()
                }
                typeFlag == '0'.code || typeFlag == 0 || typeFlag == '7'.code -> {
                    val file = File(destDir, actualName)
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
                    // 设置所有文件可读（Android 默认创建为 600，glibc 需要读权限）
                    file.setReadable(true, false)
                    // 根据 tar 权限位设置可执行标志
                    if (fileMode and 73L != 0L) { // 0o111 = owner|group|other execute
                        file.setExecutable(true, false)
                    }
                }
                typeFlag == '2'.code -> {
                    val linkEnd = (157 until 257).firstOrNull { header[it] == 0.toByte() } ?: 257
                    val linkTarget = String(header, 157, linkEnd - 157, Charsets.UTF_8).trim()
                    val linkFile = File(destDir, actualName)
                    linkFile.parentFile?.mkdirs()
                    try {
                        java.nio.file.Files.createSymbolicLink(linkFile.toPath(), java.nio.file.Paths.get(linkTarget))
                    } catch (_: Exception) {}
                }
            }

            skipPadding(input, buffer, fileSize)
        }
    }

    // 跳过 tar 条目后的 padding 到 512 字节边界
    private fun skipPadding(input: InputStream, buf: ByteArray, size: Long) {
        val padding = (512 - (size % 512)) % 512
        if (padding > 0) {
            var pos = 0
            while (pos < padding) {
                val n = input.read(buf, 0, minOf((padding - pos).toInt(), buf.size))
                if (n <= 0) break
                pos += n
            }
        }
    }

    // 跳过指定字节数的数据
    private fun skipData(input: InputStream, buf: ByteArray, size: Long) {
        var remaining = size
        while (remaining > 0) {
            val toRead = minOf(remaining.toInt(), buf.size)
            val n = input.read(buf, 0, toRead)
            if (n <= 0) break
            remaining -= n
        }
    }

    // 通过动态链接器执行 Termux 二进制（绕过 SELinux exec 限制）
    fun execInTermux(context: Context, binPath: String, vararg args: String,
        extraEnv: Map<String, String> = emptyMap(),
        directory: File? = null): Process {
        val prefix = getPrefixDir(context)
        // 只用 Termux 的 lib，不包含 glibc/lib（glibc 二进制通过 patchelf interpreter 自动找库）
        val libPath = File(prefix, "lib").absolutePath

        val linkers = listOf(
            "/apex/com.android.runtime/bin/linker64",
            "/system/bin/linker64",
            "/system/bin/linker"
        )
        var lastError: Exception? = null
        for (linker in linkers) {
            if (File(linker).isFile) {
                try {
                    val cmd = arrayOf(linker, binPath, *args)
                    val pb = ProcessBuilder(*cmd)
                    pb.environment()["LD_LIBRARY_PATH"] = libPath
                    pb.environment().putAll(extraEnv)
                    if (directory != null) pb.directory(directory)
                    val proc = pb.redirectErrorStream(false).start()
                    return proc
                } catch (e: Exception) {
                    lastError = e
                }
            }
        }
        throw lastError ?: IOException("No suitable linker found")
    }

    // 通过 exec_glibc + glibc linker 执行非 PIE 二进制（如 claude）
    fun execGlibcBinary(context: Context, binPath: String, vararg args: String,
        extraEnv: Map<String, String> = emptyMap(),
        directory: File? = null): Process {
        val prefix = getPrefixDir(context)
        // 自动部署 exec_glibc（环境已安装时不会走 install 流程）
        val execFile = File(prefix, "bin/exec_glibc")
        if (!execFile.isFile) {
            try {
                context.assets.open("exec_glibc").use { src ->
                    execFile.outputStream().use { dst -> src.copyTo(dst) }
                }
                execFile.setExecutable(true, false)
                Log.e(TAG, "execGlibcBinary: deployed exec_glibc on demand")
            } catch (e: Exception) {
                Log.w(TAG, "execGlibcBinary: deploy failed", e)
            }
        }
        val execWrapper = execFile.absolutePath
        val glibcLinker = File(prefix, "glibc/lib/ld-linux-aarch64.so.1").absolutePath
        val libPath = File(prefix, "lib").absolutePath
        val glibcLibPath = File(prefix, "glibc/lib").absolutePath
        val ldPath = "$libPath:$glibcLibPath"

        val linkers = listOf(
            "/apex/com.android.runtime/bin/linker64",
            "/system/bin/linker64",
            "/system/bin/linker"
        )
        var lastError: Exception? = null
        for (linker in linkers) {
            if (File(linker).isFile) {
                try {
                    val cmd = arrayOf(linker, execWrapper, glibcLinker, binPath, *args)
                    val pb = ProcessBuilder(*cmd)
                    pb.environment()["LD_LIBRARY_PATH"] = ldPath
                    pb.environment().putAll(extraEnv)
                    if (directory != null) pb.directory(directory)
                    return pb.redirectErrorStream(false).start()
                } catch (e: Exception) {
                    lastError = e
                }
            }
        }
        throw lastError ?: IOException("No suitable linker or glibc wrapper")
    }

    // 通过 linker + bash 执行命令（复杂命令走这个）
    fun execBash(context: Context, command: String,
        extraEnv: Map<String, String> = emptyMap()): Process {
        val prefix = getPrefixDir(context)
        val bash = File(prefix, "bin/bash").absolutePath
        // 只设 Termux 的 lib，不包含 glibc/lib
        // claude 二进制通过 patchelf 修复的 ELF interpreter 自动找 glibc 库
        // 包含 glibc/lib 会导致 head/cat 等非 glibc 二进制加载错误的 libm.so 而崩溃
        val libPath = File(prefix, "lib").absolutePath

        val linkers = listOf(
            "/apex/com.android.runtime/bin/linker64",
            "/system/bin/linker64",
            "/system/bin/linker"
        )
        var lastError: Exception? = null
        for (linker in linkers) {
            if (File(linker).isFile) {
                try {
                    val pb = ProcessBuilder(linker, bash, "-c", command)
                    pb.environment()["LD_LIBRARY_PATH"] = libPath
                    pb.environment().putAll(extraEnv)
                    return pb.redirectErrorStream(true).start()
                } catch (e: Exception) {
                    lastError = e
                }
            }
        }
        throw lastError ?: IOException("No suitable linker found")
    }

    // 构建 Termux 环境变量（基础环境）
    fun buildEnvironment(context: Context): Map<String, String> {
        val prefix = getPrefixDir(context).absolutePath
        val home = getHomeDir(context).absolutePath
        return mapOf(
            "HOME" to home,
            "PREFIX" to prefix,
            "PATH" to "$prefix/bin:$prefix/bin/applets:/system/bin:/system/xbin",
            // 不包含 glibc/lib —— claude 通过 patchelf 修复的 interpreter 自动找 glibc 库
            // 包含 glibc/lib 会导致 head/cat 等非 glibc 二进制崩溃
            "LD_LIBRARY_PATH" to "$prefix/lib",
            "TMPDIR" to "$prefix/tmp",
            "TERM" to "xterm-256color",
            "LANG" to "en_US.UTF-8",
            "SHELL" to "$prefix/bin/bash",
            "LD_PRELOAD" to ""
        )
    }
}
