package com.falazar.farmupcraft.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SoundRetryCooldownTest {
    private static final long ONE_HOUR = TimeUnit.HOURS.toNanos(1);
    private static final long FIVE_MINUTES = TimeUnit.MINUTES.toNanos(5);

    @Test
    void failedAttemptDefersAutomaticRetriesUntilFiveMinuteBoundary() {
        var throttle = new SoundRetryCooldown();
        assertTrue(throttle.allowsAutomaticRetry(10));
        throttle.recordAttempt(false, 10);
        for (long elapsed = 0; elapsed < FIVE_MINUTES; elapsed += TimeUnit.SECONDS.toNanos(1))
            assertFalse(throttle.allowsAutomaticRetry(10 + elapsed));
        assertFalse(throttle.allowsAutomaticRetry(10 + FIVE_MINUTES - 1));
        assertTrue(throttle.allowsAutomaticRetry(10 + FIVE_MINUTES));
    }

    @Test
    void laterFailureRestartsDelayAndSuccessfulManualReloadRestoresMonitoringImmediately() {
        var throttle = new SoundRetryCooldown();
        throttle.recordAttempt(false, 0);
        assertTrue(throttle.allowsAutomaticRetry(FIVE_MINUTES));
        throttle.recordAttempt(false, FIVE_MINUTES);
        assertFalse(throttle.allowsAutomaticRetry(2 * FIVE_MINUTES - 1));
        throttle.recordAttempt(true, FIVE_MINUTES + 1);
        assertTrue(throttle.allowsAutomaticRetry(FIVE_MINUTES + 1));
        throttle.recordAttempt(false, FIVE_MINUTES + 2);
        assertFalse(throttle.allowsAutomaticRetry(FIVE_MINUTES + 2));
        assertTrue(throttle.allowsAutomaticRetry(2 * FIVE_MINUTES + 2));
    }

    @Test
    void retryDelaySurvivesNanoTimeWraparound() {
        var throttle = new SoundRetryCooldown();
        long start = Long.MAX_VALUE - FIVE_MINUTES / 2;
        throttle.recordAttempt(false, start);
        assertFalse(throttle.allowsAutomaticRetry(start + FIVE_MINUTES - 1));
        assertTrue(throttle.allowsAutomaticRetry(start + FIVE_MINUTES));
    }

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
