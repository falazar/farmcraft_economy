package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.NpcData;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.OllamaService;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Central prompt construction and dispatch for NPCs and structure notes. */
public class AIManager {
    private static final CustomLogger LOGGER = new CustomLogger(AIManager.class.getSimpleName());
    private static final String SETTING_CONTEXT =
            "You are a character designer for a cozy medieval Minecraft village mod. ";
    private static final String PARAGRAPH_INSTRUCTIONS =
            "Use exactly 4 short paragraphs with NO headings or bullet points:\n"
            + "Paragraph 1: Their background and origin story (2-3 sentences).\n"
            + "Paragraph 2: Their daily habits and typical routine (2-3 sentences).\n"
            + "Paragraph 3: Things they like and enjoy (2-3 sentences).\n"
            + "Paragraph 4: Things they dislike or find annoying (2-3 sentences).\n"
            + "Give them a unique, memorable personality that fits the medieval village setting.";

    private AIManager() {}

    record CachedThreatNote(String text, int remaining) {}

    static CachedThreatNote popCachedThreatNote() {
        return popCachedThreatNote(FMLPaths.GAMEDIR.get().resolve("npcData").resolve("threat_notes_cache.json"));
    }

    // Keep the existing NpcDataLoader cache format without pulling NPC file management into this port.
    static synchronized CachedThreatNote popCachedThreatNote(Path cacheFile) {
        if (!Files.exists(cacheFile))
            return null;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(cacheFile, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonArray notes = json.has("notes") ? json.getAsJsonArray("notes") : new JsonArray();
            if (notes.isEmpty())
                return null;
            String note = notes.get(0).getAsString();
            JsonArray updated = new JsonArray();
            for (int i = 1; i < notes.size(); i++)
                updated.add(notes.get(i));
            json.add("notes", updated);
            Files.writeString(cacheFile, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n",
                    StandardCharsets.UTF_8);
            return new CachedThreatNote(note, updated.size());
        } catch (Exception e) {
            LOGGER.error("AIManager: error reading threat notes cache: {}", e.getMessage());
            return null;
        }
    }

    private static String buildVillagerNamesHint(List<String> villagerNames) {
        if (villagerNames == null || villagerNames.isEmpty())
            return "";
        return "Other villagers in the village: " + String.join(", ", villagerNames) + ".\n";
    }

    private static String buildBiomesHint(List<String> biomes) {
        if (biomes == null || biomes.isEmpty())
            return "";
        return "The village biomes include: " + String.join(", ", biomes) + ".\n";
    }

    private static String buildStructureCuriosityHint(String structureName) {
        if (structureName == null || structureName.isBlank())
            return "";
        return "There is a " + structureName + " near the village that this villager has heard about "
                + "but never fully explored. Weave in their curiosity or wonder about it — "
                + "maybe they ask questions about it or dream of visiting it.\n";
    }

    static String buildPersonalityPrompt(String name, String profession, List<String> villagerNames,
            String villageName, List<String> biomes, String structureName, boolean regenerate,
            String existingProfile) {
        String profDisplay = profession.contains(":") ? profession.substring(profession.indexOf(':') + 1) : profession;
        StringBuilder prompt = new StringBuilder(SETTING_CONTEXT);
        prompt.append(regenerate ? "Update the personality profile for a villager named "
                : "Write a personality profile for a villager named ").append(name)
                .append(" who works as a ").append(profDisplay).append(".");
        if (villageName != null && !villageName.isBlank())
            prompt.append(" They live in the village of ").append(villageName).append(".");
        prompt.append("\n").append(buildVillagerNamesHint(villagerNames)).append(buildBiomesHint(biomes))
                .append(buildStructureCuriosityHint(structureName));
        if (regenerate && existingProfile != null && !existingProfile.isBlank()) {
            prompt.append("The existing profile is:\n").append(existingProfile).append("\n\n");
            prompt.append("Keep everything that is still good, but improve or expand it where helpful.\n");
        }
        return prompt.append(PARAGRAPH_INSTRUCTIONS).toString();
    }

