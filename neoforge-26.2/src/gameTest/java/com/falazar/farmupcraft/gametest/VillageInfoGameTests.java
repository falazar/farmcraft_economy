package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.command.VillageCommand;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.message.VillageViewPacket;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.VillageView;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class VillageInfoGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> {
            functions.register(FarmUpCraftNeoForge.prefix("village_info_dashboard"), VillageInfoGameTests::verify);
            functions.register(FarmUpCraftNeoForge.prefix("village_census_payment"), VillageInfoGameTests::censusAndPayment);
        });
    }

    private static void censusAndPayment(GameTestHelper helper) {
        try {
            verifyCensusAndPayment(helper);
        } catch (RuntimeException error) {
            throw new IllegalStateException(error + " at " + java.util.Arrays.toString(error.getStackTrace()), error);
        }
    }

    private static void verifyCensusAndPayment(GameTestHelper helper) {
        try (var fixture = new ResourcePlotFixture(helper)) {
            var villages = ModEvents.getVillageDatabase(fixture.level);
            var chunks = ModEvents.getChunkDataDatabase(fixture.level);
            UUID id = UUID.randomUUID();
            var village = new VillageData(id, "Census Test", fixture.player.chunkPosition(), 2,
                    List.of(fixture.player.chunkPosition()), true, 250, "", "", "", "", List.of());
            var wallet = new PlayerData(fixture.player.getId(), fixture.player.getUUID(), id, 349);
            helper.assertTrue(!VillageCommand.payLevelUpCost(village, wallet, 600), "Insufficient combined funds rejected");
            helper.assertValueEqual(village.getCoins(), 250, "Failed payment preserves treasury");
            helper.assertValueEqual(wallet.getCoins(), 349, "Failed payment preserves wallet");
            wallet.setCoins(350);
            helper.assertTrue(VillageCommand.payLevelUpCost(village, wallet, 600), "Split payment succeeds");
            helper.assertValueEqual(village.getCoins(), 0, "Village coins spent first");
            helper.assertValueEqual(wallet.getCoins(), 0, "Player pays only remainder");
            village.setCoins(800);
            wallet.setCoins(20);
            helper.assertTrue(VillageCommand.payLevelUpCost(village, wallet, 600), "Treasury alone can cover cost");
            helper.assertValueEqual(village.getCoins(), 200, "Treasury retains change");
            helper.assertValueEqual(wallet.getCoins(), 20, "Covered cost leaves wallet unchanged");
            village.setCoins(-200);
            wallet.setCoins(600);
            helper.assertTrue(VillageCommand.payLevelUpCost(village, wallet, 600), "Player can pay while village is in debt");
            helper.assertValueEqual(village.getCoins(), -200, "Village debt not spent");
            helper.assertValueEqual(wallet.getCoins(), 0, "Player pays full cost with village debt");

            long now = System.currentTimeMillis();
            village.updateVillagerCount(42, now - 7_200_000L);
            villages.putData(id, village);
            chunks.putData(fixture.key, new ChunkData("village", fixture.player.getId(), id));
            helper.assertTrue(!VillageCommand.refreshVillageCensus(village, fixture.level, now),
                    "Offline village retains its census");
            helper.assertValueEqual(village.getVillagerCount(), 42, "Offline count is not overwritten with zero");
            setOnline(fixture, true);
            var center = village.getPosition().getWorldPosition().offset(8, 0, 8);
            var edge = new net.minecraft.core.BlockPos(center.getX() + 150, 70, center.getZ());
            long edgeKey = net.minecraft.world.level.ChunkPos.pack(edge);
            var previousEdge = chunks.getData(edgeKey);
            var resident = net.minecraft.world.entity.EntityTypes.VILLAGER.create(
                    fixture.level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            try {
                helper.assertTrue(VillageCommand.refreshVillageCensus(village, fixture.level, now),
                        "Online player inside village triggers overdue census");
                int baseline = village.getVillagerCount();
                helper.assertTrue(!village.isVillagerScanDue(now + 7_199_999L), "Census cached until exactly two hours");
                helper.assertTrue(village.isVillagerScanDue(now + 7_200_000L), "Census due at exact two-hour boundary");
                chunks.putData(edgeKey, new ChunkData("village", fixture.player.getId(), id));
                fixture.player.setPos(edge.getX(), edge.getY(), edge.getZ());
                var residentPos = new net.minecraft.core.BlockPos(center.getX() - 20, 200, center.getZ());
                fixture.level.getChunkAt(residentPos);
                resident.setPos(residentPos.getX(), residentPos.getY(), residentPos.getZ());
                helper.assertTrue(fixture.level.addFreshEntity(resident), "Census resident is added to the world");
                helper.assertTrue(!VillageCommand.refreshVillageCensus(village, fixture.level, now + 7_199_999L),
                        "Recent census does not rescan even when population changes");
                helper.assertValueEqual(village.getVillagerCount(), baseline, "Recent count retained");
                helper.assertTrue(VillageCommand.refreshVillageCensus(village, fixture.level, now + 7_200_000L),
                        "Overdue census refreshes with player in village");
                helper.assertValueEqual(village.getVillagerCount(), baseline + 1,
                        "Scan uses city center and full world height, not player's position");
                var encoded = VillageData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, village).getOrThrow();
                var restored = VillageData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow();
                helper.assertValueEqual(restored.getVillagerCount(), baseline + 1, "Saved census count survives codec round trip");
                helper.assertTrue(restored.getLastVillagerScan() == now + 7_200_000L, "Saved census timestamp survives round trip");
                var legacy = encoded.getAsJsonObject().deepCopy();
                legacy.remove("villager_count");
                legacy.remove("last_villager_scan");
                var migrated = VillageData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, legacy).getOrThrow();
                helper.assertValueEqual(migrated.getVillagerCount(), 0, "Legacy villages start without a census");
                helper.assertTrue(migrated.isVillagerScanDue(now), "Legacy villages are eligible immediately");
                chunks.removeDataBatch(List.of(edgeKey));
                helper.assertTrue(!VillageCommand.refreshVillageCensus(village, fixture.level, now + 14_400_000L),
                        "Online player outside village cannot refresh its census");
            } finally {
                resident.discard();
                setOnline(fixture, false);
                if (previousEdge == null) chunks.removeDataBatch(List.of(edgeKey));
                else chunks.putData(edgeKey, previousEdge);
                villages.removeDataBatch(List.of(id));
            }
        }
        helper.succeed();
    }

    private static void setOnline(ResourcePlotFixture fixture, boolean online) {
        try {
            var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
            field.setAccessible(true);
            List.class.getMethod(online ? "add" : "remove", Object.class)
                    .invoke(field.get(fixture.level.getServer().getPlayerList()), fixture.player);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot configure online-player fixture", error);
        }
    }

    private static void verify(GameTestHelper helper) {
        try (var fixture = new ResourcePlotFixture(helper)) {
            var villages = ModEvents.getVillageDatabase(fixture.level);
            var players = ModEvents.getPlayerDatabase(fixture.level);
            var chunks = ModEvents.getChunkDataDatabase(fixture.level);
            UUID id = UUID.randomUUID();
            var village = new VillageData(id, "Dashboard Test", fixture.player.chunkPosition(), 2,
                    List.of(fixture.player.chunkPosition()), true, -200, "", "cow", "",
                    fixture.player.getName().getString(), List.of(fixture.player.getUUID()));
            villages.putData(id, village);
            try {
                var source = fixture.player.createCommandSourceStack();
                var dispatcher = fixture.level.getServer().getCommands().getDispatcher();
                helper.assertValueEqual(dispatcher.execute("village info2", source), 0, "Missing player data rejected");
                players.putData(fixture.player.getUUID(), new PlayerData(fixture.player.getId(),
                        fixture.player.getUUID(), null, 100));
                helper.assertValueEqual(dispatcher.execute("village info2", source), 0, "No home village rejected");
                players.putData(fixture.player.getUUID(), new PlayerData(fixture.player.getId(),
                        fixture.player.getUUID(), id, 100));
                chunks.putData(fixture.key, new ChunkData("farm", fixture.player.getId(), id));
                helper.assertValueEqual(dispatcher.execute("village info2", source), 1, "Home village dashboard opens");
                helper.assertValueEqual(dispatcher.execute("village info2 \"Dashboard Test\"", source), 1,
                        "Named village dashboard opens");
                helper.assertValueEqual(VillageCommand.showVillageInfo(source, null), 1, "Existing info still works");
                helper.assertValueEqual(village.getCoins(), -200, "Dashboard does not alter treasury");
                helper.assertValueEqual(players.getData(fixture.player.getUUID()).getCoins(), 100, "Player wallet unchanged");
                helper.assertValueEqual(dispatcher.execute("village levelup confirm " + UUID.randomUUID() + " 2", source),
                        0, "A confirmation for a different village is rejected");
                helper.assertValueEqual(dispatcher.execute("village levelup confirm " + id + " 1", source),
                        0, "A stale level quote is rejected");
                helper.assertValueEqual(dispatcher.execute("village levelup confirm " + id + " 2", source),
                        0, "Existing wallet, villager and structure requirements remain authoritative");
                helper.assertValueEqual(village.getLevel(), 2, "Rejected confirmation does not upgrade");
                helper.assertValueEqual(players.getData(fixture.player.getUUID()).getCoins(), 100,
                        "Rejected confirmation does not charge wallet");
                village.setLevel(3);
                helper.assertValueEqual(dispatcher.execute("village levelup confirm " + id + " 2", source),
                        0, "Replayed confirmation after a level change is rejected");
                village.setLevel(2);
                helper.assertValueEqual(dispatcher.execute("village levelup confirm " + id + " 2",
                        fixture.level.getServer().createCommandSourceStack()), 0, "Console confirmation rejected");
                helper.assertValueEqual(dispatcher.execute("village info2 \"Missing Village\"", source), 0, "Unknown village rejected");
                helper.assertValueEqual(dispatcher.execute("village info2", fixture.level.getServer().createCommandSourceStack()),
                        0, "Console cannot open client screen");
                var view = VillageView.summary(village.getName(), 2, 2, -200, "1, 64, 2",
                        1, 1, 1, 215, 200, 0, 1, 0, "Founder", "cow", List.of("First", "Second"));
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    VillageViewPacket.STREAM_CODEC.encode(buffer, new VillageViewPacket(view));
                    helper.assertTrue(VillageViewPacket.STREAM_CODEC.decode(buffer).view().equals(view),
                            "Dashboard snapshot codec preserves all stats and colors");
                    buffer.clear();
                    buffer.writeUtf("Test", 256);
                    buffer.writeVarInt(VillageView.MAX_ENTRIES + 1);
                    boolean rejected = false;
                    try {
                        new VillageViewPacket(buffer);
                    } catch (IllegalArgumentException expected) {
                        rejected = true;
                    }
                    helper.assertTrue(rejected, "Oversized dashboard rejected before allocation");
                } finally {
                    buffer.release();
                }
            } finally {
                villages.removeDataBatch(List.of(id));
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
            throw new IllegalStateException("Village dashboard command failed", error);
        }
        helper.succeed();
    }
}
