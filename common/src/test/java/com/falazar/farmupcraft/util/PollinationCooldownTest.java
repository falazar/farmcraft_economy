package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PollinationCooldownTest {
    @Test
    void requiresExactlyFiveMinutesAndDoesNotResetWhenTimeMovesBackwards() {
        var times = new ArrayList<Long>();
        for (int i = 0; i < 5; i++) {
            assertTrue(PollinationCooldown.ready(times, 100));
            PollinationCooldown.recordSuccess(times, 100);
        }
        assertFalse(PollinationCooldown.ready(times, 100));
        assertFalse(PollinationCooldown.ready(times, 6099));
        assertTrue(PollinationCooldown.ready(times, 6100));
        assertFalse(PollinationCooldown.ready(times, 99));
        PollinationCooldown.recordSuccess(times, 6100);
        assertEquals(List.of(6100L), times);
    }

    @Test
    void staggeredBoostsExpireIndividuallyRatherThanResettingABurstWindow() {
        var times = new ArrayList<>(List.of(0L, 1000L, 2000L, 3000L, 4000L));
        assertFalse(PollinationCooldown.ready(times, 5999));
        assertTrue(PollinationCooldown.ready(times, 6000));
        PollinationCooldown.recordSuccess(times, 6000);
        assertEquals(List.of(1000L, 2000L, 3000L, 4000L, 6000L), times);
        assertFalse(PollinationCooldown.ready(times, 6000));
        assertFalse(PollinationCooldown.ready(times, 6999));
        assertTrue(PollinationCooldown.ready(times, 7000));
    }
}
