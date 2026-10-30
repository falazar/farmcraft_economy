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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Asynchronous chat against the locally running Ollama service. */
public class OllamaService {
    public static final String DEFAULT_MODEL = "llama3.2:3b";
    private static final String OLLAMA_BASE_URL = "http://127.0.0.1:11434";
    private static final String CHAT_ENDPOINT = OLLAMA_BASE_URL + "/api/chat";
    private static final int REQUEST_TIMEOUT_MS = 90_000;
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<CompletableFuture<String>> PENDING = new HashSet<>();
    private static final Set<HttpURLConnection> CONNECTIONS = new HashSet<>();
    private static ExecutorService executor;
    private static boolean stopped;

    private OllamaService() {}

    /** Re-enables requests after an integrated/dedicated server restart. */
    public static synchronized void start() {
        stopped = false;
    }

    /** Stops outstanding requests and releases owned threads; call on server shutdown. */
    public static void shutdown() {
        Set<CompletableFuture<String>> pending;
        Set<HttpURLConnection> connections;
        synchronized (OllamaService.class) {
            stopped = true;
            pending = new HashSet<>(PENDING);
            connections = new HashSet<>(CONNECTIONS);
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
            PENDING.clear();
            CONNECTIONS.clear();
        }
        for (CompletableFuture<String> future : pending)
            future.complete("[AI Error] Ollama service stopped.");
        for (HttpURLConnection connection : connections)
            connection.disconnect();
    }

    public static synchronized CompletableFuture<String> chat(String prompt, String model) {
        if (stopped)
            return CompletableFuture.completedFuture("[AI Error] Ollama service stopped.");
        if (executor == null) {
            executor = Executors.newFixedThreadPool(2, task -> {
                Thread thread = new Thread(task, "FarmUpCraft-Ollama");
                thread.setDaemon(true);
                return thread;
            });
        }
        CompletableFuture<String> result = new CompletableFuture<>();
        PENDING.add(result);
        result.whenComplete((reply, failure) -> {
            synchronized (OllamaService.class) {
                PENDING.remove(result);
            }
        });
        executor.execute(() -> {
            if (!result.isDone())
                result.complete(request(prompt, model, result));
        });
        return result;
    }

    public static CompletableFuture<String> chat(String prompt) {
        return chat(prompt, DEFAULT_MODEL);
    }

    private static String request(String prompt, String model, CompletableFuture<String> result) {
        HttpURLConnection conn = null;
        try {
            byte[] bodyBytes = buildChatRequest(model, prompt).getBytes(StandardCharsets.UTF_8);
            conn = (HttpURLConnection) URI.create(CHAT_ENDPOINT).toURL().openConnection();
            synchronized (OllamaService.class) {
                if (stopped || result.isDone())
                    return "[AI Error] Ollama service stopped.";
                CONNECTIONS.add(conn);
            }
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
                try (InputStream stream = conn.getErrorStream()) {
                    LOGGER.error("[OllamaService] Ollama returned HTTP {}: {}", status, readStream(stream));
                }
                return "[AI Error] Ollama returned HTTP " + status + ". Check server logs for details.";
            }
            try (InputStream stream = conn.getInputStream()) {
                return parseContentFromResponse(readStream(stream));
            }
        } catch (ConnectException e) {
            LOGGER.error("[OllamaService] Cannot connect to Ollama at {}. Is Ollama running? Start it with 'ollama serve'.",
                    OLLAMA_BASE_URL, e);
            return "[AI Error] Could not connect to Ollama at " + OLLAMA_BASE_URL
                    + ". Make sure Ollama is installed and running ('ollama serve' in a terminal).";
        } catch (Exception e) {
            LOGGER.error("[OllamaService] Unexpected error calling Ollama", e);
            return "[AI Error] " + e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (conn != null) {
                synchronized (OllamaService.class) {
                    CONNECTIONS.remove(conn);
                }
                conn.disconnect();
            }
        }
    }

    static String buildChatRequest(String model, String content) {
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

    private static String readStream(InputStream stream) throws IOException {
        return stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    static String parseContentFromResponse(String responseBody) {
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        return root.getAsJsonObject("message").get("content").getAsString();
    }
}
