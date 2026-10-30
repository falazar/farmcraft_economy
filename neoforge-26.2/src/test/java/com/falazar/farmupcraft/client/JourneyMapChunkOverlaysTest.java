package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.database.message.HighlightChunkPacket;
import com.falazar.farmupcraft.database.message.ShowVillageChunksPacket;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.DisplayType;
import journeymap.api.v2.client.display.PolygonOverlay;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JourneyMapChunkOverlaysTest {
    @AfterEach
    void reset() {
        new JourneyMapIntegration().initialize(null);
        JourneyMapChunkOverlays.loggedOut();
    }

    @Test
    void highlightRendersActualChunkPolygonWithDimensionLabelAndLegacyStyle() {
        var fixture = new Fixture();
        JourneyMapChunkOverlays.highlight(
                new HighlightChunkPacket(-2, 3, "Farm", "minecraft:the_nether", 0x123456), fixture.api);
        var overlay = fixture.shown.getFirst();
        assertEquals("farmupcraft", overlay.getModId());
        assertEquals("minecraft:the_nether", overlay.getDimension().identifier().toString());
        assertEquals("Farm", overlay.getLabel());
        assertEquals(List.of(new BlockPos(-32, 64, 64), new BlockPos(-16, 64, 64),
                new BlockPos(-16, 64, 48), new BlockPos(-32, 64, 48)), overlay.getOuterArea().getPoints());
        assertEquals(0xFF123456, overlay.getShapeProperties().getStrokeColor());
        assertEquals(0xFF123456, overlay.getShapeProperties().getFillColor());
        assertEquals(1.0f, overlay.getShapeProperties().getStrokeOpacity());
        assertEquals(2.0f, overlay.getShapeProperties().getStrokeWidth());
        assertEquals(0.05f, overlay.getShapeProperties().getFillOpacity());
    }

    @Test
    void replacementSelectsSameDimensionCoordinatesAndLabelOnly() {
        var fixture = new Fixture();
        var first = new HighlightChunkPacket(1, 2, "Farm", "minecraft:overworld", 1);
        JourneyMapChunkOverlays.highlight(first, fixture.api);
        JourneyMapChunkOverlays.highlight(new HighlightChunkPacket(1, 2, "Farm", "minecraft:the_nether", 2), fixture.api);
        JourneyMapChunkOverlays.highlight(new HighlightChunkPacket(1, 2, "Other", "minecraft:overworld", 3), fixture.api);
        assertTrue(fixture.removed.isEmpty());
        JourneyMapChunkOverlays.highlight(first, fixture.api);
        assertEquals(List.of(fixture.shown.getFirst()), fixture.removed);
    }

    @Test
    void villageRefreshReplacesOnlyItsDimensionAndKeepsStandaloneHighlights() {
        var fixture = new Fixture();
        JourneyMapChunkOverlays.highlight(new HighlightChunkPacket(1, 2, "Keep", "minecraft:overworld", 1), fixture.api);
        JourneyMapChunkOverlays.showVillage(village("minecraft:overworld", 0x0055FF), fixture.api);
        JourneyMapChunkOverlays.showVillage(village("minecraft:the_nether", 0x00E000), fixture.api);
        var village = fixture.shown.get(1);
        assertEquals(0xFF0055FF, village.getShapeProperties().getFillColor());
        assertEquals(0.9f, village.getShapeProperties().getStrokeOpacity());
        assertEquals(1.5f, village.getShapeProperties().getStrokeWidth());
        assertEquals(0.10f, village.getShapeProperties().getFillOpacity());
        JourneyMapChunkOverlays.showVillage(new ShowVillageChunksPacket(List.of(), true, "minecraft:overworld"), fixture.api);
        assertEquals(List.of(village), fixture.removed);
        assertEquals(0, fixture.clears);
    }

    @Test
    void explicitClearAndLogoutEndLifetimeWithoutRemovingWaypoints() {
        var fixture = new Fixture();
        new JourneyMapIntegration().initialize(fixture.api);
        var highlight = new HighlightChunkPacket(1, 2, "Farm", "minecraft:overworld", 1);
        JourneyMapChunkOverlays.highlight(highlight, fixture.api);
        JourneyMapChunkOverlays.showVillage(new ShowVillageChunksPacket(List.of(), false, "minecraft:the_nether"), fixture.api);
        assertEquals(1, fixture.clears);
        JourneyMapChunkOverlays.highlight(highlight, fixture.api);
        assertTrue(fixture.removed.isEmpty(), "clear releases the old generated display handles");
        JourneyMapChunkOverlays.loggedOut();
        assertEquals(2, fixture.clears);
        JourneyMapChunkOverlays.highlight(highlight, fixture.api);
        assertTrue(fixture.removed.isEmpty(), "logout releases handles even before API shutdown");
    }

    @Test
    void failedShowIsNotRetainedForFutureReplacement() {
        var fixture = new Fixture();
        fixture.failShow = true;
        var payload = new HighlightChunkPacket(1, 2, "Farm", "minecraft:overworld", 1);
        assertDoesNotThrow(() -> JourneyMapChunkOverlays.highlight(payload, fixture.api));
        fixture.failShow = false;
        JourneyMapChunkOverlays.highlight(payload, fixture.api);
        assertTrue(fixture.removed.isEmpty());
    }

    private static ShowVillageChunksPacket village(String dimension, int color) {
        return new ShowVillageChunksPacket(List.of(new int[]{1, 2, color}), true, dimension);
    }

    private static final class Fixture {
        final List<PolygonOverlay> shown = new ArrayList<>();
        final List<PolygonOverlay> removed = new ArrayList<>();
        int clears;
        boolean failShow;
        final IClientAPI api = (IClientAPI) Proxy.newProxyInstance(IClientAPI.class.getClassLoader(),
                new Class<?>[]{IClientAPI.class}, (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "show" -> {
                            if (failShow) {
                                throw new IllegalStateException("JourneyMap rejected the overlay");
                            }
                            shown.add((PolygonOverlay) arguments[0]);
                        }
                        case "remove" -> removed.add((PolygonOverlay) arguments[0]);
                        case "removeAll" -> {
                            assertEquals(2, arguments.length);
                            assertEquals("farmupcraft", arguments[0]);
                            assertEquals(DisplayType.Polygon, arguments[1]);
                            clears++;
                        }
                        default -> throw new AssertionError("Unexpected JourneyMap call: " + method.getName());
                    }
                    return null;
                });
    }
}
