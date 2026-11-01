package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class SpawnerLightGameTests {
    private SpawnerLightGameTests() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("spawner_light"), SpawnerLightGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var floor = level.getBlockState(pos.below());
        var difficulty = level.getDifficulty();
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        // GameTest lighting is not reliably populated; delegate everything except measured light.
        var litLevel = ServerLevelAccessor.class.cast(java.lang.reflect.Proxy.newProxyInstance(
                ServerLevelAccessor.class.getClassLoader(), new Class<?>[]{ServerLevelAccessor.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getBrightness")) {
                        return args[0] == LightLayer.BLOCK ? 15 : 0;
                    }
                    if (method.getName().equals("getMaxLocalRawBrightness")) return 15;
                    try {
                        return method.invoke(level, args);
                    } catch (java.lang.reflect.InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                }));
        try {
            helper.assertTrue(litLevel.getBrightness(LightLayer.BLOCK, pos) == 15,
                    "Fixture must have maximum block light");
            level.getServer().setDifficulty(Difficulty.HARD, true);
            for (var type : java.util.List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.CREEPER)) {
                helper.assertTrue(SpawnPlacements.checkSpawnRules(type, litLevel, EntitySpawnReason.SPAWNER,
                        pos, level.getRandom()), "Cage spawner must permit monsters at light 15: " + type);
                helper.assertFalse(SpawnPlacements.checkSpawnRules(type, litLevel, EntitySpawnReason.NATURAL,
                        pos, level.getRandom()), "Natural monsters must still be blocked by light: " + type);
            }
            helper.assertTrue(EntitySpawnReason.ignoresLightRequirements(EntitySpawnReason.TRIAL_SPAWNER),
                    "Trial spawners must retain their light exemption");
            level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
            helper.assertFalse(SpawnPlacements.checkSpawnRules(EntityTypes.ZOMBIE, litLevel,
                    EntitySpawnReason.SPAWNER, pos, level.getRandom()),
                    "Peaceful difficulty must still block zombie spawners");
            helper.succeed();
        } finally {
            level.getServer().setDifficulty(difficulty, true);
            level.setBlock(pos.below(), floor, 3);
        }
    }
}
