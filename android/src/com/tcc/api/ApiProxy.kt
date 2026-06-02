package com.tcc.api

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL

// 本地 API 代理：claude 二进制 → localhost:port → 真实 API
// 解决 glibc Node.js 在 Android 上无法联网的问题
object ApiProxy {

    private const val TAG = "TCC.Proxy"
    private var serverSocket: ServerSocket? = null
    private var proxyThread: Thread? = null
    private var running = false

    val port: Int get() = serverSocket?.localPort ?: 0

    fun start() {
        if (running) return
        running = true
        serverSocket = ServerSocket(0) // 随机端口
        Log.d(TAG, "Proxy started on port $port")

        proxyThread = Thread {
            while (running) {
                try {
                    val client = serverSocket?.accept() ?: break
                    Thread { handleRequest(client) }.start()
                } catch (_: Exception) { break }
            }
        }.apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    private fun handleRequest(client: java.net.Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            val method = parts.getOrElse(0) { "" }
            val path = parts.getOrElse(1) { "" }

            // 读取 headers
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val kv = line.split(":", limit = 2)
                if (kv.size == 2) headers[kv[0].trim().lowercase()] = kv[1].trim()
            }

            // 读取 body
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                reader.read(buf, 0, contentLength)
                String(buf)
            } else ""

            Log.d(TAG, "$method $path body_len=$body.length")

            // 转发到真实 API
            val baseUrl = headers["x-target-base-url"] ?: "https://api.anthropic.com"
            val apiKey = headers["x-api-key"] ?: ""
            val targetUrl = URL("$baseUrl$path")

            val conn = targetUrl.openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("anthropic-version", headers["anthropic-version"] ?: "2023-06-01")
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 300000

            if (body.isNotEmpty()) {
                OutputStreamWriter(conn.outputStream).use { it.write(body) }
            }

            val responseCode = conn.responseCode
            Log.d(TAG, "API response: $responseCode")

            // 写回 response
            val out = client.getOutputStream()
            val responseLine = "HTTP/1.1 $responseCode OK\r\n"
            out.write(responseLine.toByteArray())

            val contentType = conn.contentType ?: "application/json"
            out.write("Content-Type: $contentType\r\n".toByteArray())
            out.write("Transfer-Encoding: chunked\r\n".toByteArray())
            out.write("\r\n".toByteArray())

            // 流式转发响应
            val inputStream = if (responseCode >= 400) conn.errorStream else conn.inputStream
            if (inputStream != null) {
                val buf = ByteArray(8192)
                while (true) {
                    val n = inputStream.read(buf)
                    if (n <= 0) break
                    // chunked encoding
                    val chunk = String(buf, 0, n)
                    out.write("${Integer.toHexString(n)}\r\n".toByteArray())
                    out.write(buf, 0, n)
                    out.write("\r\n".toByteArray())
                    out.flush()
                }
            }
            out.write("0\r\n\r\n".toByteArray())
            out.flush()

            conn.disconnect()
            client.close()

        } catch (e: Exception) {
            Log.e(TAG, "handleRequest error: ${e.message}")
            try { client.close() } catch (_: Exception) {}
        }
    }
}
