package com.falazar.farmupcraft.saveddata;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class PollinationGameTests {
    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("pollination_budget"), PollinationGameTests::budget));
    }

    private static void budget(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos first = helper.absolutePos(BlockPos.ZERO).above(130);
        first = new BlockPos((first.getX() >> 4) * 16 + 4, first.getY(), (first.getZ() >> 4) * 16 + 4);
        BlockPos second = first.east();
        BlockPos otherChunk = first.east(16);
        CropBlock crop = (CropBlock) Blocks.WHEAT;
        var storage = level.getDataStorage();
        var previous = storage.computeIfAbsent(PollinationData.TYPE);
        var blank = PollinationData.CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonObject()).getOrThrow();
        storage.set(PollinationData.TYPE, blank);
        var positions = java.util.List.of(first, second, otherChunk);
        var originals = positions.stream().map(level::getBlockState).toList();
        var soils = positions.stream().map(pos -> level.getBlockState(pos.below())).toList();
        try {
            for (var pos : positions) {
                level.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState(), 2);
                level.setBlock(pos, crop.defaultBlockState(), 2);
            }
            PollinationData.butterfly(crop, level, first, level.getBlockState(first));
            helper.assertValueEqual(crop.getAge(level.getBlockState(first)), 1, "Butterfly adds exactly one stage");
            helper.assertTrue(PollinationData.grow(level, second, crop.getStateForAge(1)), "Bee uses second boost");
            PollinationData.butterfly(crop, level, first, level.getBlockState(first));
            helper.assertTrue(PollinationData.grow(level, second, crop.getStateForAge(2)), "Bee uses fourth boost");
            PollinationData.butterfly(crop, level, first, level.getBlockState(first));
            helper.assertValueEqual(crop.getAge(level.getBlockState(first)), 3, "Three butterfly boosts");
            for (int insect = 0; insect < 50; insect++) {
                PollinationData.butterfly(crop, level, second, level.getBlockState(second));
                helper.assertTrue(!PollinationData.grow(level, second, crop.getStateForAge(3)),
                        "Bee shares butterfly cooldown");
            }
            helper.assertValueEqual(crop.getAge(level.getBlockState(second)), 2, "50 insects cannot exceed five boosts");
            helper.assertTrue(PollinationData.grow(level, otherChunk, crop.getStateForAge(1)),
                    "Another chunk has an independent budget");

            var saved = PollinationData.CODEC.encodeStart(JsonOps.INSTANCE, storage.get(PollinationData.TYPE))
                    .getOrThrow().getAsJsonObject();
            storage.set(PollinationData.TYPE, PollinationData.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow());
            helper.assertTrue(!PollinationData.grow(level, second, crop.getStateForAge(3)),
                    "Serialized/reloaded cooldown remains blocked");
            String key = Long.toString(net.minecraft.world.level.ChunkPos.pack(first));
            var times = new com.google.gson.JsonArray();
            for (int i = 0; i < 5; i++) times.add(level.getGameTime() - 5999);
            saved.add(key, times);
            storage.set(PollinationData.TYPE, PollinationData.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow());
            helper.assertTrue(!PollinationData.grow(level, second, crop.getStateForAge(3)), "5999 ticks is too early");
            times = new com.google.gson.JsonArray();
            for (int i = 0; i < 5; i++) times.add(level.getGameTime() - 6000);
            saved.add(key, times);
            storage.set(PollinationData.TYPE, PollinationData.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow());
            level.setBlock(second, crop.defaultBlockState(), 2);
            for (int i = 1; i <= 5; i++) {
                helper.assertTrue(PollinationData.grow(level, second, crop.getStateForAge(i)), "6000 ticks permits five boosts");
            }
            helper.assertTrue(!PollinationData.grow(level, second, crop.getStateForAge(6)), "Five boosts spend budget");
            crop.growCrops(level, second, level.getBlockState(second));
            helper.assertTrue(crop.getAge(level.getBlockState(second)) == 7, "Ordinary bone meal is unaffected");

            try {
                for (String name : java.util.List.of(
                        "net.minecraft.world.entity.animal.bee.Bee$BeeGrowCropGoal",
                        "com.crispytwig.naturalist.world.entity.animal.butterfly.Butterfly$ButterflyGrowCropGoal")) {
                    if (name.startsWith("com.crispytwig.") && !net.neoforged.fml.ModList.get().isLoaded("naturalist")) {
                        continue;
                    }
                    var target = Class.forName(name);
                    helper.assertTrue(java.util.Arrays.stream(target.getDeclaredMethods())
                            .anyMatch(method -> method.getName().contains("farmupcraft$limitPollination")),
                            "Actual pollinator class has budget redirect: " + name);
                }
            } catch (ClassNotFoundException error) {
                throw new IllegalStateException("Loaded pollinator goal class is missing", error);
            }
        } finally {
            storage.set(PollinationData.TYPE, previous);
            for (int i = 0; i < positions.size(); i++) {
                level.setBlock(positions.get(i), originals.get(i), 2);
                level.setBlock(positions.get(i).below(), soils.get(i), 2);
            }
        }
        helper.succeed();
    }
}
