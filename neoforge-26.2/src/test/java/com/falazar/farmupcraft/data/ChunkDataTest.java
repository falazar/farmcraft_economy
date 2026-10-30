package com.falazar.farmupcraft.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class ChunkDataTest {
    private static final UUID VILLAGE = UUID.fromString("12345678-1234-5678-90ab-1234567890ab");
    private static final UUID OWNER = UUID.fromString("fedcba98-7654-3210-abcd-fedcba987654");

    @Test
    void legacyRequiredAndCompleteShapesRoundTripInJsonAndNbt() {
        assertShape("""
                {"type":"farm","player_id":-2147483648,"village_id":"12345678-1234-5678-90ab-1234567890ab"}
                """);
        assertShape("""
                {"type":"custom:Plot","player_id":2147483647,"village_id":"12345678-1234-5678-90ab-1234567890ab",
                 "owner_uuid":"fedcba98-7654-3210-abcd-fedcba987654","visitors":["Alice"," bob ","alice"],
                 "bought_y":-64,"plot_level":-1}
                """);
    }

    @Test
    void absentFieldsAndConstructorOverloadsRetainDefaultsAndNullableOwnership() {
        var decoded = ChunkData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"type":"farm","player_id":4,"village_id":"12345678-1234-5678-90ab-1234567890ab"}
                """)).getOrThrow();
        for (ChunkData data : List.of(decoded, new ChunkData("farm", 4, VILLAGE),
                new ChunkData("farm", 4, VILLAGE, null),
                new ChunkData("farm", 4, VILLAGE, null, null))) {
            assertNull(data.getOwnerUUID());
            assertEquals(60, data.getBoughtY());
            assertEquals(1, data.getPlotLevel());
            assertTrue(data.getVisitors().isEmpty());
            data.addVisitor("Alice");
            assertEquals(List.of("alice"), data.getVisitors());
        }
        assertEquals(OWNER, new ChunkData("farm", 4, VILLAGE, OWNER).getOwnerUUID());
        assertEquals(60, new ChunkData("farm", 4, VILLAGE, OWNER, List.of("Alice")).getBoughtY());
    }

    @Test
    void visitorMutationsKeepNormalizationDeduplicationDefensiveInputAndLiveGetter() {
        var input = new ArrayList<>(List.of("Alice"));
        var data = new ChunkData("farm", 1, VILLAGE, OWNER, input, 320);
        input.clear();
        assertEquals(List.of("Alice"), data.getVisitors());
        data.addVisitor(" ALICE ");
        data.addVisitor("alice");
        data.addVisitor(null);
        data.addVisitor("   ");
        assertEquals(List.of("Alice", "alice"), data.getVisitors());
        assertTrue(data.isVisitor(" Alice "));
        assertFalse(data.isVisitor(null));
        data.removeVisitor(" ALICE ");
        assertEquals(List.of("Alice"), data.getVisitors());
        assertFalse(data.isVisitor("Alice"));
        data.removeVisitor(null);
        data.getVisitors().add("bob");
        assertTrue(data.isVisitor(" BOB "));
        data.setType("other");
        data.setVillageId(OWNER);
        data.setOwnerUUID(null);
        data.setBoughtY(Integer.MIN_VALUE);
        data.setPlotLevel(Integer.MAX_VALUE);
        assertEquals("other", data.getType());
        assertEquals(OWNER, data.getVillageId());
        assertNull(data.getOwnerUUID());
        assertEquals(Integer.MIN_VALUE, data.getBoughtY());
        assertEquals(Integer.MAX_VALUE, data.getPlotLevel());
        assertEquals(1, data.getPlayerId());
    }

    @Test
    void requiredFieldsAndMalformedUuidAreRejected() {
        for (String invalid : List.of(
                "{}",
                "{\"type\":\"farm\",\"player_id\":1}",
                "{\"type\":\"farm\",\"player_id\":1,\"village_id\":\"not-a-uuid\"}",
                "{\"type\":\"farm\",\"player_id\":1,\"village_id\":\"" + VILLAGE + "\",\"owner_uuid\":\"bad\"}")) {
            assertTrue(ChunkData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(invalid)).error().isPresent());
        }
    }

    private static void assertShape(String source) {
        JsonElement json = JsonParser.parseString(source);
        var decoded = ChunkData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(json, ChunkData.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow());
        var nbt = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, json);
        var fromNbt = ChunkData.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
        assertEquals(ChunkData.CODEC.encodeStart(NbtOps.INSTANCE, decoded).getOrThrow(),
                ChunkData.CODEC.encodeStart(NbtOps.INSTANCE, fromNbt).getOrThrow());
        assertEquals(json, ChunkData.CODEC.encodeStart(JsonOps.INSTANCE, fromNbt).getOrThrow());
    }
}
