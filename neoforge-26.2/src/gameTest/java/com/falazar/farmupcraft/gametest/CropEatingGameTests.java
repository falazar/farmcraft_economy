package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.ai.EatCropsGoal;
import com.falazar.farmupcraft.util.CropEatingAnimals;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.LinkedHashMap;
import java.util.Map;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class CropEatingGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> {
            functions.register(FarmUpCraftNeoForge.prefix("animal_crop_eating"), helper -> eating(helper, false));
            functions.register(FarmUpCraftNeoForge.prefix("animal_crop_eating_goat"), helper -> eating(helper, true));
            functions.register(FarmUpCraftNeoForge.prefix("animal_crop_eating_species"), CropEatingGameTests::species);
        });
    }

    private static void species(GameTestHelper helper) {
        var ids = new java.util.ArrayList<>(java.util.List.of("minecraft:rabbit", "minecraft:turtle",
                "minecraft:parrot", "minecraft:bee"));
        if (net.neoforged.fml.ModList.get().isLoaded("naturalist")) {
            ids.addAll(java.util.List.of("naturalist:duck", "naturalist:ostrich", "naturalist:elephant",
                    "naturalist:rat", "naturalist:butterfly"));
        }
        for (String id : ids) {
            var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id));
            helper.assertTrue(type != null, "Expected installed entity: " + id);
            var entity = helper.spawn(type, new BlockPos(1, 1, 1));
            try {
                if (entity instanceof net.minecraft.world.entity.animal.Animal animal) {
                    EatCropsGoal.onJoin(new EntityJoinLevelEvent(animal, helper.getLevel()));
                    helper.assertValueEqual(animal.goalSelector.getAvailableGoals().stream()
                                    .filter(wrapped -> wrapped.getGoal() instanceof EatCropsGoal).count(),
                            CropEatingAnimals.includes(id) ? 1L : 0L, "Correct crop goal eligibility: " + id);
                    helper.assertTrue(animal.goalSelector.getAvailableGoals().stream()
                                    .noneMatch(wrapped -> CropEatingAnimals.replacesGoal(wrapped.getGoal().getClass().getName())),
                            "Native crop-raiding goals must be replaced: " + id);
                } else {
                    helper.assertTrue(!CropEatingAnimals.includes(id), "Ground plant-eater must be an Animal: " + id);
                }
            } finally {
                entity.discard();
            }
        }
        var seeds = BuiltInRegistries.ITEM.getValue(Identifier.parse("pamhc2crops:kaleseeditem"));
        helper.assertTrue(seeds instanceof net.minecraft.world.item.BlockItem, "HarvestCraft seed fixture exists");
        helper.assertTrue(EatCropsGoal.isCrop(((net.minecraft.world.item.BlockItem) seeds).getBlock().defaultBlockState()),
                "HarvestCraft crops are edible at newly planted age");
        helper.succeed();
    }

    private static void eating(GameTestHelper helper, boolean goat) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(0, 1, 0)).above(goat ? 120 : 90);
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-10, -1, -10), origin.offset(15, 4, 10))) {
            previous.put(pos.immutable(), level.getBlockState(pos));
            level.setBlock(pos, pos.getY() == origin.getY() - 1
                    ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
        boolean oldRule = level.getGameRules().get(GameRules.MOB_GRIEFING);
        level.getGameRules().set(GameRules.MOB_GRIEFING, true, level.getServer());
        net.minecraft.world.entity.animal.Animal cow = goat
                ? helper.spawn(EntityTypes.GOAT, new BlockPos(0, 1, 0))
                : helper.spawn(EntityTypes.COW, new BlockPos(0, 1, 0));
        cow.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        cow.setNoAi(true);
        cow.setOnGround(true);
        EatCropsGoal.onJoin(new EntityJoinLevelEvent(cow, level));
        helper.assertValueEqual(cow.goalSelector.getAvailableGoals().stream()
                .filter(wrapped -> wrapped.getGoal() instanceof EatCropsGoal).count(), 1L,
                "Rejoining must not duplicate crop goals");
        var goal = (EatCropsGoal) cow.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal()).filter(EatCropsGoal.class::isInstance).findFirst().orElseThrow();
        var crop = origin.east(2);
        var wheat = ((CropBlock) Blocks.WHEAT).getStateForAge(7);
        level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), 3);
        level.setBlock(crop, wheat, 3);
        Runnable cleanup = () -> {
            cow.discard();
            previous.forEach((pos, state) -> level.setBlock(pos, state, 3));
            level.getGameRules().set(GameRules.MOB_GRIEFING, oldRule, level.getServer());
        };
        helper.runAfterDelay(110, () -> {
            try {
                helper.assertTrue(goal.canUse(), "Open crop must have a reachable path");
                goal.start();
                cow.setPos(origin.getX() + 1.2, origin.getY(), origin.getZ() + 0.5);
                level.setBlock(origin.east(), Blocks.OAK_FENCE.defaultBlockState(), 3);
                goal.tick();
                helper.assertTrue(level.getBlockState(crop).is(Blocks.WHEAT), "Cannot eat through a fence");
                level.setBlock(origin.east(), Blocks.AIR.defaultBlockState(), 3);
                level.getGameRules().set(GameRules.MOB_GRIEFING, false, level.getServer());
                goal.tick();
                helper.assertTrue(level.getBlockState(crop).is(Blocks.WHEAT), "Respect mobGriefing=false");
                level.getGameRules().set(GameRules.MOB_GRIEFING, true, level.getServer());
                goal.tick();
                helper.assertTrue(level.getBlockState(crop).isAir(), "Crop must be completely eaten");
                helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(crop).inflate(2)).isEmpty(),
                        "Eaten mature crop must not drop items");
                helper.assertValueEqual(cow.getPersistentData().getLongOr("farmupcraft_last_crop_meal", -1),
                        level.getGameTime(), "Successful meal persists world tick");
                goal.stop();
                level.setBlock(crop, wheat, 3);
                level.setBlock(crop, Blocks.AIR.defaultBlockState(), 3);
                var distant = origin.east(5);
                level.setBlock(distant.below(), Blocks.FARMLAND.defaultBlockState(), 3);
                level.setBlock(distant, wheat, 3);
            } catch (RuntimeException | AssertionError failure) {
                cleanup.run();
                throw failure;
            }
        });
        helper.runAfterDelay(220, () -> {
            try {
                cow.getPersistentData().putLong("farmupcraft_last_crop_meal", level.getGameTime() - 1199);
                helper.assertTrue(!goal.canUse(), "1199 ticks must not permit another meal");
            } catch (RuntimeException | AssertionError failure) {
                cleanup.run();
                throw failure;
            }
        });
        helper.runAfterDelay(330, () -> {
            try {
                cow.getPersistentData().putLong("farmupcraft_last_crop_meal", level.getGameTime() - 1200);
                helper.assertTrue(goal.canUse(), "1200 ticks must permit another meal");
                goal.start();
                cow.goalSelector.removeAllGoals(other -> !(other instanceof EatCropsGoal));
                helper.assertValueEqual(cow.goalSelector.getAvailableGoals().size(), 1, "Only crop goal remains");
                cow.setNoAi(false);
            } catch (RuntimeException | AssertionError failure) {
                cleanup.run();
                throw failure;
            }
        });
        helper.runAfterDelay(560, () -> {
            try {
                helper.assertTrue(level.getBlockState(origin.east(5)).isAir(),
                        "Real AI must walk to and eat a distant crop after cooldown; cow=" + cow.position()
                                + ", crop=" + origin.east(5) + ", navigationDone=" + cow.getNavigation().isDone()
                                + ", meal=" + cow.getPersistentData().getLongOr("farmupcraft_last_crop_meal", -1)
                                + ", path=" + cow.getNavigation().getPath()
                                + ", pathTarget=" + (cow.getNavigation().getPath() == null ? null
                                : cow.getNavigation().getPath().getTarget())
                                + ", goals=" + cow.goalSelector.getAvailableGoals().stream()
                                .map(w -> w.getGoal().getClass().getSimpleName() + ":" + w.isRunning()).toList()
                                + ", ticks=" + cow.tickCount + ", noAi=" + cow.isNoAi()
                                + ", cropState=" + level.getBlockState(origin.east(5))
                                + ", goalReady=" + goal.canUse()
                                + ", now=" + level.getGameTime());
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }
}
