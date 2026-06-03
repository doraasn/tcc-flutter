package com.tcc.app

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream

object ProotInstaller {
    private const val TAG = "ProotInstaller"
    private const val MARKER_FILE = ".tcc_initialized"
    private const val PROOT_LIB_NAME = "proot-arm64"

    fun install(context: Context): InstallResult {
        val appDir = context.filesDir
        val prootFile = File(appDir, "proot")
        val rootfsDir = File(appDir, "rootfs")

        if (isInstalled(context)) {
            Log.d(TAG, "Already installed")
            return InstallResult(success = true, prootPath = prootFile.absolutePath, rootfsPath = rootfsDir.absolutePath)
        }

        try {
            ensureDirectories(appDir)
            val proot = extractProot(context, appDir) ?: return InstallResult(success = false, error = "Failed to extract proot")
            extractRootfs(context, rootfsDir)
            File(appDir, MARKER_FILE).createNewFile()
            return InstallResult(success = true, prootPath = proot.absolutePath, rootfsPath = rootfsDir.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed", e)
            cleanup(appDir)
            return InstallResult(success = false, error = e.message)
        }
    }

    fun isInstalled(context: Context): Boolean {
        val appDir = context.filesDir
        return File(appDir, MARKER_FILE).exists() && File(appDir, "proot").exists()
    }

    fun reset(context: Context) {
        cleanup(context.filesDir)
    }

    private fun ensureDirectories(appDir: File) {
        File(appDir, "rootfs").mkdirs()
    }

    private fun extractProot(context: Context, targetDir: File): File? {
        val prootFile = File(targetDir, "proot")
        if (prootFile.exists()) return prootFile

        try {
            val nativeLibDir = context.nativeLibraryDir
            val sourceFile = File(nativeLibDir, PROOT_LIB_NAME)
            if (sourceFile.exists()) {
                sourceFile.copyTo(prootFile, overwrite = true)
                prootFile.setExecutable(true)
                return prootFile
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to extract proot", e)
        }
        return null
    }

    private fun extractRootfs(context: Context, rootfsDir: File) {
        try {
            val inputStream = context.assets.open("core/rootfs.tgz")
            val gzipInputStream = GZIPInputStream(inputStream)
            val tarball = File(context.cacheDir, "rootfs.tar")
            FileOutputStream(tarball).use { output -> gzipInputStream.copyTo(output) }

            val process = Runtime.getRuntime().exec(arrayOf("tar", "xf", tarball.absolutePath, "-C", rootfsDir.absolutePath))
            process.waitFor()
            tarball.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract rootfs", e)
        }
    }

    private fun cleanup(appDir: File) {
        File(appDir, "proot").delete()
        File(appDir, MARKER_FILE).delete()
        File(appDir, "rootfs").deleteRecursively()
    }

    data class InstallResult(
        val success: Boolean,
        val prootPath: String = "",
        val rootfsPath: String = "",
        val error: String? = null
    )
}
