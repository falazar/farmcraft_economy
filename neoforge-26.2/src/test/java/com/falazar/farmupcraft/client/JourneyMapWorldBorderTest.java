package com.falazar.farmupcraft.client;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.PolygonOverlay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JourneyMapWorldBorderTest {
    private static final JourneyMapWorldBorder.Bounds OVERWORLD = new JourneyMapWorldBorder.Bounds(
            ResourceKey.create(Registries.DIMENSION, Identifier.parse("minecraft:overworld")), -5010, -5010, 5011, 5011);

    @AfterEach
    void reset() {
        new JourneyMapIntegration().initialize(null);
        JourneyMapWorldBorder.loggedOut();
    }

    @Test
    void outlinesBorderOnceWithoutFill() {
        var fixture = new Fixture();
        JourneyMapWorldBorder.update(OVERWORLD, fixture.api);
        JourneyMapWorldBorder.update(OVERWORLD, fixture.api);
        assertEquals(1, fixture.shown.size());
        var overlay = fixture.shown.getFirst();
        assertEquals(List.of(new BlockPos(-5010, 64, 5011), new BlockPos(5011, 64, 5011),
                new BlockPos(5011, 64, -5010), new BlockPos(-5010, 64, -5010)), overlay.getOuterArea().getPoints());
        assertEquals(0.0f, overlay.getShapeProperties().getFillOpacity());
        assertEquals("minecraft:overworld", overlay.getDimension().identifier().toString());
    }

    @Test
    void restoresAfterChunkOverlayClearAndReplacesOnBorderChange() {
        var fixture = new Fixture();
        JourneyMapWorldBorder.update(OVERWORLD, fixture.api);
        fixture.visible.clear();
        JourneyMapWorldBorder.update(OVERWORLD, fixture.api);
        assertEquals(2, fixture.shown.size(), "a removeAll(Polygon) must not leave the border missing");

        var smaller = new JourneyMapWorldBorder.Bounds(OVERWORLD.dimension(), -100, -100, 100, 100);
        JourneyMapWorldBorder.update(smaller, fixture.api);
        assertEquals(List.of(fixture.shown.get(0), fixture.shown.get(1)), fixture.removed);
        assertEquals(3, fixture.shown.size());

        JourneyMapWorldBorder.update(null, fixture.api);
        assertEquals(fixture.shown, fixture.removed);
    }

    @Test
    void failedShowIsNotRetriedEveryTick() {
        var fixture = new Fixture();
        fixture.failShow = true;
        assertDoesNotThrow(() -> JourneyMapWorldBorder.update(OVERWORLD, fixture.api));
        JourneyMapWorldBorder.update(OVERWORLD, fixture.api);
        assertEquals(1, fixture.attempts);
    }

    private static final class Fixture {
        final List<PolygonOverlay> shown = new ArrayList<>();
        final List<PolygonOverlay> removed = new ArrayList<>();
        final Set<Object> visible = new HashSet<>();
        boolean failShow;
        int attempts;
        final IClientAPI api = (IClientAPI) Proxy.newProxyInstance(IClientAPI.class.getClassLoader(),
                new Class<?>[]{IClientAPI.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "show" -> {
                        attempts++;
                        if (failShow) {
                            throw new IllegalStateException("JourneyMap rejected the overlay");
                        }
                        shown.add((PolygonOverlay) arguments[0]);
                        visible.add(arguments[0]);
                        yield null;
                    }
                    case "remove" -> {
                        removed.add((PolygonOverlay) arguments[0]);
                        visible.remove(arguments[0]);
                        yield null;
                    }
                    case "exists" -> visible.contains(arguments[0]);
                    default -> throw new AssertionError("Unexpected JourneyMap call: " + method.getName());
                });
    }
}
