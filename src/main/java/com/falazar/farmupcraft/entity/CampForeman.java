package com.falazar.farmupcraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CampForeman extends AbstractIllager {
    private List<StructureBuildTask> buildQueue = new ArrayList<>();
    private BlockPos buildOrigin = null;
    private int buildTimer = 0;
    private final int blocksPerTick = 3;

    public CampForeman(EntityType<? extends AbstractIllager> type, Level level) {
        super(type, level);
    }

    @Override
    public void applyRaidBuffs(int pWave, boolean pUnusedFalse) {

    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (level().isClientSide || buildQueue.isEmpty()) return;

        buildTimer++;
        if (buildTimer >= 20) { // every second
            buildTimer = 0;

            for (int i = 0; i < blocksPerTick && !buildQueue.isEmpty(); i++) {
                StructureBuildTask task = buildQueue.remove(0);
                BlockPos worldPos = buildOrigin.offset(task.relativePos);
                level().setBlock(worldPos, task.state, 3);
                level().levelEvent(2001, worldPos, Block.getId(task.state)); // particle
            }
        }
    }

    @Override
    public SoundEvent getCelebrateSound() {
        return null;
    }

    public void startBuilding(ServerLevel level, ResourceLocation structureId, BlockPos origin) {
        this.buildQueue = StructureStager.loadStructure(structureId, level);
        this.buildOrigin = origin;
    }

    public static class StructureBuildTask {
        public final BlockPos relativePos;
        public final BlockState state;
        @Nullable
        public final CompoundTag tag;

        public StructureBuildTask(BlockPos relPos, BlockState state, @Nullable CompoundTag tag) {
            this.relativePos = relPos;
            this.state = state;
            this.tag = tag;
        }
    }

    public static class StructureStager {
        public static List<StructureBuildTask> loadStructure(ResourceLocation id, ServerLevel level) {
            List<StructureBuildTask> blocks = new ArrayList<>();

            StructureTemplate template = level.getStructureManager().get(id).orElse(null);
            if (template == null) return blocks;

            List<StructureTemplate.StructureBlockInfo> infoList = ((StructureTemplateAccess)template).getStructurePalette().get(0).blocks();
            for (StructureTemplate.StructureBlockInfo info : infoList) {
                if (!info.state().isAir()) {
                    blocks.add(new StructureBuildTask(info.pos(), info.state(), info.nbt()));
                }
            }

            // Sort bottom-up for logical placement
            blocks.sort(Comparator.comparingInt(t -> t.relativePos.getY()));
            return blocks;
        }
    }
} 
