package com.tcc.api;

import com.tcc.data.ConfigManager;
import com.tcc.model.Message;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Runnable that performs a streaming chat request to the Anthropic API.
 * This is a top-level class (not an inner class) to avoid a d8 bug
 * that crashes on inner class bytecode.
 *
 * IMPORTANT: Callbacks are invoked from THIS THREAD, not the main thread.
 * UI code must use Activity.runOnUiThread() or Handler.post() when
 * receiving callbacks.
 */
public class StreamChatTask implements Runnable {

    private final AnthropicClient client;
    private final List<Message> messages;
    private final String systemPrompt;
    private final ConfigManager config;
    private final StreamCallback callback;

    public StreamChatTask(AnthropicClient client, List<Message> messages,
                          String systemPrompt, ConfigManager config,
                          StreamCallback callback) {
        this.client = client;
        this.messages = messages;
        this.systemPrompt = systemPrompt;
        this.config = config;
        this.callback = callback;
    }

    @Override
    public void run() {
        performStreamChat();
    }

    private boolean isAborted() {
        return client != null && client.isAborted();
    }

    private void performStreamChat() {
        HttpURLConnection connection = null;
        try {
            String baseUrl = config.getBaseUrl();
            if (baseUrl == null || baseUrl.isEmpty()) {
                baseUrl = "https://api.deepseek.com/anthropic";
            }
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }

            URL url = new URL(baseUrl + "/v1/messages");
            connection = (HttpURLConnection) url.openConnection();

            String apiKey = config.getApiKey();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("x-api-key", apiKey);
            connection.setRequestProperty("anthropic-version", "2023-06-01");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            connection.setChunkedStreamingMode(0);

            // Build and send request body
            JSONObject body = buildRequestBody();
            String bodyStr = body.toString();

            OutputStream os = connection.getOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);
            writer.write(bodyStr);
            writer.flush();
            writer.close();
            os.close();

            if (isAborted()) {
                callback.onError("Request aborted");
                return;
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                String errorBody = readErrorStream(connection);
                callback.onError("HTTP " + responseCode + ": " + errorBody);
                return;
            }

            InputStream inputStream = connection.getInputStream();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8));

            parseSSE(reader);

            reader.close();
            inputStream.close();

        } catch (Exception e) {
            if (!isAborted()) {
                callback.onError("Network error: " + e.getMessage());
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private JSONObject buildRequestBody() throws JSONException {
        JSONObject body = new JSONObject();

        String model = config.getModel();
        if (model == null || model.isEmpty()) {
            model = "deepseek-v4-flash";
        }
        body.put("model", model);

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            body.put("system", systemPrompt);
        }

        JSONArray msgArray = new JSONArray();
        if (messages != null) {
            for (Message msg : messages) {
                if (msg == null) continue;
                JSONObject msgJson = new JSONObject();
                msgJson.put("role", msg.role);
                msgJson.put("content", msg.content != null ? msg.content : "");
                msgArray.put(msgJson);
            }
        }
        body.put("messages", msgArray);
        body.put("stream", true);

        return body;
    }

    private void parseSSE(BufferedReader reader) throws java.io.IOException {
        StringBuilder currentData = new StringBuilder();
        int ch;

        while (!isAborted() && (ch = reader.read()) != -1) {
            char c = (char) ch;

            if (c == '\n') {
                String line = currentData.toString().trim();
                currentData.setLength(0);

                if (line.isEmpty()) {
                    continue;
                }

                if (line.startsWith("data: ")) {
                    String jsonStr = line.substring(6).trim();
                    if (!jsonStr.isEmpty() && !isAborted()) {
                        handleSSEEvent(jsonStr);
                    }
                }
            } else if (c == '\r') {
                continue;
            } else {
                currentData.append(c);
            }
        }
    }

    private void handleSSEEvent(String jsonStr) {
        try {
            JSONObject event = new JSONObject(jsonStr);
            String type = event.optString("type", "");

            if ("ping".equals(type)) {
                return;
            }

            if ("message_start".equals(type)) {
                return;
            }

            if ("content_block_start".equals(type)) {
                JSONObject contentBlock = event.optJSONObject("content_block");
                if (contentBlock != null) {
                    String blockType = contentBlock.optString("type", "");
                    if ("text".equals(blockType)) {
                        String text = contentBlock.optString("text", "");
                        if (!text.isEmpty()) {
                            callback.onChunk(text);
                        }
                    }
                }
                return;
            }

            if ("content_block_delta".equals(type)) {
                JSONObject delta = event.optJSONObject("delta");
                if (delta != null) {
                    String deltaType = delta.optString("type", "");
                    if ("text_delta".equals(deltaType)) {
                        String text = delta.optString("text", "");
                        if (!text.isEmpty()) {
                            callback.onChunk(text);
                        }
                    } else if ("input_json_delta".equals(deltaType)) {
                        String partialJson = delta.optString("partial_json", "");
                        if (!partialJson.isEmpty()) {
                            callback.onChunk(partialJson);
                        }
                    }
                }
                return;
            }

            if ("content_block_stop".equals(type)) {
                return;
            }

            if ("message_delta".equals(type)) {
                JSONObject delta = event.optJSONObject("delta");
                if (delta != null) {
                    String stopReason = delta.optString("stop_reason", "");
                    if (!stopReason.isEmpty()) {
                        callback.onDone(stopReason);
                    }
                }
                return;
            }

            if ("message_stop".equals(type)) {
                callback.onDone("");
                return;
            }

            if ("error".equals(type)) {
                JSONObject error = event.optJSONObject("error");
                String errorMsg = "Unknown error";
                if (error != null) {
                    errorMsg = error.optString("message", "API error");
                }
                callback.onError(errorMsg);
                return;
            }

        } catch (JSONException e) {
            // Ignore malformed JSON events
        }
    }

    private String readErrorStream(HttpURLConnection conn) {
        try {
            InputStream es = conn.getErrorStream();
            if (es == null) return "";
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(es, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
