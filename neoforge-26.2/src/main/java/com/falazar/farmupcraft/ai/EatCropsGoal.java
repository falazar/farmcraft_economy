package com.falazar.farmupcraft.ai;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.CropEatingAnimals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.EnumSet;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class EatCropsGoal extends Goal {
    private static final String LAST_MEAL = "farmupcraft_last_crop_meal";
    private static final long COOLDOWN = 1200;
    private final Animal animal;
    private BlockPos crop;
    private Path path;
    private long nextSearch;
    private long deadline;

    public EatCropsGoal(Animal animal) {
        this.animal = animal;
        nextSearch = animal.level().getGameTime() + animal.getRandom().nextInt(100);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof Animal animal)
                || !CropEatingAnimals.includes(BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()).toString())) return;
        animal.goalSelector.removeAllGoals(goal -> CropEatingAnimals.replacesGoal(goal.getClass().getName()));
        if (animal.goalSelector.getAvailableGoals().stream().noneMatch(goal -> goal.getGoal() instanceof EatCropsGoal)) {
            animal.goalSelector.addGoal(4, new EatCropsGoal(animal));
        }
    }

    public static boolean isCrop(BlockState state) {
        return state.getBlock() instanceof CropBlock || state.is(BlockTags.CROPS);
    }

    private boolean allowed() {
        if (!(animal.level() instanceof ServerLevel level) || animal.isPassenger() || animal.isVehicle()
                || animal.getTarget() != null || animal.isInLove() || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return false;
        }
        var data = animal.getPersistentData();
        if (!data.contains(LAST_MEAL)) return true;
        long last = data.getLongOr(LAST_MEAL, 0);
        long now = level.getGameTime();
        return now >= last && now - last >= COOLDOWN;
    }

    @Override
    public boolean canUse() {
        long now = animal.level().getGameTime();
        if (now < nextSearch) return false;
        nextSearch = now + 100;
        if (!allowed()) return false;
        crop = null;
        path = null;
        double nearest = Double.MAX_VALUE;
        BlockPos origin = animal.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -2, -8), origin.offset(8, 2, 8))) {
            double distance = origin.distSqr(pos);
            if (distance > 64 || distance >= nearest || !animal.level().hasChunkAt(pos)
                    || !isCrop(animal.level().getBlockState(pos))) continue;
            Path candidate = animal.getNavigation().createPath(pos.immutable(), 0);
            if (candidate == null || !candidate.canReach()) continue;
            crop = pos.immutable();
            path = candidate;
            nearest = distance;
        }
        return crop != null;
    }

    @Override
    public void start() {
        deadline = animal.level().getGameTime() + 200;
        directBrainToCrop();
        animal.getNavigation().moveTo(path, 1.0);
    }

    private void directBrainToCrop() {
        // Brain-driven animals otherwise overwrite goal navigation with idle wandering.
        if (crop != null && animal.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
            animal.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(crop, 1.0F, 0));
        }
    }

    @Override
    public boolean canContinueToUse() {
        return crop != null && allowed() && animal.level().getGameTime() < deadline
                && animal.level().hasChunkAt(crop) && isCrop(animal.level().getBlockState(crop))
                && (!animal.getNavigation().isDone() || withinReach());
    }

    private boolean withinReach() {
        if (crop == null || animal.distanceToSqr(Vec3.atBottomCenterOf(crop)) > 2.25) return false;
        // A low bite ray prevents tall animals from eating over a fence.
        Vec3 mouth = animal.position().add(0, 0.5, 0);
        var hit = animal.level().clip(new ClipContext(mouth, Vec3.atCenterOf(crop),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, animal));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(crop);
    }

    @Override
    public void tick() {
        if (crop == null || !allowed() || !animal.level().hasChunkAt(crop)) return;
        directBrainToCrop();
        animal.getLookControl().setLookAt(crop.getX() + 0.5, crop.getY() + 0.5, crop.getZ() + 0.5);
        if (withinReach() && animal.level() instanceof ServerLevel level && isCrop(level.getBlockState(crop))
                && CommonHooks.canEntityDestroy(level, crop, animal) && level.destroyBlock(crop, false, animal)) {
            animal.getPersistentData().putLong(LAST_MEAL, level.getGameTime());
        }
    }

    @Override
    public void stop() {
        animal.getNavigation().stop();
        if (crop != null && animal.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
            animal.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                    .filter(target -> crop.equals(target.getTarget().currentBlockPosition()))
                    .ifPresent(target -> animal.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET));
        }
        crop = null;
        path = null;
    }
}
