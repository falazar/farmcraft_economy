package com.falazar.farmupcraft.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class NpcDataTest {
    private static final UUID ID = UUID.fromString("12345678-1234-5678-90ab-1234567890ab");
    private static final UUID VILLAGE = UUID.fromString("fedcba98-7654-3210-abcd-fedcba987654");

    @Test
    void legacyAndCompleteJsonAndNbtKeepAllFieldNamesAndStringUuidRepresentation() {
        assertShape("""
                {"id":"12345678-1234-5678-90ab-1234567890ab","name":"Ada"}
                """);
        assertShape("""
                {"id":"12345678-1234-5678-90ab-1234567890ab","name":"Ada",
                 "home_village_id":"fedcba98-7654-3210-abcd-fedcba987654","description":"A farmer",
                 "personality":"Quiet","village_name":"River Town","forever_kid":true,
                 "behavior":"Custom:HOSTILE","ai_profile_file":"profiles/ada.json"}
                """);
    }

    @Test
    void missingOptionalFieldsHaveLegacyDefaults() {
        var decoded = NpcData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"id":"12345678-1234-5678-90ab-1234567890ab","name":"Ada"}
                """)).getOrThrow();
        assertEquals(ID, decoded.getUUID());
        assertEquals("Ada", decoded.getName());
        assertNull(decoded.getHomeVillageUUID());
        assertEquals("", decoded.getDescription());
        assertEquals("", decoded.getPersonality());
        assertEquals("", decoded.getVillageName());
        assertFalse(decoded.isForeverKid());
        assertEquals("nonhostile", decoded.getBehavior());
        assertEquals("", decoded.getAiProfileFile());
    }

    @Test
    void constructorOverloadsAndNullableInputsPreserveNormalizationRules() {
        var legacy = new NpcData(ID, "Ada", null, "description");
        assertEquals("", legacy.getPersonality());
        assertEquals("", legacy.getVillageName());
        assertFalse(legacy.isForeverKid());
        assertEquals("nonhostile", legacy.getBehavior());
        assertEquals("", legacy.getAiProfileFile());
        var personality = new NpcData(ID, "Ada", VILLAGE, "description", "kind");
        assertEquals("kind", personality.getPersonality());
        assertEquals("", personality.getVillageName());
        var village = new NpcData(ID, "Ada", VILLAGE, "description", "kind", "Town");
        assertEquals("Town", village.getVillageName());
        assertFalse(village.isForeverKid());
        assertTrue(new NpcData(ID, "Ada", VILLAGE, "", "", "", true).isForeverKid());
        for (String behavior : new String[]{null, "", " \t\n"}) {
            var data = new NpcData(ID, "Ada", null, "", "", null, false, behavior, null);
            assertEquals("nonhostile", data.getBehavior());
            assertEquals("", data.getVillageName());
            assertEquals("", data.getAiProfileFile());
        }
        assertEquals(" HOSTILE ", new NpcData(ID, "Ada", null, "", "", "", false,
                " HOSTILE ", "").getBehavior());
    }

    @Test
    void settersAreLiteralAndNullableVillageIsOmittedFromEncoding() {
        var data = new NpcData(ID, "Ada", VILLAGE, "");
        data.setName("Bob");
        data.setDescription("description");
        data.setPersonality("kind");
        data.setVillageName("Town");
        data.setForeverKid(true);
        data.setBehavior(" ");
        data.setAiProfileFile("profiles/bob.json");
        assertEquals(ID, data.getUUID());
        assertEquals("Bob", data.getName());
        assertEquals("description", data.getDescription());
        assertEquals("kind", data.getPersonality());
        assertEquals("Town", data.getVillageName());
        assertTrue(data.isForeverKid());
        assertEquals(" ", data.getBehavior());
        assertEquals("profiles/bob.json", data.getAiProfileFile());
        assertEquals(VILLAGE, data.getHomeVillageUUID());
        data.setHomeVillageId(null);
        assertNull(data.getHomeVillageUUID());
        assertFalse(NpcData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow()
                .getAsJsonObject().has("home_village_id"));
        data.setHomeVillageId(ID);
        assertEquals(ID, data.getHomeVillageUUID());
    }

    @Test
    void requiredFieldsAndInvalidUuidAreRejected() {
        for (String source : List.of("{}", "{\"id\":\"" + ID + "\"}",
                "{\"id\":\"bad\",\"name\":\"Ada\"}",
                "{\"id\":\"" + ID + "\",\"name\":\"Ada\",\"home_village_id\":\"bad\"}")) {
            assertTrue(NpcData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(source)).error().isPresent());
        }
    }

    private static void assertShape(String source) {
        JsonElement json = JsonParser.parseString(source);
        var decoded = NpcData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(json, NpcData.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow());
        var nbt = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, json);
        var fromNbt = NpcData.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
        assertEquals(NpcData.CODEC.encodeStart(NbtOps.INSTANCE, decoded).getOrThrow(),
                NpcData.CODEC.encodeStart(NbtOps.INSTANCE, fromNbt).getOrThrow());
        assertEquals(json, NpcData.CODEC.encodeStart(JsonOps.INSTANCE, fromNbt).getOrThrow());
    }
}
