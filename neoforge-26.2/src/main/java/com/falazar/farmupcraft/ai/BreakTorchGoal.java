package com.falazar.farmupcraft.ai;

import com.falazar.farmupcraft.data.WorldData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.EnumSet;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class BreakTorchGoal extends Goal {
    private final Mob mob;
    private BlockPos torch;
    private Path path;
    private long nextSearch;
    private long deadline;

    public BreakTorchGoal(Mob mob) {
        this.mob = mob;
        nextSearch = mob.level().getGameTime() + mob.getRandom().nextInt(100);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof Mob mob)
                || (mob.getType() != EntityTypes.ZOMBIE && mob.getType() != EntityTypes.SKELETON)) return;
        if (mob.goalSelector.getAvailableGoals().stream().noneMatch(goal -> goal.getGoal() instanceof BreakTorchGoal)) {
            mob.goalSelector.addGoal(5, new BreakTorchGoal(mob));
        }
    }

    public static int searchRadius(int roll) {
        if (roll < 0 || roll >= 100) throw new IllegalArgumentException("Expected a roll from 0 to 99");
        return roll == 0 ? 16 : 6;
    }

    public static boolean isTorch(BlockState state) {
        return state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH);
    }

    private boolean allowed() {
        return mob.level() instanceof ServerLevel level && mob.getTarget() == null
                && level.getGameRules().get(GameRules.MOB_GRIEFING)
                && ModEvents.getWorldDataDatabase(level).getOrCreate(0, WorldData::new).isMobTorchBreaking();
    }

    @Override
    public boolean canUse() {
        long now = mob.level().getGameTime();
        if (now < nextSearch) return false;
        nextSearch = now + 100;
        if (!allowed()) return false;
        int radius = searchRadius(mob.getRandom().nextInt(100));
        BlockPos origin = mob.blockPosition();
        double nearest = Double.MAX_VALUE;
        torch = null;
        // Inspect only loaded blocks; distant targets never force chunk loading.
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius))) {
            double distance = origin.distSqr(pos);
            if (distance > radius * radius || distance >= nearest || !mob.level().hasChunkAt(pos)
                    || !isTorch(mob.level().getBlockState(pos)) || !visible(pos)) continue;
            torch = pos.immutable();
            nearest = distance;
        }
        if (torch == null) return false;
        path = mob.getNavigation().createPath(torch, 1);
        return path != null && path.canReach();
    }

    private boolean visible(BlockPos pos) {
        var hit = mob.level().clip(new ClipContext(mob.getEyePosition(), Vec3.atCenterOf(pos),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    @Override
    public void start() {
        deadline = mob.level().getGameTime() + 200;
        mob.getNavigation().moveTo(path, 1.0);
    }

    @Override
    public boolean canContinueToUse() {
        return torch != null && allowed() && mob.level().getGameTime() < deadline
                && mob.level().hasChunkAt(torch) && isTorch(mob.level().getBlockState(torch))
                && (!mob.getNavigation().isDone() || mob.distanceToSqr(Vec3.atCenterOf(torch)) <= 4);
    }

    @Override
    public void tick() {
        if (torch == null || !allowed()) return;
        mob.getLookControl().setLookAt(torch.getX() + 0.5, torch.getY() + 0.5, torch.getZ() + 0.5);
        if (mob.distanceToSqr(Vec3.atCenterOf(torch)) <= 4 && visible(torch)
                && mob.level() instanceof ServerLevel level && isTorch(level.getBlockState(torch))
                && CommonHooks.canEntityDestroy(level, torch, mob)) {
            level.destroyBlock(torch, true, mob);
            torch = null;
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        torch = null;
        path = null;
    }
}
