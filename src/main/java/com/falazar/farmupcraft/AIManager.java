package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.NpcData;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.OllamaService;

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

    /**
     * Asks the AI to write a 3-paragraph personality profile for an NPC:
     * paragraph 1 = daily habits & routine,
     * paragraph 2 = things they like & enjoy,
     * paragraph 3 = things they dislike or find annoying.
     *
     * @param name       the villager's display name (e.g. "Alaina Fae")
     * @param profession the villager's profession string (e.g. "minecraft:farmer")
     * @return a future that resolves to the generated personality text
     */
    public static CompletableFuture<String> generateNpcPersonality(String name, String profession) {
        // Strip the namespace prefix for readability in the prompt (e.g. "minecraft:farmer" → "farmer").
        String profDisplay = profession.contains(":") ? profession.substring(profession.indexOf(':') + 1) : profession;
        String prompt = "You are a character designer for a cozy medieval Minecraft village mod. " +
                "Write a personality profile for a villager named " + name +
                " who works as a " + profDisplay + ". " +
                "Use exactly 4 short paragraphs with NO headings or bullet points:\n" +
                "Paragraph 1: Their background and origin story (2-3 sentences).\n" +
                "Paragraph 2: Their daily habits and typical routine (2-3 sentences).\n" +
                "Paragraph 3: Things they like and enjoy (2-3 sentences).\n" +
                "Paragraph 4: Things they dislike or find annoying (2-3 sentences).\n" +
                "Give them a unique, memorable personality that fits the medieval village setting.";

        LOGGER.info("AIManager: requesting personality for '{}' ({})", name, profDisplay);
        return OllamaService.chat(prompt);
    }

    // -------------------------------------------------------------------------
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
