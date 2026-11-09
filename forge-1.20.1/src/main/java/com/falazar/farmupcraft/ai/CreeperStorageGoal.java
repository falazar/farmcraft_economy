package com.falazar.farmupcraft.ai;

import com.falazar.farmupcraft.FarmUpCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumSet;

@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID)
public final class CreeperStorageGoal extends Goal {
    public static final int SEARCH_RADIUS = 12;
    private final Creeper creeper;
    private BlockPos storage;
    private Path path;
    private long nextSearch;
    private long deadline;

    public CreeperStorageGoal(Creeper creeper) {
        this.creeper = creeper;
        nextSearch = creeper.level().getGameTime() + creeper.getRandom().nextInt(40);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof Creeper creeper)) return;
        if (creeper.goalSelector.getAvailableGoals().stream()
                .noneMatch(goal -> goal.getGoal() instanceof CreeperStorageGoal)) {
            // Own the fuse while targeting storage; vanilla SwellGoal otherwise defuses a creeper with no entity target.
            creeper.goalSelector.addGoal(1, new CreeperStorageGoal(creeper));
        }
    }

    public static boolean isStorage(BlockState state) {
        return state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL);
    }

    private boolean allowed() {
        return creeper.isAlive() && !creeper.isIgnited() && creeper.getTarget() == null
                && creeper.level() instanceof ServerLevel level
                && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    private boolean visible(BlockPos pos) {
        var hit = creeper.level().clip(new ClipContext(creeper.getEyePosition(), Vec3.atCenterOf(pos),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, creeper));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    @Override
    public boolean canUse() {
        long now = creeper.level().getGameTime();
        if (now < nextSearch) return false;
        nextSearch = now + 40;
        if (!allowed()) return false;
        var origin = creeper.blockPosition();
        double nearest = SEARCH_RADIUS * SEARCH_RADIUS + 1;
        storage = null;
        path = null;
        for (var pos : BlockPos.betweenClosed(origin.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS))) {
            double distance = origin.distSqr(pos);
            if (distance > SEARCH_RADIUS * SEARCH_RADIUS || distance >= nearest
                    || !creeper.level().hasChunkAt(pos) || !isStorage(creeper.level().getBlockState(pos))
                    || !visible(pos)) continue;
            var candidatePath = creeper.getNavigation().createPath(pos, 1);
            if (candidatePath == null || !candidatePath.canReach()) continue;
            storage = pos.immutable();
            path = candidatePath;
            nearest = distance;
        }
        return storage != null;
    }

    @Override
    public void start() {
        deadline = creeper.level().getGameTime() + 300;
        creeper.getNavigation().moveTo(path, 1.0);
    }

    @Override
    public boolean canContinueToUse() {
        return storage != null && allowed() && creeper.level().getGameTime() < deadline
                && creeper.level().hasChunkAt(storage) && isStorage(creeper.level().getBlockState(storage))
                && visible(storage)
                && (!creeper.getNavigation().isDone() || creeper.distanceToSqr(Vec3.atCenterOf(storage)) <= 4);
    }

    @Override
    public void tick() {
        if (!canContinueToUse()) {
            creeper.setSwellDir(-1);
            return;
        }
        creeper.getLookControl().setLookAt(storage.getX() + 0.5, storage.getY() + 0.5, storage.getZ() + 0.5);
        if (creeper.distanceToSqr(Vec3.atCenterOf(storage)) <= 4) {
            creeper.getNavigation().stop();
            creeper.setSwellDir(1);
        } else {
            creeper.setSwellDir(-1);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        creeper.getNavigation().stop();
        if (!creeper.isIgnited()) creeper.setSwellDir(-1);
        storage = null;
        path = null;
    }
}
