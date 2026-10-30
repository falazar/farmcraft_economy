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
        helper.succeed();
    }
}
