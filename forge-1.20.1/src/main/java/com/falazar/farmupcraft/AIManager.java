package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.NpcData;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.OllamaService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Central manager for AI/LLM calls in FarmUpCraft.
 *
 * All prompts that go to Ollama should be built and dispatched from here so
 * they can be tuned or swapped in one place.
 */
public class AIManager {
    private static final CustomLogger LOGGER = new CustomLogger(AIManager.class.getSimpleName());

    private AIManager() {
    }

    // -------------------------------------------------------------------------
    // NPC personality generation
    // -------------------------------------------------------------------------

    /** Shared setting sentence used in every personality prompt. */
    private static final String SETTING_CONTEXT = "You are a character designer for a cozy medieval Minecraft village mod. ";

    /** Shared paragraph structure instructions used in every personality prompt. */
    private static final String PARAGRAPH_INSTRUCTIONS = "Use exactly 4 short paragraphs with NO headings or bullet points:\n"
            +
            "Paragraph 1: Their background and origin story (2-3 sentences).\n" +
            "Paragraph 2: Their daily habits and typical routine (2-3 sentences).\n" +
            "Paragraph 3: Things they like and enjoy (2-3 sentences).\n" +
            "Paragraph 4: Things they dislike or find annoying (2-3 sentences).\n" +
            "Give them a unique, memorable personality that fits the medieval village setting.";

    /**
     * Formats a villager names list into a short context hint for the AI.
     * Returns an empty string if the list is null or empty.
     */
    private static String buildVillagerNamesHint(List<String> villagerNames) {
        if (villagerNames == null || villagerNames.isEmpty())
            return "";
        return "Other villagers in the village: " + String.join(", ", villagerNames) + ".\n";
    }

    /**
     * Formats village biomes into a context hint. Returns empty string if
     * null/empty.
     */
    private static String buildBiomesHint(List<String> biomes) {
        if (biomes == null || biomes.isEmpty())
            return "";
        return "The village biomes include: " + String.join(", ", biomes) + ".\n";
    }

    /**
     * Builds an optional sentence making the villager curious about one nearby
     * structure.
     * Returns empty string if structureName is null or blank.
     */
    private static String buildStructureCuriosityHint(String structureName) {
        if (structureName == null || structureName.isBlank())
            return "";
        return "There is a " + structureName + " near the village that this villager has heard about "
                + "but never fully explored. Weave in their curiosity or wonder about it — "
                + "maybe they ask questions about it or dream of visiting it.\n";
    }

    /**
     * Asks the AI to write a personality profile for an NPC.
     *
     * @param name          the villager's display name (e.g. "Alaina Fae")
     * @param profession    the villager's profession string (e.g.
     *                      "minecraft:farmer")
     * @param villagerNames names of other villagers in the village (may be null)
     * @param villageName   name of the village (may be null)
     * @param biomes        biome names present in the village (may be null)
     * @param structureName one nearby structure to make the villager curious about
     *                      (may be null)
     * @return a future that resolves to the generated personality text
     */
    public static CompletableFuture<String> generateNpcPersonality(String name, String profession,
            List<String> villagerNames, String villageName, List<String> biomes, String structureName) {
        String profDisplay = profession.contains(":") ? profession.substring(profession.indexOf(':') + 1) : profession;
        StringBuilder prompt = new StringBuilder(SETTING_CONTEXT);
        prompt.append("Write a personality profile for a villager named ").append(name)
                .append(" who works as a ").append(profDisplay).append(".");
        if (villageName != null && !villageName.isBlank())
            prompt.append(" They live in the village of ").append(villageName).append(".");
        prompt.append("\n");
        prompt.append(buildVillagerNamesHint(villagerNames));
        prompt.append(buildBiomesHint(biomes));
        prompt.append(buildStructureCuriosityHint(structureName));
        prompt.append(PARAGRAPH_INSTRUCTIONS);
        LOGGER.info("AIManager: requesting personality for '{}' ({}) village={} structure={}",
                name, profDisplay, villageName, structureName);
        return OllamaService.chat(prompt.toString());
    }

    /** Back-compat overload — no village context. */
    public static CompletableFuture<String> generateNpcPersonality(String name, String profession,
            List<String> villagerNames) {
        return generateNpcPersonality(name, profession, villagerNames, null, null, null);
    }

