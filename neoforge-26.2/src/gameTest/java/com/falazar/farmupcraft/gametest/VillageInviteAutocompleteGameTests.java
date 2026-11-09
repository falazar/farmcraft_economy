package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class VillageInviteAutocompleteGameTests {
    private VillageInviteAutocompleteGameTests() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("village_invite_autocomplete"),
                VillageInviteAutocompleteGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var argument = dispatcher.getRoot().getChild("village").getChild("invite").getChild("playerName");
        helper.assertTrue(argument instanceof ArgumentCommandNode<CommandSourceStack, ?> node
                && node.getCustomSuggestions() != null, "Invite player name has a registered suggestion provider");
        var source = server.createCommandSourceStack();
        var actual = dispatcher.getCompletionSuggestions(dispatcher.parse("village invite ", source)).join();
        helper.assertValueEqual(actual.getList().stream().map(suggestion -> suggestion.getText()).sorted().toList(),
                java.util.Arrays.stream(server.getPlayerNames()).sorted().toList(),
                "Registered command suggests precisely the currently online player names");
        var names = new String[]{"LazzyMars", "BossPanda96366"};
        var partial = SharedSuggestionProvider.suggest(names, new SuggestionsBuilder("village invite la", 15)).join();
        helper.assertValueEqual(partial.getList().stream().map(suggestion -> suggestion.getText()).toList(),
                List.of("LazzyMars"), "Partial lowercase names match and retain the correct player-name casing");
        helper.succeed();
    }
}
