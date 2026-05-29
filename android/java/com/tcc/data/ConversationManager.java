package com.tcc.data;

import android.content.Context;

import com.tcc.model.Conversation;
import com.tcc.model.Message;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages conversations stored as individual JSON files.
 * Singleton pattern.
 */
public class ConversationManager {

    private static ConversationManager instance;

    private static final String CONVERSATIONS_DIR = "conversations";
    private static final String INDEX_FILE = "index.json";

    private Context context;
    private File convDir;

    private ConversationManager(Context context) {
        this.context = context.getApplicationContext();
        this.convDir = new File(this.context.getFilesDir(), CONVERSATIONS_DIR);
        if (!this.convDir.exists()) {
            this.convDir.mkdirs();
        }
    }

    public static synchronized ConversationManager getInstance(Context context) {
        if (instance == null) {
            instance = new ConversationManager(context);
        }
        return instance;
    }

    /**
     * List all conversations, sorted by updatedAt descending.
     */
    public List<Conversation> listConversations() {
        List<Conversation> result = new ArrayList<Conversation>();
        JSONArray index = loadIndex();
        if (index == null) return result;

        for (int i = 0; i < index.length(); i++) {
            try {
                String id = index.getString(i);
                Conversation conv = getConversation(id);
                if (conv != null) {
                    result.add(conv);
                }
            } catch (JSONException e) {
                // Skip invalid entries
            }
        }

        // Sort by updatedAt descending (manual sort to avoid inner classes)
        for (int i = 0; i < result.size() - 1; i++) {
            for (int j = i + 1; j < result.size(); j++) {
                Conversation a = result.get(i);
                Conversation b = result.get(j);
                if (b.updatedAt > a.updatedAt) {
                    result.set(i, b);
                    result.set(j, a);
                }
            }
        }

        return result;
    }

    /**
     * Get a specific conversation by ID.
     */
    public Conversation getConversation(String id) {
        if (id == null || id.isEmpty()) return null;
        File file = new File(convDir, id + ".json");
        if (!file.exists()) return null;

        try {
            FileInputStream fis = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(fis, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }
            reader.close();
            fis.close();

            JSONObject json = new JSONObject(sb.toString());
            return Conversation.fromJSON(json);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Create a new conversation with a generated ID and current timestamps.
     */
    public Conversation createConversation(String model) {
        String id = UUID.randomUUID().toString();
        Conversation conv = new Conversation(id, "", model);
        saveConversation(conv);
        return conv;
    }

    /**
     * Delete a conversation by ID.
     */
    public void deleteConversation(String id) {
        if (id == null || id.isEmpty()) return;

        // Remove the file
        File file = new File(convDir, id + ".json");
        if (file.exists()) {
            file.delete();
        }

        // Remove from index
        JSONArray index = loadIndex();
        if (index == null) return;

        JSONArray newIndex = new JSONArray();
        for (int i = 0; i < index.length(); i++) {
            try {
                String existingId = index.getString(i);
                if (!existingId.equals(id)) {
                    newIndex.put(existingId);
                }
            } catch (JSONException e) {
                // Skip invalid
            }
        }
        saveIndex(newIndex);
    }

    /**
     * Delete all conversations.
     */
    public void deleteAllConversations() {
        File[] files = convDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".json") &&
                        !file.getName().equals(INDEX_FILE)) {
                    file.delete();
                }
            }
        }
        saveIndex(new JSONArray());
    }

    /**
     * Save a conversation to its file and update the index.
     */
    public void saveConversation(Conversation c) {
        if (c == null || c.id == null || c.id.isEmpty()) return;

        // Write conversation file
        try {
            File file = new File(convDir, c.id + ".json");
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            writer.write(c.toJSON().toString(2));
            writer.flush();
            writer.close();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // Update index (add if not present)
        JSONArray index = loadIndex();
        if (index == null) {
            index = new JSONArray();
        }

        boolean found = false;
        for (int i = 0; i < index.length(); i++) {
            try {
                if (index.getString(i).equals(c.id)) {
                    found = true;
                    break;
                }
            } catch (JSONException e) {
                // Skip invalid
            }
        }
        if (!found) {
            index.put(c.id);
        }
        saveIndex(index);
    }

    /**
     * Add a message to a conversation and save.
     */
    public void addMessage(String convId, Message m) {
        if (convId == null || m == null) return;
        Conversation conv = getConversation(convId);
        if (conv == null) return;
        conv.addMessage(m);
        saveConversation(conv);
    }

    /**
     * Update a specific message at the given index (for streaming updates).
     */
    public void updateMessage(String convId, int index, Message m) {
        if (convId == null || m == null) return;
        Conversation conv = getConversation(convId);
        if (conv == null) return;
        List<Message> messages = conv.getMessages();
        if (index >= 0 && index < messages.size()) {
            messages.set(index, m);
            conv.updatedAt = System.currentTimeMillis();
            saveConversation(conv);
        }
    }

    // ─── Index management ──────────────────────────────────────

    private JSONArray loadIndex() {
        File indexFile = new File(convDir, INDEX_FILE);
        if (!indexFile.exists()) {
            return new JSONArray();
        }
        try {
            FileInputStream fis = new FileInputStream(indexFile);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(fis, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }
            reader.close();
            fis.close();
            return new JSONArray(sb.toString());
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void saveIndex(JSONArray index) {
        if (index == null) return;
        try {
            File indexFile = new File(convDir, INDEX_FILE);
            FileOutputStream fos = new FileOutputStream(indexFile);
            OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            writer.write(index.toString(2));
            writer.flush();
            writer.close();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
