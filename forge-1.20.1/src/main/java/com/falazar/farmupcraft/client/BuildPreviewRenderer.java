package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.database.message.BuildPreviewPacket;
import com.falazar.farmupcraft.mixin.client.PreviewRenderTypeAccess;
import com.falazar.farmupcraft.util.BuildPreviewGeometry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.model.data.ModelData;
import org.slf4j.Logger;

import java.util.*;

public final class BuildPreviewRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Active active;

    private static final class Active {
        final BuildPreviewPacket packet;
        Object models;
        Map<ResourceLocation, List<Vertex>> geometry = Map.of();
        Active(BuildPreviewPacket packet) { this.packet = packet; }
    }

    public record Vertex(double x, double y, double z, float u, float v, int argb) {}

    private BuildPreviewRenderer() {}

    public static void receive(BuildPreviewPacket packet) {
        active = packet.states().isEmpty() ? null : new Active(packet);
    }

    public static void clear() { active = null; }

    public static void invalidateModels() {
        if (active != null) active.models = null;
    }

    public static void render(double camX, double camY, double camZ) {
        Active current = active;
        if (current == null) return;
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null || minecraft.player == null
                || !level.dimension().location().toString().equals(current.packet.dimension())
                || level.getGameTime() >= current.packet.expiresGameTime()) {
            active = null;
            return;
        }
        Object models = minecraft.getModelManager().getBlockModelShaper();
        if (current.models != models) {
            current.geometry = buildGeometry(current.packet, level, minecraft);
            current.models = models;
        }
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        try {
            for (var entry : current.geometry.entrySet()) {
                RenderSystem.setShaderTexture(0, entry.getKey());
                var tesselator = Tesselator.getInstance();
                var buffer = tesselator.getBuilder();
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                for (Vertex vertex : entry.getValue()) {
                    int color = vertex.argb();
                    buffer.vertex(vertex.x() - camX, vertex.y() - camY, vertex.z() - camZ)
                            .uv(vertex.u(), vertex.v())
                            .color((color >>> 16) & 255, (color >>> 8) & 255, color & 255, (color >>> 24) & 255)
                            .endVertex();
                }
                tesselator.end();
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static Map<ResourceLocation, List<Vertex>> buildGeometry(BuildPreviewPacket packet, ClientLevel level,
                                                                    Minecraft minecraft) {
        Map<ResourceLocation, List<Vertex>> geometry = new LinkedHashMap<>();
        Map<BlockPos, BlockState> source = statesAt(packet.source(), packet.states());
        Map<BlockPos, BlockState> destination = statesAt(packet.destination(), packet.states());
        Map<BlockPos, BlockPos> originals = new HashMap<>();
        for (int i = 0; i < packet.source().size(); i++) {
            originals.put(position(packet.destination().get(i)), position(packet.source().get(i)));
        }
        var sourceView = new PreviewLevel(level, source, Map.of());
        var destinationView = new PreviewLevel(level, destination, originals);
        var renderer = new ModelBlockRenderer(minecraft.getBlockColors());
        source.forEach((pos, state) -> {
            if (!destination.containsKey(pos)) addBlock(geometry, minecraft, renderer, sourceView, pos, state, 0x50FF8888);
        });
        destination.forEach((pos, state) ->
                addBlock(geometry, minecraft, renderer, destinationView, pos, state, 0x80FFFFFF));
        geometry.replaceAll((texture, vertices) -> List.copyOf(vertices));
        return Map.copyOf(geometry);
    }

    private static Map<BlockPos, BlockState> statesAt(List<BuildPreviewGeometry.Entry> entries, List<BlockState> states) {
        Map<BlockPos, BlockState> result = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) result.put(position(entries.get(i)), states.get(i));
        return result;
    }

    private static BlockPos position(BuildPreviewGeometry.Entry entry) {
        return new BlockPos(entry.x(), entry.y(), entry.z());
    }

    private static void addBlock(Map<ResourceLocation, List<Vertex>> geometry, Minecraft minecraft,
                                 ModelBlockRenderer renderer, PreviewLevel view, BlockPos pos, BlockState state, int tint) {
        if (state.getRenderShape() == RenderShape.MODEL) {
            var model = minecraft.getBlockRenderer().getBlockModel(state);
            var capture = new VertexCapture(pos, tint);
            ModelData data = view.getBlockEntity(pos) == null ? ModelData.EMPTY : view.getBlockEntity(pos).getModelData();
            data = model.getModelData(view, pos, state, data);
            for (var layer : model.getRenderTypes(state, RandomSource.create(state.getSeed(view.original(pos))), data)) {
                renderer.tesselateBlock(view, model, state, pos, new PoseStack(), capture, true,
                        RandomSource.create(state.getSeed(view.original(pos))), state.getSeed(view.original(pos)),
                        OverlayTexture.NO_OVERLAY, data, layer);
            }
            geometry.computeIfAbsent(TextureAtlas.LOCATION_BLOCKS, key -> new ArrayList<>()).addAll(capture.finish());
        }
        BlockEntity entity = view.getBlockEntity(pos);
        if (state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED && entity != null) {
            var entityRenderer = minecraft.getBlockEntityRenderDispatcher().getRenderer(entity);
            if (entityRenderer == null) {
                LOGGER.warn("No block-entity preview renderer at {} for {}", pos, state);
                return;
            }
            List<VertexCapture> captures = new ArrayList<>();
            entityRenderer.render(entity, 0, new PoseStack(), type -> {
                var capture = new VertexCapture(pos, tint);
                if (type.mode() == VertexFormat.Mode.QUADS && type instanceof PreviewRenderTypeAccess.Type access) {
                    var textureState = ((PreviewRenderTypeAccess.State) (Object) access.farmupcraft$state())
                            .farmupcraft$texture();
                    var texture = ((PreviewRenderTypeAccess.Texture) textureState).farmupcraft$textureLocation();
                    if (texture.isPresent()) {
                        capture.texture = texture.get();
                        captures.add(capture);
                        return capture;
                    }
                }
                LOGGER.warn("Unsupported block-entity preview texture at {}: {}", pos, type);
                return capture;
            }, 0xF000F0, OverlayTexture.NO_OVERLAY);
            for (var capture : captures) {
                geometry.computeIfAbsent(capture.texture, key -> new ArrayList<>()).addAll(capture.finish());
            }
        }
    }

    private record PreviewLevel(ClientLevel level, Map<BlockPos, BlockState> states,
                                Map<BlockPos, BlockPos> originals) implements BlockAndTintGetter {
        BlockPos original(BlockPos pos) { return originals.getOrDefault(pos, pos); }
        @Override public float getShade(Direction direction, boolean shade) { return level.getShade(direction, shade); }
        @Override public LevelLightEngine getLightEngine() { return level.getLightEngine(); }
        @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) { return level.getBlockTint(pos, resolver); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) {
            return states.containsKey(pos) ? level.getBlockEntity(original(pos)) : null;
        }
        @Override public BlockState getBlockState(BlockPos pos) {
            return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return level.getHeight(); }
        @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
    }

    static final class VertexCapture implements VertexConsumer {
        private final BlockPos pos;
        private final int tint;
        private final List<Vertex> vertices = new ArrayList<>();
        private ResourceLocation texture;
        private double x, y, z;
        private float u, v;
        private int color = -1;

        VertexCapture(BlockPos pos, int tint) { this.pos = pos; this.tint = tint; }
        @Override public VertexConsumer vertex(double x, double y, double z) {
            this.x = x; this.y = y; this.z = z; return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) {
            color = a << 24 | r << 16 | g << 8 | b; return this;
        }
        @Override public VertexConsumer uv(float u, float v) { this.u = u; this.v = v; return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void defaultColor(int r, int g, int b, int a) { color(r, g, b, a); }
        @Override public void unsetDefaultColor() { color = -1; }
        @Override public void endVertex() {
            int argb = 0;
            for (int shift : new int[]{0, 8, 16, 24}) {
                argb |= (((color >>> shift) & 255) * ((tint >>> shift) & 255) / 255) << shift;
            }
            vertices.add(new Vertex(pos.getX() + (x - 0.5) * 1.004 + 0.5,
                    pos.getY() + (y - 0.5) * 1.004 + 0.5, pos.getZ() + (z - 0.5) * 1.004 + 0.5, u, v, argb));
        }
        List<Vertex> finish() {
            if (vertices.size() % 4 != 0) throw new IllegalStateException("Preview model emitted incomplete quads");
            return List.copyOf(vertices);
        }
    }
}
