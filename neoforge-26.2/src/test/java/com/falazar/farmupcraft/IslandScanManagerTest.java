package com.falazar.farmupcraft;

import com.falazar.farmupcraft.database.message.ScanWaypointsPacket;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.ClickEvent;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the immutable terrain algorithm, not a fake Minecraft world. */
class IslandScanManagerTest {
    private static final IslandScanManager.Surface WATER =
            new IslandScanManager.Surface(63, true, false, "minecraft:water");
    private static final IslandScanManager.Surface LAND =
            new IslandScanManager.Surface(63, false, false, "minecraft:stone");

    @Test
    void geometryIncludesCircleEdgeAndNegativeGridCoordinatesWithoutIntegerOverflow() {
        assertTrue(IslandScanManager.inRadius(3, 4, 0, 0, 5));
        assertFalse(IslandScanManager.inRadius(4, 4, 0, 0, 5));
        assertTrue(IslandScanManager.inRadius(-8, -9, -11, -13, 5));
        assertFalse(IslandScanManager.inRadius(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 0, 2_048));
        assertEquals(-10, IslandScanManager.floorGrid(-6));
        assertEquals(-5, IslandScanManager.ceilGrid(-6));
        assertEquals(-5, IslandScanManager.floorGrid(-5));
        assertEquals(0, IslandScanManager.ceilGrid(-1));
        assertEquals(10, IslandScanManager.ceilGrid(6));
        long packed = IslandScanManager.pack(-123, Integer.MIN_VALUE);
        assertEquals(-123, IslandScanManager.px(packed));
        assertEquals(Integer.MIN_VALUE, IslandScanManager.pz(packed));
        assertDoesNotThrow(() -> IslandScanManager.validatePosition(29_997_936, -29_997_936));
        assertThrows(IllegalArgumentException.class, () -> IslandScanManager.validatePosition(29_997_937, 0));
        assertThrows(IllegalArgumentException.class, () -> IslandScanManager.validatePosition(Integer.MIN_VALUE, 0));
    }

    @Test
    void lakesRespectMinimumOceanAndRiverFilters() {
        assertNull(detect(rectangle(0, 0, 3, 3, true), 0, 0).result());
        var minimum = detect(rectangle(0, 0, 2, 5, true), 0, 0);
        assertEquals("lake", minimum.result().get("type").getAsString());
        assertEquals(10, minimum.result().get("block_count").getAsInt());
        assertEquals(1_000, detect(rectangle(0, 0, 25, 40, true), 0, 0)
                .result().get("block_count").getAsInt());
        var ocean = detect(rectangle(0, 0, 7, 143, true), 0, 0);
        assertNull(ocean.result());
        assertTrue(ocean.reason().startsWith("Ocean"));
        assertEquals(1_001, ocean.skippedBody().size());

        IslandScanManager.Terrain lake = rectangle(-2, -2, 5, 5, true);
        var river = detect((x, z) -> x == 2 && z == 2
                ? new IslandScanManager.Surface(63, true, true, "minecraft:water") : lake.at(x, z), 0, 0);
        assertNull(river.result());
        assertTrue(river.reason().startsWith("River"));
        assertEquals(25, river.skippedBody().size());
    }

    @Test
    void islandsIncludeCoastalOnlyAndInteriorBodiesButNeverUnloadedBoundaryFragments() {
        assertEquals("island", detect(rectangle(0, 0, 2, 5, false), 0, 0)
                .result().get("type").getAsString());
        assertEquals(49, detect(rectangle(-3, -3, 7, 7, false), 0, 0)
                .result().get("block_count").getAsInt());
        IslandScanManager.Terrain coast = rectangle(-3, -3, 7, 7, false);
        assertFalse(IslandScanManager.isCoastal(coast, 0, 0));
        assertTrue(IslandScanManager.isCoastal(coast, 2, 0));
        assertTrue(IslandScanManager.isCoastal(coast, 0, -2));

        IslandScanManager.Terrain incomplete = (x, z) -> x >= 0 && x < 5 && z >= 0 && z < 5 ? LAND : null;
        var edge = detect(incomplete, 0, 0);
        assertNull(edge.result());
        assertTrue(edge.reason().startsWith("Incomplete"));
        assertNull(edge.skippedBody());
        assertNull(detect((x, z) -> null, 0, 0).result());
    }

