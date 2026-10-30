package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
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
}
