package com.falazar.farmupcraft.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Map;

/** Read-only block overlay for attachment survival checks before a build move. */
public record PlannedBlockView(ServerLevel level, Map<BlockPos, BlockState> blocks) implements LevelReader {
    @Override public BlockState getBlockState(BlockPos pos) {
        return blocks.getOrDefault(pos, level.getBlockState(pos));
    }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) {
        return blocks.containsKey(pos) ? null : level.getBlockEntity(pos);
    }
    @Override public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean load) {
        return level.getChunk(x, z, status, load);
    }
    @Override public boolean hasChunk(int x, int z) { return level.hasChunk(x, z); }
    @Override public int getHeight(Heightmap.Types type, int x, int z) { return level.getHeight(type, x, z); }
    @Override public int getSkyDarken() { return level.getSkyDarken(); }
    @Override public BiomeManager getBiomeManager() { return level.getBiomeManager(); }
    @Override public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return level.getUncachedNoiseBiome(x, y, z);
    }
    @Override public boolean isClientSide() { return level.isClientSide(); }
    @Override public int getSeaLevel() { return level.getSeaLevel(); }
    @Override public DimensionType dimensionType() { return level.dimensionType(); }
    @Override public RegistryAccess registryAccess() { return level.registryAccess(); }
    @Override public FeatureFlagSet enabledFeatures() { return level.enabledFeatures(); }
    @Override public EnvironmentAttributeReader environmentAttributes() { return level.environmentAttributes(); }
    @Override public LevelLightEngine getLightEngine() { return level.getLightEngine(); }
    @Override public WorldBorder getWorldBorder() { return level.getWorldBorder(); }
    @Override public BlockGetter getChunkForCollisions(int x, int z) { return this; }
    @Override public List<VoxelShape> getEntityCollisions(Entity entity, AABB bounds) {
        return level.getEntityCollisions(entity, bounds);
    }
}
