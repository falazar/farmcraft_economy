package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.command.PlotCommand;
import com.falazar.farmupcraft.command.VillageCommand;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.GameStructureData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.message.PlotViewPacket;
import com.falazar.farmupcraft.database.message.VillageStructuresViewPacket;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class PlotAndStructuresGuiGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("plot_structures_gui"), PlotAndStructuresGuiGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        try (var f = new ResourcePlotFixture(helper)) {
            var plots = ModEvents.getChunkDataDatabase(f.level);
            var players = ModEvents.getPlayerDatabase(f.level);
            var villages = ModEvents.getVillageDatabase(f.level);
            var structures = ModEvents.getGameStructureDatabase(f.level);
            UUID id = UUID.randomUUID();
            long structureId = UUID.randomUUID().getMostSignificantBits();
            while (structures.getData(structureId) != null) structureId++;
            var village = new VillageData(id, "GUI Test",
                    new net.minecraft.world.level.ChunkPos(f.player.chunkPosition().x() - 1, f.player.chunkPosition().z()), 2,
                    List.of(f.player.chunkPosition()), true, 250, "", "cow", "",
                    f.player.getName().getString(), List.of(f.player.getUUID()));
            villages.putData(id, village);
            try {
                var source = f.player.createCommandSourceStack();
                var dispatcher = f.level.getServer().getCommands().getDispatcher();
                helper.assertValueEqual(dispatcher.execute("plot info2", source), 1, "Wilderness info opens");
                var wilderness = PlotCommand.plotSnapshot(source, f.player);
                helper.assertTrue(!wilderness.canBuy() && !wilderness.canChange(), "Wilderness has no purchase buttons");
                helper.assertValueEqual(dispatcher.execute("village structures2", source), 0, "No membership rejected");
                players.putData(f.player.getUUID(), new PlayerData(f.player.getId(), f.player.getUUID(), id, 1000));
                plots.putData(f.key, new ChunkData("plot", f.player.getId(), id, f.player.getUUID()));
                helper.assertValueEqual(dispatcher.execute("village structures2", source), 1, "Empty structures screen opens");
                var snapshot = PlotCommand.plotSnapshot(source, f.player);
                helper.assertTrue(!snapshot.canBuy() && snapshot.canChange(), "Purchased plot allows founder type changes only");
                helper.assertValueEqual(snapshot.buyCost(), 215, "Snapshot uses actual next plot price");
                helper.assertValueEqual(snapshot.changeCost(), 108, "Odd type-change price rounds half up");
                helper.assertTrue(snapshot.details().entries().stream().noneMatch(e -> List.of("Village", "Your wallet",
                        "Village level", "Village treasury", "Actions", "Plot level").contains(e.label())),
                        "Plain plot omits village, unused cards and unupgradeable level");
                String change = snapshot.actionCommand(false, "road");
                helper.assertValueEqual(dispatcher.execute(change.replace(" 108 ", " 0 "), source), 0, "Forged cheaper quote rejected");
                helper.assertValueEqual(dispatcher.execute(change.replace(" 108 ", " 215 "), source), 0, "Old full-price quote rejected");
                f.player.setPos(f.pos.getX() + 32, f.pos.getY(), f.pos.getZ());
                helper.assertValueEqual(dispatcher.execute(change, source), 0, "Moved player cannot confirm old plot");
                f.player.setPos(f.pos.getX() + 0.5, f.pos.getY() + 1, f.pos.getZ() + 0.5);
                helper.assertValueEqual(dispatcher.execute(change.replace("minecraft:overworld", "minecraft:the_nether"), source),
                        0, "Wrong dimension rejected");
                village.setFounder("SomeoneElse");
                villages.putData(id, village);
                helper.assertValueEqual(dispatcher.execute(change, source), 0, "Server still requires founder");
                helper.assertTrue(!PlotCommand.plotSnapshot(source, f.player).canChange(), "Non-founder button disabled");
                village.setFounder(f.player.getName().getString());
                villages.putData(id, village);
                helper.assertValueEqual(dispatcher.execute(change, source), 1, "Confirmed type change succeeds");
                helper.assertValueEqual(plots.getData(f.key).getType(), "road", "Road persists");
                helper.assertValueEqual(players.getData(f.player.getUUID()).getCoins(), 892, "Exactly quoted cost deducted");
                helper.assertValueEqual(dispatcher.execute(change, source), 0, "Replayed old confirmation rejected");
                snapshot = PlotCommand.plotSnapshot(source, f.player);
                helper.assertValueEqual(dispatcher.execute(snapshot.actionCommand(false, "road"), source), 1, "Same type remains free");
                helper.assertValueEqual(players.getData(f.player.getUUID()).getCoins(), 892, "No duplicate fee");
                f.player.setGameMode(GameType.CREATIVE);
                snapshot = PlotCommand.plotSnapshot(source, f.player);
                helper.assertValueEqual(snapshot.changeCost(), 0, "Creative type change quote is free");
                helper.assertValueEqual(dispatcher.execute(snapshot.actionCommand(false, "farm"), source), 1, "Creative change works");
                helper.assertValueEqual(players.getData(f.player.getUUID()).getCoins(), 892, "Creative wallet unchanged");
                f.player.setGameMode(GameType.SURVIVAL);
                plots.putData(f.key, new ChunkData("village", -1, id));
                snapshot = PlotCommand.plotSnapshot(source, f.player);
                helper.assertTrue(snapshot.canBuy(), "Unbought village plot permits Buy");
                helper.assertValueEqual(snapshot.buyCost(), 200, "Unbought land uses 200-coin quote");
                helper.assertValueEqual(dispatcher.execute(snapshot.actionCommand(true, "plot"), source), 0, "Buy preserves legacy return value");
                helper.assertValueEqual(plots.getData(f.key).getType(), "plot", "Confirmed buy persists purchased type");
                helper.assertTrue(f.player.getUUID().equals(plots.getData(f.key).getOwnerUUID()), "Confirmed buy records owner");
                helper.assertValueEqual(players.getData(f.player.getUUID()).getCoins(), 692, "Buy charges exactly once");

                var structure = new GameStructureData(structureId, "exclusives/Test village", f.pos,
                        "test:ruin", false, true);
                structures.putData(structureId, structure);
                var list = VillageCommand.structuresSnapshot(village, f.player);
                var row = list.entries().stream().filter(e -> e.id() == structure.getId()).findFirst().orElseThrow();
                helper.assertValueEqual(row.name(), "Test village", "Display-only exclusives prefix removed");
                helper.assertTrue(row.visited() && row.claimed(), "Visited structure on purchased center plot is claimed");
                helper.assertValueEqual(dispatcher.execute(row.waypointCommand(), source), 1, "GUI waypoint uses stable authorized ID");
                helper.assertValueEqual(dispatcher.execute("village structures2", source), 1, "Structures GUI command opens");
                plots.putData(f.key, new ChunkData("village", -1, id));
                list = VillageCommand.structuresSnapshot(village, f.player);
                helper.assertTrue(list.filtered("unclaimed").stream().anyMatch(e -> e.id() == structure.getId()),
                        "Recomputed center claim status matches existing chat semantics");
                plots.putData(f.key, new ChunkData("village", -1, UUID.randomUUID()));
                helper.assertTrue(VillageCommand.structuresSnapshot(village, f.player).entries().stream()
                        .noneMatch(e -> e.id() == structure.getId()), "Other village's structures excluded");
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    PlotViewPacket.STREAM_CODEC.encode(buffer, new PlotViewPacket(snapshot));
                    helper.assertTrue(PlotViewPacket.STREAM_CODEC.decode(buffer).view().equals(snapshot), "Plot packet roundtrip");
                    buffer.clear();
                    VillageStructuresViewPacket.STREAM_CODEC.encode(buffer, new VillageStructuresViewPacket(list));
                    helper.assertTrue(VillageStructuresViewPacket.STREAM_CODEC.decode(buffer).view().equals(list), "Structure packet roundtrip");
                } finally {
                    buffer.release();
                }
                helper.assertValueEqual(dispatcher.execute("plot info2", f.level.getServer().createCommandSourceStack()),
                        0, "Console cannot open plot GUI");
            } finally {
                structures.removeDataBatch(List.of(structureId));
                villages.removeDataBatch(List.of(id));
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
            throw new IllegalStateException("Plot or structures GUI dispatch failed", error);
        }
        helper.succeed();
    }
}
