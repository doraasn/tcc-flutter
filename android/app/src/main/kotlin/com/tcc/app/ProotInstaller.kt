package com.tcc.app

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Extracts and initializes the proot environment on first run.
 *
 * - proot binary: extracted from APK's jniLibs (arm64-v8a/proot-arm64)
 * - rootfs:       extracted from assets/core/rootfs.tgz
 *
 * After extraction, the app data directory layout is:
 *   filesDir/
 *     proot                    (native binary)
 *     rootfs/                  (Alpine Linux root filesystem)
 *       bin/
 *       usr/
 *       root/
 *         workspace/
 *         .tcc/versions/v2.1.153/
 *         .claude/
 *     .tcc_initialized         (marker file)
 */
object ProotInstaller {
    private const val TAG = "ProotInstaller"
    private const val MARKER_FILE = ".tcc_initialized"
    private const val ROOTFS_ASSET_PATH = "core/rootfs.tgz"
    private const val PROOT_LIB_NAME = "proot-arm64"

    /**
     * Result of the installation process.
     */
    data class InstallResult(
        val success: Boolean,
        val prootPath: String,
        val rootfsPath: String,
        val error: String? = null,
    )

    /**
     * Initialize the proot environment. No-op if already initialized.
     * Must be called from a background thread.
     */
    fun install(context: Context): InstallResult {
        val appDir = context.filesDir
        val marker = File(appDir, MARKER_FILE)

        // Already initialized
        if (marker.exists()) {
            val prootFile = File(appDir, "proot")
            val rootfsDir = File(appDir, "rootfs")
            if (prootFile.exists() && prootFile.canExecute() && rootfsDir.exists()) {
                Log.d(TAG, "Already initialized")
                return InstallResult(
                    success = true,
                    prootPath = prootFile.absolutePath,
                    rootfsPath = rootfsDir.absolutePath,
                )
            }
            // Marker exists but files missing - remove marker and reinitialize
            Log.w(TAG, "Marker exists but files missing, reinitializing")
            marker.delete()
        }

        Log.i(TAG, "Starting installation...")

        try {
            // Step 1: Extract proot binary from JNI libs
            val prootFile = extractProot(context, appDir)
                ?: return InstallResult(
                    success = false,
                    prootPath = "",
                    rootfsPath = "",
                    error = "Failed to extract proot binary from JNI libs",
                )

            // Step 2: Extract rootfs from assets
            val rootfsDir = File(appDir, "rootfs")
            extractRootfs(context, rootfsDir)
                ?: return InstallResult(
                    success = false,
                    prootPath = prootFile.absolutePath,
                    rootfsPath = "",
                    error = "Failed to extract rootfs from assets",
                )

            // Step 3: Ensure workspace directories exist
            ensureDirectories(rootfsDir)

            // Step 4: Write marker file
            marker.writeText("installed:${System.currentTimeMillis()}")

            Log.i(TAG, "Installation complete")
            return InstallResult(
                success = true,
                prootPath = prootFile.absolutePath,
                rootfsPath = rootfsDir.absolutePath,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed", e)
            // Clean up partial installation
            cleanup(appDir)
            return InstallResult(
                success = false,
                prootPath = "",
                rootfsPath = "",
                error = "Installation failed: ${e.message}",
            )
        }
    }

    /**
     * Reset installation state, forcing re-extraction on next install().
     */
    fun reset(context: Context) {
        val appDir = context.filesDir
        cleanup(appDir)
        File(appDir, MARKER_FILE).delete()
        Log.i(TAG, "Installation state reset")
    }

    /**
     * Check if installation is complete.
     */
    fun isInstalled(context: Context): Boolean {
        val appDir = context.filesDir
        val marker = File(appDir, MARKER_FILE)
        val prootFile = File(appDir, "proot")
        val rootfsDir = File(appDir, "rootfs")
        return marker.exists() && prootFile.exists() && prootFile.canExecute() && rootfsDir.exists()
    }

    // ---- Internal ----

    /**
     * Extract proot binary from the APK's native library directory.
     * Android unpacks jniLibs/<abi>/* into the app's nativeLibraryDir.
     */
    private fun extractProot(context: Context, targetDir: File): File? {
        val prootFile = File(targetDir, "proot")
        if (prootFile.exists() && prootFile.canExecute()) {
            Log.d(TAG, "proot binary already extracted")
            return prootFile
        }

        try {
            // The binary is in the APK's nativeLibraryDir (extracted by Android)
            val nativeLibDir = context.nativeLibraryDir
            val sourceFile = File(nativeLibDir, PROOT_LIB_NAME)

            if (!sourceFile.exists()) {
                Log.e(TAG, "proot not found in native lib dir: $nativeLibDir")
                // Fallback: try to copy from assets if bundled there
                return extractProotFromAssets(context, prootFile)
            }

            Log.d(TAG, "Extracting proot from $sourceFile")
            sourceFile.copyTo(prootFile, overwrite = true)
            prootFile.setExecutable(true, false)
            prootFile.setReadable(true, false)

            Log.i(TAG, "proot extracted: ${prootFile.absolutePath} (${prootFile.length()} bytes)")
            return prootFile
        } catch (e: IOException) {
            Log.e(TAG, "Failed to extract proot from native libs", e)
            return null
        }
    }

    /**
     * Fallback: extract proot from assets if not available in native libs.
     */
    private fun extractProotFromAssets(context: Context, targetFile: File): File? {
        return try {
            val inputStream = context.assets.open(PROOT_LIB_NAME)
            FileOutputStream(targetFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            targetFile.setExecutable(true, false)
            targetFile.setReadable(true, false)
            Log.i(TAG, "proot extracted from assets: ${targetFile.length()} bytes")
            targetFile
        } catch (e: IOException) {
            Log.e(TAG, "proot not found in assets either", e)
            null
        }
    }

    /**
     * Extract and decompress rootfs.tgz from assets to the target directory.
     * Handles the tgz format: gzip decompression followed by tar extraction.
     */
    private fun extractRootfs(context: Context, targetDir: File): File? {
        if (targetDir.exists() && targetDir.list()?.isNotEmpty() == true) {
            Log.d(TAG, "rootfs directory already populated")
            return targetDir
        }

        targetDir.mkdirs()

        try {
            Log.d(TAG, "Opening asset: $ROOTFS_ASSET_PATH")
            val inputStream = context.assets.open(ROOTFS_ASSET_PATH)
            val bufferedInput = BufferedInputStream(inputStream)

            // Decompress gzip
            val gzipInput = GZIPInputStream(bufferedInput, 8192)

            // Extract tar
            extractTar(gzipInput, targetDir)

            gzipInput.close()
            inputStream.close()

            // Verify extraction
            val rootCount = targetDir.list()?.size ?: 0
            if (rootCount == 0) {
                Log.e(TAG, "rootfs extraction produced empty directory")
                return null
            }

            Log.i(TAG, "rootfs extracted: $rootCount items in ${targetDir.absolutePath}")
            return targetDir
        } catch (e: IOException) {
            Log.e(TAG, "Failed to extract rootfs", e)
            return null
        }
    }

    /**
     * Extract a tar archive from the given InputStream into targetDir.
     * Implements minimal tar format parsing (POSIX ustar).
     */
    @Throws(IOException::class)
    private fun extractTar(input: InputStream, targetDir: File) {
        val buffer = ByteArray(512)
        var consecutiveZeros = 0

        while (true) {
            // Read header block (512 bytes)
            val header = readFull(input, buffer, 512)
            if (header < 512) {
                // End of archive (short read or EOF)
                break
            }

            // Check for zero block (end of archive marker)
            if (isZeroBlock(buffer)) {
                consecutiveZeros++
                if (consecutiveZeros >= 2) {
                    break // Two consecutive zero blocks = end of archive
                }
                continue
            }
            consecutiveZeros = 0

            // Parse file name from header
            // Name field: bytes 0-99
            val nameBytes = buffer.copyOfRange(0, 100)
            val name = String(nameBytes).trim(' ')
            if (name.isEmpty()) {
                continue
            }

            // File size field: bytes 124-135 (octal string)
            val sizeStr = String(buffer.copyOfRange(124, 136)).trim(' ')
            val size = sizeStr.toLongOrNull(8) ?: 0L

            // Type flag: byte 156
            val typeFlag = if (buffer.size > 156) buffer[156].toInt().toChar() else '0'

            // Prefix field: bytes 345-499 (for long names in ustar)
            val prefixBytes = buffer.copyOfRange(345, 500)
            val prefix = String(prefixBytes).trim(' ')
            val fullName = if (prefix.isNotEmpty()) "$prefix/$name" else name

            // Skip entry names that could escape the root
            if (fullName.contains("..")) {
                skipBytes(input, size)
                continue
            }

            val outFile = File(targetDir, fullName)

            when (typeFlag) {
                '5' -> {
                    // Directory
                    outFile.mkdirs()
                }
                '2' -> {
                    // Symbolic link - skip for safety
                    Log.d(TAG, "Skipping symlink: $fullName")
                    skipBytes(input, size)
                }
                '0', ' ' -> {
                    // Regular file
                    outFile.parentFile?.mkdirs()
                    if (size > 0) {
                        FileOutputStream(outFile).use { fos ->
                            copyBytes(input, fos, size)
                        }
                    }
                }
                'x' -> {
                    // Extended header - skip
                    skipBytes(input, size)
                }
                'L' -> {
                    // Long name entry - read the actual name from data
                    val longNameBuf = ByteArray(size.toInt())
                    readFull(input, longNameBuf, size.toInt())
                    // Pad to 512-byte boundary
                    val remainder = (512 - (size % 512).toInt()) % 512
                    skipBytes(input, remainder.toLong())
                    // The next entry will use this name (simplified: we just skip)
                }
                else -> {
                    // Unknown type, skip data
                    skipBytes(input, size)
                }
            }

            // Pad to 512-byte boundary
            val remainder = (512 - (size % 512).toInt()) % 512
            if (remainder > 0) {
                skipBytes(input, remainder.toLong())
            }
        }
    }

    /**
     * Read exactly `count` bytes from input, returning actual bytes read.
     */
    private fun readFull(input: InputStream, buf: ByteArray, count: Int): Int {
        var offset = 0
        while (offset < count) {
            val read = input.read(buf, offset, count - offset)
            if (read == -1) break
            offset += read
        }
        return offset
    }

    /**
     * Check if a 512-byte block is all zeros.
     */
    private fun isZeroBlock(buf: ByteArray): Boolean {
        for (i in 0 until minOf(512, buf.size)) {
            if (buf[i] != 0.toByte()) return false
        }
        return true
    }

    /**
     * Skip `count` bytes in the input stream.
     */
    private fun skipBytes(input: InputStream, count: Long) {
        var remaining = count
        val skipBuf = ByteArray(4096)
        while (remaining > 0) {
            val toRead = minOf(remaining, skipBuf.size.toLong()).toInt()
            val read = input.read(skipBuf, 0, toRead)
            if (read == -1) break
            remaining -= read
        }
    }

    /**
     * Copy exactly `count` bytes from input to output.
     */
    @Throws(IOException::class)
    private fun copyBytes(input: InputStream, output: FileOutputStream, count: Long) {
        var remaining = count
        val copyBuf = ByteArray(8192)
        while (remaining > 0) {
            val toRead = minOf(remaining, copyBuf.size.toLong()).toInt()
            val read = input.read(copyBuf, 0, toRead)
            if (read == -1) throw IOException("Unexpected EOF while reading tar entry")
            output.write(copyBuf, 0, read)
            remaining -= read
        }
    }

    /**
     * Ensure required workspace directories exist inside rootfs.
     */
    private fun ensureDirectories(rootfsDir: File) {
        val dirs = listOf(
            "root/workspace",
            "root/.tcc",
            "root/.tcc/versions",
            "root/.claude",
        )
        for (dir in dirs) {
            File(rootfsDir, dir).mkdirs()
        }
    }

    /**
     * Clean up extracted files.
     */
    private fun cleanup(appDir: File) {
        val prootFile = File(appDir, "proot")
        if (prootFile.exists()) prootFile.delete()

        val rootfsDir = File(appDir, "rootfs")
        if (rootfsDir.exists()) {
            rootfsDir.deleteRecursively()
        }
    }
}
