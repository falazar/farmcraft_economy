package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.database.message.BuildPreviewPacket;
import com.falazar.farmupcraft.util.BuildPreviewGeometry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.Level;

import java.util.List;

public final class BuildPreviewRenderer {
    private static volatile Active active;

    private record Active(String dimension, long expiresGameTime, List<BuildPreviewGeometry.Quad> quads) {}

    private BuildPreviewRenderer() {}

    public static void receive(BuildPreviewPacket packet) {
        if (packet.source().isEmpty() && packet.destination().isEmpty()) {
            active = null;
            return;
        }
        active = new Active(packet.dimension(), packet.expiresGameTime(),
                BuildPreviewGeometry.buildQuads(packet.source(), packet.destination()));
    }

    public static void clear() {
        active = null;
    }

    public static void render(double camX, double camY, double camZ) {
        Active current = active;
        if (current == null || current.quads().isEmpty()) return;
        Level level = Minecraft.getInstance().level;
        if (level == null || Minecraft.getInstance().player == null) {
            active = null;
            return;
        }
        String dim = level.dimension().location().toString();
        if (!dim.equals(current.dimension()) || level.getGameTime() >= current.expiresGameTime()) {
            active = null;
            return;
        }

        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (BuildPreviewGeometry.Quad q : current.quads()) {
            int a = (q.argb() >>> 24) & 0xFF;
            int r = (q.argb() >>> 16) & 0xFF;
            int g = (q.argb() >>> 8) & 0xFF;
            int b = q.argb() & 0xFF;
            buffer.vertex(q.x1() - camX, q.y1() - camY, q.z1() - camZ).color(r, g, b, a).endVertex();
            buffer.vertex(q.x2() - camX, q.y2() - camY, q.z2() - camZ).color(r, g, b, a).endVertex();
            buffer.vertex(q.x3() - camX, q.y3() - camY, q.z3() - camZ).color(r, g, b, a).endVertex();
            buffer.vertex(q.x4() - camX, q.y4() - camY, q.z4() - camZ).color(r, g, b, a).endVertex();
        }

        tesselator.end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
