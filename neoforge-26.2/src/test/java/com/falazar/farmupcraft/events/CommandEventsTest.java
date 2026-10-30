package com.falazar.farmupcraft.events;

import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(EphemeralTestServerProvider.class)
class CommandEventsTest {
    @Test
    void allFourteenOriginalCommandRootsRegisterOnTheRealDispatcher(MinecraftServer server) {
        var root = server.getCommands().getDispatcher().getRoot();
        for (String command : List.of("show", "managers", "database", "player", "plot", "village",
                "market", "farmcraft", "tester", "npc", "structures", "zone", "ai", "scan")) {
            assertNotNull(root.getChild(command), command);
        }
    }
}
