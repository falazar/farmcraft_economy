package com.falazar.farmupcraft.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

public class BuildableStructureInstance {

    private final BuildableStructure structure;
    private final BlockPos origin;
    private int progress = 0;
    private final Rotation rotation;

    public BuildableStructureInstance(BuildableStructure structure, BlockPos origin, Rotation rotation) {
        this.structure = structure;
        this.origin = origin;
        this.rotation = rotation;
    }

    public void tickBuild(ServerLevel level, int blocksPerTick) {
        structure.ensurePopulated(level);

        int built = 0;
        while (progress < structure.blockTasks.size() && built < blocksPerTick) {
            BuildableStructure.StructureBuildTask task = structure.blockTasks.get(progress);

            // Rotate relative position
            BlockPos rotatedRelPos = rotate(task.relativePos);
            BlockPos placePos = origin.offset(rotatedRelPos);

            // Rotate block state
            BlockState rotatedState = task.state.rotate(rotation);

            level.setBlock(placePos, rotatedState, 3);

            if (task.tag != null) {
                BlockEntity be = level.getBlockEntity(placePos);
                if (be != null) {
                    be.load(task.tag);
                }
            }

            progress++;
            built++;
        }

        if (progress >= structure.blockTasks.size()) {
            spawnEntities(level);
        }
    }

    public BlockPos rotate(BlockPos pos) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(structure.structureSize.getZ() - 1 - pos.getZ(), pos.getY(), pos.getX());
            case CLOCKWISE_180 -> new BlockPos(structure.structureSize.getX() - 1 - pos.getX(), pos.getY(), structure.structureSize.getZ() - 1 - pos.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(pos.getZ(), pos.getY(), structure.structureSize.getX() - 1 - pos.getX());
            default -> pos;
        };
    }



    public void spawnEntities(ServerLevel level) {
        for (BuildableStructure.StructureEntityTask entityTask : structure.entityTasks) {
            CompoundTag entityTag = entityTask.entityData.copy();
            entityTag.putDouble("PosX", origin.getX() + entityTask.pos.x());
            entityTag.putDouble("PosY", origin.getY() + entityTask.pos.y());
            entityTag.putDouble("PosZ", origin.getZ() + entityTask.pos.z());
            Optional<Entity> entityOptional = EntityType.create(entityTag, level);
            if(entityOptional.isEmpty()) return;
            Entity entity = entityOptional.get();
            if(entity instanceof Mob entity1) {
                entity1.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.getOnPos()), MobSpawnType.STRUCTURE, null, null);
            } else {
                level.addFreshEntity(entity);
            }
        }
    }

    public boolean isFinished() {
        return progress >= structure.blockTasks.size();
    }

    public BlockPos getOrigin() {
        return origin;
    }

    public int getProgress() {
        return progress;
    }

    public BuildableStructure getStructure() {
        return structure;
    }

    public Rotation getRotation() {
        return rotation;
    }
}

