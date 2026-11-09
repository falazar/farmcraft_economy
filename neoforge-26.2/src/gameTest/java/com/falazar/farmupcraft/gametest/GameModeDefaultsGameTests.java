package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.commands.GameModeCommand;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Arrays;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class GameModeDefaultsGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("gamemode_defaults"), GameModeDefaultsGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        try (var f = new ResourcePlotFixture(helper)) {
            var server = f.level.getServer();
            var previousDefault = server.getDefaultGameType();
            var dispatcher = server.getCommands().getDispatcher();
            var source = f.player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER);
            try {
                server.setDefaultGameType(GameType.SURVIVAL);
                var redirects = Arrays.stream(GameModeCommand.class.getDeclaredMethods())
                        .filter(m -> m.getName().contains("farmupcraft$keepPersonalModeSeparate")).toList();
                helper.assertValueEqual(redirects.size(), 1, "Production mixin applied to vanilla command class");
                var redirect = redirects.getFirst();
                redirect.setAccessible(true);
                for (GameType type : GameType.values()) {
                    redirect.invoke(null, server, type);
                    helper.assertValueEqual(server.getDefaultGameType(), GameType.SURVIVAL,
                            "Intercepted host-default update must not change join mode for " + type);
                }
                helper.assertValueEqual(dispatcher.execute("gamemode creative", source), 1, "Personal command works");
                helper.assertTrue(f.player.isCreative(), "Host can still enter Creative");
                helper.assertValueEqual(server.getDefaultGameType(), GameType.SURVIVAL, "Personal command leaves default alone");
                helper.assertValueEqual(dispatcher.execute("gamemode creative", source), 0, "Same-mode command remains a no-op");
                GameModeCommand.setGameMode(f.player, GameType.SPECTATOR);
                helper.assertTrue(f.player.isSpectator(), "Programmatic command path still changes personal mode");
                helper.assertValueEqual(server.getDefaultGameType(), GameType.SURVIVAL, "Programmatic path leaves default alone");
                dispatcher.execute("defaultgamemode adventure", source);
                helper.assertValueEqual(server.getDefaultGameType(), GameType.ADVENTURE,
                        "Explicit defaultgamemode still deliberately changes join default");
                helper.assertValueEqual(dispatcher.execute("gamemode survival", source), 1, "Can return to Survival");
                helper.assertValueEqual(server.getDefaultGameType(), GameType.ADVENTURE, "Personal changes preserve explicit default");
            } catch (ReflectiveOperationException | com.mojang.brigadier.exceptions.CommandSyntaxException error) {
                throw new IllegalStateException("Cannot verify personal/default game mode separation", error);
            } finally {
                server.setDefaultGameType(previousDefault);
            }
        }
        helper.succeed();
    }
}
