package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class VillageMapCenterGameTests {
    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("village_map_center"), VillageMapCenterGameTests::center));
    }

    private static void center(GameTestHelper helper) {
        var level = helper.getLevel();
        var players = ModEvents.getPlayerDatabase(level);
        var villages = ModEvents.getVillageDatabase(level);
        var chunks = ModEvents.getChunkDataDatabase(level);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "map-center-test"));
        UUID villageId = UUID.randomUUID();
        ChunkPos center = new ChunkPos(20000, 20000);
        ChunkPos territory = new ChunkPos(20001, 20000);
        ChunkPos plot = new ChunkPos(20002, 20000);
        players.putData(player.getUUID(), new PlayerData(player.getId(), player.getUUID(), villageId, 10));
        villages.putData(villageId, new VillageData(villageId, "Center Test", center, 1,
                List.of(center, territory, plot), true, 250, "", "cow", "",
                player.getUUID().toString(), List.of(player.getUUID())));
        var positions = List.of(center, territory, plot);
        var previous = positions.stream().map(pos -> chunks.getData(pos.pack())).toList();
        try {
            chunks.removeDataBatch(List.of(center.pack()));
            chunks.putData(territory.pack(), new ChunkData("village", 1, villageId));
            chunks.putData(plot.pack(), new ChunkData("farm", 1, villageId));
            verify(helper, player);
            for (String type : List.of("village", "village center")) {
                chunks.putData(center.pack(), new ChunkData(type, 1, villageId));
                verify(helper, player);
            }
        } finally {
            for (int i = 0; i < positions.size(); i++) {
                long key = positions.get(i).pack();
                if (previous.get(i) == null) chunks.removeDataBatch(List.of(key));
                else chunks.putData(key, previous.get(i));
            }
            players.removeDataBatch(List.of(player.getUUID()));
            villages.removeDataBatch(List.of(villageId));
            player.discard();
        }
        helper.succeed();
    }

    private static void verify(GameTestHelper helper, net.minecraft.server.level.ServerPlayer player) {
        var overlay = FarmCraftCommand.buildVillageChunkOverlayData(player);
        helper.assertValueEqual(overlay.get(0)[2], 0x0055FF, "Center is purchased blue");
        helper.assertValueEqual(overlay.get(1)[2], 0x00E000, "Ordinary territory stays green");
        helper.assertValueEqual(overlay.get(2)[2], 0x0055FF, "Purchased farm stays blue");
    }
}
