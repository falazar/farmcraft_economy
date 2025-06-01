package com.falazar.farmupcraft.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class BuildStructureGoal extends Goal {

    private final PathfinderMob mob;
    private final BuildableStructureInstance instance;
    private final int blocksPerTick;
    private final BlockPos origin;

    private int progress = 0;

    public BuildStructureGoal(PathfinderMob mob, BuildableStructureInstance instance, BlockPos origin, int blocksPerTick) {
        this.mob = mob;
        this.instance = instance;
        this.origin = origin;
        this.blocksPerTick = blocksPerTick;
    }

    @Override
    public boolean canUse() {
        return !instance.isFinished();
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level)) return;

        int built = 0;
        var structure = instance.getStructure();
        while (progress < structure.blockTasks.size() && built < blocksPerTick) {
            var task = structure.blockTasks.get(progress);
            BlockPos rotatedPos = instance.rotate(task.relativePos);
            BlockPos targetPos = origin.offset(rotatedPos);

            // Skip blocks outside bounding box
            if (!structure.getBoundingBox(origin).isInside(targetPos)) {
                progress++;
                continue;
            }

            // Check if block already exists or is obstructed
            if (!level.getBlockState(targetPos).isAir()) {
                progress++;
                continue;
            }

            // Check if mob is close enough
            if (!canReachBlock(targetPos)) {
                // Try placing scaffold under or near
                tryPlaceScaffold(level, targetPos.below());
                mob.getNavigation().moveTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), 1.0);
                return;
            }

            // Place block
            level.setBlock(targetPos, task.state, 3);
            if (task.tag != null) {
                BlockEntity be = level.getBlockEntity(targetPos);
                if (be != null) be.load(task.tag);
            }

            // Visual: held item changes
            mob.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(task.state.getBlock().asItem()));

            built++;
            progress++;
        }

        if (instance.isFinished()) {
            instance.spawnEntities(level);
        }
    }

    private boolean canReachBlock(BlockPos pos) {
        double distSq = mob.distanceToSqr(Vec3.atCenterOf(pos));
        return distSq < 9.0 && Math.abs(pos.getY() - mob.blockPosition().getY()) <= 3;
    }

    private void tryPlaceScaffold(ServerLevel level, BlockPos scaffoldPos) {
        if (level.getBlockState(scaffoldPos).isAir() && level.getBlockState(scaffoldPos.below()).isSolidRender(level, scaffoldPos.below())) {
            level.setBlock(scaffoldPos, Blocks.SCAFFOLDING.defaultBlockState(), 3);
        }
    }

    private boolean canBuildHere(ServerLevel level, BlockPos pos, BlockState targetState) {
        BlockState existing = level.getBlockState(pos);

        // Already built
        if (existing.is(targetState.getBlock())) return false;

        // Break if non-air and not replaceable
        if (!existing.isAir()) {
            if (existing.getDestroySpeed(level, pos) < 0) return false; // unbreakable
            level.destroyBlock(pos, true); // drop item
        }

        return true;
    }

    private boolean ensureAccess(ServerLevel level, BlockPos pos) {
        if (canReachBlock(pos)) return true;

        BlockPos scaffoldBelow = pos.below();
        if (level.getBlockState(scaffoldBelow).isAir() && level.getBlockState(scaffoldBelow.below()).isSolidRender(level, scaffoldBelow.below())) {
            level.setBlock(scaffoldBelow, Blocks.SCAFFOLDING.defaultBlockState(), 3);
            return false; // retry next tick
        }

        mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
        return false;
    }

    private boolean dependenciesMet(BlockPos targetPos) {
        // For now: simple Y check
        return mob.level().getBlockState(targetPos.below()).isSolidRender(mob.level(), targetPos.below());
    }


    @Override
    public boolean canContinueToUse() {
        return !instance.isFinished();
    }
}
