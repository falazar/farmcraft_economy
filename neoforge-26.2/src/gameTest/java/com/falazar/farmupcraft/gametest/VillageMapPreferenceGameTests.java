package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.command.FarmCraftCommand;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.events.ForgeEvents;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class VillageMapPreferenceGameTests {
    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("village_map_preference"), VillageMapPreferenceGameTests::preference));
    }

    private static void preference(GameTestHelper helper) {
        try (var fixture = new ResourcePlotFixture(helper)) {
            var players = ModEvents.getPlayerDatabase(fixture.level);
            var villages = ModEvents.getVillageDatabase(fixture.level);
            UUID villageId = UUID.randomUUID();
            UUID playerId = fixture.player.getUUID();
            PlayerData data = new PlayerData(fixture.player.getId(), playerId, villageId, 10);
            players.putData(playerId, data);
            villages.putData(villageId, new VillageData(villageId, "Map Test", fixture.player.chunkPosition(),
                    1, List.of(fixture.player.chunkPosition()), true, 250, "", "cow", "",
                    playerId.toString(), List.of(playerId)));
            try {
                var legacy = PlayerData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().getAsJsonObject();
                legacy.remove("village_map_show");
                helper.assertTrue(!PlayerData.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow().isVillageMapShow(),
                        "Old saves default to hidden");
                var source = fixture.player.createCommandSourceStack();
                helper.assertValueEqual(FarmCraftCommand.showVillageChunks(source), 1, "Show command succeeds");
                reload(players, playerId);
                ForgeEvents.onLoginEvent(new PlayerEvent.PlayerLoggedInEvent(fixture.player));
                helper.assertTrue(players.getData(playerId).isVillageMapShow(), "Enabled survives reload and login");

                FarmCraftCommand.clearVillageChunks(source);
                reload(players, playerId);
                ForgeEvents.onLoginEvent(new PlayerEvent.PlayerLoggedInEvent(fixture.player));
                FarmCraftCommand.refreshVillageChunksOverlay(fixture.player);
                helper.assertTrue(!players.getData(playerId).isVillageMapShow(),
                        "Disabled survives reload, login, and plot refresh");

                FarmCraftCommand.showVillageChunks(source);
                FarmCraftCommand.clearAllMapOverlays(source);
                reload(players, playerId);
                helper.assertTrue(!players.getData(playerId).isVillageMapShow(), "Clear-all persists disabled");
            } finally {
                villages.removeDataBatch(List.of(villageId));
            }
        }
        helper.succeed();
    }

    private static void reload(com.falazar.farmupcraft.database.DataBase<UUID, PlayerData> players, UUID id) {
        var saved = PlayerData.CODEC.encodeStart(JsonOps.INSTANCE, players.getData(id)).getOrThrow();
        players.putData(id, PlayerData.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow());
    }
}
