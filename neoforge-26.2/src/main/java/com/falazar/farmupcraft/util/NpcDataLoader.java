package com.falazar.farmupcraft.util;

import com.falazar.farmupcraft.data.NpcData;
import com.falazar.farmupcraft.events.ModEvents;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** On-demand profiles in npcData/&lt;Name&gt;-&lt;UUID&gt;.json; assigned filenames take precedence. */
public final class NpcDataLoader {
    private static final CustomLogger LOGGER = new CustomLogger(NpcDataLoader.class.getSimpleName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String THREAT_NOTES_FILE = "threat_notes_cache.json";

    private NpcDataLoader() {}

    public static @Nullable NpcData findProfile(String name, UUID uuid) {
        return findProfile(name, uuid, ModEvents.getNpcDatabase().getData(uuid));
    }

    public static @Nullable NpcData findProfile(Level level, String name, UUID uuid) {
        return findProfile(name, uuid, ModEvents.getNpcDatabase(level).getData(uuid));
    }

    private static @Nullable NpcData findProfile(String name, UUID uuid, @Nullable NpcData assigned) {
        Path file = resolveProfile(name, uuid, assigned);
        if (file == null || !Files.isRegularFile(file)) return null;
        try {
            return parseProfile(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | JsonParseException | IllegalArgumentException | IllegalStateException e) {
            LOGGER.error("Cannot read NPC profile {}: {}", file, e.getMessage(), e);
            return null;
        }
    }

    public static boolean hasProfile(String name, UUID uuid) {
        return resolveExisting(name, uuid, ModEvents.getNpcDatabase().getData(uuid)) != null;
    }

    public static boolean hasProfile(Level level, String name, UUID uuid) {
        return resolveExisting(name, uuid, ModEvents.getNpcDatabase(level).getData(uuid)) != null;
    }

    private static @Nullable Path resolveExisting(String name, UUID uuid, @Nullable NpcData assigned) {
        Path path = resolveProfile(name, uuid, assigned);
        return path != null && Files.isRegularFile(path) ? path : null;
    }

    private static @Nullable Path resolveProfile(String name, UUID uuid, @Nullable NpcData assigned) {
        Path dir = getOrCreateDir();
        if (dir == null) return null;
        if (assigned != null && !assigned.getAiProfileFile().isBlank()) {
            Path file = dir.resolve(normalizeProfileFileName(assigned.getAiProfileFile()));
            if (Files.isRegularFile(file)) return file;
            LOGGER.warn("Assigned NPC profile missing for {}: {}", uuid, file);
        }
        return profilePath(dir, name, uuid);
    }

    /** Keeps normal legacy filenames, but never permits an entity name to escape npcData/. */
    static Path profilePath(Path dir, String name, UUID uuid) {
        String safe = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return dir.resolve(safe + "-" + uuid + ".json");
    }

    public static boolean renameProfile(String oldName, String newName, UUID uuid) {
        return renameProfile(oldName, newName, uuid, ModEvents.getNpcDatabase().getData(uuid));
    }

    public static boolean renameProfile(Level level, String oldName, String newName, UUID uuid) {
        return renameProfile(oldName, newName, uuid, ModEvents.getNpcDatabase(level).getData(uuid));
    }

    private static synchronized boolean renameProfile(String oldName, String newName, UUID uuid,
                                                       @Nullable NpcData assigned) {
        Path oldPath = resolveExisting(oldName, uuid, assigned);
        if (oldPath == null) return false;
        Path dir = oldPath.getParent();
        // Explicit assigned filenames are stable identifiers, not display names.
        Path newPath = assigned != null && !assigned.getAiProfileFile().isBlank()
                ? oldPath : profilePath(dir, newName, uuid);
        return renameProfileFiles(oldPath, newPath, newName);
    }

    static synchronized boolean renameProfileFiles(Path oldPath, Path newPath, String newName) {
        try {
            JsonObject json = readObject(oldPath);
            json.addProperty("name", newName);
            if (!newPath.equals(oldPath) && Files.exists(newPath)) {
                LOGGER.error("Cannot rename NPC profile: destination already exists: {}", newPath);
                return false;
            }
            writeObject(newPath, json);
            if (!newPath.equals(oldPath)) Files.delete(oldPath);
            return true;
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.error("Cannot rename NPC profile {}: {}", oldPath, e.getMessage(), e);
            return false;
        }
    }

    public static boolean writeProfile(String name, UUID uuid, String description, String personality) {
        return writeProfile(name, uuid, description, personality, "");
    }

    public static boolean writeProfile(String name, UUID uuid, String description, String personality,
                                       String villageName) {
        Path dir = getOrCreateDir();
        return dir != null && writeProfile(profilePath(dir, name, uuid), name, uuid, description,
                personality, villageName);
    }

    public static boolean writeProfileToFile(String profileFileName, String name, UUID uuid, String description,
                                             String personality, String villageName) {
        Path dir = getOrCreateDir();
        String normalized = normalizeProfileFileName(profileFileName);
        return dir != null && !normalized.isBlank() && writeProfile(dir.resolve(normalized), name, uuid,
                description, personality, villageName);
    }

    static boolean writeProfile(Path path, String name, UUID uuid, String description,
                                String personality, String villageName) {
        JsonObject obj = new JsonObject();
        obj.addProperty("uuid", uuid.toString());
        obj.addProperty("name", name);
        obj.addProperty("description", description == null ? "" : description);
        obj.addProperty("personality", personality == null ? "" : personality);
        obj.addProperty("village_name", villageName == null ? "" : villageName);
        try {
            writeObject(path, obj);
            return true;
        } catch (IOException e) {
            LOGGER.error("Cannot write NPC profile {}: {}", path, e.getMessage(), e);
            return false;
        }
    }

    public static boolean profileFileExists(String profileFileName) {
        Path dir = getOrCreateDir();
        String normalized = normalizeProfileFileName(profileFileName);
        return dir != null && !normalized.isBlank() && Files.isRegularFile(dir.resolve(normalized));
    }

    public static @Nullable JsonObject readRawProfile(String name, UUID uuid) {
        return readRawProfile(resolveProfile(name, uuid, ModEvents.getNpcDatabase().getData(uuid)));
    }

    public static @Nullable JsonObject readRawProfile(Level level, String name, UUID uuid) {
        return readRawProfile(resolveProfile(name, uuid, ModEvents.getNpcDatabase(level).getData(uuid)));
    }

    private static @Nullable JsonObject readRawProfile(@Nullable Path file) {
        if (file == null || !Files.isRegularFile(file)) return null;
        try {
            return readObject(file);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.error("Cannot read raw NPC profile {}: {}", file, e.getMessage(), e);
            return null;
        }
    }

    public static synchronized int deleteErrorProfiles() {
        Path dir = getOrCreateDir();
        if (dir == null) return 0;
        int deleted = 0;
        try (var files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                if (file.getFileName().toString().equals(THREAT_NOTES_FILE)) continue;
                try {
                    if (text(readObject(file), "personality").contains("[AI Error]")) {
                        Files.delete(file);
                        deleted++;
                    }
                } catch (IOException | JsonParseException | IllegalStateException e) {
                    LOGGER.error("Cannot clean NPC profile {}: {}", file, e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Cannot list NPC profiles {}: {}", dir, e.getMessage(), e);
        }
        return deleted;
    }

    public static synchronized @Nullable String popThreatNote() {
        Path dir = getOrCreateDir();
        if (dir == null) return null;
        try {
            return popThreatNote(dir.resolve(THREAT_NOTES_FILE));
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.error("Cannot pop threat note: {}", e.getMessage(), e);
            return null;
        }
    }

    static @Nullable String popThreatNote(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return null;
        JsonObject json = readObject(file);
        JsonArray notes = notes(json);
        if (notes.isEmpty()) return null;
        String note = notes.remove(0).getAsString();
        writeObject(file, json);
        return note;
    }

    public static synchronized void addThreatNotes(List<String> newNotes) {
        Path dir = getOrCreateDir();
        if (dir == null || newNotes.isEmpty()) return;
        try {
            addThreatNotes(dir.resolve(THREAT_NOTES_FILE), newNotes);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.error("Cannot append threat notes: {}", e.getMessage(), e);
        }
    }

    static void addThreatNotes(Path file, List<String> newNotes) throws IOException {
        JsonObject json = Files.isRegularFile(file) ? readObject(file) : new JsonObject();
        JsonArray notes = notes(json);
        for (String note : newNotes) {
            if (note == null || note.isBlank() || note.contains("[AI Error]"))
                throw new IllegalArgumentException("Invalid threat note");
            notes.add(note);
        }
        writeObject(file, json);
    }

    public static synchronized int getThreatNoteCacheSize() {
        Path dir = getOrCreateDir();
        if (dir == null) return 0;
        Path file = dir.resolve(THREAT_NOTES_FILE);
        if (!Files.isRegularFile(file)) return 0;
        try {
            return notes(readObject(file)).size();
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.error("Cannot count threat notes in {}: {}", file, e.getMessage(), e);
            return 0;
        }
    }

    private static JsonArray notes(JsonObject json) {
        if (!json.has("notes")) json.add("notes", new JsonArray());
        JsonArray notes = json.getAsJsonArray("notes");
        for (var note : notes) {
            if (!note.isJsonPrimitive() || !note.getAsJsonPrimitive().isString())
                throw new JsonParseException("Threat cache notes must be strings");
        }
        return notes;
    }

    static NpcData parseProfile(String content) {
        JsonObject json = JsonParser.parseString(content).getAsJsonObject();
        if (!json.has("uuid") || !json.has("name"))
            throw new JsonParseException("NPC profile requires uuid and name");
        UUID uuid = UUID.fromString(text(json, "uuid"));
        String name = text(json, "name");
        String description = text(json, "description");
        String personality = text(json, "personality");
        String full = personality.isEmpty() ? description : description + " Personality: " + personality;
        return new NpcData(uuid, name, new UUID(0, 0), full, personality, text(json, "village_name"));
    }

    private static String text(JsonObject json, String key) {
        if (!json.has(key)) return "";
        var value = json.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new JsonParseException("NPC field " + key + " must be a string");
        return value.getAsString();
    }

    public static String normalizeProfileFileName(@Nullable String name) {
        if (name == null || name.isBlank()) return "";
        String safe = name.trim().replace('\\', '/');
        safe = safe.substring(safe.lastIndexOf('/') + 1);
        safe = safe.replaceAll("[:*?\"<>|\\p{Cntrl}]", "_");
        if (safe.isBlank() || safe.equals(".") || safe.equals("..")) return "";
        return safe.toLowerCase(Locale.ROOT).endsWith(".json") ? safe : safe + ".json";
    }

    private static @Nullable Path getOrCreateDir() {
        Path dir = FMLPaths.GAMEDIR.get().resolve("npcData");
        try {
            boolean createExample = !Files.exists(dir);
            Files.createDirectories(dir);
            if (createExample) {
                writeProfile(profilePath(dir, "_EXAMPLE", new UUID(0, 0)), "Example Villager", new UUID(0, 0),
                        "A friendly villager who has lived in the village all their life.",
                        "cheerful, helpful, a little shy", "");
            }
            return dir;
        } catch (IOException e) {
            LOGGER.error("Cannot create NPC data directory {}: {}", dir, e.getMessage(), e);
            return null;
        }
    }

    private static JsonObject readObject(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static synchronized void writeObject(Path file, JsonObject json) throws IOException {
        // Keep the old file intact if serialization or writing fails, without using OS temp directories.
        Path pending = file.resolveSibling(file.getFileName() + ".pending");
        try {
            Files.writeString(pending, GSON.toJson(json) + "\n", StandardCharsets.UTF_8);
            Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(pending);
        }
    }
}
