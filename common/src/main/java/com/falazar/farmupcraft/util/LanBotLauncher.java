package com.falazar.farmupcraft.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class LanBotLauncher {
    public static final String DEFAULT_BOT_NAME = "BridgeBot";
    public static final String BRIDGE_DIR_PROP = "farmupcraft.lanBridgeDir";
    public static final String BRIDGE_DIR_ENV = "FARMUPCRAFT_LAN_BRIDGE_DIR";
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Map<String, Process> PROCESSES = new ConcurrentHashMap<>();

    private LanBotLauncher() {}

    public static String validateBotName(String rawName) {
        String name = rawName == null || rawName.isBlank() ? DEFAULT_BOT_NAME : rawName.trim();
        if (!VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Bot name must be 3-16 characters using only letters, numbers, or underscores.");
        }
        return name;
    }

    public static Path resolveBridgeDir() {
        String configured = System.getProperty(BRIDGE_DIR_PROP);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(BRIDGE_DIR_ENV);
        }
        if (configured != null && !configured.isBlank()) {
            Path custom = Paths.get(configured.trim());
            if (Files.isRegularFile(custom.resolve("src").resolve("cli.cjs"))) {
                return custom;
            }
        }
        String home = System.getProperty("user.home", "");
        List<Path> candidates = List.of(
                Paths.get(home, "IdeaProjects", "random_test_projects", "neoforge_lan_bridge"),
                Paths.get("C:\\Users\\robin\\IdeaProjects\\random_test_projects\\neoforge_lan_bridge"));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate.resolve("src").resolve("cli.cjs"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find neoforge_lan_bridge/src/cli.cjs. Set -D"
                + BRIDGE_DIR_PROP + " or " + BRIDGE_DIR_ENV + ".");
    }

    public static synchronized boolean isRunning(String name) {
        String key = validateBotName(name).toLowerCase(Locale.ROOT);
        Process proc = PROCESSES.get(key);
        if (proc != null && proc.isAlive()) {
            return true;
        }
        PROCESSES.remove(key);
        return false;
    }

    public static synchronized Process startBot(String rawName, int port) throws IOException {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("World is not Open to LAN (port=" + port + "). Open to LAN first.");
        }
        String name = validateBotName(rawName);
        if ("stop".equalsIgnoreCase(name)) {
            throw new IllegalArgumentException("Use /bot stop [name] to stop a bot.");
        }
        String key = name.toLowerCase(Locale.ROOT);
        Process existing = PROCESSES.get(key);
        if (existing != null && existing.isAlive()) {
            throw new IllegalStateException("Bot '" + name + "' is already running (PID " + existing.pid() + ").");
        }
        Path bridgeDir = resolveBridgeDir();
        Path logFile = bridgeDir.resolve("bot-" + key + ".log");
        ProcessBuilder pb = new ProcessBuilder("node", "src\\cli.cjs");
        pb.directory(bridgeDir.toFile());
        Map<String, String> env = pb.environment();
        env.put("MC_PORT", Integer.toString(port));
        env.put("MC_USERNAME", name);
        env.put("MC_HOST", "127.0.0.1");
        env.put("MC_AUTH", "offline");
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        Process process = pb.start();
        PROCESSES.put(key, process);
        return process;
    }

    public static synchronized int stopBot(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            int stopped = 0;
            for (var entry : List.copyOf(PROCESSES.entrySet())) {
                if (terminate(entry.getValue())) {
                    stopped++;
                }
                PROCESSES.remove(entry.getKey());
            }
            return stopped;
        }
        String key = validateBotName(rawName).toLowerCase(Locale.ROOT);
        Process proc = PROCESSES.remove(key);
        return terminate(proc) ? 1 : 0;
    }

    public static synchronized void stopAll() {
        stopBot(null);
    }

    private static boolean terminate(Process proc) {
        if (proc == null || !proc.isAlive()) {
            return false;
        }
        proc.destroy();
        try {
            if (!proc.waitFor(2, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            proc.destroyForcibly();
        }
        return true;
    }
}
