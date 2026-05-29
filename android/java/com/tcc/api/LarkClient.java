package com.tcc.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Lightweight client for executing lark-cli commands.
 * lark-cli must be installed on the system (e.g., in Termux).
 */
public class LarkClient {

    private static final String LARK_CLI = "lark-cli";

    /**
     * Check if lark-cli is available on the system.
     *
     * @return true if lark-cli can be executed
     */
    public static boolean isAvailable() {
        try {
            Process process = new ProcessBuilder("which", LARK_CLI)
                    .redirectErrorStream(true)
                    .start();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            reader.close();
            process.waitFor();
            return line != null && !line.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Execute a lark-cli command and return stdout as a string.
     *
     * @param args The command arguments (e.g., {"auth", "status"})
     * @return The stdout output as a string
     */
    public static String execute(String[] args) {
        if (args == null) {
            return "";
        }

        String[] cmd = new String[args.length + 1];
        cmd[0] = LARK_CLI;
        System.arraycopy(args, 0, cmd, 1, args.length);

        try {
            Process process = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) {
                    output.append('\n');
                }
                output.append(line);
            }
            reader.close();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return output.toString();
            }

            return output.toString();

        } catch (IOException e) {
            return "Error: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: Process interrupted";
        }
    }

    /**
     * Execute a lark-cli command and parse the output as JSON.
     *
     * @param args The command arguments
     * @return A JSONObject parsed from stdout, or an empty JSONObject on failure
     */
    public static JSONObject executeJson(String[] args) {
        String output = execute(args);
        if (output == null || output.isEmpty()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(output);
        } catch (JSONException e) {
            JSONObject error = new JSONObject();
            try {
                error.put("error", "Failed to parse output as JSON");
                error.put("raw", output);
            } catch (JSONException je) {
                // Should not happen
            }
            return error;
        }
    }

    /**
     * Execute a lark-cli command with a specific working directory.
     *
     * @param args The command arguments
     * @param workingDir The working directory for the process
     * @return The stdout output as a string
     */
    public static String execute(String[] args, String workingDir) {
        if (args == null) {
            return "";
        }

        String[] cmd = new String[args.length + 1];
        cmd[0] = LARK_CLI;
        System.arraycopy(args, 0, cmd, 1, args.length);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (workingDir != null && !workingDir.isEmpty()) {
                pb.directory(new File(workingDir));
            }
            pb.redirectErrorStream(true);

            Process process = pb.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) {
                    output.append('\n');
                }
                output.append(line);
            }
            reader.close();

            process.waitFor();
            return output.toString();

        } catch (IOException e) {
            return "Error: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: Process interrupted";
        }
    }
}