    @Test
    void cachedSeedsCannotCutARealBodyIntoFalseSmallFeaturesAndFillIsBounded() {
        var terrain = rectangle(0, 0, 5, 5, true);
        Set<Long> oldCache = Set.of(IslandScanManager.pack(1, 0), IslandScanManager.pack(0, 1));
        Set<Long> newlyVisited = new HashSet<>();
        var lake = IslandScanManager.detectBody(terrain, 0, 0, oldCache, newlyVisited);
        assertEquals(25, lake.result().get("block_count").getAsInt());
        assertEquals(25, newlyVisited.size());
        assertNull(IslandScanManager.detectBody(terrain, 0, 0, oldCache, newlyVisited).result());

        Set<Long> mainlandVisited = new HashSet<>();
        var mainland = IslandScanManager.detectBody((x, z) -> LAND, 0, 0, Set.of(), mainlandVisited);
        assertNull(mainland.result());
        assertTrue(mainland.reason().startsWith("Mainland"));
        assertEquals(IslandScanManager.MAX_BODY_SIZE + 1, mainlandVisited.size());
        assertEquals(mainlandVisited, mainland.skippedBody());
        var ocean = detect((x, z) -> WATER, 0, 0);
        assertEquals(IslandScanManager.MAX_BODY_SIZE + 1, ocean.skippedBody().size());
        assertTrue(ocean.reason().startsWith("Ocean"));
    }

    @Test
    void interruptedFillsStopWithoutPublishingAPartialBody() {
        Set<Long> newlyVisited = new HashSet<>();
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class,
                    () -> IslandScanManager.detectBody((x, z) -> WATER, 0, 0, Set.of(), newlyVisited));
            assertTrue(newlyVisited.isEmpty());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void resultsPreserveSchemaCentersDeduplicationNamesAndFourCornerWaypoints() {
        JsonObject result = detect(rectangle(-10, -20, 3, 4, true), -10, -20).result();
        assertEquals(-9, result.getAsJsonObject("center").get("x").getAsInt());
        assertEquals(-18, result.getAsJsonObject("center").get("z").getAsInt());
        assertEquals(12, result.get("block_count").getAsInt());
        assertFalse(result.get("partial").getAsBoolean());
        JsonObject box = result.getAsJsonObject("bounding_box");
        assertEquals(-10, box.get("min_x").getAsInt());
        assertEquals(-8, box.get("max_x").getAsInt());
        assertEquals(-20, box.get("min_z").getAsInt());
        assertEquals(-17, box.get("max_z").getAsInt());

        JsonArray old = new JsonArray();
        old.add(result.deepCopy());
        result.addProperty("name", "Mist-12");
        var message = IslandScanManager.formatFeatureMessage(result);
        assertTrue(message.getString().contains("Found lake 'Mist-12' at -9, -18"));
        var teleport = assertInstanceOf(ClickEvent.RunCommand.class,
                message.getSiblings().get(3).getStyle().getClickEvent());
        assertEquals("/tp @s -9 ~ -18", teleport.command());
        assertEquals("Mist-12", result.get("name").getAsString());
        JsonObject island = result.deepCopy();
        island.addProperty("type", "island");
        JsonArray merged = IslandScanManager.mergeFindings(old, List.of(result, result, island));
        assertEquals(2, merged.size());
        assertEquals("Mist-12", merged.get(0).getAsJsonObject().get("name").getAsString());
        assertFalse(old.get(0).getAsJsonObject().has("name"), "Merging must not mutate previous file contents");

        ScanWaypointsPacket packet = IslandScanManager.waypoints(result, "Mist-12");
        assertEquals(ScanWaypointsPacket.ADD, packet.action());
        assertEquals(List.of("Mist-12-NW", "Mist-12-NE", "Mist-12-SW", "Mist-12-SE"),
                packet.entries().stream().map(ScanWaypointsPacket.Entry::name).toList());
        assertEquals(List.of(-10, -8, -10, -8), packet.entries().stream().map(ScanWaypointsPacket.Entry::x).toList());
        assertEquals(List.of(-20, -20, -17, -17), packet.entries().stream().map(ScanWaypointsPacket.Entry::z).toList());
        assertTrue(packet.entries().stream().allMatch(entry -> entry.y() == 64));
        assertEquals(1, packet.entries().stream().map(ScanWaypointsPacket.Entry::color).distinct().count());
        assertThrows(IllegalArgumentException.class, () -> IslandScanManager.buildResult("lake", Set.of()));
    }

    private static IslandScanManager.Detection detect(IslandScanManager.Terrain terrain, int x, int z) {
        return IslandScanManager.detectBody(terrain, x, z, Set.of(), new HashSet<>());
    }

    private static IslandScanManager.Terrain rectangle(int minX, int minZ, int width, int depth, boolean water) {
        return (x, z) -> (x >= minX && x < minX + width && z >= minZ && z < minZ + depth) == water
                ? WATER : LAND;
    }
}