    public static CompletableFuture<String> generateNpcPersonality(String name, String profession,
            List<String> villagerNames, String villageName, List<String> biomes, String structureName) {
        LOGGER.info("AIManager: requesting personality for '{}' ({}) village={} structure={}",
                name, profession, villageName, structureName);
        return OllamaService.chat(buildPersonalityPrompt(name, profession, villagerNames,
                villageName, biomes, structureName, false, null));
    }

    public static CompletableFuture<String> generateNpcPersonality(String name, String profession,
            List<String> villagerNames) {
        return generateNpcPersonality(name, profession, villagerNames, null, null, null);
    }

    public static CompletableFuture<String> regenerateNpcPersonality(String name, String profession,
            String existingProfile, List<String> villagerNames, String villageName,
            List<String> biomes, String structureName) {
        LOGGER.info("AIManager: regenerating personality for '{}' ({}) village={} structure={}",
                name, profession, villageName, structureName);
        return OllamaService.chat(buildPersonalityPrompt(name, profession, villagerNames,
                villageName, biomes, structureName, true, existingProfile));
    }

    public static CompletableFuture<String> regenerateNpcPersonality(String name, String profession,
            String existingProfile, List<String> villagerNames) {
        return regenerateNpcPersonality(name, profession, existingProfile, villagerNames, null, null, null);
    }

    static String buildChestThreatNotePrompt(String structureName) {
        return "You are writing a short, menacing note left by a sinister villain inside a " +
                structureName + " in a medieval fantasy world. " +
                "Write exactly 1 to 3 sentences. The note should threaten the adventurer who found it, " +
                "rant about how this place belongs to the villain, and hint at dark consequences for trespassers. " +
                "Use archaic or dramatic language. Return ONLY the note text, no titles, no quotes, no labels.";
    }

    public static CompletableFuture<String> generateChestThreatNote(String structureName) {
        LOGGER.info("AIManager: requesting chest threat note for structure '{}'", structureName);
        return OllamaService.chat(buildChestThreatNotePrompt(structureName));
    }

    static String buildChestThreatNotesBatchPrompt(String structureName, int count) {
        return "You are writing " + count + " short, menacing notes each left by a sinister villain inside a "
                + structureName + " in a medieval fantasy world. "
                + "Each note should be 1 to 3 sentences. Notes should threaten the adventurer who found them, "
                + "rant about how the place belongs to the villain, and hint at dark consequences for trespassers. "
                + "Use archaic or dramatic language. "
                + "Return ONLY the " + count + " note texts, each separated by exactly '---NEXT---'. "
                + "No titles, no quotes, no labels, no numbering — just the note texts separated by the delimiter.";
    }

    public static CompletableFuture<List<String>> generateChestThreatNotesBatch(String structureName, int count) {
        LOGGER.info("AIManager: requesting batch of {} chest threat notes for structure '{}'", count, structureName);
        return OllamaService.chat(buildChestThreatNotesBatchPrompt(structureName, count))
                .thenApply(AIManager::parseChestThreatNotesBatch);
    }

    static List<String> parseChestThreatNotesBatch(String response) {
        if (response == null || response.startsWith("[AI Error]")) {
            LOGGER.warn("AIManager: batch threat note request failed: {}", response);
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String part : response.split("---NEXT---")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty())
                result.add(trimmed);
        }
        LOGGER.info("AIManager: batch threat notes parsed {} note(s) from response.", result.size());
        return result;
    }

    public static String buildNpcSystemPrompt(NpcData profile, String playerName, String playerMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a Minecraft villager named ").append(profile.getName()).append(". ");
        if (!profile.getDescription().isEmpty())
            sb.append(profile.getDescription()).append(" ");
        if (!profile.getPersonality().isEmpty())
            sb.append("Your personality and background: ").append(profile.getPersonality()).append(" ");
        sb.append("Keep your reply short (1-3 sentences), in character, and friendly. ");
        sb.append("The player '").append(playerName).append("' says to you: ").append(playerMessage);
        return sb.toString();
    }
}
