package com.falazar.farmupcraft.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SoundRetryCooldownTest {
    private static final long ONE_HOUR = TimeUnit.HOURS.toNanos(1);

    @Test
    void logsFirstFailureAndThenAtMostOncePerHour() {
        var throttle = new SoundRetryCooldown();
        assertTrue(throttle.shouldLogFailure(10));
        assertFalse(throttle.shouldLogFailure(10 + ONE_HOUR - 1));
        assertTrue(throttle.shouldLogFailure(10 + ONE_HOUR));
    }

    @Test
    void repeatedFailuresDoNotResetTheLogInterval() {
        var throttle = new SoundRetryCooldown();
        assertTrue(throttle.shouldLogFailure(0));
        assertFalse(throttle.shouldLogFailure(ONE_HOUR - 1));
        assertTrue(throttle.shouldLogFailure(ONE_HOUR));
    }

    @Test
    void nanoTimeWraparoundPreservesElapsedDuration() {
        var throttle = new SoundRetryCooldown();
        long start = Long.MAX_VALUE - ONE_HOUR / 2;
        assertTrue(throttle.shouldLogFailure(start));
        assertFalse(throttle.shouldLogFailure(start + ONE_HOUR - 1));
        assertTrue(throttle.shouldLogFailure(start + ONE_HOUR));
    }
}
