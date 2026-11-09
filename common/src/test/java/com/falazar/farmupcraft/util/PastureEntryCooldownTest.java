package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PastureEntryCooldownTest {
    @Test
    void exactTenMinuteCooldownDoesNotExtendOnReentry() {
        var cooldown = new PastureEntryCooldown();
        var player = UUID.randomUUID();
        assertTrue(cooldown.shouldShow(player, "minecraft:overworld", 1, 1000));
        assertFalse(cooldown.shouldShow(player, "minecraft:overworld", 1, 1001));
        assertFalse(cooldown.shouldShow(player, "minecraft:overworld", 1, 600999));
        assertTrue(cooldown.shouldShow(player, "minecraft:overworld", 1, 601000));
    }

    @Test
    void playersPasturesAndDimensionsAreIndependentAndResetAtShutdown() {
        var cooldown = new PastureEntryCooldown();
        var player = UUID.randomUUID();
        assertTrue(cooldown.shouldShow(player, "minecraft:overworld", 1, 0));
        assertTrue(cooldown.shouldShow(UUID.randomUUID(), "minecraft:overworld", 1, 1));
        assertTrue(cooldown.shouldShow(player, "minecraft:overworld", 2, 1));
        assertTrue(cooldown.shouldShow(player, "minecraft:the_nether", 1, 1));
        assertFalse(cooldown.shouldShow(player, "minecraft:overworld", 1, 2));
        cooldown.clear();
        assertTrue(cooldown.shouldShow(player, "minecraft:overworld", 1, 3));
    }
}
