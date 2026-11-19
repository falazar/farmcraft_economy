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
import net.minecraft.client.model.Model;
import net.minecraft.client.multiplayer.ClientLevel;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BuildPreviewRenderer {
    private static final ContextKey<Map<Identifier, List<Vertex>>> PREVIEW_GEOMETRY = new ContextKey<>(
            Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, "build_preview_geometry"));
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Active active;

    private static final class Active {
        private final BuildPreviewPacket packet;
        private Object models;
        private Map<Identifier, List<Vertex>> geometry = Map.of();

        private Active(BuildPreviewPacket packet) {
            this.packet = packet;
        }
    }

    public record Vertex(double x, double y, double z, float u, float v, int argb) {}

    private BuildPreviewRenderer() {}

    private static final class RenderResources {
        private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.WEATHER_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, "pipeline/build_preview"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, "core/claim_borders"))
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .build();
        private static final Map<Identifier, RenderType> TYPES = new HashMap<>();

        private static RenderType type(Identifier atlas) {
            return TYPES.computeIfAbsent(atlas, texture -> RenderType.create("farmupcraft_build_preview",
                    RenderSetup.builder(PIPELINE).withTexture("Sampler0", texture)
                            .useLightmap().sortOnUpload().createRenderSetup()));
        }
    }

    public static void receive(BuildPreviewPacket packet) {
        active = packet.states().isEmpty() ? null : new Active(packet);
    }

    public static void clear() {
        active = null;
    }

    public static BuildPreviewPacket currentPreview() {
        Active current = active;
        return current == null ? BuildPreviewPacket.clear() : current.packet;
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        Active current = active;
        Map<Identifier, List<Vertex>> geometry = Map.of();
        var level = event.getLevel();
        var minecraft = Minecraft.getInstance();
        if (current != null && level != null && minecraft.player != null) {
            if (!level.dimension().identifier().toString().equals(current.packet.dimension())
                    || level.getGameTime() >= current.packet.expiresGameTime()) {
                active = null;
            } else {
                var models = minecraft.getModelManager().getBlockModelSet();
                if (current.models != models) {
                    current.geometry = buildGeometry(current.packet, level, minecraft,
                            event.getRenderState().cameraRenderState);
                    current.models = models;
                }
                geometry = current.geometry;
            }
        }
        event.getRenderState().setRenderData(PREVIEW_GEOMETRY, geometry);
    }

    private static Map<Identifier, List<Vertex>> buildGeometry(BuildPreviewPacket packet, ClientLevel level,
            Minecraft minecraft, CameraRenderState camera) {
        Map<Identifier, List<Vertex>> geometry = new LinkedHashMap<>();
        Map<BlockPos, BlockState> source = statesAt(packet.source(), packet.states());
        Map<BlockPos, BlockState> destination = statesAt(packet.destination(), packet.states());
        Map<BlockPos, BlockPos> originals = new HashMap<>();
        for (int i = 0; i < packet.source().size(); i++) {
            originals.put(position(packet.destination().get(i)), position(packet.source().get(i)));
        }
        var renderer = new ModelBlockRenderer(false, true, minecraft.getBlockColors());
        var sourceView = new PreviewLevel(level, source, Map.of());
        var destinationView = new PreviewLevel(level, destination, originals);
        source.forEach((pos, state) -> {
            if (!destination.containsKey(pos)) {
                addBlock(geometry, minecraft, renderer, sourceView, pos, state, 0x50FF8888, camera);
            }
        });
        destination.forEach((pos, state) ->
                addBlock(geometry, minecraft, renderer, destinationView, pos, state, 0x80FFFFFF, camera));
        geometry.replaceAll((atlas, vertices) -> List.copyOf(vertices));
        return Map.copyOf(geometry);
    }

    private static Map<BlockPos, BlockState> statesAt(List<BuildPreviewGeometry.Entry> entries,
            List<BlockState> states) {
        Map<BlockPos, BlockState> result = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            result.put(position(entries.get(i)), states.get(i));
        }
        return result;
    }

    private static BlockPos position(BuildPreviewGeometry.Entry entry) {
        return new BlockPos(entry.x(), entry.y(), entry.z());
    }

    private static void addBlock(Map<Identifier, List<Vertex>> geometry, Minecraft minecraft,
            ModelBlockRenderer renderer, PreviewLevel view, BlockPos pos, BlockState state, int tint,
            CameraRenderState camera) {
        if (state.getRenderShape() == RenderShape.MODEL) {
            var model = minecraft.getModelManager().getBlockStateModelSet().get(state);
            boolean[] emitted = {false};
            renderer.tesselateBlock((x, y, z, quad, instance) -> {
                emitted[0] = true;
                var capture = new VertexCapture(pos, tint);
                capture.putBlockBakedQuad(x, y, z, quad, instance);
                geometry.computeIfAbsent(quad.materialInfo().sprite().atlasLocation(), key -> new ArrayList<>())
                        .addAll(capture.finish());
            }, 0, 0, 0, view, pos, state, model, state.getSeed(view.original(pos)));
            if (!emitted[0]) {
                BlockEntity entity = view.getBlockEntity(pos);
                if (entity != null) {
                    var entityRenderer = minecraft.getBlockEntityRenderDispatcher()
                            .<BlockEntity, BlockEntityRenderState>getRenderer(entity);
                    if (entityRenderer != null) {
                        var renderState = entityRenderer.createRenderState();
                        entityRenderer.extractRenderState(entity, renderState, 0, camera.pos, null);
                        var capture = new SpecialModelCapture(geometry, pos, tint);
                        entityRenderer.submit(renderState, new PoseStack(), capture, camera);
                        capture.allPhases().forEach(phase -> {
                            if (!phase.isEmpty()) {
                                LOGGER.warn("Cannot preview unsupported block-entity geometry at {}: {}", pos, phase);
                            }
                        });
                    }
                }
            }
        }
    }

    private static final class SpecialModelCapture extends SubmitNodeCollection implements SubmitNodeCollector {
        private final Map<Identifier, List<Vertex>> geometry;
        private final BlockPos pos;
        private final int tint;

        private SpecialModelCapture(Map<Identifier, List<Vertex>> geometry, BlockPos pos, int tint) {
            this.geometry = geometry;
            this.pos = pos;
            this.tint = tint;
        }

        @Override
        public OrderedSubmitNodeCollector order(int order) {
            return this;
        }

        @Override
        public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
                int lightCoords, int overlayCoords, int tintedColor, TextureAtlasSprite sprite, int outlineColor,
                ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
            if (sprite == null) {
                LOGGER.warn("Cannot preview non-atlas block-entity model at {}: {}", pos, renderType);
                return;
            }
            var capture = new VertexCapture(pos, tint);
            model.setupAnim(state);
            model.renderToBuffer(poseStack, sprite.wrap(capture), lightCoords, overlayCoords, tintedColor);
            geometry.computeIfAbsent(sprite.atlasLocation(), key -> new ArrayList<>()).addAll(capture.finish());
        }
    }

    private record PreviewLevel(ClientLevel level, Map<BlockPos, BlockState> states,
            Map<BlockPos, BlockPos> originals) implements BlockAndTintGetter {
        private BlockPos original(BlockPos pos) { return originals.getOrDefault(pos, pos); }
        @Override public CardinalLighting cardinalLighting() { return level.cardinalLighting(); }
        @Override public LevelLightEngine getLightEngine() { return level.getLightEngine(); }
        @Override public int getBlockTint(BlockPos pos, ColorResolver color) { return level.getBlockTint(pos, color); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) {
            return states.containsKey(pos) ? level.getBlockEntity(original(pos)) : null;
        }
        @Override public BlockState getBlockState(BlockPos pos) {
            return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return level.getHeight(); }
        @Override public int getMinY() { return level.getMinY(); }
    }

    static final class VertexCapture implements VertexConsumer {
        private final BlockPos pos;
        private final int tint;
        private final List<Vertex> vertices = new ArrayList<>();
        private float x, y, z, u, v;
        private int color = -1;
        private boolean pending;

        VertexCapture(BlockPos pos, int tint) {
            this.pos = pos;
            this.tint = tint;
        }

        private void flush() {
            if (pending) {
                // Keep ghost surfaces just outside existing blocks to avoid z-fighting.
                vertices.add(new Vertex(pos.getX() + (x - 0.5) * 1.004 + 0.5,
                        pos.getY() + (y - 0.5) * 1.004 + 0.5,
                        pos.getZ() + (z - 0.5) * 1.004 + 0.5, u, v, ARGB.multiply(color, tint)));
                pending = false;
            }
        }

        List<Vertex> finish() {
            flush();
            if (vertices.size() % 4 != 0) {
                throw new IllegalStateException("Build preview model emitted incomplete quads");
            }
            return List.copyOf(vertices);
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            flush();
            this.x = x;
            this.y = y;
            this.z = z;
            pending = true;
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor(ARGB.color(a, r, g, b));
        }
        @Override public VertexConsumer setColor(int color) { this.color = color; return this; }
        @Override public VertexConsumer setUv(float u, float v) { this.u = u; this.v = v; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        Map<Identifier, List<Vertex>> geometry = event.getLevelRenderState().getRenderData(PREVIEW_GEOMETRY);
        if (geometry == null || geometry.isEmpty()) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        geometry.forEach((atlas, vertices) ->
                event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderResources.type(atlas),
                        (pose, buffer) -> emit(vertices, camera, pose, buffer)));
    }

    public static void emit(List<Vertex> vertices, Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer) {
        for (Vertex vertex : vertices) {
            buffer.addVertex(pose, (float) (vertex.x() - camera.x), (float) (vertex.y() - camera.y),
                            (float) (vertex.z() - camera.z)).setUv(vertex.u(), vertex.v())
                    .setColor(vertex.argb()).setLight(LightCoordsUtil.FULL_BRIGHT);
        }
    }
}
