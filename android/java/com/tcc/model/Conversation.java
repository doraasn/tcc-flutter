package com.tcc.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a conversation containing multiple messages.
 */
public class Conversation {

    public String id;
    public String title;
    public String model;
    public long createdAt;
    public long updatedAt;
    public List<Message> messages;

    public Conversation() {
        this.id = "";
        this.title = "";
        this.model = "";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.messages = new ArrayList<Message>();
    }

    public Conversation(String id, String title, String model) {
        this.id = id;
        this.title = title;
        this.model = model;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.messages = new ArrayList<Message>();
    }

    /**
     * Add a message to the conversation and update the timestamp.
     */
    public void addMessage(Message msg) {
        if (msg != null) {
            messages.add(msg);
            updatedAt = System.currentTimeMillis();
            // Auto-generate title from first user message if empty
            if (title == null || title.isEmpty()) {
                if (Message.ROLE_USER.equals(msg.role) && msg.content != null) {
                    title = generateTitle(msg.content);
                }
            }
        }
    }

    /**
     * Get all messages in this conversation.
     */
    public List<Message> getMessages() {
        return messages;
    }

    // ─── Getters (used by UI layer) ──────────────────────────

    public String getId() {
        return id;
    }

    public String getModel() {
        return model;
    }

    /**
     * Get the last updated timestamp (for UI sorting/display).
     */
    public long getTimestamp() {
        return updatedAt;
    }

    /**
     * Get the title, auto-shortened to a reasonable length.
     */
    public String getTitle() {
        if (title == null || title.isEmpty()) {
            return "New Chat";
        }
        if (title.length() > 50) {
            return title.substring(0, 47) + "...";
        }
        return title;
    }

    // ─── Setters (used by UI layer) ──────────────────────────

    public void setTitle(String title) {
        this.title = title;
    }

    public void setModel(String model) {
        this.model = model;
    }

    /**
     * Generate a short title from the first user message content.
     */
    private String generateTitle(String content) {
        if (content == null || content.isEmpty()) {
            return "New Chat";
        }
        // Take the first line or first 60 characters
        String cleaned = content.trim();
        int newlineIdx = cleaned.indexOf('\n');
        if (newlineIdx > 0) {
            cleaned = cleaned.substring(0, newlineIdx);
        }
        if (cleaned.length() > 60) {
            cleaned = cleaned.substring(0, 57) + "...";
        }
        return cleaned;
    }

    /**
     * Serialize this conversation to a JSONObject.
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("title", title != null ? title : "");
            json.put("model", model);
            json.put("createdAt", createdAt);
            json.put("updatedAt", updatedAt);

            JSONArray msgArray = new JSONArray();
            for (Message msg : messages) {
                if (msg != null) {
                    msgArray.put(msg.toJSON());
                }
            }
            json.put("messages", msgArray);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    /**
     * Deserialize a Conversation from a JSONObject.
     */
    public static Conversation fromJSON(JSONObject json) {
        Conversation conv = new Conversation();
        try {
            if (json.has("id")) {
                conv.id = json.getString("id");
            }
            if (json.has("title")) {
                conv.title = json.getString("title");
            }
            if (json.has("model")) {
                conv.model = json.getString("model");
            }
            if (json.has("createdAt")) {
                conv.createdAt = json.getLong("createdAt");
            }
            if (json.has("updatedAt")) {
                conv.updatedAt = json.getLong("updatedAt");
            }
            if (json.has("messages")) {
                JSONArray msgArray = json.getJSONArray("messages");
                conv.messages = new ArrayList<Message>();
                for (int i = 0; i < msgArray.length(); i++) {
                    JSONObject msgJson = msgArray.getJSONObject(i);
                    conv.messages.add(Message.fromJSON(msgJson));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return conv;
    }
}
