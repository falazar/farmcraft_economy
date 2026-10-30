package com.falazar.farmupcraft.util;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class NpcDataLoaderTest {
    private final List<Path> fixtures = new ArrayList<>();

    private Path fixture() throws IOException {
        Path dir = Path.of("build", "test-fixtures", "npc-loader-" + UUID.randomUUID());
        Files.createDirectories(dir);
        fixtures.add(dir);
        return dir.resolve("threat_notes_cache.json");
    }

    @AfterEach
    void cleanFixtures() throws IOException {
        for (Path dir : fixtures) {
            try (var paths = Files.walk(dir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    @Test
    void readsLegacyProfileAndPreservesPersonalityAndVillageContext() {
        var profile = NpcDataLoader.parseProfile("""
                {"uuid":"12345678-1234-5678-90ab-1234567890ab","name":"Alaina Fae",
                 "description":"A friendly farmer.","personality":"warm, helpful",
                 "village_name":"Rivière"}
                """);
        assertEquals(UUID.fromString("12345678-1234-5678-90ab-1234567890ab"), profile.getUUID());
        assertEquals("Alaina Fae", profile.getName());
        assertEquals("A friendly farmer. Personality: warm, helpful", profile.getDescription());
        assertEquals("warm, helpful", profile.getPersonality());
        assertEquals("Rivière", profile.getVillageName());
        assertEquals(new UUID(0, 0), profile.getHomeVillageUUID());
        var minimal = NpcDataLoader.parseProfile("""
                {"uuid":"00000000-0000-0000-0000-000000000001","name":"Ada"}
                """);
        assertEquals("", minimal.getDescription());
        assertEquals("", minimal.getPersonality());
        assertEquals("", minimal.getVillageName());
    }

    @Test
    void rejectsMalformedProfilesInsteadOfInventingAnIdentity() {
        assertThrows(JsonParseException.class, () -> NpcDataLoader.parseProfile("{}"));
        assertThrows(IllegalArgumentException.class, () -> NpcDataLoader.parseProfile("""
                {"uuid":"not-a-uuid","name":"Ada"}
                """));
        assertThrows(JsonParseException.class, () -> NpcDataLoader.parseProfile("""
                {"uuid":"00000000-0000-0000-0000-000000000001","name":null}
                """));
        assertThrows(JsonParseException.class, () -> NpcDataLoader.parseProfile("""
                {"uuid":"00000000-0000-0000-0000-000000000001","name":"Ada","personality":[]}
                """));
    }

    @Test
    void keepsLegacyFilenamesAndContainsExplicitAndEntityNamePaths() {
        UUID id = new UUID(0, 1);
        Path dir = Path.of("build", "npcData");
        assertEquals(dir.resolve("Alaina Fae-" + id + ".json"),
                NpcDataLoader.profilePath(dir, "Alaina Fae", id));
        assertEquals(dir, NpcDataLoader.profilePath(dir, "..\\..\\elsewhere/Ada\n", id).getParent());
        assertEquals("ada.json", NpcDataLoader.normalizeProfileFileName(" ../../profiles/ada "));
        assertEquals("Ada.JSON", NpcDataLoader.normalizeProfileFileName("C:\\profiles\\Ada.JSON"));
        assertEquals("", NpcDataLoader.normalizeProfileFileName("profiles/"));
        assertEquals("", NpcDataLoader.normalizeProfileFileName(".."));
        assertEquals("", NpcDataLoader.normalizeProfileFileName(null));
        assertEquals("bad_name.json", NpcDataLoader.normalizeProfileFileName("bad:name"));
    }

    @Test
    void notesCacheIsUtf8FifoAndPreservesOtherJsonFields() throws IOException {
        Path file = fixture();
        assertNull(NpcDataLoader.popThreatNote(file));
        Files.writeString(file, "{\"notes\":[\"première\"],\"metadata\":\"keep\"}", StandardCharsets.UTF_8);
        NpcDataLoader.addThreatNotes(file, List.of("second", "third"));
        assertEquals("première", NpcDataLoader.popThreatNote(file));
        assertEquals("second", NpcDataLoader.popThreatNote(file));
        assertEquals("third", NpcDataLoader.popThreatNote(file));
        assertNull(NpcDataLoader.popThreatNote(file));
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("keep", json.get("metadata").getAsString());
        assertTrue(json.getAsJsonArray("notes").isEmpty());
        assertFalse(Files.exists(file.resolveSibling(file.getFileName() + ".pending")));
    }

    @Test
    void malformedCacheAndFailedNotesAreNeverSilentlyOverwritten() throws IOException {
        Path file = fixture();
        String bad = "{\"notes\":[{\"text\":\"not a string\"}]}";
        Files.writeString(file, bad);
        assertThrows(JsonParseException.class, () -> NpcDataLoader.popThreatNote(file));
        assertThrows(JsonParseException.class, () -> NpcDataLoader.addThreatNotes(file, List.of("new")));
        assertEquals(bad, Files.readString(file));
        Files.writeString(file, "{\"notes\":[\"old\"]}");
        assertThrows(IllegalArgumentException.class,
                () -> NpcDataLoader.addThreatNotes(file, List.of("valid", "[AI Error] timed out")));
        assertEquals("{\"notes\":[\"old\"]}", Files.readString(file));
    }

    @Test
    void profileWritesAndRenamesPreserveLegacyDataAndAssignedFileContents() throws IOException {
        Path dir = fixture().getParent();
        UUID id = new UUID(0, 1);
        Path oldFile = NpcDataLoader.profilePath(dir, "Old Name", id);
        Path newFile = NpcDataLoader.profilePath(dir, "New Name", id);
        assertTrue(NpcDataLoader.writeProfile(oldFile, "Old Name", id, "description", "kind", "Rivière"));
        var json = JsonParser.parseString(Files.readString(oldFile)).getAsJsonObject();
        assertEquals(id.toString(), json.get("uuid").getAsString());
        assertEquals("Rivière", json.get("village_name").getAsString());
        json.addProperty("custom_field", "keep");
        Files.writeString(oldFile, json.toString());
        assertTrue(NpcDataLoader.renameProfileFiles(oldFile, newFile, "New Name"));
        assertFalse(Files.exists(oldFile));
        var renamed = JsonParser.parseString(Files.readString(newFile)).getAsJsonObject();
        assertEquals("New Name", renamed.get("name").getAsString());
        assertEquals("keep", renamed.get("custom_field").getAsString());
        assertEquals("kind", renamed.get("personality").getAsString());
        // Assigned filenames and same-name renames must update, not delete the file.
        assertTrue(NpcDataLoader.renameProfileFiles(newFile, newFile, "Assigned Name"));
        assertTrue(Files.isRegularFile(newFile));
        assertEquals("Assigned Name",
                JsonParser.parseString(Files.readString(newFile)).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void renameDoesNotOverwriteAnExistingDifferentProfile() throws IOException {
        Path dir = fixture().getParent();
        Path old = dir.resolve("old.json");
        Path existing = dir.resolve("existing.json");
        Files.writeString(old, "{\"name\":\"Old\",\"personality\":\"kind\"}");
        Files.writeString(existing, "{\"name\":\"Existing\"}");
        assertFalse(NpcDataLoader.renameProfileFiles(old, existing, "New"));
        assertEquals("{\"name\":\"Old\",\"personality\":\"kind\"}", Files.readString(old));
        assertEquals("{\"name\":\"Existing\"}", Files.readString(existing));
    }
}
