package com.falazar.farmupcraft.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Service for communicating with a locally running Ollama instance.
 *
 * PREREQUISITES: Ollama must be installed and running locally before using this
 * service.
 * - Install Ollama: https://ollama.com
 * - Start Ollama: Run `ollama serve` in a terminal (on Windows it may start
 * automatically)
 * - Pull model: Run `ollama pull gemini-3-flash-preview:cloud` (or whichever
 * model you want)
 * - Verify: Run `ollama list` to confirm the model is available
 *
 * API reference:
 * https://github.com/ollama/ollama/blob/main/docs/api.md#generate-a-chat-completion
 */
public class OllamaService {

    /** The Ollama model to use. Change this to any model shown by `ollama list`. */
    public static final String DEFAULT_MODEL = "gemini-3-flash-preview:cloud";

    // Use 127.0.0.1 explicitly — on Windows, "localhost" can resolve to IPv6 (::1)
    // while Ollama only listens on IPv4, causing "Connection refused".
    private static final String OLLAMA_BASE_URL = "http://127.0.0.1:11434";
    private static final String CHAT_ENDPOINT = OLLAMA_BASE_URL + "/api/chat";

    /** How long to wait for a response before giving up (milliseconds). */
    private static final int REQUEST_TIMEOUT_MS = 60_000;

    /** Connection timeout — fail fast if Ollama is not reachable (milliseconds). */
    private static final int CONNECT_TIMEOUT_MS = 5_000;

    private static final CustomLogger LOGGER = new CustomLogger(OllamaService.class.getSimpleName());
    // SLF4J logger used directly so we can pass Throwable for full stack traces.
    private static final Logger SLF4J = LogUtils.getLogger();

    private OllamaService() {
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Sends a chat message to Ollama asynchronously and returns the assistant
     * reply.
     *
     * @param prompt The user message to send to the model.
     * @param model  The Ollama model name (e.g. "gemini-3-flash-preview:cloud").
     * @return A future that resolves to the assistant's reply text, or an error
     *         string on failure.
     */
    public static CompletableFuture<String> chat(String prompt, String model) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection conn = null;
            try {
                String requestBody = buildChatRequest(model, prompt);
                byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);

                // HttpURLConnection is used here intentionally — java.net.http.HttpClient
                // uses NIO channels that are incompatible with Forge's JVM environment
                // and throw ClosedChannelException. HttpURLConnection works reliably.
                conn = (HttpURLConnection) new URL(CHAT_ENDPOINT).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(REQUEST_TIMEOUT_MS);
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bodyBytes);
                }

                int status = conn.getResponseCode();
                if (status != 200) {
                    String errorBody = readStream(conn.getErrorStream());
                    SLF4J.error("[OllamaService] Ollama returned HTTP {}: {}", status, errorBody);
                    return "[AI Error] Ollama returned HTTP " + status + ". Check server logs for details.";
                }

                String responseBody = readStream(conn.getInputStream());
                return parseContentFromResponse(responseBody);

            } catch (ConnectException e) {
                // Ollama is not running or the port is wrong — surface a clear actionable
                // message.
                SLF4J.error(
                        "[OllamaService] Cannot connect to Ollama at {}. Is Ollama running? Start it with 'ollama serve'.",
                        OLLAMA_BASE_URL, e);
                return "[AI Error] Could not connect to Ollama at " + OLLAMA_BASE_URL
                        + ". Make sure Ollama is installed and running ('ollama serve' in a terminal).";

            } catch (Exception e) {
                SLF4J.error("[OllamaService] Unexpected error calling Ollama", e);
                return "[AI Error] " + e.getClass().getSimpleName() + ": " + e.getMessage();

            } finally {
                if (conn != null)
                    conn.disconnect();
            }
        });
    }

    /** Convenience overload that uses {@link #DEFAULT_MODEL}. */
    public static CompletableFuture<String> chat(String prompt) {
        return chat(prompt, DEFAULT_MODEL);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String buildChatRequest(String model, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", content);

        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        body.addProperty("stream", false);

        return body.toString();
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null)
            return "";
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String parseContentFromResponse(String responseBody) {
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        return root.getAsJsonObject("message").get("content").getAsString();
    }
}
