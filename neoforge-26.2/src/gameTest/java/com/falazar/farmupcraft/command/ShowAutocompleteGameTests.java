package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class ShowAutocompleteGameTests {
    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("show_short_autocomplete"), ShowAutocompleteGameTests::suggestions));
    }

    private static void suggestions(GameTestHelper helper) {
        var source = helper.getLevel().getServer().createCommandSourceStack();
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        ShowBiomesCommand.register(dispatcher);
        var biomes = ShowBiomesCommand.biomeAliases(source);
        helper.assertValueEqual(biomes.get("plains"), "minecraft:plains", "Vanilla short biome resolves");
        var registry = source.registryAccess().lookupOrThrow(Registries.BIOME);
        for (var entry : biomes.entrySet()) {
            helper.assertTrue(registry.get(Identifier.parse(entry.getValue())).isPresent(),
                    "Suggested biome resolves: " + entry.getKey());
        }
        var biomeSuggestions = dispatcher.getCompletionSuggestions(
                dispatcher.parse("show biomecrops pla", source)).join().getList();
        helper.assertTrue(biomeSuggestions.stream().anyMatch(value -> value.getText().equals("plains")),
                "Actual biome command suggests plains without namespace");
        var crops = ShowBiomesCommand.cropAliases(source);
        helper.assertTrue(!crops.isEmpty(), "Crop rules provide suggestions");
        for (var entry : crops.entrySet()) {
            helper.assertTrue(ShowBiomesCommand.resolveCrop(entry.getValue())
                            == BuiltInRegistries.ITEM.getValue(Identifier.parse(entry.getValue())),
                    "Suggested crop resolves to original item: " + entry.getKey());
        }
        var cropSuggestions = dispatcher.getCompletionSuggestions(
                dispatcher.parse("show cropbiomes ", source)).join().getList();
        helper.assertTrue(cropSuggestions.stream().allMatch(value -> crops.containsKey(value.getText())),
                "Command suggestions match backend alias table");
        helper.assertValueEqual(cropSuggestions.size(), crops.size(), "Every crop alias is suggested");
        for (String prefix : java.util.List.of("carrot ", "carrot potato ")) {
            var next = dispatcher.getCompletionSuggestions(
                    dispatcher.parse("show cropbiomes " + prefix, source)).join().getList();
            helper.assertTrue(next.stream().anyMatch(value -> value.getText().equals("wheat")),
                    "Crop autocomplete works after earlier crops: " + prefix);
        }
        try {
            var messages = new java.util.ArrayList<String>();
            var player = new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "crop-query")) {
                @Override
                public void sendSystemMessage(net.minecraft.network.chat.Component message) {
                    messages.add(message.getString());
                }
            };
            try {
                var playerSource = player.createCommandSourceStack();
                var manager = com.falazar.farmupcraft.saveddata.BiomeRulesManager.get(helper.getLevel());
                for (var names : java.util.List.of(java.util.List.of("carrot"),
                        java.util.List.of("carrot", "potato"), java.util.List.of("carrot", "potato", "wheat"))) {
                    var expected = com.falazar.farmupcraft.util.CropBiomeQuery.shared(names, crop ->
                            manager.getBiomesForItem(ShowBiomesCommand.resolveCrop(crops.getOrDefault(crop, crop)))
                                    .stream().flatMap(b -> b.unwrapKey().stream())
                                    .collect(java.util.stream.Collectors.toSet()));
                    String expectedBiomes = expected.stream()
                            .map(key -> net.minecraft.network.chat.Component.translatable(
                                    com.falazar.farmupcraft.CropsManager.getBiomeLangKey(key.identifier())).getString())
                            .sorted().collect(java.util.stream.Collectors.joining(", "));
                    if (expectedBiomes.isEmpty()) expectedBiomes = names.size() == 1 ? "None" : "No shared biomes";
                    messages.clear();
                    dispatcher.execute("show cropbiomes " + String.join(" ", names), playerSource);
                    helper.assertTrue(messages.size() == 1 && messages.getFirst().endsWith(
                            (names.size() == 1 ? " in: " : " together in: ") + expectedBiomes),
                            "Exact common-biome output for " + names + ": " + messages);
                }
                messages.clear();
                dispatcher.execute("show cropbiomes carrot potato wheat beetroot", playerSource);
                helper.assertTrue(messages.stream().anyMatch(s -> s.contains("at most three")),
                        "Fourth crop gets explicit rejection");
                messages.clear();
                dispatcher.execute("show cropbiomes carrot nonexistent_crop", playerSource);
                helper.assertTrue(messages.stream().anyMatch(s -> s.contains("Crop not found: nonexistent_crop")),
                        "Unknown crop does not yield partial success");
            } finally {
                player.discard();
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
            throw new IllegalStateException("Multiple-crop command failed", error);
        }
        helper.succeed();
    }
}
