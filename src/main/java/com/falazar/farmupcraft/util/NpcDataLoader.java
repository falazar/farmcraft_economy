package com.falazar.farmupcraft.util;

import com.falazar.farmupcraft.data.NpcData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Looks up NPC AI profiles from the npcData/ directory on demand.
 *
 * Profiles are NOT loaded at server start — they are read from disk when a
 * player right-clicks a villager. This keeps startup fast and makes it easy to
 * add/edit profiles without restarting the server.
 *
 * HOW TO ADD AN NPC PROFILE:
 * 1. Run /village villagers in-game — the UUID is shown in both chat and logs.
 * 2. Create a file: run/npcData/<Name>-<UUID>.json
 * Example: run/npcData/Alaina Fae-a1b2c3d4-....json
 * 3. Use this format:
 * {
 * "uuid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
 * "name": "Alaina Fae",
 * "description": "A friendly farmer who loves growing wheat.",
 * "personality": "warm, helpful, nostalgic"
 * }
 *
 * Only villagers with a matching profile file will respond to AI chat.
 * Villagers without a profile still say hello on right-click, but cannot be
 * spoken to.
 */
public class NpcDataLoader {
    private static final CustomLogger LOGGER = new CustomLogger(NpcDataLoader.class.getSimpleName());

    /** Folder name relative to the game's working directory (run/). */
    private static final String NPC_DATA_DIR = "npcData";

    private NpcDataLoader() {
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Looks for a profile file matching "<Name>-<UUID>.json" in the npcData/ dir.
     * Called on demand when a player interacts with a villager — no server-start
     * scan.
     *
     * @param name the villager's display name (e.g. "Alaina Fae")
     * @param uuid the villager's entity UUID
     * @return the loaded NpcData, or null if no profile file exists
     */
    @Nullable
    public static NpcData findProfile(String name, UUID uuid) {
        Path dir = getOrCreateDir();
        if (dir == null)
            return null;

        // Expected filename: "Alaina Fae-a1b2c3d4-xxxx-xxxx-xxxx-xxxxxxxxxxxx.json"
        String expectedFile = name + "-" + uuid + ".json";
        Path profilePath = dir.resolve(expectedFile);

        if (!Files.exists(profilePath)) {
            LOGGER.info("NpcDataLoader: no profile found for '{}' ({}). Expected file: {}", name, uuid, expectedFile);
            return null;
        }

        return parseProfile(profilePath);
    }

    /**
     * Returns true if a profile file exists for this villager, without loading it.
     * Cheap check — just a file-exists test.
     */
    public static boolean hasProfile(String name, UUID uuid) {
        Path dir = getOrCreateDir();
        if (dir == null)
            return false;
        return Files.exists(dir.resolve(name + "-" + uuid + ".json"));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Gets the npcData/ dir, creating it (with an example file) if missing. */
    @Nullable
    private static Path getOrCreateDir() {
        Path dir = FMLPaths.GAMEDIR.get().resolve(NPC_DATA_DIR);
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
                LOGGER.info("Created npcData/ directory at: {}  — add <Name>-<UUID>.json files here.", dir);
                writeExampleFile(dir);
            } catch (IOException e) {
                LOGGER.error("Failed to create npcData/ directory: {}", e.getMessage());
                return null;
            }
        }
        return dir;
    }

    @Nullable
    private static NpcData parseProfile(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();

            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            String name = json.get("name").getAsString();
            String desc = json.has("description") ? json.get("description").getAsString() : "";
            String pers = json.has("personality") ? json.get("personality").getAsString() : "";

            // Merge description + personality into the AI context string.
            String fullDescription = pers.isEmpty() ? desc : desc + " Personality: " + pers;

            LOGGER.info("NpcDataLoader: loaded profile '{}' from {}", name, file.getFileName());
            // home village UUID not relevant for AI profiles — placeholder used.
            return new NpcData(uuid, name, new UUID(0, 0), fullDescription);

        } catch (Exception e) {
            LOGGER.error("Failed to parse NPC profile {}: {}", file.getFileName(), e.getMessage());
            return null;
        }
    }

    /** Creates an example file on first run so the format is self-documenting. */
    private static void writeExampleFile(Path dir) {
        String example = """
                {
                  "uuid":        "00000000-0000-0000-0000-000000000000",
                  "name":        "Example Villager",
                  "description": "A friendly villager who has lived in the village all their life.",
                  "personality": "cheerful, helpful, a little shy"
                }
                """;
        try {
            Files.writeString(dir.resolve("_EXAMPLE-00000000-0000-0000-0000-000000000000.json"),
                    example, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to write example NPC profile: {}", e.getMessage());
        }
    }
}
