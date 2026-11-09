package com.falazar.farmupcraft.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SoundRetryCooldownTest {
    private static final long FIVE_MINUTES = TimeUnit.MINUTES.toNanos(5);

    @Test
    void failureWaitsExactlyFiveRealTimeMinutes() {
        var cooldown = new SoundRetryCooldown();
        assertFalse(cooldown.hasFailed());
        cooldown.recordAttempt(false, 10);
        assertTrue(cooldown.hasFailed());
        assertFalse(cooldown.retryDue(10));
        assertFalse(cooldown.retryDue(10 + FIVE_MINUTES - 1));
        assertTrue(cooldown.retryDue(10 + FIVE_MINUTES));
    }

    @Test
    void manualAttemptIsNotGatedAndSuccessClearsFailure() {
        var cooldown = new SoundRetryCooldown();
        cooldown.recordAttempt(false, 0);
        cooldown.recordAttempt(true, 1);
        assertFalse(cooldown.hasFailed());
        assertFalse(cooldown.retryDue(FIVE_MINUTES));
    }

    @Test
    void failedManualOrAutomaticAttemptRestartsFiveMinutes() {
        var cooldown = new SoundRetryCooldown();
        cooldown.recordAttempt(false, 0);
        cooldown.recordAttempt(false, 100);
        assertFalse(cooldown.retryDue(FIVE_MINUTES));
        assertTrue(cooldown.retryDue(FIVE_MINUTES + 100));
    }

    @Test
    void nanoTimeWraparoundPreservesElapsedDuration() {
        var cooldown = new SoundRetryCooldown();
        long start = Long.MAX_VALUE - FIVE_MINUTES / 2;
        cooldown.recordAttempt(false, start);
        assertFalse(cooldown.retryDue(start + FIVE_MINUTES - 1));
        assertTrue(cooldown.retryDue(start + FIVE_MINUTES));
    }
}
