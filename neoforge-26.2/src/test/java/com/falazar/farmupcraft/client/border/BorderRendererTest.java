package com.falazar.farmupcraft.client.border;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class BorderRendererTest {
    @Test
    void wallVerticesSpanTheFullHeightInFogSafeSections() {
        var heights = new ArrayList<Float>();
        VertexConsumer buffer = (VertexConsumer) Proxy.newProxyInstance(
                VertexConsumer.class.getClassLoader(), new Class<?>[]{VertexConsumer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("addVertex") && args.length == 4) {
                        heights.add((Float) args[2]);
                    }
                    return proxy;
                });
        var snapshot = BorderRenderer.snapshot(Set.of(ChunkPos.pack(0, 0)),
                8, 8, -64, 321, 0, BorderRenderer.color("blue"));
        BorderRenderer.emit(snapshot, Vec3.ZERO, new PoseStack().last(), buffer);
        assertEquals(4 * 25 * 4, heights.size());
        assertEquals(-64F, heights.stream().min(Float::compareTo).orElseThrow());
        assertEquals(321F, heights.stream().max(Float::compareTo).orElseThrow());
        for (int i = 0; i < heights.size(); i += 4) {
            assertEquals(heights.get(i), heights.get(i + 1));
            assertEquals(heights.get(i + 2), heights.get(i + 3));
            float span = heights.get(i) - heights.get(i + 2);
            assertTrue(span > 0 && span <= 16, "Nearby wall sections must retain nearby fog vertices");
        }
    }

    @Test
    void claimFragmentShaderPreservesLowAlphaTexelsAndFog() throws IOException {
        try (var stream = BorderRenderer.class.getResourceAsStream(
                "/assets/farmupcraft/shaders/core/claim_borders.fsh")) {
            assertNotNull(stream);
            String shader = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(shader.contains("discard"), "The particle alpha cutoff hides every claim-border texel");
            assertTrue(shader.contains("sampled * vertexColor * ColorModulator"));
            assertTrue(shader.contains("apply_fog("));
        }
    }

    @Test
    void adjacentClaimsDoNotRenderTheirSharedWall() {
        var snapshot = BorderRenderer.snapshot(Set.of(ChunkPos.pack(0, 0), ChunkPos.pack(1, 0)),
                8, 8, -64, 320, 0.5F, BorderRenderer.color("red"));
        assertEquals(6, snapshot.walls().size());
        assertTrue(snapshot.walls().stream().noneMatch(wall -> wall.x1() == 16 && wall.x2() == 16));
        assertEquals(-64, snapshot.minY());
        assertEquals(320, snapshot.maxY());
    }

    @Test
    void negativeCameraCoordinatesSelectTheCorrectClaim() {
        var snapshot = BorderRenderer.snapshot(Set.of(ChunkPos.pack(-1, -1)),
                -0.1, -0.1, -64, 320, 0, BorderRenderer.color("blue"));
        assertEquals(4, snapshot.walls().size());
        assertTrue(snapshot.walls().contains(new BorderRenderer.Wall(-16, 0, 0, 0)));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.walls().clear());
    }

    @Test
    void distantAndEmptyClaimsDoNotProduceGeometry() {
        assertTrue(BorderRenderer.snapshot(Set.of(), 0, 0, 0, 256, 0, 0).walls().isEmpty());
        assertTrue(BorderRenderer.snapshot(Set.of(ChunkPos.pack(20, 0)),
                0, 0, 0, 256, 0, 0).walls().isEmpty());
    }

    @Test
    void diamondAndPreferenceEnableBordersWithLegacyColors() {
        assertFalse(BorderRenderer.shouldShow(false, false));
        assertTrue(BorderRenderer.shouldShow(true, false));
        assertTrue(BorderRenderer.shouldShow(false, true));
        assertEquals(0x1EFF3333, BorderRenderer.color("RED"));
        assertEquals(0x1E3399FF, BorderRenderer.color(null));
        assertEquals(BorderRenderer.color("blue"), BorderRenderer.color("unknown"));
    }
}