    /**
     * Like generateNpcPersonality, but includes the existing profile text so the AI
     * can update/improve it rather than starting from scratch.
     *
     * @param name            the villager's display name
     * @param profession      the villager's profession string
     * @param existingProfile the full text of the existing JSON profile (may be
     *                        null/empty)
     * @param villagerNames   names of other villagers in the village (may be null)
     * @param villageName     name of the village (may be null)
     * @param biomes          biome names present in the village (may be null)
     * @param structureName   one nearby structure to make the villager curious
     *                        about (may be null)
     * @return a future that resolves to the updated personality text
     */
    public static CompletableFuture<String> regenerateNpcPersonality(String name, String profession,
            String existingProfile, List<String> villagerNames, String villageName,
            List<String> biomes, String structureName) {
        String profDisplay = profession.contains(":") ? profession.substring(profession.indexOf(':') + 1) : profession;
        StringBuilder prompt = new StringBuilder(SETTING_CONTEXT);
        prompt.append("Update the personality profile for a villager named ").append(name)
                .append(" who works as a ").append(profDisplay).append(".");
        if (villageName != null && !villageName.isBlank())
            prompt.append(" They live in the village of ").append(villageName).append(".");
        prompt.append("\n");
        prompt.append(buildVillagerNamesHint(villagerNames));
        prompt.append(buildBiomesHint(biomes));
        prompt.append(buildStructureCuriosityHint(structureName));
        if (existingProfile != null && !existingProfile.isBlank()) {
            prompt.append("The existing profile is:\n").append(existingProfile).append("\n\n");
            prompt.append("Keep everything that is still good, but improve or expand it where helpful.\n");
        }
        prompt.append(PARAGRAPH_INSTRUCTIONS);
        LOGGER.info("AIManager: regenerating personality for '{}' ({}) village={} structure={}",
                name, profDisplay, villageName, structureName);
        return OllamaService.chat(prompt.toString());
    }

    /** Back-compat overload — no village context. */
    public static CompletableFuture<String> regenerateNpcPersonality(String name, String profession,
            String existingProfile, List<String> villagerNames) {
        return regenerateNpcPersonality(name, profession, existingProfile, villagerNames, null, null, null);
    }

    // -------------------------------------------------------------------------
    // Structure chest threat notes
    // -------------------------------------------------------------------------

    /**
     * Generates a short menacing note (1-3 sentences) from a mysterious villain
     * for placing in a structure's special chest. Returns a CompletableFuture so
     * it can be called async without blocking the server tick.
     *
     * @param structureName the type/name of the structure (e.g. "jungle tree
     *                      house")
     */
    public static CompletableFuture<String> generateChestThreatNote(String structureName) {
        String prompt = "You are writing a short, menacing note left by a sinister villain inside a " +
                structureName + " in a medieval fantasy world. " +
                "Write exactly 1 to 3 sentences. The note should threaten the adventurer who found it, " +
                "rant about how this place belongs to the villain, and hint at dark consequences for trespassers. " +
                "Use archaic or dramatic language. Return ONLY the note text, no titles, no quotes, no labels.";
        LOGGER.info("AIManager: requesting chest threat note for structure '{}'", structureName);
        return OllamaService.chat(prompt);
    }

    /**
     * Generates a batch of {@code count} short threat notes in a single AI call.
     * Notes in the response are separated by "---NEXT---".
     * Returns a future resolving to a list of note strings (may be empty on error).
     *
     * @param structureName the type/name of the structure (used as flavor context)
     * @param count         how many notes to request (e.g. 5)
     */
    public static CompletableFuture<List<String>> generateChestThreatNotesBatch(String structureName, int count) {
        String prompt = "You are writing " + count + " short, menacing notes each left by a sinister villain inside a "
                + structureName + " in a medieval fantasy world. "
                + "Each note should be 1 to 3 sentences. Notes should threaten the adventurer who found them, "
                + "rant about how the place belongs to the villain, and hint at dark consequences for trespassers. "
                + "Use archaic or dramatic language. "
                + "Return ONLY the " + count + " note texts, each separated by exactly '---NEXT---'. "
                + "No titles, no quotes, no labels, no numbering — just the note texts separated by the delimiter.";
        LOGGER.info("AIManager: requesting batch of {} chest threat notes for structure '{}'", count, structureName);
        return OllamaService.chat(prompt).thenApply(response -> {
            if (response == null || response.startsWith("[AI Error]")) {
                LOGGER.warn("AIManager: batch threat note request failed: {}", response);
                return java.util.Collections.<String>emptyList();
            }
            String[] parts = response.split("---NEXT---");
            List<String> result = new java.util.ArrayList<>();
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            LOGGER.info("AIManager: batch threat notes parsed {} note(s) from response.", result.size());
            return result;
        });
    }

    // NPC conversation prompt
    // -------------------------------------------------------------------------

    /**
     * Builds the system prompt sent to the AI when a player chats with an NPC.
     * Includes description, personality, and the player's latest message.
     */
    public static String buildNpcSystemPrompt(NpcData profile, String playerName, String playerMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a Minecraft villager named ").append(profile.getName()).append(". ");
        if (!profile.getDescription().isEmpty()) {
            sb.append(profile.getDescription()).append(" ");
        }
        if (!profile.getPersonality().isEmpty()) {
            sb.append("Your personality and background: ").append(profile.getPersonality()).append(" ");
        }
        sb.append("Keep your reply short (1-3 sentences), in character, and friendly. ");
        sb.append("The player '").append(playerName).append("' says to you: ").append(playerMessage);
        return sb.toString();
    }
}
