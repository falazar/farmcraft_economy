package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class BiomeCropsGameTests {
    private BiomeCropsGameTests() {}

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("current_biome_crops"), BiomeCropsGameTests::currentBiome));
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("farmland_inventory_crops"), BiomeCropsGameTests::farmlandInventory));
    }

    private static void farmlandInventory(GameTestHelper helper) {
        try (var fixture = new ResourcePlotFixture(helper)) {
            var messages = new ArrayList<String>();
            var player = new FakePlayer(fixture.level, fixture.player.getGameProfile()) {
                @Override
                public void sendSystemMessage(Component message) {
                    messages.add(message.getString());
                }
            };
            var manager = com.falazar.farmupcraft.saveddata.BiomeRulesManager.get(fixture.level);
            var biome = fixture.level.getBiome(fixture.pos);
            var previous = manager.getBiomeRules(biome);
            var world = com.falazar.farmupcraft.events.ModEvents.getWorldData(fixture.level);
            boolean previousEnabled = world.isUseBiomeCropRules();
            try {
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                player.setPos(fixture.pos.getX(), fixture.pos.getY() + 1, fixture.pos.getZ());
                fixture.level.setBlock(fixture.pos, net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState(), 2);
                world.setUseBiomeCropRules(true);
                manager.setBiomeRules(biome, new com.falazar.farmupcraft.saveddata.BiomeRulesInstance(
                        com.falazar.farmupcraft.data.BiomeRulesData.EMPTY,
                        java.util.List.of(net.minecraft.world.item.Items.WHEAT_SEEDS)));
                player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS, 5));
                player.getInventory().setItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS, 3));
                player.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CARROT, 4));
                player.getInventory().setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE, 9));
                var dispatcher = fixture.level.getServer().getCommands().getDispatcher();
                var source = player.createCommandSourceStack();
                String command = "show plantable " + fixture.pos.getX() + " " + fixture.pos.getY() + " " + fixture.pos.getZ();
                helper.assertValueEqual(dispatcher.execute(command, source), 1, "Farmland listing succeeds");
                helper.assertTrue(messages.stream().anyMatch(s -> s.contains("Wheat Seeds (8)")), "Inventory and offhand counts merge");
                helper.assertTrue(messages.stream().noneMatch(s -> s.contains("Carrot") || s.contains("Apple")), "Biome-disallowed crops and nonplantable foods excluded");
                helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 5, "Listing never changes inventory");
                world.setUseBiomeCropRules(false);
                messages.clear();
                dispatcher.execute(command, source);
                helper.assertTrue(messages.stream().anyMatch(s -> s.contains("Carrot (4)")), "Disabled biome restrictions reflected");
                world.setUseBiomeCropRules(true);
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                messages.clear();
                dispatcher.execute(command, source);
                helper.assertTrue(messages.stream().anyMatch(s -> s.contains("Carrot (4)")), "Creative exemption reflected");
                fixture.level.setBlock(fixture.pos, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 2);
                helper.assertValueEqual(dispatcher.execute(command, source), 0, "Nonfarmland target rejected");
                helper.assertValueEqual(dispatcher.execute("show plantable 0 200 0", source), 0, "Distant target rejected");
            } finally {
                manager.setBiomeRules(biome, previous);
                world.setUseBiomeCropRules(previousEnabled);
                player.discard();
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
            throw new IllegalStateException("Farmland inventory command failed", error);
        }
        helper.succeed();
    }

    private static void currentBiome(GameTestHelper helper) {
        try (var fixture = new ResourcePlotFixture(helper)) {
            var messages = new ArrayList<String>();
            var player = new FakePlayer(fixture.level, fixture.player.getGameProfile()) {
                @Override
                public void sendSystemMessage(Component message) {
                    messages.add(message.getString());
                }
            };
            try {
                player.setPos(fixture.pos.getX(), fixture.pos.getY(), fixture.pos.getZ());
                var dispatcher = fixture.level.getServer().getCommands().getDispatcher();
                var source = player.createCommandSourceStack();
                String biome = player.level().getBiome(player.blockPosition()).unwrapKey().orElseThrow()
                        .identifier().toString();
                dispatcher.execute("show biomecrops " + biome, source);
                var explicit = new ArrayList<>(messages);
                helper.assertTrue(explicit.stream().anyMatch(text -> text.contains("Crops you can plant in " + biome)),
                        "Explicit biome lookup must produce a crop list");
                messages.clear();
                dispatcher.execute("show biomecrops", source);
                helper.assertValueEqual(messages, explicit,
                        "Default lookup must match the biome at the player's actual position");
                var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("show biomecrops ", source)).join();
                var registered = fixture.level.registryAccess().lookupOrThrow(Registries.BIOME)
                        .listElementIds().map(key -> key.identifier().toString()).collect(java.util.stream.Collectors.toSet());
                var suggested = suggestions.getList().stream().map(com.mojang.brigadier.suggestion.Suggestion::getText)
                        .collect(java.util.stream.Collectors.toSet());
                helper.assertValueEqual(suggested, registered, "Autocomplete must include every registered biome");
                helper.assertTrue(suggested.stream().anyMatch(id -> id.startsWith("biomesoplenty:")),
                        "Installed Biomes O' Plenty biomes must be suggested");
                for (String id : suggested) {
                    var parsed = dispatcher.parse("show biomecrops " + id, source);
                    helper.assertTrue(parsed.getExceptions().isEmpty() && !parsed.getReader().canRead(),
                            "Every suggested namespaced biome must parse without quotes");
                }
                var crops = dispatcher.getCompletionSuggestions(dispatcher.parse("show cropbiomes ", source)).join();
                var expectedCrops = com.falazar.farmupcraft.saveddata.BiomeRulesManager.get(fixture.level).getItems()
                        .stream().map(net.minecraft.core.registries.BuiltInRegistries.ITEM::getKey)
                        .map(Object::toString).collect(java.util.stream.Collectors.toSet());
                helper.assertTrue(!expectedCrops.isEmpty(), "Crop rules must be loaded");
                helper.assertValueEqual(crops.getList().stream().map(com.mojang.brigadier.suggestion.Suggestion::getText)
                                .collect(java.util.stream.Collectors.toSet()), expectedCrops,
                        "Crop suggestions must match configured crop items");
            } finally {
                player.discard();
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new IllegalStateException("Biome crop command failed: " + failure.getMessage(), failure);
        }
        helper.succeed();
    }
}
