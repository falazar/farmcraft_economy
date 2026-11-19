package com.falazar.farmupcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Proxy;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class BuildPreviewRendererTest {
    @Test
    void modelCapturePreservesPartialShapesAtlasUvsAndTintWithGhostAlpha() {
        var capture = new BuildPreviewRenderer.VertexCapture(new BlockPos(20, 64, 30), 0x80FFFFFF);
        for (float[] vertex : new float[][] {{0, 0.5F, 0}, {0, 0.5F, 1}, {1, 0.5F, 1}, {1, 0.5F, 0}}) {
            capture.addVertex(vertex[0], vertex[1], vertex[2]).setColor(0xFF228844).setUv(0.125F, 0.75F);
        }
        var vertices = capture.finish();
        assertEquals(4, vertices.size());
        assertEquals(64.5, vertices.getFirst().y(), 0.000001, "Slabs must not become full-height cubes");
        assertEquals(0.125F, vertices.getFirst().u());
        assertEquals(0.75F, vertices.getFirst().v());
        assertEquals(0x80228844, vertices.getFirst().argb(), "Retain model tint while reducing opacity");
        var emittedPositions = new ArrayList<float[]>();
        var emittedUvs = new ArrayList<float[]>();
        var emittedColors = new ArrayList<Integer>();
        VertexConsumer buffer = (VertexConsumer) Proxy.newProxyInstance(
                VertexConsumer.class.getClassLoader(), new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("addVertex") && args.length == 4) {
                        emittedPositions.add(new float[] {(Float) args[1], (Float) args[2], (Float) args[3]});
                    } else if (method.getName().equals("setUv")) {
                        emittedUvs.add(new float[] {(Float) args[0], (Float) args[1]});
                    } else if (method.getName().equals("setColor") && args.length == 1) {
                        emittedColors.add((Integer) args[0]);
                    }
                    return proxy;
                });
        BuildPreviewRenderer.emit(vertices, new Vec3(20, 64, 30), new PoseStack().last(), buffer);
        assertEquals(4, emittedPositions.size());
        assertEquals(0.5F, emittedPositions.getFirst()[1]);
        assertArrayEquals(new float[] {0.125F, 0.75F}, emittedUvs.getFirst());
        assertEquals(0x80228844, emittedColors.getFirst());
    }
}
