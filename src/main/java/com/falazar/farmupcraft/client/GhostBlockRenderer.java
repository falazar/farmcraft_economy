package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.client.rendertype.FUCRenderType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;

import java.util.*;

public class GhostBlockRenderer {
    private static final ThreadLocal<ThreadLocalRenderContext> RENDER_CONTEXT = ThreadLocal.withInitial(ThreadLocalRenderContext::new);

    private final Map<BlockPos, GhostBlockData> ghostBlocks = new HashMap<>();
    private final Map<ChunkPos, Map<RenderType, VertexBuffer>> chunkBuffers = new HashMap<>();
    private final Set<ChunkPos> dirtyChunks = new HashSet<>();
    private float alpha = 0.6f;
    private int colorTint = 0xFFFFFF; // default: white

    private final RandomSource random = RandomSource.create();

    public void addGhostBlock(BlockPos pos, BlockState state, boolean blocked) {
        BlockPos immutablePos = pos.immutable();
        ghostBlocks.put(immutablePos, new GhostBlockData(state, blocked));

        ChunkPos chunk = new ChunkPos(pos);
        dirtyChunks.add(chunk);
    }

    // Overload for convenience
    public void addGhostBlock(BlockPos pos, BlockState state) {
        addGhostBlock(pos, state, false);
    }



    private RenderType sanitizeRenderLayer(RenderType original) {
        // Allow translucent blocks to be rendered properly
        if (original == RenderType.translucent()) {
            return original;
        }
        if(original == RenderType.cutout()) {
            return original;
        }

        if(original == RenderType.cutoutMipped()) {
            return original;
        }
        // Route all others through your ghost layer
        return FUCRenderType.getGhostBlock();
    }

    private Set<RenderType> getRenderLayers(BlockState state) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        ChunkRenderTypeSet typeSet = model.getRenderTypes(state, random, ModelData.EMPTY);
        return new HashSet<>(typeSet.asList());
    }


    public void clearGhostBlocks() {
        ghostBlocks.clear();
        dirtyChunks.addAll(chunkBuffers.keySet());
    }

    private static final int MAX_CHUNKS_PER_TICK = 4;

    public void tick() {
        if (dirtyChunks.isEmpty()) return;

        Iterator<ChunkPos> it = dirtyChunks.iterator();
        int count = 0;

        while (it.hasNext() && count < MAX_CHUNKS_PER_TICK) {
            ChunkPos chunk = it.next();
            rebuildChunk(chunk);
            it.remove();
            count++;
        }
    }



    private void rebuildChunk(ChunkPos chunkPos) {
        // Clean up old buffers
        Map<RenderType, VertexBuffer> old = chunkBuffers.remove(chunkPos);
        if (old != null) old.values().forEach(VertexBuffer::close);

        // Prepare new buffer map
        Map<RenderType, BufferBuilder> builders = new HashMap<>();

        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        ModelBlockRenderer renderer = dispatcher.getModelRenderer();
        ThreadLocalRenderContext ctx = RENDER_CONTEXT.get();
        PoseStack poseStack = ctx.poseStack;


        for (Map.Entry<BlockPos, GhostBlockData> entry : ghostBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!new ChunkPos(pos).equals(chunkPos)) continue;

            GhostBlockData data = entry.getValue();
            BlockState state = data.state();
            if (state.getRenderShape() != RenderShape.MODEL || data.blocked) continue;

            for (RenderType layer : getRenderLayers(state)) {
                RenderType filtered = sanitizeRenderLayer(layer);
                BufferBuilder bb = builders.computeIfAbsent(filtered, rt -> new BufferBuilder(16 * 1024));
                if (!bb.building()) bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);

                BakedModel model = dispatcher.getBlockModel(state);
                poseStack.pushPose();
                poseStack.translate(pos.getX(), pos.getY(), pos.getZ());

                renderer.tesselateBlock(
                        mc.level,
                        model,
                        state,
                        pos,
                        poseStack,
                        bb,
                        true,
                        RandomSource.create(state.getSeed(pos)),
                        state.getSeed(pos),
                        0xF000F0,
                        ModelData.EMPTY,
                        filtered
                );

                poseStack.popPose();
            }
        }

        Map<RenderType, VertexBuffer> result = new HashMap<>();
        for (Map.Entry<RenderType, BufferBuilder> entry : builders.entrySet()) {
            BufferBuilder.RenderedBuffer rendered = entry.getValue().endOrDiscardIfEmpty();
            if (rendered == null) continue;
            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vb.bind();
            vb.upload(rendered);
            VertexBuffer.unbind();
            result.put(entry.getKey(), vb);
        }

        if (!result.isEmpty()) {
            chunkBuffers.put(chunkPos, result);
        }
    }


    public void render(PoseStack poseStack, Vec3 camPos) {
        if (chunkBuffers.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        float r = ((colorTint >> 16) & 0xFF) / 255f;
        float g = ((colorTint >> 8) & 0xFF) / 255f;
        float b = (colorTint & 0xFF) / 255f;

        RenderSystem.setShaderColor(r, g, b, alpha);


        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.scale(1.0001F, 1.0001F, 1.0001F);
        poseStack.translate(-0.5, -0.5, -0.5);

        for (Map<RenderType, VertexBuffer> chunkLayerMap : chunkBuffers.values()) {
            for (Map.Entry<RenderType, VertexBuffer> entry : chunkLayerMap.entrySet()) {
                RenderType layer = entry.getKey();
                VertexBuffer vb = entry.getValue();

                layer.setupRenderState();
                vb.bind();
                vb.drawWithShader(
                        poseStack.last().pose(),
                        RenderSystem.getProjectionMatrix(),
                        GameRenderer.getPositionColorTexLightmapShader()
                );
                VertexBuffer.unbind();
                layer.clearRenderState();
            }
        }

        poseStack.popPose();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public void setColorTint(int rgb) {
        this.colorTint = rgb;
    }

    public void setColorTint(float r, float g, float b) {
        this.colorTint = ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }

    public void setColorTint(int r, int g, int b) {
        this.colorTint = (r << 16) | (g << 8) | b;
    }

    public void setAlpha(float alpha) {
        this.alpha = Mth.clamp(alpha, 0.0f, 1.0f);
    }


    public void clear() {
        dispose();
        ghostBlocks.clear();
        dirtyChunks.clear();
    }


    public void dispose() {
        chunkBuffers.values().forEach(map -> map.values().forEach(VertexBuffer::close));
        chunkBuffers.clear();
    }


    public record GhostBlockData(BlockState state, boolean blocked) {}

    public Collection<GhostBlockData> getGhostBlocks() {
        return ghostBlocks.values();
    }
    private static class ThreadLocalRenderContext {
        final PoseStack poseStack = new PoseStack();
        final BufferBuilder bufferBuilder = new BufferBuilder(512);
    }
}


