package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.data.ChunkData;
import com.pam.pamhc2trees.PamFruitBlock;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class HarvestWithEaseGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("harvest_with_ease_failures"), HarvestWithEaseGameTests::verify));
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("harvest_with_ease_tree_fruit"), HarvestWithEaseGameTests::verifyTreeFruit));
    }

    private static void verifyTreeFruit(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("harvest_with_ease"), "Actual harvesting mod must be loaded");
        java.lang.reflect.Method isCrop;
        try {
            isCrop = Class.forName("it.crystalnest.harvest_with_ease.api.HarvestUtils")
                    .getMethod("isCrop", net.minecraft.world.level.block.Block.class);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot inspect loaded Harvest With Ease crop selection", error);
        }
        long[] seeds = new long[100];
        boolean[] found = new boolean[100];
        var random = RandomSource.create();
        int remaining = 100;
        for (long seed = 0; remaining > 0; seed++) {
            random.setSeed(seed);
            int roll = random.nextInt(100);
            if (!found[roll]) {
                found[roll] = true;
                seeds[roll] = seed;
                remaining--;
            }
        }
        try (var fixture = new ResourcePlotFixture(helper)) {
            var level = fixture.level;
            var above = fixture.pos.above();
            var previous = level.getBlockState(above);
            try {
                level.setBlock(above, Blocks.OAK_LEAVES.defaultBlockState(), 2);
                int fruitTypes = 0;
                for (var block : BuiltInRegistries.BLOCK) {
                    var id = BuiltInRegistries.BLOCK.getKey(block);
                    if (!id.getNamespace().equals("pamhc2trees") || !block.defaultBlockState().hasProperty(PamFruitBlock.AGE)) continue;
                    fruitTypes++;
                    try {
                        helper.assertTrue(Boolean.TRUE.equals(isCrop.invoke(null, block)),
                                "Installed HarvestCraft tree fruit extends CropBlock and is selected by Harvest With Ease: " + id);
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException("Cannot query tree harvesting selection for " + id, error);
                    }
                    var mature = block.defaultBlockState().setValue(PamFruitBlock.AGE, 7);
                    for (int nurseryLevel : new int[]{1, 4}) {
                        var plot = new ChunkData("nursery", -1, new UUID(0, 0));
                        plot.setPlotLevel(nurseryLevel);
                        plot.setBoughtY(fixture.pos.getY() + 1);
                        com.falazar.farmupcraft.events.ModEvents.getChunkDataDatabase(level).putData(fixture.key, plot);
                        for (int xp : new int[]{0, 20, 100}) {
                            fixture.player.experienceLevel = xp;
                            boolean orchard = level.getBiome(fixture.pos).unwrapKey().orElseThrow()
                                    .identifier().getPath().equals("orchard");
                            int successRate = Math.min(40 + xp + (orchard ? 20 : 0), 98);
                            int failures = 0;
                            for (int roll = 0; roll < 100; roll++) {
                                fixture.newDrops().forEach(net.minecraft.world.entity.item.ItemEntity::discard);
                                fixture.player.getInventory().clearContent();
                                level.setBlock(fixture.pos, mature, 2);
                                level.getRandom().setSeed(seeds[roll]);
                                fixture.player.gameMode.useItemOn(fixture.player, level,
                                        fixture.player.getMainHandItem(), InteractionHand.MAIN_HAND,
                                        new BlockHitResult(Vec3.atCenterOf(fixture.pos), Direction.UP, fixture.pos, false));
                                boolean failed = level.getBlockState(fixture.pos).isAir();
                                helper.assertTrue(failed == (roll >= successRate),
                                        id + " nursery=" + nurseryLevel + " xp=" + xp + " roll=" + roll);
                                if (failed) {
                                    failures++;
                                    helper.assertTrue(fixture.newDrops().isEmpty() && fixture.player.getInventory().isEmpty(),
                                            "Fruit failure removes the block and gives no fruit or nursery bonus: " + id);
                                } else {
                                    helper.assertTrue(!fixture.newDrops().isEmpty() || !fixture.player.getInventory().isEmpty(),
                                            "Successful native tree-fruit harvest must give fruit: " + id);
                                }
                            }
                            helper.assertValueEqual(failures, 100 - successRate, "Exact tree-fruit failure count for " + id);
                        }
                    }
                }
                helper.assertTrue(fruitTypes >= 50, "Exercise every registered mature HarvestCraft tree-fruit type");
            } finally {
                level.setBlock(above, previous, 2);
            }
        }
        helper.succeed();
    }

    private static void verify(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("harvest_with_ease"),
                "This integration test requires the actual Harvest With Ease jar");
        long[] seeds = new long[100];
        boolean[] found = new boolean[100];
        var random = RandomSource.create();
        int remaining = 100;
        for (long seed = 0; remaining > 0; seed++) {
            random.setSeed(seed);
            int roll = random.nextInt(100);
            if (!found[roll]) {
                found[roll] = true;
                seeds[roll] = seed;
                remaining--;
            }
        }
        try (var fixture = new ResourcePlotFixture(helper)) {
            var level = fixture.level;
            var soil = fixture.pos.below();
            var previous = level.getBlockState(soil);
            try {
                level.setBlock(soil, Blocks.FARMLAND.defaultBlockState(), 2);
                var pepper = BuiltInRegistries.BLOCK.getValue(Identifier.parse("pamhc2crops:pambellpeppercrop"));
                helper.assertTrue(pepper instanceof CropBlock, "Actual HarvestCraft pepper must be registered");
                for (var block : java.util.List.of(Blocks.CARROTS, pepper)) {
                    var crop = (CropBlock) block;
                    for (int farmLevel = 1; farmLevel <= 4; farmLevel++) {
                        var plot = new ChunkData("farm", -1, new UUID(0, 0));
                        plot.setPlotLevel(farmLevel);
                        plot.setBoughtY(fixture.pos.getY() + 1);
                        com.falazar.farmupcraft.events.ModEvents.getChunkDataDatabase(level).putData(fixture.key, plot);
                        int expected = farmLevel == 1 ? 30 : farmLevel == 2 ? 20 : farmLevel == 3 ? 10 : 0;
                        int failures = 0;
                        for (int roll = 0; roll < 100; roll++) {
                            fixture.newDrops().forEach(net.minecraft.world.entity.item.ItemEntity::discard);
                            fixture.player.getInventory().clearContent();
                            level.setBlock(fixture.pos, crop.getStateForAge(crop.getMaxAge()), 2);
                            level.getRandom().setSeed(seeds[roll]);
                            fixture.player.gameMode.useItemOn(fixture.player, level,
                                    fixture.player.getMainHandItem(), InteractionHand.MAIN_HAND,
                                    new BlockHitResult(Vec3.atCenterOf(fixture.pos), Direction.UP, fixture.pos, false));
                            helper.assertValueEqual(level.getBlockState(fixture.pos), crop.getStateForAge(0),
                                    "Real right-click must keep and reset the harvested crop");
                            boolean noLoot = fixture.newDrops().isEmpty() && fixture.player.getInventory().isEmpty();
                            helper.assertTrue(noLoot == (roll < expected),
                                    block + " level=" + farmLevel + " roll=" + roll
                                            + ": failure must give nothing, success must retain harvesting loot");
                            if (noLoot) failures++;
                        }
                        helper.assertValueEqual(failures, expected, "Exact failure count with Harvest With Ease loaded");
                    }
                }
            } finally {
                level.setBlock(soil, previous, 2);
            }
        }
        helper.succeed();
    }
}
