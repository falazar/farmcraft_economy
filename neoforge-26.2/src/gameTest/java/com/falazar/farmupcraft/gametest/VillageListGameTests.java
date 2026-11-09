package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.command.VillageCommand;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.message.VillageListViewPacket;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.VillageListView;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class VillageListGameTests {
    private VillageListGameTests() {}

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("village_list_dashboard"), VillageListGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        try (var fixture = new ResourcePlotFixture(helper)) {
            var villages = ModEvents.getVillageDatabase(fixture.level);
            var added = new ArrayList<UUID>();
            try {
                for (int i = 0; i < 65; i++) {
                    UUID id = UUID.randomUUID();
                    added.add(id);
                    villages.putData(id, new VillageData(id, String.format("List Test %02d", 64 - i),
                            fixture.player.chunkPosition(), 2,
                            List.of(fixture.player.chunkPosition(), fixture.player.chunkPosition()),
                            true, 10, "", "", "", "", List.of()));
                }
                ModEvents.getPlayerDatabase(fixture.level).putData(fixture.player.getUUID(),
                        new com.falazar.farmupcraft.data.PlayerData(fixture.player.getId(),
                                fixture.player.getUUID(), added.get(0), 100));
                var first = VillageCommand.villageListView(fixture.player, 1);
                helper.assertValueEqual(first.entries().size(), 64, "First directory page is bounded");
                helper.assertTrue(first.pages() >= 2, "Large directory has more pages");
                var all = new ArrayList<VillageListView.Entry>();
                for (int page = 1; page <= first.pages(); page++) {
                    all.addAll(VillageCommand.villageListView(fixture.player, page).entries());
                }
                helper.assertValueEqual(all.size(), villages.getValues().size(), "All villages remain reachable");
                helper.assertValueEqual(all, all.stream().sorted(VillageListView.ORDER).toList(),
                        "Alphabetical ordering is consistent across pages");
                helper.assertValueEqual(all.stream().map(VillageListView.Entry::id).distinct().count(),
                        (long) all.size(), "No villages repeat across pages");
                helper.assertValueEqual(all.stream().filter(VillageListView.Entry::home).count(), 1L,
                        "Exactly the player's home village is highlighted");
                helper.assertTrue(all.stream().filter(VillageListView.Entry::home).findFirst().orElseThrow()
                        .id().equals(added.get(0)), "Home highlight uses membership, not location or name");
                var testEntry = all.stream().filter(e -> added.contains(e.id())).findFirst().orElseThrow();
                helper.assertValueEqual(testEntry.chunks(), 1, "Duplicate chunk entries do not inflate claim count");
                helper.assertValueEqual(testEntry.x(), fixture.player.chunkPosition().getWorldPosition().getX(),
                        "Directory preserves existing village-list coordinate convention");
                var dispatcher = fixture.level.getServer().getCommands().getDispatcher();
                var source = fixture.player.createCommandSourceStack();
                helper.assertValueEqual(dispatcher.execute("village list2", source), 1, "Player can open directory");
                helper.assertValueEqual(dispatcher.execute("village list2 2", source), 1, "Player can request next page");
                helper.assertValueEqual(dispatcher.execute("village list2 " + (first.pages() + 1), source), 0,
                        "Out-of-range page fails explicitly");
                helper.assertValueEqual(dispatcher.execute("village list2",
                        fixture.level.getServer().createCommandSourceStack()), 0, "Console cannot open client screen");
                var parsed = dispatcher.parse(testEntry.infoCommand(), source);
                helper.assertTrue(parsed.getExceptions().isEmpty() && !parsed.getReader().canRead(),
                        "Info button command accepts names containing spaces");
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    VillageListViewPacket.STREAM_CODEC.encode(buffer, new VillageListViewPacket(first));
                    helper.assertValueEqual(VillageListViewPacket.STREAM_CODEC.decode(buffer).view(), first,
                            "Directory payload preserves every field");
                    helper.assertValueEqual(buffer.readableBytes(), 0, "Directory decoder consumes exactly its payload");
                    buffer.clear();
                    buffer.writeVarInt(1); buffer.writeVarInt(100); buffer.writeVarInt(65);
                    boolean rejected = false;
                    try { new VillageListViewPacket(buffer); }
                    catch (IllegalArgumentException expected) { rejected = true; }
                    helper.assertTrue(rejected, "Oversized directory payload rejected before allocation");
                } finally {
                    buffer.release();
                }
            } finally {
                villages.removeDataBatch(added);
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
            throw new IllegalStateException("Village directory commands failed", error);
        }
        helper.succeed();
    }
}
