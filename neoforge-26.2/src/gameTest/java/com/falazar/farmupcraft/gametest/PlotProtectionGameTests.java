package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Locale;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class PlotProtectionGameTests {
    private PlotProtectionGameTests() {
    }

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> {
            functions.register(FarmUpCraftNeoForge.prefix("plot_house_owner_break"),
                    PlotProtectionGameTests::houseOwnerBreak);
            functions.register(FarmUpCraftNeoForge.prefix("plot_house_visitor_access"),
                    PlotProtectionGameTests::houseVisitorAccess);
        });
    }

    private static void houseOwnerBreak(GameTestHelper helper) {
        try (var fixture = new ResourcePlotFixture(helper)) {
            var player = fixture.player;
            UUID village = UUID.randomUUID();
            UUID owner = UUID.randomUUID();
            var plot = new ChunkData("house", player.getId(), village, owner);
            plot.setBoughtY(fixture.pos.getY());
            ModEvents.getChunkDataDatabase(fixture.level).putData(fixture.key, plot);
            ModEvents.getPlayerDatabase(fixture.level).putData(player.getUUID(),
                    new PlayerData(player.getId(), player.getUUID(), village, 37));
            fixture.level.setBlock(fixture.pos, Blocks.WOOL.white().defaultBlockState(), 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));

            helper.assertTrue(!player.gameMode.destroyBlock(fixture.pos),
                    "A village member with a matching legacy entity ID must not override a different UUID owner");
            helper.assertTrue(fixture.level.getBlockState(fixture.pos).is(Blocks.WOOL.white()),
                    "Denied breaking must leave the actual house block intact");
            helper.assertValueEqual(fixture.newDrops().size(), 0, "denied house break drops");
            helper.assertValueEqual(player.getMainHandItem().getDamageValue(), 0, "denied break tool damage");
            helper.assertValueEqual(plot.getOwnerUUID(), owner, "owner UUID after denied legacy-ID access");

            plot.setOwnerUUID(player.getUUID());
            ModEvents.getChunkDataDatabase(fixture.level).putData(fixture.key, plot);
            helper.assertTrue(player.gameMode.destroyBlock(fixture.pos), "The UUID owner must be able to break");
            helper.assertTrue(fixture.level.getBlockState(fixture.pos).isAir(), "The owner's block must be removed");
            helper.assertValueEqual(fixture.dropCount(Items.WOOL.white()), 1, "actual owner block loot");
            helper.assertValueEqual(ModEvents.getPlayerDatabase(fixture.level).getData(player.getUUID()).getCoins(),
                    37, "plot protection must not change the player's coins");
        }
        helper.succeed();
    }

    private static void houseVisitorAccess(GameTestHelper helper) {
        try (var fixture = new ResourcePlotFixture(helper)) {
            var player = fixture.player;
            UUID village = UUID.randomUUID();
            UUID owner = UUID.randomUUID();
            var plot = new ChunkData("house", -1, village, owner);
            plot.setBoughtY(fixture.pos.getY());
            var plots = ModEvents.getChunkDataDatabase(fixture.level);
            var players = ModEvents.getPlayerDatabase(fixture.level);
            plots.putData(fixture.key, plot);
            players.putData(player.getUUID(), new PlayerData(player.getId(), player.getUUID(), village, 37));
            fixture.level.setBlock(fixture.pos, Blocks.OAK_TRAPDOOR.defaultBlockState(), 2);

            useTrapdoor(fixture);
            assertOpen(helper, fixture, false, "An uninvited village member must not open the house trapdoor");
            plot.addVisitor("  " + player.getName().getString().toUpperCase(Locale.ROOT) + "  ");
            plots.putData(fixture.key, plot);
            useTrapdoor(fixture);
            assertOpen(helper, fixture, true, "An invited village member must open the real trapdoor");

            plot.removeVisitor(player.getName().getString());
            plots.putData(fixture.key, plot);
            useTrapdoor(fixture);
            assertOpen(helper, fixture, true, "Revocation must immediately prevent closing the trapdoor");

            plot.addVisitor(player.getName().getString());
            plots.putData(fixture.key, plot);
            players.putData(player.getUUID(),
                    new PlayerData(player.getId(), player.getUUID(), UUID.randomUUID(), 37));
            useTrapdoor(fixture);
            assertOpen(helper, fixture, true, "An invitation must not bypass village membership protection");
            helper.assertTrue(!player.gameMode.destroyBlock(fixture.pos),
                    "A visitor from another village must also be denied block breaking");
            assertOpen(helper, fixture, true, "Denied breaking must preserve the trapdoor and its open state");
            helper.assertValueEqual(fixture.newDrops().size(), 0, "denied visitor interaction drops");
            helper.assertTrue(player.getMainHandItem().isEmpty(), "Empty-handed access must not create items");
            helper.assertValueEqual(plot.getOwnerUUID(), owner, "visitor interactions must preserve ownership");
        }
        helper.succeed();
    }

    private static void useTrapdoor(ResourcePlotFixture fixture) {
        fixture.player.gameMode.useItemOn(fixture.player, fixture.level, fixture.player.getMainHandItem(),
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(fixture.pos), Direction.UP, fixture.pos, false));
    }

    private static void assertOpen(GameTestHelper helper, ResourcePlotFixture fixture, boolean open, String message) {
        var state = fixture.level.getBlockState(fixture.pos);
        helper.assertTrue(state.is(Blocks.OAK_TRAPDOOR)
                && state.getValue(BlockStateProperties.OPEN) == open, message);
    }
}
