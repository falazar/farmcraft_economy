package com.falazar.farmupcraft.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class GameStructureDataTest {
    @Test
    void minimalAndCompleteLegacyShapesRoundTripInJsonAndNbt() {
        assertShape("""
                {"id":-9223372036854775808,"name":"Tower","center_pos":{"x":-16,"y":-64,"z":0},"type":"CUSTOM:Tower"}
                """);
        assertShape("""
                {"id":9223372036854775807,"name":"Tower","center_pos":{"x":16,"y":320,"z":-32},"type":"tower",
                 "on_claimed_plot":true,"was_visited":true,"min_pos":{"x":0,"y":-64,"z":-32},
                 "max_pos":{"x":31,"y":320,"z":-1},"chunk_positions":[-9223372036854775808,9223372036854775807],
                 "special_chest_pos":{"x":1,"y":2,"z":3},"special_chest_opened":true,"total_chest_count":-1,
                 "opened_chest_positions":[{"x":1,"y":2,"z":3},{"x":4,"y":5,"z":6}]}
                """);
    }

    @Test
    void missingOptionalFieldsAndAllConstructorOverloadsPreserveDefaults() {
        var decoded = GameStructureData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"id":1,"name":"Tower","center_pos":{"x":0,"y":0,"z":0},"type":"tower"}
                """)).getOrThrow();
        for (var data : List.of(decoded, new GameStructureData(1L, "Tower", BlockPos.ZERO, "tower"),
                new GameStructureData(1L, "Tower", BlockPos.ZERO, "tower", false, false),
                new GameStructureData(1L, "Tower", BlockPos.ZERO, "tower", false, false, BlockPos.ZERO, BlockPos.ZERO),
                new GameStructureData(1L, "Tower", BlockPos.ZERO, "tower", false, false, BlockPos.ZERO, BlockPos.ZERO, null),
                new GameStructureData(1L, "Tower", BlockPos.ZERO, "tower", false, false, BlockPos.ZERO, BlockPos.ZERO,
                        null, null, false),
                new GameStructureData(1L, "Tower", BlockPos.ZERO, "tower", false, false, BlockPos.ZERO, BlockPos.ZERO,
                        null, null, false, 0, null))) {
            assertFalse(data.isOnClaimedPlot());
            assertFalse(data.wasVisited());
            assertFalse(data.hasBoundingBox());
            assertFalse(data.hasChunkPositions());
            assertFalse(data.hasSpecialChest());
            assertFalse(data.isSpecialChestOpened());
            assertEquals(0, data.getTotalChestCount());
            assertTrue(data.getOpenedChestPositions().isEmpty());
            assertEquals(BlockPos.ZERO, data.getMinPos());
            assertEquals(BlockPos.ZERO, data.getMaxPos());
            assertEquals(BlockPos.ZERO, data.getSpecialChestPos());
        }
        decoded.getChunkPositions().add(1L);
        decoded.markChestOpened(new BlockPos(1, 2, 3));
        var fresh = new GameStructureData(2L, "Other", BlockPos.ZERO, "tower");
        assertFalse(fresh.hasChunkPositions());
        assertTrue(fresh.getOpenedChestPositions().isEmpty());
    }

    @Test
    void collectionInputsAreCopiedButGettersRemainMutableAndOpenedPositionsBecomeImmutable() {
        var chunks = new ArrayList<>(List.of(-1L));
        var opened = new ArrayList<>(List.of(new BlockPos(1, 2, 3)));
        var data = new GameStructureData(1L, "Tower", BlockPos.ZERO, "tower", false, false,
                BlockPos.ZERO, BlockPos.ZERO, chunks, null, false, 0, opened);
        chunks.clear();
        opened.clear();
        assertEquals(List.of(-1L), data.getChunkPositions());
        assertTrue(data.isChestOpened(new BlockPos(1, 2, 3)));
        data.getChunkPositions().add(0L);
        assertTrue(data.hasChunkPositions());
        var replacement = new ArrayList<>(List.of(Long.MAX_VALUE));
        data.setChunkPositions(replacement);
        replacement.clear();
        assertEquals(List.of(Long.MAX_VALUE), data.getChunkPositions());
        data.setChunkPositions(null);
        assertFalse(data.hasChunkPositions());
        var mutable = new BlockPos.MutableBlockPos(4, 5, 6);
        data.markChestOpened(mutable);
        mutable.set(7, 8, 9);
        data.markChestOpened(new BlockPos(4, 5, 6));
        assertEquals(2, data.getOpenedChestPositions().size());
        assertTrue(data.isChestOpened(new BlockPos(4, 5, 6)));
        assertFalse(data.isChestOpened(mutable));
        assertFalse(data.isChestOpened(null));
        data.getOpenedChestPositions().clear();
        assertFalse(data.isChestOpened(new BlockPos(1, 2, 3)));
    }

    @Test
    void mutationsRetainZeroSentinelsUnclampedCountsAndExactTextFormat() {
        var center = new BlockPos(1, 2, 3);
        var data = new GameStructureData(9L, "Tower", center, "Custom:Tower");
        data.setName("Renamed");
        data.setOnClaimedPlot(true);
        data.markAsVisited();
        assertTrue(data.wasVisited());
        data.setWasVisited(false);
        data.setMinPos(new BlockPos(-1, -64, 0));
        assertTrue(data.hasBoundingBox());
        data.setMinPos(BlockPos.ZERO);
        data.setMaxPos(new BlockPos(0, 320, 0));
        assertTrue(data.hasBoundingBox());
        data.setMaxPos(BlockPos.ZERO);
        assertFalse(data.hasBoundingBox());
        data.setSpecialChestPos(center);
        assertTrue(data.hasSpecialChest());
        data.setSpecialChestOpened(true);
        assertTrue(data.isSpecialChestOpened());
        data.setSpecialChestPos(null);
        assertFalse(data.hasSpecialChest());
        assertEquals(BlockPos.ZERO, data.getSpecialChestPos());
        data.setTotalChestCount(Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, data.getTotalChestCount());
        assertEquals(9L, data.getId());
        assertSame(center, data.getCenterPos());
        assertEquals("Custom:Tower", data.getType());
        assertEquals("GameStructure ID: 9, Name: Renamed, Position: " + center
                + ", Type: Custom:Tower, Claimed: true, Visited: false", data.toString());
    }

    @Test
    void positionCodecKeepsObjectShapeAndRejectsMissingCoordinatesAndArrayFormat() {
        JsonElement position = JsonParser.parseString("{\"x\":-2147483648,\"y\":2147483647,\"z\":0}");
        var decoded = GameStructureData.BLOCK_POS_CODEC.parse(JsonOps.INSTANCE, position).getOrThrow();
        assertEquals(Integer.MIN_VALUE, decoded.getX());
        assertEquals(Integer.MAX_VALUE, decoded.getY());
        assertEquals(position, GameStructureData.BLOCK_POS_CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow());
        for (String invalid : List.of("{}", "[1,2,3]", "{\"x\":1,\"z\":3}")) {
            assertTrue(GameStructureData.BLOCK_POS_CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString(invalid)).error().isPresent());
        }
        assertTrue(GameStructureData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"id\":1,\"name\":\"Tower\",\"type\":\"tower\"}")).error().isPresent());
    }

    private static void assertShape(String source) {
        JsonElement json = JsonParser.parseString(source);
        var decoded = GameStructureData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(json, GameStructureData.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow());
        var nbt = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, json);
        var fromNbt = GameStructureData.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
        assertEquals(GameStructureData.CODEC.encodeStart(NbtOps.INSTANCE, decoded).getOrThrow(),
                GameStructureData.CODEC.encodeStart(NbtOps.INSTANCE, fromNbt).getOrThrow());
        assertEquals(json, GameStructureData.CODEC.encodeStart(JsonOps.INSTANCE, fromNbt).getOrThrow());
    }
}
