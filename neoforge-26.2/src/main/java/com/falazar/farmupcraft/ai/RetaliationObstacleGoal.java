package com.falazar.farmupcraft.ai;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class RetaliationObstacleGoal extends Goal {
    private final Mob mob;
    private UUID attacker;
    private boolean actionAvailable;
    private long nextAttempt;
    private Vec3 previousPosition;
    private BlockPos jumpBase;
    private long jumpDeadline;

    public RetaliationObstacleGoal(Mob mob) {
        this.mob = mob;
    }

    @SubscribeEvent
    public static void hurt(LivingDamageEvent.Post event) {
        if (event.getInflictedDamage() <= 0 || !(event.getEntity() instanceof Mob mob)
                || !(mob instanceof Enemy) || !(mob.level() instanceof ServerLevel)
                || !(event.getSource().getEntity() instanceof Player player)) return;
        var goal = mob.goalSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                .filter(RetaliationObstacleGoal.class::isInstance).map(RetaliationObstacleGoal.class::cast)
                .findFirst().orElseGet(() -> {
                    var added = new RetaliationObstacleGoal(mob);
                    mob.goalSelector.addGoal(1, added);
                    return added;
                });
        goal.rememberHit(player);
    }

    void rememberHit(Player player) {
        actionAvailable = true;
        if (!player.getUUID().equals(attacker)) {
            attacker = player.getUUID();
            jumpBase = null;
            nextAttempt = mob.level().getGameTime() + 40;
            previousPosition = mob.position();
        }
    }

    @Override
    public boolean canUse() {
        if (attacker == null || !actionAvailable) return false;
        if (mob.getTarget() != null && !attacker.equals(mob.getTarget().getUUID())) {
            attacker = null;
            return false;
        }
        return mob.getTarget() instanceof Player player && player.isAlive()
                && !player.isCreative() && !player.isSpectator() && mob.isAlive() && !mob.isNoAi();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        jumpBase = null;
    }

    @Override
    public void tick() {
        if (!canUse() || !(mob.level() instanceof ServerLevel level)) return;
        var player = (Player) mob.getTarget();
        if (!EventHooks.canEntityGrief(level, mob)) {
            jumpBase = null;
            return;
        }
        if (jumpBase != null) {
            finishPillar(level, player);
            return;
        }
        long now = level.getGameTime();
        if (now < nextAttempt) return;
        nextAttempt = now + 40;
        boolean stationary = previousPosition != null && mob.position().distanceToSqr(previousPosition) < 0.25;
        previousPosition = mob.position();
        if (!isBlocked(player, stationary) || !winsRoll(mob.getRandom().nextInt(100))) return;
        if (!startPillar(player)) breakObstacle(player);
    }

    public static boolean winsRoll(int roll) {
        if (roll < 0 || roll >= 100) throw new IllegalArgumentException("Expected a roll from 0 to 99");
        return roll < 20;
    }

    private boolean isBlocked(Player player, boolean stationary) {
        double dx = player.getX() - mob.getX(), dz = player.getZ() - mob.getZ();
        if (dx * dx + dz * dz > 256) return false;
        if (mob.distanceToSqr(player) < 4 && mob.hasLineOfSight(player)) return false;
        var path = mob.getNavigation().createPath(player, 0);
        return path == null || !path.canReach() || (stationary && mob.horizontalCollision);
    }

    boolean breakObstacle(Player player) {
        if (!(mob.level() instanceof ServerLevel level) || !canUse()
                || !EventHooks.canEntityGrief(level, mob)) return false;
        for (Vec3 start : new Vec3[]{mob.getEyePosition(), mob.position().add(0, 0.25, 0)}) {
            Vec3 end = player.position().add(0, Math.min(player.getBbHeight() / 2, start.y - mob.getY()), 0);
            Vec3 direction = end.subtract(start);
            if (direction.lengthSqr() > 6.25) end = start.add(direction.normalize().scale(2.5));
            boolean loaded = true;
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(start), BlockPos.containing(end))) {
                if (!level.hasChunkAt(pos)) {
                    loaded = false;
                    break;
                }
            }
            if (!loaded) continue;
            var hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            BlockPos pos = hit.getBlockPos();
            if (!level.hasChunkAt(pos) || mob.position().distanceToSqr(Vec3.atCenterOf(pos)) > 6.25) continue;
            var state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()
                    || level.getBlockEntity(pos) != null || state.getDestroySpeed(level, pos) < 0
                    || !CommonHooks.canEntityDestroy(level, pos, mob)) continue;
            if (level.destroyBlock(pos, true, mob)) {
                actionAvailable = false;
                mob.getNavigation().stop();
                return true;
            }
        }
        return false;
    }

    boolean startPillar(Player player) {
        if (!(mob.level() instanceof ServerLevel level) || !canUse() || !mob.onGround()
                || mob.isPassenger() || mob.isInLiquid() || !EventHooks.canEntityGrief(level, mob)) return false;
        double gap = player.getY() - mob.getY();
        double dx = player.getX() - mob.getX(), dz = player.getZ() - mob.getZ();
        BlockPos base = mob.blockPosition();
        if (gap < 1.5 || dx * dx + dz * dz > 6.25 || !level.hasChunkAt(base)
                || !level.getBlockState(base).isAir() || !level.getFluidState(base).isEmpty()
                || !level.noCollision(mob, mob.getBoundingBox().move(0, 1.05, 0))) return false;
        jumpBase = base;
        jumpDeadline = level.getGameTime() + 20;
        mob.getNavigation().stop();
        mob.getJumpControl().jump();
        return true;
    }

    void finishPillar(ServerLevel level, Player player) {
        BlockPos base = jumpBase;
        if (base == null) return;
        if (!canUse()) {
            jumpBase = null;
            return;
        }
        if (level.getGameTime() > jumpDeadline || player.getY() <= base.getY() + 1
                || mob.blockPosition().getX() != base.getX() || mob.blockPosition().getZ() != base.getZ()) {
            jumpBase = null;
            return;
        }
        if (mob.getY() < base.getY() + 1) return;
        jumpBase = null;
        if (!level.hasChunkAt(base) || !level.getBlockState(base).isAir()
                || !level.getFluidState(base).isEmpty() || !EventHooks.canEntityGrief(level, mob)
                || !level.isUnobstructed(Blocks.STONE.defaultBlockState(), base, net.minecraft.world.phys.shapes.CollisionContext.empty())) return;
        var before = BlockSnapshot.create(level.dimension(), level, base);
        if (!level.setBlock(base, Blocks.STONE.defaultBlockState(), 3)) return;
        if (EventHooks.onBlockPlace(mob, before, Direction.UP)) {
            level.setBlock(base, Blocks.AIR.defaultBlockState(), 3);
            return;
        }
        actionAvailable = false;
    }
}
