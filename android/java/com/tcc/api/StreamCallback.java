package com.tcc.api;

/**
 * Callback interface for streaming responses from the Anthropic API.
 */
public interface StreamCallback {

    /**
     * Called when a text chunk is received from the stream.
     *
     * @param text The text content delta
     */
    void onChunk(String text);

    /**
     * Called when the stream is complete.
     *
     * @param stopReason The reason the stream stopped (e.g., "end_turn", "max_tokens", "stop_sequence")
     */
    void onDone(String stopReason);

    /**
     * Called when an error occurs during streaming.
     *
     * @param error A description of the error
     */
    void onError(String error);
}
