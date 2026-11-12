package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.NpcData;
import com.google.gson.JsonParser;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class AIManagerTest {
    private final List<Path> fixtures = new ArrayList<>();

    @AfterEach
    void cleanFixtures() throws IOException {
        for (Path fixture : fixtures) {
            try (var paths = Files.walk(fixture)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.delete(path);
            }
        }
    }

    private Path fixture() throws IOException {
        Path dir = Path.of("build", "test-fixtures", "structure-ai-" + UUID.randomUUID());
        Files.createDirectories(dir);
        fixtures.add(dir);
        return dir.resolve("threat_notes_cache.json");
    }

    @Test
    void personalityPromptRetainsExactVillageBiomeCuriosityAndParagraphInstructions() {
        assertEquals(
                """
                        You are a character designer for a cozy medieval Minecraft village mod. Write a personality profile for a villager named Alaina Fae who works as a farmer. They live in the village of Willow.
                        Other villagers in the village: Cyrus, Lazarus.
                        The village biomes include: plains, grove.
                        There is a jungle tree house near the village that this villager has heard about but never fully explored. Weave in their curiosity or wonder about it — maybe they ask questions about it or dream of visiting it.
                        Use exactly 4 short paragraphs with NO headings or bullet points:
                        Paragraph 1: Their background and origin story (2-3 sentences).
                        Paragraph 2: Their daily habits and typical routine (2-3 sentences).
                        Paragraph 3: Things they like and enjoy (2-3 sentences).
                        Paragraph 4: Things they dislike or find annoying (2-3 sentences).
                        Give them a unique, memorable personality that fits the medieval village setting.""",
                AIManager.buildPersonalityPrompt("Alaina Fae", "minecraft:farmer", List.of("Cyrus", "Lazarus"),
                        "Willow", List.of("plains", "grove"), "jungle tree house", false, null));
    }

    @Test
    void absentContextAndRegenerationRetainLegacyWordingAndExistingProfile() {
        String minimal = AIManager.buildPersonalityPrompt("Ada", "librarian", null,
                null, null, null, false, null);
        assertTrue(minimal.startsWith("You are a character designer for a cozy medieval Minecraft village mod. "
                + "Write a personality profile for a villager named Ada who works as a librarian.\nUse exactly"));
        assertEquals(minimal, AIManager.buildPersonalityPrompt("Ada", "minecraft:librarian", List.of(),
                " ", List.of(), " ", false, "ignored existing text"));
        String regenerated = AIManager.buildPersonalityPrompt("Ada", "minecraft:librarian", null,
                null, null, null, true, "{\"background\":\"kept\"}");
        assertTrue(regenerated.contains("Update the personality profile for a villager named Ada"));
        assertTrue(regenerated.contains("The existing profile is:\n{\"background\":\"kept\"}\n\n"
                + "Keep everything that is still good, but improve or expand it where helpful.\nUse exactly"));
        assertFalse(AIManager.buildPersonalityPrompt("Ada", "librarian", null,
                null, null, null, true, " ").contains("The existing profile"));
    }

    @Test
    void singleAndBatchNotesUseVariedAtmosphericInstructionsAndRetainDelimiter() {
        String single = AIManager.buildChestThreatNotePrompt("Tower");
        assertTrue(single.contains("found note for Tower"));
        assertTrue(single.contains("1 to 3 sentences, at most 600 characters"));
        assertTrue(single.contains("do not always use a villain"));
        String batch = AIManager.buildChestThreatNotesBatchPrompt("Tower", 5);
        assertTrue(batch.contains("Produce 5 DISTINCT notes"));
        assertTrue(batch.contains("exactly ---NEXT---"));
        assertTrue(batch.contains("journals, letters, riddles, reports"));
    }

    @Test
    void batchParserDropsEmptyPiecesPreservesTextAndReturnsEmptyOnServiceFailure() {
        assertEquals(List.of("first", "second\nline", "雪"), AIManager.parseChestThreatNotesBatch(
                "  first  ---NEXT--- \n---NEXT--- second\nline ---NEXT---雪---NEXT---"));
        assertEquals(List.of(), AIManager.parseChestThreatNotesBatch(null));
        assertEquals(List.of(), AIManager.parseChestThreatNotesBatch("[AI Error] no connection"));
        assertEquals(List.of(), AIManager.parseChestThreatNotesBatch("  "));
        assertEquals(List.of("no delimiter"), AIManager.parseChestThreatNotesBatch("no delimiter"));
    }

    @Test
    void conversationPromptKeepsDescriptionPersonalityPlayerNameAndLatestMessage() {
        var profile = new NpcData(UUID.randomUUID(), "Ada", null, "", "");
        String basic = AIManager.buildNpcSystemPrompt(profile, "Robin", "Hello");
        assertTrue(basic.contains("Ada"));
        assertTrue(basic.contains("Robin"));
        assertTrue(basic.contains("Hello"));
        profile.setDescription("A baker.");
        profile.setPersonality("Loves bread.");
        String withProfile = AIManager.buildNpcSystemPrompt(profile, "Robin", "Hello");
        assertTrue(withProfile.contains("A baker."));
        assertTrue(withProfile.contains("Loves bread."));
        assertTrue(withProfile.contains("Robin"));
        assertTrue(withProfile.contains("Hello"));
    }

    @Test
    void conversationPromptIncludesCurrentVillagerProfession() {
        var profile = new NpcData(UUID.randomUUID(), "Ada", null, "A local villager.", "friendly");
        assertTrue(AIManager.buildNpcSystemPrompt(profile, "Robin", "Where are gems?", "farmer")
                .endsWith(
                        "Their current villager profession is farmer. Let this role inform what they know and how they speak."));
    }

    @Test
    void cachedNotesAreConsumedFifoUsingExistingDiskFormatAndKeepOtherFields() throws IOException {
        Path file = fixture();
        assertNull(AIManager.popCachedThreatNote(file));
        assertFalse(Files.exists(file));
        Files.writeString(file, "{\"notes\":[\"First — 雪\",\"Second\"],\"source\":\"manual\"}");
        assertEquals(new AIManager.CachedThreatNote("First — 雪", 1), AIManager.popCachedThreatNote(file));
        var updated = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("manual", updated.get("source").getAsString());
        assertEquals(List.of("Second"), updated.getAsJsonArray("notes").asList().stream()
                .map(element -> element.getAsString()).toList());
        assertEquals(new AIManager.CachedThreatNote("Second", 0), AIManager.popCachedThreatNote(file));
        assertNull(AIManager.popCachedThreatNote(file));
    }

    @Test
    void malformedAndEmptyCachesFailLocallyWithoutDestroyingTheirContents() throws IOException {
        Path file = fixture();
        for (String text : List.of("{}", "{\"notes\":[]}", "not json", "{\"notes\":[{}]}")) {
            Files.writeString(file, text);
            assertNull(AIManager.popCachedThreatNote(file));
            assertEquals(text, Files.readString(file));
        }
    }
}
