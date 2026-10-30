package com.falazar.farmupcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class PlayerCommandTest {
    @Test
    void walletAdjustmentsRejectDebtAndOverflowBeforeMutation() {
        assertEquals(0, PlayerCommand.adjustedBalance(0, 0));
        assertEquals(75, PlayerCommand.adjustedBalance(100, -25));
        assertEquals(0, PlayerCommand.adjustedBalance(100, -100));
        assertEquals(125, PlayerCommand.adjustedBalance(100, 25));
        assertEquals(Integer.MAX_VALUE, PlayerCommand.adjustedBalance(Integer.MAX_VALUE - 1, 1));
        assertThrows(IllegalArgumentException.class, () -> PlayerCommand.adjustedBalance(10, -11));
        assertThrows(ArithmeticException.class, () -> PlayerCommand.adjustedBalance(Integer.MAX_VALUE, 1));
    }

    @Test
    void everyPlayerBranchRegistersAndAdminLookupsStayRestricted(MinecraftServer server) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        PlayerCommand.register(dispatcher);
        MarketCommandTest.Feedback feedback = new MarketCommandTest.Feedback();
        CommandSourceStack ordinary = MarketCommandTest.source(server, PermissionSet.NO_PERMISSIONS, feedback);
        CommandSourceStack moderator = MarketCommandTest.source(server, LevelBasedPermissionSet.MODERATOR, feedback);
        CommandSourceStack admin = MarketCommandTest.source(server, LevelBasedPermissionSet.GAMEMASTER, feedback);
        var root = dispatcher.getRoot().getChild("player");
        for (String branch : List.of("admin", "givecoins")) {
            assertFalse(root.getChild(branch).canUse(ordinary));
            assertFalse(root.getChild(branch).canUse(moderator));
            assertTrue(root.getChild(branch).canUse(admin));
        }
        assertTrue(root.getChild("info").canUse(ordinary));
        assertFalse(root.getChild("info").getChild("playerName").canUse(ordinary));
        assertTrue(root.getChild("info").getChild("playerName").canUse(admin));
        for (String command : List.of("player info", "player info Alex", "player leavevillage",
                "player givevillagecoins 0", "player takevillagecoins 10", "player givecoins Alex -10000",
                "player border show", "player border color teal", "player admin joinvillage \"Test Village\"",
                "player admin givecoins -10000")) {
            var parsed = dispatcher.parse(command, admin);
            assertTrue(parsed.getExceptions().isEmpty(), command);
            assertEquals("", parsed.getReader().getRemaining(), command);
            assertNotNull(parsed.getContext().getCommand(), command);
        }
        assertFalse(dispatcher.parse("player givevillagecoins -1", ordinary).getExceptions().isEmpty());
        assertFalse(dispatcher.parse("player takevillagecoins -1", ordinary).getExceptions().isEmpty());
        assertFalse(dispatcher.parse("player admin givecoins -10001", admin).getExceptions().isEmpty());
    }

    @Test
    void playerOnlyEntryPointsReportMissingPlayerWithoutInventingAWorld(MinecraftServer server) {
        assertNull(server.overworld());
        MarketCommandTest.Feedback feedback = new MarketCommandTest.Feedback();
        CommandSourceStack source = MarketCommandTest.source(server, PermissionSet.NO_PERMISSIONS, feedback);
        assertNull(PlayerCommand.getPlayer(source));
        assertEquals(0, PlayerCommand.givePlayerCoins(source, 10));
        assertEquals(0, PlayerCommand.setVillage(source, "none"));
        assertEquals(0, PlayerCommand.giveVillageCoins(source, 10));
        assertEquals(0, PlayerCommand.takeVillageCoins(source, 10));
        assertEquals(0, PlayerCommand.setBorderShow(source));
        assertEquals(0, PlayerCommand.setBorderColor(source, "blue"));
        assertEquals(0, PlayerCommand.leaveVillage(source));
        assertTrue(feedback.messages.contains("Player not found."));
        assertTrue(feedback.messages.contains("Only a player can use this command."));
    }
}
