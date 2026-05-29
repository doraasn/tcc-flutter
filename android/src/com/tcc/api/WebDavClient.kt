package com.tcc.api

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class WebDavClient(
    private val serverUrl: String,
    private val username: String,
    private val password: String
) {
    data class Result(val ok: Boolean, val message: String, val data: ByteArray? = null)

    private fun createConnection(path: String, method: String): HttpURLConnection {
        val url = URL("${serverUrl.trimEnd('/')}/$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.doInput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 30000

        val credentials = android.util.Base64.encodeToString(
            "$username:$password".toByteArray(), android.util.Base64.NO_WRAP
        )
        conn.setRequestProperty("Authorization", "Basic $credentials")

        return conn
    }

    fun testConnection(): Result {
        return try {
            val conn = createConnection("", "PROPFIND")
            conn.setRequestProperty("Depth", "0")
            conn.doOutput = false
            val code = conn.responseCode
            conn.disconnect()

            if (code in 200..299) {
                Result(true, "连接成功")
            } else if (code == 401) {
                Result(false, "认证失败，请检查用户名和密码")
            } else {
                Result(false, "服务器返回: $code")
            }
        } catch (e: Exception) {
            Result(false, "连接失败: ${e.message ?: "未知错误"}")
        }
    }

    fun upload(path: String, data: ByteArray): Result {
        return try {
            val conn = createConnection(path, "PUT")
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(data.size)
            conn.outputStream.write(data)
            conn.outputStream.flush()
            val code = conn.responseCode
            conn.disconnect()

            if (code in 200..299) {
                Result(true, "上传成功")
            } else {
                Result(false, "上传失败: $code")
            }
        } catch (e: Exception) {
            Result(false, "上传失败: ${e.message ?: "未知错误"}")
        }
    }

    fun download(path: String): Result {
        return try {
            val conn = createConnection(path, "GET")
            val code = conn.responseCode
            if (code in 200..299) {
                val baos = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                val input = conn.inputStream
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    baos.write(buffer, 0, bytesRead)
                }
                input.close()
                conn.disconnect()
                Result(true, "下载成功", baos.toByteArray())
            } else {
                conn.disconnect()
                Result(false, "下载失败: $code")
            }
        } catch (e: Exception) {
            Result(false, "下载失败: ${e.message ?: "未知错误"}")
        }
    }

    fun list(path: String): Result {
        return try {
            val conn = createConnection(path, "PROPFIND")
            conn.setRequestProperty("Depth", "1")
            conn.doOutput = false
            val code = conn.responseCode
            if (code in 200..299) {
                val baos = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                val input = conn.inputStream
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    baos.write(buffer, 0, bytesRead)
                }
                input.close()
                conn.disconnect()
                val xml = String(baos.toByteArray(), Charsets.UTF_8)
                val files = Regex("<d:displayname>(.*?)</d:displayname>")
                    .findAll(xml).map { it.groupValues[1] }.toList()
                Result(true, "共 ${files.size} 个文件", files.joinToString("\n").toByteArray())
            } else {
                conn.disconnect()
                Result(false, "列出失败: $code")
            }
        } catch (e: Exception) {
            Result(false, "列出失败: ${e.message ?: "未知错误"}")
        }
    }

    fun mkdir(path: String): Result {
        return try {
            val conn = createConnection(path, "MKCOL")
            conn.doOutput = false
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) {
                Result(true, "目录创建成功")
            } else if (code == 405) {
                Result(true, "目录已存在")
            } else {
                Result(false, "创建目录失败: $code")
            }
        } catch (e: Exception) {
            Result(false, "创建目录失败: ${e.message ?: "未知错误"}")
        }
    }
}
