package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.data.BiomeRulesData;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.falazar.farmupcraft.saveddata.BiomeRulesManager;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.ArrayList;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class HarvestCraftPlantingGameTests {
    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("harvestcraft_kale_planting"), HarvestCraftPlantingGameTests::plant));
    }

    private static void plant(GameTestHelper helper) {
        try (var plot = new ResourcePlotFixture(helper)) {
            var world = ModEvents.getWorldData(plot.level);
            boolean previousRules = world.isUseBiomeCropRules();
            var manager = BiomeRulesManager.get(plot.level);
            var biome = plot.level.getBiome(plot.pos);
            var previous = manager.getBiomeRules(biome);
            var above = plot.level.getBlockState(plot.pos.above());
            var oldConnection = plot.player.connection;
            List<Packet<?>> packets = new ArrayList<>();
            plot.player.connection = new ServerGamePacketListenerImpl(plot.level.getServer(),
                    new Connection(PacketFlow.SERVERBOUND), plot.player,
                    CommonListenerCookie.createInitial(plot.player.getGameProfile(), false)) {
                @Override
                public void send(Packet<?> packet) {
                    packets.add(packet);
                }
            };
            plot.player.initInventoryMenu();
            try {
                var produce = BuiltInRegistries.ITEM.getValue(Identifier.parse("pamhc2crops:kaleitem"));
                var seed = BuiltInRegistries.ITEM.getValue(Identifier.parse("pamhc2crops:kaleseeditem"));
                helper.assertTrue(produce != Items.AIR && seed != Items.AIR, "Real kale items are loaded");
                helper.assertTrue(!(produce instanceof BlockItem), "Kale food is not registered as plantable");
                helper.assertTrue(seed instanceof BlockItem, "Kale seed is registered as plantable");
                var village = plot.player.getUUID();
                ModEvents.getPlayerDatabase(plot.level).putData(village,
                        new PlayerData(plot.player.getId(), village, village, 0));
                ModEvents.getChunkDataDatabase(plot.level).putData(plot.key,
                        new ChunkData("farm", plot.player.getId(), village, village));
                world.setUseBiomeCropRules(true);
                manager.setBiomeRules(biome, new BiomeRulesInstance(BiomeRulesData.EMPTY, List.of(produce, seed)));
                plot.level.setBlock(plot.pos, Blocks.FARMLAND.defaultBlockState(), 2);
                for (var item : List.of(produce, seed)) {
                    plot.level.setBlock(plot.pos.above(), Blocks.AIR.defaultBlockState(), 2);
                    plot.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 3));
                    var result = plot.player.gameMode.useItemOn(plot.player, plot.level, plot.player.getMainHandItem(),
                            InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(plot.pos), Direction.UP, plot.pos, false));
                    helper.assertTrue(!plot.level.getBlockState(plot.pos.above()).isAir(),
                            "Kale plants using " + BuiltInRegistries.ITEM.getKey(item) + "; result=" + result);
                    helper.assertValueEqual(plot.player.getMainHandItem().getCount(), 2, "One kale item consumed");
                }
                for (boolean deniedBiome : List.of(true, false)) {
                    plot.level.setBlock(plot.pos.above(), Blocks.AIR.defaultBlockState(), 2);
                    manager.setBiomeRules(biome, new BiomeRulesInstance(BiomeRulesData.EMPTY,
                            deniedBiome ? List.of(Items.WHEAT_SEEDS) : List.of(produce, seed)));
                    if (!deniedBiome) {
                        ModEvents.getChunkDataDatabase(plot.level).putData(plot.key,
                                new ChunkData("farm", plot.player.getId(), java.util.UUID.randomUUID()));
                    }
                    plot.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(produce, 3));
                    plot.player.gameMode.useItemOn(plot.player, plot.level, plot.player.getMainHandItem(),
                            InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(plot.pos), Direction.UP, plot.pos, false));
                    helper.assertTrue(plot.level.getBlockState(plot.pos.above()).isAir(),
                            deniedBiome ? "Wrong biome still blocks produce" : "Other village protection blocks produce");
                    helper.assertValueEqual(plot.player.getMainHandItem().getCount(), 3,
                            "Denied produce is not consumed");
                }
                ModEvents.getChunkDataDatabase(plot.level).putData(plot.key,
                        new ChunkData("farm", plot.player.getId(), village, village));
                manager.setBiomeRules(biome, new BiomeRulesInstance(BiomeRulesData.EMPTY, List.of(Items.CARROT)));
                for (var item : List.of(produce, seed, Items.WHEAT_SEEDS)) {
                    for (var hand : InteractionHand.values()) {
                        plot.player.setItemInHand(hand, new ItemStack(item, 3));
                        packets.clear();
                        var result = plot.player.gameMode.useItemOn(plot.player, plot.level,
                                plot.player.getItemInHand(hand), hand,
                                new BlockHitResult(Vec3.atCenterOf(plot.pos), Direction.UP, plot.pos, false));
                        helper.assertTrue(result == net.minecraft.world.InteractionResult.FAIL,
                                "Rejected planting returns FAIL: " + item + " " + hand);
                        helper.assertTrue(plot.level.getBlockState(plot.pos.above()).isAir(),
                                "Rejected planting leaves no crop");
                        helper.assertValueEqual(plot.player.getItemInHand(hand).getCount(), 3,
                                "Rejected planting preserves all items");
                        for (var pos : List.of(plot.pos, plot.pos.above())) {
                            helper.assertTrue(packets.stream().anyMatch(packet ->
                                            packet instanceof ClientboundBlockUpdatePacket update
                                                    && update.getPos().equals(pos)
                                                    && update.getBlockState().equals(plot.level.getBlockState(pos))),
                                    "Send authoritative block correction for " + pos);
                        }
                        helper.assertTrue(packets.stream().anyMatch(packet ->
                                        packet instanceof ClientboundContainerSetContentPacket content
                                                && content.items().stream().anyMatch(stack ->
                                                stack.is(item) && stack.getCount() == 3)),
                                "Send authoritative inventory count, not just unchanged server state");
                    }
                }
            } finally {
                plot.player.connection = oldConnection;
                plot.level.setBlock(plot.pos.above(), above, 2);
                world.setUseBiomeCropRules(previousRules);
                if (previous == null) manager.removeBiomeRule(biome);
                else manager.setBiomeRules(biome, previous);
            }
        }
        helper.succeed();
    }
}
