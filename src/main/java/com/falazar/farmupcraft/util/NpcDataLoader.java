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
     * Renames a profile file from "{oldName}-{uuid}.json" to
     * "{newName}-{uuid}.json"
     * and updates the "name" field inside the JSON.
     *
     * @return true if the file existed and was renamed successfully; false if not
     *         found or on error
     */
    public static boolean renameProfile(String oldName, String newName, UUID uuid) {
        Path dir = getOrCreateDir();
        if (dir == null)
            return false;
        Path oldPath = dir.resolve(oldName + "-" + uuid + ".json");
        Path newPath = dir.resolve(newName + "-" + uuid + ".json");

        if (!Files.exists(oldPath)) {
            LOGGER.info("NpcDataLoader.renameProfile: no profile file found for '{}' ({})", oldName, uuid);
            return false;
        }

        try {
            String content = Files.readString(oldPath, StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            json.addProperty("name", newName);
            String updated = new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                    .toJson(json) + "\n";
            Files.writeString(newPath, updated, StandardCharsets.UTF_8);
            Files.delete(oldPath);
            LOGGER.info("NpcDataLoader.renameProfile: renamed '{}' → '{}' ({})", oldName, newName, uuid);
            return true;
        } catch (IOException e) {
            LOGGER.error("NpcDataLoader.renameProfile: failed for '{}': {}", oldName, e.getMessage());
            return false;
        }
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

    /**
     * Writes (or overwrites) a profile JSON file to the npcData/ directory.
     *
     * @param name        the villager's display name
     * @param uuid        the villager's entity UUID
     * @param description a short description sentence
     * @param personality the AI-generated multi-paragraph personality text
     * @param villageName the name of the villager's home village (may be empty)
     * @return true on success
     */
    public static boolean writeProfile(String name, UUID uuid, String description, String personality,
            String villageName) {
        Path dir = getOrCreateDir();
        if (dir == null)
            return false;
        String filename = name + "-" + uuid + ".json";
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
                .create();
        com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
        obj.addProperty("uuid", uuid.toString());
        obj.addProperty("name", name);
        obj.addProperty("village_name", villageName == null ? "" : villageName);
        obj.addProperty("description", description);
        obj.addProperty("personality", personality);
        try {
            Files.writeString(dir.resolve(filename), gson.toJson(obj) + "\n", StandardCharsets.UTF_8);
            LOGGER.info("NpcDataLoader: wrote profile for '{}' → {}", name, filename);
            return true;
        } catch (IOException e) {
            LOGGER.error("NpcDataLoader: failed to write profile for '{}': {}", name, e.getMessage());
            return false;
        }
    }

    /** Legacy overload — no village_name. */
    public static boolean writeProfile(String name, UUID uuid, String description, String personality) {
        return writeProfile(name, uuid, description, personality, "");
    }

    /**
     * Reads the raw JSON object from a profile file, or null if it doesn't exist.
     * Used by redoprofile to pass the existing profile to the AI.
     */
    @Nullable
    public static com.google.gson.JsonObject readRawProfile(String name, UUID uuid) {
        Path dir = getOrCreateDir();
        if (dir == null)
            return null;
        Path path = dir.resolve(name + "-" + uuid + ".json");
        if (!Files.exists(path))
            return null;
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return JsonParser.parseString(content).getAsJsonObject();
        } catch (Exception e) {
            LOGGER.error("NpcDataLoader.readRawProfile: failed for '{}': {}", name, e.getMessage());
            return null;
        }
    }

    /**
     * Scans the npcData/ directory and deletes any profile whose personality field
     * contains "[AI Error]" (e.g. timeout/failure entries).
     *
     * @return number of files deleted
     */
    public static int deleteErrorProfiles() {
        Path dir = getOrCreateDir();
        if (dir == null)
            return 0;
        int deleted = 0;
        try (var stream = Files.list(dir)) {
            for (Path file : stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .collect(java.util.stream.Collectors.toList())) {
                try {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                    String pers = json.has("personality") ? json.get("personality").getAsString() : "";
                    if (pers.contains("[AI Error]")) {
                        Files.delete(file);
                        LOGGER.info("NpcDataLoader.deleteErrorProfiles: deleted {}", file.getFileName());
                        deleted++;
                    }
                } catch (Exception e) {
                    LOGGER.error("NpcDataLoader.deleteErrorProfiles: skipping {}: {}", file.getFileName(),
                            e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.error("NpcDataLoader.deleteErrorProfiles: failed to list dir: {}", e.getMessage());
        }
        return deleted;
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
