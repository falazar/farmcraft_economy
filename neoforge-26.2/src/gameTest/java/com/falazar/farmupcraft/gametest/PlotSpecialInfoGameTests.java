package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.command.PlotCommand;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.VillageView;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class PlotSpecialInfoGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("plot_special_info"), PlotSpecialInfoGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        try (var f = new ResourcePlotFixture(helper)) {
            var messages = new ArrayList<String>();
            var player = new FakePlayer(f.level, new GameProfile(UUID.randomUUID(), "plot_detail")) {
                @Override
                public void sendSystemMessage(Component message) { messages.add(message.getString()); }
                @Override
                public void sendSystemMessage(Component message, boolean overlay) { messages.add(message.getString()); }
            };
            UUID villageId = UUID.randomUUID();
            var villages = ModEvents.getVillageDatabase(f.level);
            var players = ModEvents.getPlayerDatabase(f.level);
            var chunks = ModEvents.getChunkDataDatabase(f.level);
            var village = new VillageData(villageId, "Detail test", f.player.chunkPosition(), 8,
                    List.of(f.player.chunkPosition()), true, 250, "", "cow", "",
                    player.getName().getString(), List.of(player.getUUID()));
            villages.putData(villageId, village);
            players.putData(player.getUUID(), new PlayerData(player.getId(), player.getUUID(), villageId, 1000));
            var animals = new ArrayList<Entity>();
            try {
                player.setPos(f.pos.getX() + 0.5, f.pos.getY(), f.pos.getZ() + 0.5);
                var source = player.createCommandSourceStack();
                var dispatcher = f.level.getServer().getCommands().getDispatcher();
                for (int plotLevel = 1; plotLevel <= 4; plotLevel++) {
                    var farm = new ChunkData("farm", player.getId(), villageId);
                    farm.setPlotLevel(plotLevel);
                    chunks.putData(f.key, farm);
                    var snapshot = PlotCommand.plotSnapshot(source, player);
                    helper.assertTrue(value(snapshot.details(), "Plot level").startsWith("" + plotLevel),
                            "Upgradeable farm keeps its plot level");
                    helper.assertTrue(plotLevel == 1 ? value(snapshot.details(), "Harvest bonus").equals("None")
                            : value(snapshot.details(), "Harvest bonus").startsWith(((plotLevel - 1) * 20) + "%"),
                            "Farm bonus matches exact level " + plotLevel);
                    helper.assertTrue(value(snapshot.details(), "Crop scan height").contains("" + (f.pos.getY() - 1)),
                            "Farm scan height is explicit");
                    helper.assertValueEqual(value(snapshot.details(), "Trample protection"), "Protection at level 2",
                            "Compact trample description");
                    helper.assertValueEqual(value(snapshot.details(), "Growth speed"), "No bonus", "Compact growth description");
                    helper.assertTrue(snapshot.details().entries().stream().noneMatch(e -> e.label().equals("Harvest scope")),
                            "Harvest scope row removed");
                    helper.assertTrue(value(snapshot.details(), "Next upgrade").contains(plotLevel == 4
                            ? "maximum plot level" : "215 coins"), "Current upgrade cost or hard cap shown");
                    messages.clear();
                    dispatcher.execute("plot info", source);
                    String chat = String.join("\n", messages);
                    helper.assertTrue(chat.contains(value(snapshot.details(), "Harvest bonus"))
                            && chat.contains(value(snapshot.details(), "Growth speed")), "Chat and GUI share farm info");
                }
                for (int i = 0; i < 30; i++) {
                    var animal = (i < 20 ? EntityTypes.COW : EntityTypes.SHEEP).create(f.level, EntitySpawnReason.TRIGGERED);
                    helper.assertTrue(animal != null, "Animal fixture created");
                    animal.setPos(player.position());
                    animal.setNoAi(true);
                    helper.assertTrue(f.level.addFreshEntity(animal), "Animal fixture added");
                    animals.add(animal);
                }
                var pasture = new ChunkData("pasture", player.getId(), villageId);
                pasture.setBoughtY(f.pos.getY());
                pasture.setPlotLevel(3);
                chunks.putData(f.key, pasture);
                var snapshot = PlotCommand.plotSnapshot(source, player);
                helper.assertValueEqual(value(snapshot.details(), "Animals"), "30 / 30 - FULL", "Pasture cap and fullness");
                helper.assertTrue(snapshot.details().entries().stream().noneMatch(e -> List.of("Animal scan height",
                        "Village", "Village level", "Your wallet", "Village treasury", "Actions").contains(e.label())),
                        "Pasture snapshot omits scan height and removed cards");
                player.setPos(player.getX(), f.pos.getY() + 40, player.getZ());
                var highSnapshot = PlotCommand.plotSnapshot(player.createCommandSourceStack(), player);
                helper.assertValueEqual(value(highSnapshot.details(), "Animals"), "30 / 30 - FULL",
                        "Moving viewer vertically must not change animal count");
                player.setPos(player.getX(), f.pos.getY(), player.getZ());
                helper.assertTrue(value(snapshot.details(), "Animal breakdown").contains("Cow: 20")
                        && value(snapshot.details(), "Animal breakdown").contains("Sheep: 10"), "Real per-type counts");
                helper.assertTrue(value(snapshot.details(), "Pasture drop bonus").startsWith("+2 wool"), "Level-three drop bonus");
                helper.assertTrue(value(snapshot.details(), "Sheep dye success").startsWith("60%"), "Level-three dye chance");
                helper.assertValueEqual(value(snapshot.details(), "Breeding species"), "cow", "Village breeding species");
                helper.assertTrue(value(snapshot.details(), "Breeding feed").contains("Not assigned"), "Missing grains stated");
                helper.assertTrue(!village.hasAnimalGrainsAssigned(), "Viewing must not assign random grains");
                helper.assertTrue(snapshot.details().entries().size() <= VillageView.MAX_ENTRIES, "Full pasture fits snapshot");
                messages.clear();
                dispatcher.execute("plot info", source);
                helper.assertTrue(String.join("\n", messages).contains("Cow: 20")
                        && String.join("\n", messages).contains("30 / 30 - FULL"), "Chat has live counts too");
                village.setAnimalType("duck");
                snapshot = PlotCommand.plotSnapshot(source, player);
                helper.assertTrue(value(snapshot.details(), "Breeding feed").endsWith("; fish also accepted"),
                        "GUI uses simplified fish wording");
                messages.clear();
                dispatcher.execute("plot info", source);
                String chat = String.join("\n", messages);
                helper.assertTrue(chat.contains("; fish also accepted") && !chat.contains("Naturalist")
                        && !chat.contains("Animal scan height") && !chat.contains("Adult cows")
                        && !chat.contains("adults ready to breed"), "Chat uses the same simplified pasture wording");
                helper.assertValueEqual(players.getData(player.getUUID()).getCoins(), 1000, "Viewing costs nothing");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
                throw new IllegalStateException("Plot detail command failed", error);
            } finally {
                animals.forEach(Entity::discard);
                player.discard();
                players.removeDataBatch(List.of(player.getUUID()));
                villages.removeDataBatch(List.of(villageId));
            }
        }
        helper.succeed();
    }

    private static String value(VillageView view, String label) {
        return view.entries().stream().filter(e -> e.label().equals(label)).findFirst().orElseThrow().value();
    }
}
