package com.tcc.api;

import com.tcc.data.ConfigManager;
import com.tcc.model.Message;

import java.util.List;

/**
 * HTTP client for streaming chat completions from the Anthropic API.
 *
 * Callback methods are invoked from a background thread.
 * UI code must post to the main thread using Activity.runOnUiThread() or Handler.
 */
public class AnthropicClient {

    private volatile boolean aborted = false;

    /**
     * Start a streaming chat request to the Anthropic API.
     */
    public void streamChat(final List<Message> messages, final String systemPrompt,
                           final ConfigManager config, final StreamCallback callback) {
        aborted = false;
        StreamChatTask task = new StreamChatTask(this, messages, systemPrompt, config, callback);
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Abort the current streaming request.
     */
    public void abort() {
        aborted = true;
    }

    /**
     * Check if the request has been aborted.
     */
    public boolean isAborted() {
        return aborted;
    }
}
