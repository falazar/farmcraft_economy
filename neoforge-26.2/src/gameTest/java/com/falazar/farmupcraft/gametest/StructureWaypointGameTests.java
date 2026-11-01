package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.command.VillageCommand;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.GameStructureData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.ClickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class StructureWaypointGameTests {
    private StructureWaypointGameTests() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("structure_waypoint"), StructureWaypointGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        try (var fixture = new ResourcePlotFixture(helper)) {
            var players = ModEvents.getPlayerDatabase(fixture.level);
            var villages = ModEvents.getVillageDatabase(fixture.level);
            var structures = ModEvents.getGameStructureDatabase(fixture.level);
            var chunks = ModEvents.getChunkDataDatabase(fixture.level);
            UUID villageId = UUID.randomUUID();
            UUID playerId = fixture.player.getUUID();
            long id = UUID.randomUUID().getMostSignificantBits();
            while (structures.getData(id) != null) id++;
            var structure = new GameStructureData(id, "Test ruin with spaces", fixture.pos,
                    "gametest_ruin", false, false);
            structures.putData(id, structure);
            villages.putData(villageId, new VillageData(villageId, "Waypoint Test", fixture.player.chunkPosition(),
                    1, List.of(fixture.player.chunkPosition()), true, 250, "", "cow", "",
                    playerId.toString(), List.of(playerId)));
            try {
                players.putData(playerId, new PlayerData(fixture.player.getId(), playerId, villageId, 10));
                chunks.putData(fixture.key, new ChunkData("village", -1, villageId));
                var coords = VillageCommand.structureWaypointCoordinates(id, fixture.pos);
                helper.assertTrue(coords.getString().equals(fixture.pos.toShortString()), "Coordinates remain visible");
                helper.assertTrue(coords.getStyle().isUnderlined() && coords.getStyle().getHoverEvent() != null,
                        "Coordinates must advertise the click action");
                helper.assertTrue(coords.getStyle().getClickEvent() instanceof ClickEvent.RunCommand,
                        "Coordinates must execute a waypoint command");
                var click = (ClickEvent.RunCommand) coords.getStyle().getClickEvent();
                helper.assertTrue(click.command().equals("/village structures waypoint " + id),
                        "Click must use stable ID rather than list index or untrusted name");
                var source = fixture.player.createCommandSourceStack();
                var dispatcher = fixture.level.getServer().getCommands().getDispatcher();
                helper.assertValueEqual(dispatcher.execute(click.command().substring(1), source), 1,
                        "Ordinary village member can create a waypoint via chat command");
                fixture.player.setPos(fixture.pos.getX() + 4, fixture.pos.getY(), fixture.pos.getZ() + 4);
                helper.assertValueEqual(dispatcher.execute(click.command().substring(1), source), 1,
                        "Moving after listing must not change the selected structure");
                chunks.putData(fixture.key, new ChunkData("village", -1, UUID.randomUUID()));
                helper.assertValueEqual(VillageCommand.waypointVillageStructure(source, id), 0,
                        "Another village's structure must be rejected");
                structures.removeDataBatch(List.of(id));
                helper.assertValueEqual(VillageCommand.waypointVillageStructure(source, id), 0,
                        "Deleted structure must be rejected");
                players.putData(playerId, new PlayerData(fixture.player.getId(), playerId, null, 10));
                helper.assertValueEqual(VillageCommand.waypointVillageStructure(source, id), 0,
                        "Players without village membership must be rejected");
            } finally {
                structures.removeDataBatch(List.of(id));
                villages.removeDataBatch(List.of(villageId));
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
            throw new IllegalStateException("Structure waypoint command failed", error);
        }
        helper.succeed();
    }
}
