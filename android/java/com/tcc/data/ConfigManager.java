package com.tcc.data;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * Manages app configuration as a JSON file.
 * Singleton pattern.
 */
public class ConfigManager {

    private static ConfigManager instance;

    private static final String CONFIG_FILE = "config.json";

    // Default values
    private static final String DEFAULT_API_KEY = "";
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com/anthropic";
    private static final String DEFAULT_MODEL = "deepseek-v4-flash";
    private static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful AI assistant.";
    private static final int DEFAULT_FONT_SIZE = 14;
    private static final String DEFAULT_THEME = "dark";

    private Context context;
    private JSONObject config;
    private boolean loaded;

    private ConfigManager(Context context) {
        this.context = context.getApplicationContext();
        this.config = new JSONObject();
        this.loaded = false;
    }

    public static synchronized ConfigManager getInstance(Context context) {
        if (instance == null) {
            instance = new ConfigManager(context);
        }
        return instance;
    }

    // ─── Getters with defaults ─────────────────────────────────

    public String getApiKey() {
        return getString("apiKey", DEFAULT_API_KEY);
    }

    public String getBaseUrl() {
        return getString("baseUrl", DEFAULT_BASE_URL);
    }

    public String getModel() {
        return getString("model", DEFAULT_MODEL);
    }

    public String getSystemPrompt() {
        return getString("systemPrompt", DEFAULT_SYSTEM_PROMPT);
    }

    public int getFontSize() {
        return getInt("fontSize", DEFAULT_FONT_SIZE);
    }

    public String getTheme() {
        return getString("theme", DEFAULT_THEME);
    }

    // ─── Setters (auto-save) ───────────────────────────────────

    public void setApiKey(String key) {
        setString("apiKey", key);
    }

    public void setBaseUrl(String url) {
        setString("baseUrl", url);
    }

    public void setModel(String model) {
        setString("model", model);
    }

    public void setSystemPrompt(String prompt) {
        setString("systemPrompt", prompt);
    }

    public void setFontSize(int size) {
        setInt("fontSize", size);
    }

    public void setTheme(String theme) {
        setString("theme", theme);
    }

    // ─── Internal helpers ──────────────────────────────────────

    private String getString(String key, String defaultValue) {
        ensureLoaded();
        if (config.has(key)) {
            try {
                return config.getString(key);
            } catch (JSONException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private int getInt(String key, int defaultValue) {
        ensureLoaded();
        if (config.has(key)) {
            try {
                return config.getInt(key);
            } catch (JSONException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private void setString(String key, String value) {
        ensureLoaded();
        try {
            config.put(key, value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        save();
    }

    private void setInt(String key, int value) {
        ensureLoaded();
        try {
            config.put(key, value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        save();
    }

    private void ensureLoaded() {
        if (!loaded) {
            load();
            loaded = true;
        }
    }

    // ─── Batch operations ──────────────────────────────────────

    /**
     * Get all configuration as a JSONObject.
     */
    public JSONObject getAll() {
        ensureLoaded();
        JSONObject copy = new JSONObject();
        try {
            Iterator<String> keys = config.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                copy.put(key, config.get(key));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return copy;
    }

    /**
     * Update multiple config values from a JSON object and save.
     */
    public void updateFromJson(JSONObject json) {
        ensureLoaded();
        if (json == null) return;
        try {
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                config.put(key, json.get(key));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        save();
    }

    /**
     * Force-save the current configuration to disk.
     */
    public void save() {
        try {
            File configFile = new File(context.getFilesDir(), CONFIG_FILE);
            FileOutputStream fos = new FileOutputStream(configFile);
            OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            writer.write(config.toString(2));
            writer.flush();
            writer.close();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Load configuration from disk.
     */
    public void load() {
        try {
            File configFile = new File(context.getFilesDir(), CONFIG_FILE);
            if (!configFile.exists()) {
                config = new JSONObject();
                return;
            }
            FileInputStream fis = new FileInputStream(configFile);
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
            config = new JSONObject(sb.toString());
        } catch (Exception e) {
            config = new JSONObject();
        }
    }
}
