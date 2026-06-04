package com.tcc.app

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.tcc.app/native"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getNativeLibraryDir" -> {
                        result.success(applicationInfo.nativeLibraryDir)
                    }
                    "getExecutableDir" -> {
                        val execDir = getExecutableDir()
                        result.success(execDir)
                    }
                    "copyAndMakeExecutable" -> {
                        val srcPath = call.argument<String>("src")
                        val dstName = call.argument<String>("dst")
                        if (srcPath == null || dstName == null) {
                            result.error("INVALID_ARGS", "src and dst required", null)
                            return@setMethodCallHandler
                        }
                        try {
                            val execDir = getExecutableDir()
                            val dst = File(execDir, dstName)

                            // Use shell commands to copy and chmod (more reliable)
                            val copyResult = Runtime.getRuntime().exec(
                                arrayOf("sh", "-c", "cp '$srcPath' '${dst.absolutePath}' && chmod 755 '${dst.absolutePath}'")
                            ).waitFor()

                            if (copyResult != 0) {
                                // Fallback: try Java copy
                                File(srcPath).copyTo(dst, overwrite = true)
                                Runtime.getRuntime().exec(arrayOf("chmod", "755", dst.absolutePath)).waitFor()
                            }

                            if (dst.exists() && dst.length() > 0) {
                                result.success(dst.absolutePath)
                            } else {
                                result.error("COPY_FAILED", "File not created: ${dst.absolutePath}", null)
                            }
                        } catch (e: Exception) {
                            result.error("COPY_FAILED", e.message, null)
                        }
                    }
                    else -> {
                        result.notImplemented()
                    }
                }
            }
    }

    private fun getExecutableDir(): String {
        // Try multiple locations for executable directory
        val candidates = listOf(
            "/data/local/tmp/tcc",
            "${cacheDir.absolutePath}/exec",
            "${filesDir.absolutePath}/exec"
        )

        for (path in candidates) {
            try {
                val dir = File(path)
                if (!dir.exists()) dir.mkdirs()
                if (dir.exists() && dir.canWrite()) {
                    // Test if we can execute from this directory
                    val testFile = File(dir, "test_exec")
                    testFile.writeText("#!/bin/sh\necho ok")
                    Runtime.getRuntime().exec(arrayOf("chmod", "755", testFile.absolutePath)).waitFor()
                    val testResult = Runtime.getRuntime().exec(arrayOf(testFile.absolutePath)).waitFor()
                    testFile.delete()
                    if (testResult == 0) {
                        return path
                    }
                }
            } catch (_: Exception) {}
        }

        // Last resort: return the first candidate even if untested
        return candidates[0]
    }
}
