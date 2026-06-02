package com.tcc.app

import android.content.Context
import java.io.*
import java.util.zip.GZIPInputStream

object ProotInstaller {
    private const val PROOT_VERSION = "5.4.0"
    private const val ALPINE_VERSION = "3.19"

    fun install(context: Context) {
        val appDir = context.filesDir
        val rootfsDir = File(appDir, "rootfs")

        if (rootfsDir.exists()) return

        rootfsDir.mkdirs()

        // Extract proot binary from assets
        extractProot(context, appDir)

        // Download and extract Alpine rootfs
        extractAlpineRootfs(context, rootfsDir)

        // Install Node.js
        installNodeJs(context, rootfsDir)

        // Install Claude Code
        installClaudeCode(context, rootfsDir)
    }

    private fun extractProot(context: Context, targetDir: File) {
        val prootBin = File(targetDir, "proot")
        if (prootBin.exists()) return

        try {
            val assetManager = context.assets
            val inputStream = assetManager.open("proot-arm64")
            val outputStream = FileOutputStream(prootBin)

            inputStream.copyTo(outputStream)
            outputStream.close()
            inputStream.close()

            prootBin.setExecutable(true)
            prootBin.setReadable(true)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun extractAlpineRootfs(context: Context, rootfsDir: File) {
        try {
            val assetManager = context.assets
            val inputStream = assetManager.open("alpine-rootfs.tar.gz")
            val gzipInputStream = GZIPInputStream(inputStream)
            val outputStream = FileOutputStream(File(rootfsDir, "rootfs.tar"))

            gzipInputStream.copyTo(outputStream)
            outputStream.close()
            gzipInputStream.close()
            inputStream.close()

            // Extract tar
            val process = Runtime.getRuntime().exec(arrayOf(
                "tar", "xf", File(rootfsDir, "rootfs.tar").absolutePath,
                "-C", rootfsDir.absolutePath
            ))
            process.waitFor()

            File(rootfsDir, "rootfs.tar").delete()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun installNodeJs(context: Context, rootfsDir: File) {
        try {
            val prootBin = File(context.filesDir, "proot")
            val process = Runtime.getRuntime().exec(arrayOf(
                prootBin.absolutePath,
                "-r", rootfsDir.absolutePath,
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
                "-w", "/root",
                "/bin/sh", "-c",
                "apk add --no-cache nodejs npm"
            ))
            process.waitFor()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun installClaudeCode(context: Context, rootfsDir: File) {
        try {
            val prootBin = File(context.filesDir, "proot")
            val versionDir = File(rootfsDir, "root/.tcc/versions/v2.1.153")
            versionDir.mkdirs()

            val process = Runtime.getRuntime().exec(arrayOf(
                prootBin.absolutePath,
                "-r", rootfsDir.absolutePath,
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
                "-w", "/root",
                "/bin/sh", "-c",
                "npm install -g @anthropic-ai/claude-code@2.1.153 --prefix ${versionDir.absolutePath}"
            ))
            process.waitFor()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
