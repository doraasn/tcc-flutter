package com.tcc.api

import com.tcc.data.ConfigManager
import com.tcc.model.Message
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class AnthropicClient {

    @Volatile
    private var aborted = false

    private var doneSent = false

    fun interface StreamCallback {
        fun onEvent(event: StreamEvent)
    }

    sealed class StreamEvent {
        data class Chunk(val text: String) : StreamEvent()
        data class ToolUse(val name: String, val input: String) : StreamEvent()
        data class Done(val stopReason: String) : StreamEvent()
        data class Error(val message: String) : StreamEvent()
    }

    fun streamChat(
        messages: List<Message>,
        systemPrompt: String?,
        config: ConfigManager,
        callback: StreamCallback
    ) {
        aborted = false
        doneSent = false

        Thread {
            var connection: HttpURLConnection? = null
            try {
                val baseUrl = config.getBaseUrl().trimEnd('/')
                val url = URL("$baseUrl/v1/messages")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("x-api-key", config.getApiKey())
                connection.setRequestProperty("anthropic-version", "2023-06-01")
                connection.doOutput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 120000

                val body = buildRequestBody(messages, systemPrompt, config)
                val outputStream = connection.outputStream
                outputStream.write(body.toString().toByteArray())
                outputStream.flush()
                outputStream.close()

                val responseCode = connection.responseCode
                Log.d("MCC", "API response code: $responseCode")
                if (responseCode !in 200..299) {
                    val errorBody = try {
                        BufferedReader(InputStreamReader(connection.errorStream)).readText()
                    } catch (e: Exception) {
                        "HTTP $responseCode"
                    }
                    Log.e("MCC", "API error ($responseCode): $errorBody")
                    try { java.io.File("/sdcard/Download/mcc_error.txt").writeText("API $responseCode: $errorBody") } catch (_: Exception) {}
                    callback.onEvent(StreamEvent.Error(errorBody))
                    return@Thread
                }

                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (aborted) {
                        reader.close()
                        return@Thread
                    }
                    val currentLine = line ?: continue
                    if (currentLine.startsWith("data: ")) {
                        val data = currentLine.substring(6).trim()
                        if (data == "[DONE]") {
                            if (!doneSent) {
                                doneSent = true
                                callback.onEvent(StreamEvent.Done("end_turn"))
                            }
                            continue
                        }
                        if (data.isEmpty()) continue
                        try {
                            val json = JSONObject(data)
                            handleStreamEvent(json, callback)
                        } catch (e: Exception) {
                            // Malformed JSON in SSE data — skip
                        }
                    }
                }
                reader.close()

            } catch (e: Exception) {
                Log.e("MCC", "Connection exception: ${e.javaClass.name}: ${e.message}", e)
                try { java.io.File("/sdcard/Download/mcc_error.txt").writeText("${e.javaClass.name}: ${e.message}") } catch (_: Exception) {}
                if (!aborted) {
                    callback.onEvent(StreamEvent.Error(e.message ?: "Unknown error"))
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    fun abort() {
        aborted = true
    }

    private fun buildRequestBody(
        messages: List<Message>,
        systemPrompt: String?,
        config: ConfigManager
    ): JSONObject {
        val effectiveSystem = systemPrompt
            ?: config.getSystemPrompt()
            ?: ""

        val body = JSONObject().apply {
            put("model", config.getModel())
            put("max_tokens", config.getMaxTokens())
            put("stream", true)
            if (effectiveSystem.isNotEmpty()) {
                put("system", effectiveSystem)
            }
        }

        val msgArray = JSONArray()
        for (msg in messages) {
            if (msg.role == "system") continue
            val m = JSONObject()
            m.put("role", msg.role)
            m.put("content", msg.content)
            msgArray.put(m)
        }
        body.put("messages", msgArray)

        return body
    }

    private fun handleStreamEvent(json: JSONObject, callback: StreamCallback) {
        val type = json.optString("type", "")

        when (type) {
            "content_block_delta" -> {
                val delta = json.optJSONObject("delta")
                if (delta != null) {
                    // text delta
                    val text = delta.optString("text", "")
                    if (text.isNotEmpty()) {
                        callback.onEvent(StreamEvent.Chunk(text))
                    }
                    // input_json_delta (for tool use streaming)
                    val partialJson = delta.optString("partial_json", "")
                    if (partialJson.isNotEmpty()) {
                        callback.onEvent(StreamEvent.Chunk(partialJson))
                    }
                }
            }

            "content_block_start" -> {
                val block = json.optJSONObject("content_block")
                if (block != null && block.optString("type") == "tool_use") {
                    callback.onEvent(StreamEvent.ToolUse(
                        name = block.optString("name", ""),
                        input = block.optString("input", "")
                    ))
                }
            }

            "message_delta" -> {
                val delta = json.optJSONObject("delta")
                if (delta != null && delta.has("stop_reason")) {
                    if (!doneSent) {
                        doneSent = true
                        val reason = delta.optString("stop_reason", "end_turn")
                        callback.onEvent(StreamEvent.Done(reason))
                    }
                }
            }

            "message_start" -> {
                // Optionally parse stop_reason from initial message if stop_sequence triggered
                val message = json.optJSONObject("message")
                if (message != null && message.has("stop_reason") && !message.isNull("stop_reason")) {
                    if (!doneSent) {
                        doneSent = true
                        val reason = message.optString("stop_reason", "")
                        if (reason.isNotEmpty()) {
                            callback.onEvent(StreamEvent.Done(reason))
                        }
                    }
                }
            }

            "error" -> {
                val errorObj = json.optJSONObject("error")
                val msg = if (errorObj != null) {
                    errorObj.optString("message", "API error")
                } else {
                    json.optString("message", "API error")
                }
                callback.onEvent(StreamEvent.Error(msg))
            }
        }
    }
}
