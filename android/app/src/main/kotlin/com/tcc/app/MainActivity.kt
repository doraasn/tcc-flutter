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
                        // Return a directory where binaries can be executed.
                        // /data/local/tmp/ is accessible to all apps and allows execution.
                        val tmpDir = File("/data/local/tmp/tcc")
                        if (!tmpDir.exists()) tmpDir.mkdirs()
                        result.success(tmpDir.absolutePath)
                    }
                    "copyAndMakeExecutable" -> {
                        // Copy a binary to the executable directory and chmod +x.
                        val srcPath = call.argument<String>("src")
                        val dstName = call.argument<String>("dst")
                        if (srcPath == null || dstName == null) {
                            result.error("INVALID_ARGS", "src and dst required", null)
                            return@setMethodCallHandler
                        }
                        try {
                            val tmpDir = File("/data/local/tmp/tcc")
                            if (!tmpDir.exists()) tmpDir.mkdirs()
                            val dst = File(tmpDir, dstName)
                            File(srcPath).copyTo(dst, overwrite = true)
                            Runtime.getRuntime().exec(arrayOf("chmod", "755", dst.absolutePath)).waitFor()
                            result.success(dst.absolutePath)
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
}
