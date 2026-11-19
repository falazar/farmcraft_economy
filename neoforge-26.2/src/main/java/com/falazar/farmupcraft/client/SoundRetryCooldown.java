package com.falazar.farmupcraft.client;

import java.util.concurrent.TimeUnit;

public final class SoundRetryCooldown {
    private static final long INTERVAL = TimeUnit.HOURS.toNanos(1);
    private static final long RETRY_INTERVAL = TimeUnit.MINUTES.toNanos(5);
    private boolean loggedFailure;
    private long lastLoggedAt;
    private boolean failedAttempt;
    private long lastFailedAt;

    public void recordAttempt(boolean initialized, long now) {
        failedAttempt = !initialized;
        if (failedAttempt)
            lastFailedAt = now;
    }

    public boolean allowsAutomaticRetry(long now) {
        return !failedAttempt || now - lastFailedAt >= RETRY_INTERVAL;
    }

    public boolean shouldLogFailure(long now) {
        if (loggedFailure && now - lastLoggedAt < INTERVAL) {
            return false;
        }
        loggedFailure = true;
        lastLoggedAt = now;
        return true;
    }
}
