package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.database.message.BuildPreviewPacket;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.BuildPreviewGeometry;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.List;

public final class BuildPreviewRenderer {
    private static final ContextKey<List<BuildPreviewGeometry.Quad>> PREVIEW_QUADS = new ContextKey<>(
            Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, "build_preview_quads"));
    private static volatile Active active;

    private record Active(String dimension, long expiresGameTime, List<BuildPreviewGeometry.Quad> quads) {}

    private BuildPreviewRenderer() {}

    private static final class RenderResources {
        private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.WEATHER_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, "pipeline/build_preview"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, "core/claim_borders"))
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .build();
        private static final RenderType TYPE = RenderType.create("farmupcraft_build_preview",
                RenderSetup.builder(PIPELINE)
                        .withTexture("Sampler0", Identifier.withDefaultNamespace("textures/misc/white.png"))
                        .useLightmap().sortOnUpload().createRenderSetup());
    }

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

    public static List<BuildPreviewGeometry.Quad> currentQuads() {
        Active current = active;
        return current == null ? List.of() : current.quads();
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        Active current = active;
        List<BuildPreviewGeometry.Quad> quads = List.of();
        var level = event.getLevel();
        var player = Minecraft.getInstance().player;
        if (current != null && level != null && player != null) {
            String dim = level.dimension().identifier().toString();
            if (!dim.equals(current.dimension()) || level.getGameTime() >= current.expiresGameTime()) {
                active = null;
            } else {
                quads = current.quads();
            }
        }
        event.getRenderState().setRenderData(PREVIEW_QUADS, quads);
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        List<BuildPreviewGeometry.Quad> quads = event.getLevelRenderState().getRenderData(PREVIEW_QUADS);
        if (quads == null || quads.isEmpty()) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderResources.TYPE,
                (pose, buffer) -> emit(quads, camera, pose, buffer));
    }

    public static void emit(List<BuildPreviewGeometry.Quad> quads, Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer) {
        for (BuildPreviewGeometry.Quad q : quads) {
            vertex(buffer, pose, q.x1() - camera.x, q.y1() - camera.y, q.z1() - camera.z, 0, 0, q.argb());
            vertex(buffer, pose, q.x2() - camera.x, q.y2() - camera.y, q.z2() - camera.z, 0, 1, q.argb());
            vertex(buffer, pose, q.x3() - camera.x, q.y3() - camera.y, q.z3() - camera.z, 1, 1, q.argb());
            vertex(buffer, pose, q.x4() - camera.x, q.y4() - camera.y, q.z4() - camera.z, 1, 0, q.argb());
        }
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, double x, double y, double z,
                               float u, float v, int color) {
        buffer.addVertex(pose, (float) x, (float) y, (float) z).setUv(u, v)
                .setColor(color).setLight(LightCoordsUtil.FULL_BRIGHT);
    }
}
