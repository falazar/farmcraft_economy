package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.TreeManager;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class ResourceDropGameTests {
    private ResourceDropGameTests() {
    }

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> {
            functions.register(FarmUpCraftNeoForge.prefix("mining_stone_drop_gate"),
                    ResourceDropGameTests::stoneDropGate);
            functions.register(FarmUpCraftNeoForge.prefix("tree_nursery_leaf_drops"),
                    ResourceDropGameTests::nurseryLeafDrops);
            functions.register(FarmUpCraftNeoForge.prefix("mangrove_planting"),
                    ResourceDropGameTests::mangrovePlanting);
            functions.register(FarmUpCraftNeoForge.prefix("sapling_planting_and_decay"),
                    ResourceDropGameTests::saplingPlantingAndDecay);
            functions.register(FarmUpCraftNeoForge.prefix("farm_harvest_failure"),
                    ResourceDropGameTests::farmHarvestFailure);
        });
    }

    private static void stoneDropGate(GameTestHelper helper) {
        try (var fixture = new ResourcePlotFixture(helper)) {
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            fixture.level.setBlock(fixture.pos, Blocks.STONE.defaultBlockState(), 2);
            fixture.player.experienceLevel = 0;
            var attempt = rejectedAttempt(fixture);
            helper.assertTrue(attempt.isCanceled(), "A failed low-experience mining roll must cancel normal loot");
            helper.assertTrue(attempt.shouldNotifyClient(), "Suppressed mining must request a client block update");
            helper.assertTrue(fixture.level.getBlockState(fixture.pos).isAir(),
                    "Failed mining must remove the actual stone, not leave a retryable block");
            helper.assertValueEqual(fixture.newDrops().size(), 0, "suppressed stone loot");
            helper.assertValueEqual(fixture.player.getMainHandItem().getDamageValue(), 0,
                    "canceled pre-break mining tool damage");

            fixture.level.setBlock(fixture.pos, Blocks.STONE.defaultBlockState(), 2);
            fixture.player.experienceLevel = 35;
            helper.assertTrue(fixture.player.gameMode.destroyBlock(fixture.pos),
                    "Level 35 must guarantee the ordinary survival mining path");
            helper.assertTrue(fixture.level.getBlockState(fixture.pos).isAir(), "Successful stone must be removed");
            helper.assertValueEqual(fixture.dropCount(Items.COBBLESTONE), 1, "actual mined stone loot");
            helper.assertValueEqual(fixture.player.getMainHandItem().getDamageValue(), 1,
                    "successful survival mining tool damage");

            UUID villageId = UUID.randomUUID();
            var villages = ModEvents.getVillageDatabase(fixture.level);
            var chunks = ModEvents.getChunkDataDatabase(fixture.level);
            var village = new com.falazar.farmupcraft.data.VillageData(villageId, "MiningVillage",
                    fixture.player.chunkPosition(), 6, java.util.List.of(fixture.player.chunkPosition()), true, 0,
                    "2000-01-01", "", "2000-01-01", "Founder", java.util.List.of(fixture.player.getUUID()));
            villages.putData(villageId, village);
            var plot = new ChunkData("plot", fixture.player.getId(), villageId, fixture.player.getUUID());
            plot.setBoughtY(fixture.pos.getY() + 1);
            chunks.putData(fixture.key, plot);
            try {
                fixture.newDrops().forEach(net.minecraft.world.entity.Entity::discard);
                fixture.level.setBlock(fixture.pos, Blocks.STONE.defaultBlockState(), 2);
                helper.assertTrue(fixture.player.gameMode.destroyBlock(fixture.pos), "Level 6 village plot break");
                helper.assertValueEqual(fixture.dropCount(Items.COBBLESTONE), 1,
                        "Level 6 village plot gives normal 1x stone drop");

                village.setLevel(7);
                villages.putData(villageId, village);
                fixture.newDrops().forEach(net.minecraft.world.entity.Entity::discard);
                fixture.level.setBlock(fixture.pos, Blocks.STONE.defaultBlockState(), 2);
                helper.assertTrue(fixture.player.gameMode.destroyBlock(fixture.pos), "Level 7 bought plot break");
                helper.assertValueEqual(fixture.dropCount(Items.COBBLESTONE), 2,
                        "Level 7 village bought plot gives 2x stone drop on success");

                plot.setType("village");
                chunks.putData(fixture.key, plot);
                fixture.newDrops().forEach(net.minecraft.world.entity.Entity::discard);
                fixture.level.setBlock(fixture.pos, Blocks.STONE.defaultBlockState(), 2);
                helper.assertTrue(fixture.player.gameMode.destroyBlock(fixture.pos), "Level 7 green village chunk break");
                helper.assertValueEqual(fixture.dropCount(Items.COBBLESTONE), 1,
                        "Unbought green village chunk does not get bought-plot 2x mining bonus");
            } finally {
                villages.removeDataBatch(java.util.List.of(villageId));
                chunks.removeDataBatch(java.util.List.of(fixture.key));
            }
        }
        helper.succeed();
    }

    private static void nurseryLeafDrops(GameTestHelper helper) {
        try (var fixture = new ResourcePlotFixture(helper)) {
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
            var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
            fixture.level.setBlock(fixture.pos, leaves, 2);
            var attempt = rejectedAttempt(fixture);
            helper.assertTrue(attempt.isCanceled(), "A failed wilderness leaf roll must suppress even sheared loot");
            helper.assertTrue(attempt.shouldNotifyClient(), "Suppressed leaves must request a client block update");
            helper.assertTrue(fixture.level.getBlockState(fixture.pos).isAir(),
                    "Suppressed leaves must really be removed");
            helper.assertValueEqual(fixture.newDrops().size(), 0, "suppressed leaf loot");
            helper.assertValueEqual(fixture.player.getMainHandItem().getDamageValue(), 0,
                    "suppressed leaf shears damage");

            var nursery = new ChunkData("nursery", -1, new UUID(0, 0));
            nursery.setPlotLevel(4);
            // Work below protection height so this test isolates nursery drop rules, not village membership.
            nursery.setBoughtY(fixture.pos.getY() + 1);
            ModEvents.getChunkDataDatabase(fixture.level).putData(fixture.key, nursery);
            fixture.level.setBlock(fixture.pos, leaves, 2);
            helper.assertTrue(fixture.player.gameMode.destroyBlock(fixture.pos),
                    "A level-four nursery must allow the normal shearing loot path");
            helper.assertTrue(fixture.level.getBlockState(fixture.pos).isAir(), "Sheared leaves must be removed");
            helper.assertValueEqual(fixture.dropCount(Items.OAK_LEAVES), 1, "actual nursery shearing loot");
            helper.assertValueEqual(fixture.player.getMainHandItem().getDamageValue(), 1,
                    "successful leaf shears damage");
        }
        helper.succeed();
    }

    private static BreakBlockEvent rejectedAttempt(ResourcePlotFixture fixture) {
        // The first roll is 60: both normal production rates (20% leaves and 30% stone) reject it.
        var event = new BreakBlockEvent(fixture.level, fixture.pos,
                fixture.level.getBlockState(fixture.pos), fixture.player);
        fixture.level.getRandom().setSeed(0L);
        return NeoForge.EVENT_BUS.post(event);
    }

    private static void mangrovePlanting(GameTestHelper helper) {
        long[] seeds = new long[100];
        boolean[] found = new boolean[100];
        int remaining = 100;
        for (long seed = 0; remaining > 0; seed++) {
            int roll = RandomSource.create(seed).nextInt(100);
            if (!found[roll]) {
                found[roll] = true;
                seeds[roll] = seed;
                remaining--;
            }
        }
        try (var fixture = new ResourcePlotFixture(helper)) {
            for (boolean nursery : new boolean[]{false, true}) {
                var plots = ModEvents.getChunkDataDatabase(fixture.level);
                if (nursery) {
                    plots.putData(fixture.key, new ChunkData("nursery", -1, new UUID(0, 0)));
                }
                for (var soil : new net.minecraft.world.level.block.Block[]{
                        Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.MUD, Blocks.CLAY}) {
                    fixture.level.setBlock(fixture.pos, soil.defaultBlockState(), 2);
                    for (int experience : new int[]{0, 20}) {
                        fixture.player.experienceLevel = experience;
                        int threshold = (nursery ? 65 : 10) + experience / 2;
                        int successes = 0;
                        for (int roll = 0; roll < 100; roll++) {
                            fixture.player.setItemInHand(InteractionHand.MAIN_HAND,
                                    new ItemStack(Items.MANGROVE_PROPAGULE, 2));
                            fixture.level.getRandom().setSeed(seeds[roll]);
                            var event = plantingAttempt(fixture);
                            TreeManager.onRightClickSaplingPlant(event);
                            boolean failure = roll >= threshold;
                            helper.assertTrue(event.isCanceled() == failure,
                                    soil + ": exact propagule roll " + roll + " versus " + threshold);
                            helper.assertValueEqual(fixture.player.getMainHandItem().getCount(),
                                    failure ? 1 : 2, "Only failures consume one propagule");
                            if (!event.isCanceled()) successes++;
                        }
                        helper.assertValueEqual(successes, threshold, "Exact success percentage");
                    }
                }
            }
            fixture.level.setBlock(fixture.pos, Blocks.DIRT.defaultBlockState(), 2);
            fixture.player.setGameMode(GameType.CREATIVE);
            fixture.player.experienceLevel = 0;
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MANGROVE_PROPAGULE, 2));
            fixture.level.getRandom().setSeed(seeds[99]);
            var creative = plantingAttempt(fixture);
            TreeManager.onRightClickSaplingPlant(creative);
            helper.assertTrue(!creative.isCanceled(), "Creative propagule planting remains exempt");
            helper.assertValueEqual(fixture.player.getMainHandItem().getCount(), 2, "Creative consumes nothing");
        }
        helper.succeed();
    }

    private static void saplingPlantingAndDecay(GameTestHelper helper) {
        long[] seeds = new long[100];
        boolean[] found = new boolean[100];
        int remaining = 100;
        for (long seed = 0; remaining > 0; seed++) {
            int roll = RandomSource.create(seed).nextInt(100);
            if (!found[roll]) {
                found[roll] = true;
                seeds[roll] = seed;
                remaining--;
            }
        }
        try (var fixture = new ResourcePlotFixture(helper)) {
            var above = fixture.pos.above();
            var previousAbove = fixture.level.getBlockState(above);
            try {
                var plots = ModEvents.getChunkDataDatabase(fixture.level);
                for (boolean nursery : new boolean[]{false, true}) {
                    if (nursery) plots.putData(fixture.key, new ChunkData("nursery", -1, new UUID(0, 0)));
                    fixture.level.setBlock(fixture.pos, Blocks.DIRT.defaultBlockState(), 2);
                    for (var hand : InteractionHand.values()) {
                        for (boolean replacePlant : new boolean[]{false, true}) {
                            fixture.level.setBlock(above,
                                    (replacePlant ? Blocks.SHORT_GRASS : Blocks.AIR).defaultBlockState(), 2);
                            var clicked = replacePlant ? above : fixture.pos;
                            for (int experience : new int[]{0, 20, 200}) {
                                fixture.player.experienceLevel = experience;
                                int threshold = Math.min(100, (nursery ? 65 : 10) + experience / 2);
                                int successes = 0;
                                for (int roll = 0; roll < 100; roll++) {
                                    fixture.player.setItemInHand(hand, new ItemStack(Items.OAK_SAPLING, 2));
                                    fixture.level.getRandom().setSeed(seeds[roll]);
                                    var event = new PlayerInteractEvent.RightClickBlock(fixture.player, hand, clicked,
                                            new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false));
                                    NeoForge.EVENT_BUS.post(event);
                                    boolean failure = roll >= threshold;
                                    helper.assertTrue(event.isCanceled() == failure,
                                            hand + " grass=" + replacePlant + " exact planting roll " + roll);
                                    helper.assertValueEqual(fixture.player.getItemInHand(hand).getCount(),
                                            failure ? 1 : 2, "Failed planting consumes exactly one sapling");
                                    if (!event.isCanceled()) successes++;
                                }
                                helper.assertValueEqual(successes, threshold, "Exact ordinary sapling success rate");
                            }
                        }
                    }
                }
                fixture.level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
                plots.removeDataBatch(java.util.List.of(fixture.key));
                for (var hand : InteractionHand.values()) {
                    for (int experience : new int[]{0, 200}) {
                        fixture.level.setBlock(above, Blocks.SHORT_GRASS.defaultBlockState(), 2);
                        fixture.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        fixture.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                        fixture.player.setItemInHand(hand, new ItemStack(Items.OAK_SAPLING, 2));
                        fixture.player.experienceLevel = experience;
                        fixture.level.getRandom().setSeed(seeds[99]);
                        fixture.player.gameMode.useItemOn(fixture.player, fixture.level,
                                fixture.player.getItemInHand(hand), hand,
                                new BlockHitResult(Vec3.atCenterOf(above), Direction.UP, above, false));
                        helper.assertTrue(fixture.level.getBlockState(above).is(
                                        experience == 0 ? Blocks.SHORT_GRASS : Blocks.OAK_SAPLING),
                                "Actual " + hand + " planting transaction obeys success/failure");
                        helper.assertValueEqual(fixture.player.getItemInHand(hand).getCount(), 1,
                                "Actual transaction consumes exactly one sapling, not two");
                    }
                }
                fixture.level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
                for (int plotLevel = 0; plotLevel <= 4; plotLevel++) {
                    if (plotLevel > 0) {
                        var plot = new ChunkData("nursery", -1, new UUID(0, 0));
                        plot.setPlotLevel(plotLevel);
                        plots.putData(fixture.key, plot);
                    }
                    int allowed = 0;
                    int threshold = plotLevel == 0 ? 20 : Math.min(100, plotLevel * 30);
                    for (int roll = 0; roll < 100; roll++) {
                        fixture.level.getRandom().setSeed(seeds[roll]);
                        boolean actual = TreeManager.allowSaplingDrop(fixture.pos, fixture.level);
                        helper.assertTrue(actual == (roll < threshold), "Exact leaf gate at level " + plotLevel);
                        if (actual) allowed++;
                    }
                    helper.assertValueEqual(allowed, threshold, "Exact leaf gate percentage");
                }
                plots.removeDataBatch(java.util.List.of(fixture.key));
                var leaves = Blocks.OAK_LEAVES.defaultBlockState()
                        .setValue(BlockStateProperties.PERSISTENT, false)
                        .setValue(BlockStateProperties.DISTANCE, 7);
                for (int i = 0; i < 1024; i++) {
                    fixture.level.setBlock(fixture.pos, leaves, 2);
                    fixture.level.getRandom().setSeed(seeds[20 + i % 80]);
                    leaves.randomTick(fixture.level, fixture.pos, fixture.level.getRandom());
                    helper.assertTrue(fixture.level.getBlockState(fixture.pos).isAir(),
                            "Rejected natural decay still removes leaves");
                }
                helper.assertValueEqual(fixture.newDrops().size(), 0, "Failed natural decay gates suppress real loot");
                var nursery = new ChunkData("nursery", -1, new UUID(0, 0));
                nursery.setPlotLevel(4);
                plots.putData(fixture.key, nursery);
                for (int i = 0; i < 1024; i++) {
                    fixture.level.setBlock(fixture.pos, leaves, 2);
                    leaves.randomTick(fixture.level, fixture.pos, fixture.level.getRandom());
                }
                helper.assertTrue(fixture.dropCount(Items.OAK_SAPLING) > 0,
                        "Guaranteed nursery gates retain real vanilla sapling loot during natural decay");
            } finally {
                fixture.level.setBlock(above, previousAbove, 2);
            }
        }
        helper.succeed();
    }

    private static void farmHarvestFailure(GameTestHelper helper) {
        long[] seeds = new long[100];
        boolean[] found = new boolean[100];
        int remaining = 100;
        for (long seed = 0; remaining > 0; seed++) {
            int roll = RandomSource.create(seed).nextInt(100);
            if (!found[roll]) {
                found[roll] = true;
                seeds[roll] = seed;
                remaining--;
            }
        }
        try (var fixture = new ResourcePlotFixture(helper)) {
            var below = fixture.pos.below();
            var previousBelow = fixture.level.getBlockState(below);
            try {
                fixture.level.setBlock(below, Blocks.FARMLAND.defaultBlockState(), 2);
                var pam = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                        net.minecraft.resources.Identifier.parse("pamhc2crops:pamonioncrop"));
                for (var block : java.util.List.of(Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS, pam)) {
                    var crop = (net.minecraft.world.level.block.CropBlock) block;
                    for (int farmLevel = 1; farmLevel <= 4; farmLevel++) {
                        var plot = new ChunkData("farm", -1, new UUID(0, 0));
                        plot.setPlotLevel(farmLevel);
                        plot.setBoughtY(fixture.pos.getY() + 1);
                        ModEvents.getChunkDataDatabase(fixture.level).putData(fixture.key, plot);
                        int expected = farmLevel == 1 ? 30 : farmLevel == 2 ? 20 : farmLevel == 3 ? 10 : 0;
                        for (boolean breaking : new boolean[]{false, true}) {
                            int failures = 0;
                            for (int roll = 0; roll < 100; roll++) {
                                fixture.level.setBlock(fixture.pos, crop.getStateForAge(crop.getMaxAge()), 2);
                                fixture.level.getRandom().setSeed(seeds[roll]);
                                boolean canceled;
                                if (breaking) {
                                    var event = new BreakBlockEvent(fixture.level, fixture.pos,
                                            fixture.level.getBlockState(fixture.pos), fixture.player);
                                    NeoForge.EVENT_BUS.post(event);
                                    canceled = event.isCanceled();
                                } else {
                                    var event = plantingAttempt(fixture);
                                    NeoForge.EVENT_BUS.post(event);
                                    canceled = event.isCanceled();
                                }
                                helper.assertTrue(canceled == (roll < expected),
                                        block + " level " + farmLevel + " breaking=" + breaking + " roll=" + roll);
                                helper.assertValueEqual(fixture.level.getBlockState(fixture.pos),
                                        crop.getStateForAge(canceled ? 0 : crop.getMaxAge()),
                                        "Failure resets growth without destroying the crop; success remains normal");
                                if (canceled) {
                                    failures++;
                                    helper.assertTrue(fixture.player.getInventory().isEmpty(),
                                            "Failure must not grant an inventory farm bonus");
                                }
                                fixture.player.getInventory().clearContent();
                            }
                            helper.assertValueEqual(failures, expected, "Exact harvest failure percentage");
                        }
                    }
                }
                helper.assertValueEqual(fixture.newDrops().size(), 0, "Failed events must not spawn crop/seed drops");
                var plot = new ChunkData("farm", -1, new UUID(0, 0));
                plot.setBoughtY(fixture.pos.getY() + 1);
                ModEvents.getChunkDataDatabase(fixture.level).putData(fixture.key, plot);
                var wheat = (net.minecraft.world.level.block.CropBlock) Blocks.WHEAT;
                fixture.level.setBlock(fixture.pos, wheat.getStateForAge(7), 2);
                fixture.level.getRandom().setSeed(seeds[0]);
                fixture.player.gameMode.useItemOn(fixture.player, fixture.level,
                        fixture.player.getMainHandItem(), InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(fixture.pos), Direction.UP, fixture.pos, false));
                helper.assertValueEqual(fixture.level.getBlockState(fixture.pos), wheat.getStateForAge(0),
                        "Actual failed right-click harvest keeps and resets the crop");
                helper.assertValueEqual(fixture.newDrops().size(), 0, "Actual failed right-click produces no loot");
                fixture.level.setBlock(fixture.pos, wheat.getStateForAge(3), 2);
                fixture.level.getRandom().setSeed(seeds[0]);
                helper.assertTrue(!NeoForge.EVENT_BUS.post(plantingAttempt(fixture)).isCanceled(),
                        "Immature crop interactions are not failed harvests");
                fixture.level.setBlock(fixture.pos, wheat.getStateForAge(7), 2);
                fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE_MEAL));
                fixture.level.getRandom().setSeed(seeds[0]);
                helper.assertTrue(!NeoForge.EVENT_BUS.post(plantingAttempt(fixture)).isCanceled(),
                        "Bone meal does not trigger a harvest failure");
                fixture.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                fixture.player.setGameMode(GameType.CREATIVE);
                fixture.level.getRandom().setSeed(seeds[0]);
                helper.assertTrue(!NeoForge.EVENT_BUS.post(plantingAttempt(fixture)).isCanceled(),
                        "Creative harvesting remains exempt");
                fixture.player.setGameMode(GameType.SURVIVAL);
                ModEvents.getChunkDataDatabase(fixture.level).putData(fixture.key,
                        new ChunkData("village", -1, new UUID(0, 0)));
                fixture.level.getRandom().setSeed(seeds[0]);
                helper.assertTrue(!NeoForge.EVENT_BUS.post(plantingAttempt(fixture)).isCanceled(),
                        "Non-farm crops retain their original harvest behavior");
                ModEvents.getChunkDataDatabase(fixture.level).putData(fixture.key, plot);
                fixture.level.setBlock(fixture.pos, wheat.getStateForAge(7), 2);
                fixture.level.getRandom().setSeed(seeds[0]);
                fixture.player.gameMode.destroyBlock(fixture.pos);
                helper.assertValueEqual(fixture.level.getBlockState(fixture.pos), wheat.getStateForAge(0),
                        "Actual breaking failure preserves a newly planted crop");
                helper.assertValueEqual(fixture.newDrops().size(), 0, "Actual failed breaking produces no loot");
                fixture.level.setBlock(fixture.pos, wheat.getStateForAge(7), 2);
                fixture.level.getRandom().setSeed(seeds[99]);
                helper.assertTrue(fixture.player.gameMode.destroyBlock(fixture.pos),
                        "Successful actual breaking still destroys the crop normally");
                helper.assertTrue(fixture.dropCount(Items.WHEAT) > 0, "Successful breaking retains original crop loot");
            } finally {
                fixture.level.setBlock(below, previousBelow, 2);
            }
        }
        helper.succeed();
    }

    private static PlayerInteractEvent.RightClickBlock plantingAttempt(ResourcePlotFixture fixture) {
        return new PlayerInteractEvent.RightClickBlock(fixture.player, InteractionHand.MAIN_HAND, fixture.pos,
                new BlockHitResult(Vec3.atCenterOf(fixture.pos), Direction.UP, fixture.pos, false));
    }
}
