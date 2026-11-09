package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LanBotLauncherTest {
    @Test
    void validatesDefaultAndCustomMinecraftBotNames() {
        assertEquals("BridgeBot", LanBotLauncher.validateBotName(null));
        assertEquals("BridgeBot", LanBotLauncher.validateBotName("  "));
        assertEquals("Alice_2", LanBotLauncher.validateBotName("Alice_2"));
        assertThrows(IllegalArgumentException.class, () -> LanBotLauncher.validateBotName("ab"));
        assertThrows(IllegalArgumentException.class, () -> LanBotLauncher.validateBotName("bad name!"));
        assertThrows(IllegalArgumentException.class, () -> LanBotLauncher.validateBotName("a".repeat(17)));
    }

    @Test
    void rejectsInvalidPortBeforeSpawningProcess() {
        assertThrows(IllegalArgumentException.class, () -> LanBotLauncher.startBot("BridgeBot", -1));
        assertThrows(IllegalArgumentException.class, () -> LanBotLauncher.startBot("BridgeBot", 0));
        assertThrows(IllegalArgumentException.class, () -> LanBotLauncher.startBot("BridgeBot", 70000));
        assertThrows(IllegalArgumentException.class, () -> LanBotLauncher.startBot("stop", 25565));
    }
}
