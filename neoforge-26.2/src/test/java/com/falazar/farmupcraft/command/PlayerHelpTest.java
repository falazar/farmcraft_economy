package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.util.PlayerOnboarding;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.PermissionSet;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class PlayerHelpTest {
    @Test
    void ordinaryPlayersCanReadBothHelpSpellingsWithoutAdminCommands(MinecraftServer server) throws Exception {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        FarmCraftCommand.register(dispatcher);
        var feedback = new MarketCommandTest.Feedback();
        var source = MarketCommandTest.source(server, PermissionSet.NO_PERMISSIONS, feedback);
        for (String command : List.of("farmupcraft help", "farmupcraft", "farmcraft help")) {
            feedback.messages.clear();
            assertEquals(1, dispatcher.execute(command, source));
            assertEquals(PlayerOnboarding.HELP, feedback.messages);
            assertTrue(feedback.messages.stream().noneMatch(line ->
                    line.contains("admin") || line.contains("runchecks") || line.contains("importall")));
        }
    }

    @Test
    void everyAdvertisedCommandParsesForAnOrdinaryPlayer(MinecraftServer server) {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        FarmCraftCommand.register(dispatcher);
        PlayerCommand.register(dispatcher);
        MarketCommand.register(dispatcher);
        VillageCommand.register(dispatcher);
        PlotCommand.register(dispatcher);
        BuildCommand.register(dispatcher);
        ShowBiomesCommand.register(dispatcher);
        var source = MarketCommandTest.source(server, PermissionSet.NO_PERMISSIONS,
                new MarketCommandTest.Feedback());
        for (String line : PlayerOnboarding.HELP) {
            if (!line.startsWith("/")) continue;
            String command = line.substring(1).split(" - ", 2)[0]
                    .replace("<food|wood|stone|general>", "food")
                    .replace("<name>", "TestVillage")
                    .replace("<player>", "TestPlayer")
                    .replace("<amount>", "1")
                    .replace("<distance>", "1")
                    .replace("<biome>", "plains")
                    .replace("<crop>", "wheat")
                    .replace("<type>", "farm")
                    .replace("<itemId>", "minecraft:stone");
            var parsed = dispatcher.parse(command, source);
            assertTrue(parsed.getExceptions().isEmpty(), command);
            assertEquals("", parsed.getReader().getRemaining(), command);
            assertNotNull(parsed.getContext().getCommand(), command);
        }
    }

    @Test
    void welcomeStartSurvivesCodecRoundTripAndLegacyRowsDefaultToUnset() {
        var player = new PlayerData(42, UUID.randomUUID(), null, 83);
        player.setFirstWelcomeDay(LocalDate.of(2026, 10, 29).toEpochDay());
        var json = PlayerData.CODEC.encodeStart(JsonOps.INSTANCE, player).getOrThrow().getAsJsonObject();
        var restored = PlayerData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(player.getFirstWelcomeDay(), restored.getFirstWelcomeDay());
        assertEquals(83, restored.getCoins());
        assertNull(restored.getHomeVillageUUID());
        JsonObject legacy = json.deepCopy();
        legacy.remove("first_welcome_day");
        assertEquals(PlayerOnboarding.UNSET_DAY,
                PlayerData.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow().getFirstWelcomeDay());
    }
}
