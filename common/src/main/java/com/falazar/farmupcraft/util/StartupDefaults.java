package com.falazar.farmupcraft.util;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StartupDefaults {
    private StartupDefaults() {}

    public static boolean installOnion(Path config, boolean modern) throws IOException {
        Path marker = config.resolve("farmupcraft").resolve("solonion-progression-v1.done");
        if (Files.exists(marker)) return false;
        String resource = "/farmupcraft/defaults/solonion-" + (modern ? "modern" : "legacy") + ".json";
        JsonObject preset;
        try (InputStream input = StartupDefaults.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing startup resource: " + resource);
            preset = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        Path target = config.resolve("solonion.json");
        JsonObject settings = Files.exists(target)
                ? JsonParser.parseString(Files.readString(target)).getAsJsonObject() : new JsonObject();
        Files.createDirectories(marker.getParent());
        if (Files.exists(target)) {
            Path backup = config.resolve("farmupcraft").resolve("solonion-before-progression-v1.json");
            if (!Files.exists(backup)) Files.copy(target, backup);
        }
        preset.entrySet().forEach(entry -> settings.add(entry.getKey(), entry.getValue()));
        Files.writeString(target, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(settings));
        Files.writeString(marker, "Installed FarmUpCraft food progression v1. Later edits are preserved.\n");
        return true;
    }

    public static boolean initializeWorld(Path world, Runnable applyRules) throws IOException {
        Path marker = world.resolve("data").resolve("farmupcraft-startup-rules-v1.done");
        if (Files.exists(marker)) return false;
        Files.createDirectories(marker.getParent());
        applyRules.run();
        Files.writeString(marker, "Initialized random tick speed 1 and sleeping percentage 50.\n");
        return true;
    }
}
