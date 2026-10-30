package com.falazar.farmupcraft.client.border;

import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BorderRenderer {
    private static final ContextKey<Snapshot> BORDERS = new ContextKey<>(
            Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, "claim_borders"));

    private BorderRenderer() {}

    // Lazy initialization keeps GPU/render setup out of geometry-only tests and extraction.
    private static final class RenderResources {
        // The particle shader's 0.1 cutoff rejects every texel at the legacy border opacity.
        private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.WEATHER_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, "pipeline/claim_borders"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, "core/claim_borders"))
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .build();
        private static final RenderType TYPE = RenderType.create("farmupcraft_claim_borders",
                RenderSetup.builder(PIPELINE)
                        .withTexture("Sampler0", Identifier.withDefaultNamespace("textures/misc/forcefield.png"))
                        .useLightmap().sortOnUpload().createRenderSetup());
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        Snapshot snapshot = new Snapshot(List.of(), 0, 0, 0, 0);
        var player = Minecraft.getInstance().player;
        var level = event.getLevel();
        if (player != null) {
            PlayerData data = ModEvents.getPlayerDatabase(level).getData(player.getUUID());
            if (data != null && shouldShow(player.getMainHandItem().is(Items.DIAMOND), data.isBorderShow())
                    && data.getHomeVillageUUID() != null) {
                var village = ModEvents.getVillageDatabase(level).getData(data.getHomeVillageUUID());
                if (village != null) {
                    Vec3 camera = event.getRenderState().cameraRenderState.pos;
                    snapshot = snapshot(village.getClaimedChunkSet(), camera.x, camera.z,
                            level.getMinY(), level.getMaxY() + 1,
                            -0.05F * (level.getGameTime() + event.getDeltaTracker().getGameTimeDeltaPartialTick(false)),
                            color(data.getBorderColor()));
                }
            }
        }
        event.getRenderState().setRenderData(BORDERS, snapshot);
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        Snapshot snapshot = event.getLevelRenderState().getRenderData(BORDERS);
        if (snapshot == null || snapshot.walls().isEmpty()) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderResources.TYPE,
                (pose, buffer) -> emit(snapshot, camera, pose, buffer));
    }

    static boolean shouldShow(boolean holdingDiamond, boolean enabled) {
        return holdingDiamond || enabled;
    }

    static Snapshot snapshot(Set<Long> claims, double cameraX, double cameraZ, int minY, int maxY,
                             float scroll, int color) {
        int cx = Mth.floor(cameraX) >> 4;
        int cz = Mth.floor(cameraZ) >> 4;
        boolean near = false;
        for (int dx = -5; dx < 5 && !near; dx++) {
            for (int dz = -5; dz < 5 && !near; dz++) {
                near = claims.contains(ChunkPos.pack(cx + dx, cz + dz));
            }
        }
        List<Wall> walls = new ArrayList<>();
        if (near) {
            for (int x = cx - 12; x <= cx + 12; x++) {
                for (int z = cz - 12; z <= cz + 12; z++) {
                    if (!claims.contains(ChunkPos.pack(x, z))) continue;
                    double bx = x * 16.0;
                    double bz = z * 16.0;
                    if (!claims.contains(ChunkPos.pack(x, z + 1))) walls.add(new Wall(bx, bz + 16, bx + 16, bz + 16));
                    if (!claims.contains(ChunkPos.pack(x + 1, z))) walls.add(new Wall(bx + 16, bz, bx + 16, bz + 16));
                    if (!claims.contains(ChunkPos.pack(x, z - 1))) walls.add(new Wall(bx + 16, bz, bx, bz));
                    if (!claims.contains(ChunkPos.pack(x - 1, z))) walls.add(new Wall(bx, bz, bx, bz + 16));
                }
            }
        }
        return new Snapshot(List.copyOf(walls), minY, maxY, scroll, color);
    }

    static void emit(Snapshot snapshot, Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer) {
        for (Wall wall : snapshot.walls()) {
            // Full-height quad corners are all distant, making interpolated vertex fog hide nearby walls.
            for (int bottom = snapshot.minY(); bottom < snapshot.maxY(); bottom += 16) {
                int top = Math.min(bottom + 16, snapshot.maxY());
                float topV = snapshot.maxY() - top + snapshot.scroll();
                float bottomV = snapshot.maxY() - bottom + snapshot.scroll();
                vertex(buffer, pose, wall.x1() - camera.x, top - camera.y, wall.z1() - camera.z,
                        0, topV, snapshot.color());
                vertex(buffer, pose, wall.x2() - camera.x, top - camera.y, wall.z2() - camera.z,
                        1, topV, snapshot.color());
                vertex(buffer, pose, wall.x2() - camera.x, bottom - camera.y, wall.z2() - camera.z,
                        1, bottomV, snapshot.color());
                vertex(buffer, pose, wall.x1() - camera.x, bottom - camera.y, wall.z1() - camera.z,
                        0, bottomV, snapshot.color());
            }
        }
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, double x, double y, double z,
                               float u, float v, int color) {
        buffer.addVertex(pose, (float) x, (float) y, (float) z).setUv(u, v)
                .setColor(color).setLight(LightCoordsUtil.FULL_BRIGHT);
    }

    static int color(String name) {
        int rgb = switch (name == null ? "blue" : name.toLowerCase(Locale.ROOT)) {
            case "yellow" -> 0xFFFF00;
            case "orange" -> 0xFF7F00;
            case "pink" -> 0xFF66CC;
            case "teal" -> 0x00CCCC;
            case "green" -> 0x33CC33;
            case "red" -> 0xFF3333;
            case "purple" -> 0xB200FF;
            case "white" -> 0xE5E5E5;
            default -> 0x3399FF;
        };
        return 0x1E000000 | rgb;
    }

    record Wall(double x1, double z1, double x2, double z2) {}
    record Snapshot(List<Wall> walls, int minY, int maxY, float scroll, int color) {}
}
