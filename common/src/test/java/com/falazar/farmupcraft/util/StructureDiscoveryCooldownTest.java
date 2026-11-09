package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StructureDiscoveryCooldownTest {
    @Test
    void repeatedVillageDiscoveryWaitsExactlyTwoMinutesWithoutExtendingOnSpam() {
        var cooldown = new StructureDiscoveryCooldown();
        var player = UUID.randomUUID();
        assertTrue(cooldown.shouldShow(player, "minecraft:overworld", 1, true, 0));
        assertFalse(cooldown.shouldShow(player, "minecraft:overworld", 1, true, 1));
        assertFalse(cooldown.shouldShow(player, "minecraft:overworld", 1, true, 119_999));
        assertTrue(cooldown.shouldShow(player, "minecraft:overworld", 1, true, 120_000));
        assertFalse(cooldown.shouldShow(player, "minecraft:overworld", 1, true, 120_001));
    }

    @Test
    void otherStructuresPlayersDimensionsAndWildernessRemainIndependent() {
        var cooldown = new StructureDiscoveryCooldown();
        var player = UUID.randomUUID();
        assertTrue(cooldown.shouldShow(player, "overworld", 1, true, 0));
        assertTrue(cooldown.shouldShow(player, "overworld", 2, true, 1));
        assertTrue(cooldown.shouldShow(UUID.randomUUID(), "overworld", 1, true, 1));
        assertTrue(cooldown.shouldShow(player, "nether", 1, true, 1));
        assertTrue(cooldown.shouldShow(player, "overworld", 1, false, 1));
        assertTrue(cooldown.shouldShow(player, "overworld", 1, false, 2));
        cooldown.clear();
        assertTrue(cooldown.shouldShow(player, "overworld", 1, true, 3));
    }
}
