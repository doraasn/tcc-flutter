package com.tcc.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Represents a single message in a conversation.
 */
public class Message {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_SYSTEM = "system";

    public String id;
    public String role;
    public String content;
    public long timestamp;
    public boolean isStreaming;

    public Message() {
        this.id = UUID.randomUUID().toString();
        this.role = ROLE_USER;
        this.content = "";
        this.timestamp = System.currentTimeMillis();
        this.isStreaming = false;
    }

    public Message(String role, String content) {
        this.id = UUID.randomUUID().toString();
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
        this.isStreaming = false;
    }

    public Message(String role, String content, long timestamp) {
        this.id = UUID.randomUUID().toString();
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
        this.isStreaming = false;
    }

    // ─── Getters (used by UI layer) ──────────────────────────

    public String getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    // ─── Setters (used by UI layer) ──────────────────────────

    public void setContent(String content) {
        this.content = content;
    }

    public void setStreaming(boolean streaming) {
        isStreaming = streaming;
    }

    /**
     * Serialize this message to a JSONObject.
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("role", role);
            json.put("content", content);
            json.put("timestamp", timestamp);
            json.put("isStreaming", isStreaming);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    /**
     * Deserialize a Message from a JSONObject.
     */
    public static Message fromJSON(JSONObject json) {
        Message msg = new Message();
        try {
            if (json.has("id")) {
                msg.id = json.getString("id");
            }
            if (json.has("role")) {
                msg.role = json.getString("role");
            }
            if (json.has("content")) {
                msg.content = json.getString("content");
            }
            if (json.has("timestamp")) {
                msg.timestamp = json.getLong("timestamp");
            }
            if (json.has("isStreaming")) {
                msg.isStreaming = json.getBoolean("isStreaming");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return msg;
    }
}
