package com.falazar.farmupcraft;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VillagerHomeSignsTest {
    @Test
    void placementAttemptsRequireAFullRealWorldDayAndSurviveMetadataChanges() {
        long now = 1_800_000_000_000L;
        long day = 24L * 60 * 60 * 1000;
        var persistent = new CompoundTag();
        assertTrue(VillagerHomeSigns.attemptAllowed(persistent, now));
        persistent.putLong("farmupcraft_home_sign_last_attempt_ms", now);
        persistent.put("farmupcraft_home_sign", new CompoundTag());
        var saved = persistent.copy();
        assertFalse(VillagerHomeSigns.attemptAllowed(saved, now + day - 1));
        assertTrue(VillagerHomeSigns.attemptAllowed(saved, now + day));
        assertFalse(VillagerHomeSigns.attemptAllowed(saved, now - 1),
                "A clock rollback cannot bypass the daily cap");
    }
}
