package com.falazar.farmupcraft.client;

import java.util.concurrent.TimeUnit;

public final class SoundRetryCooldown {
    private static final long INTERVAL = TimeUnit.HOURS.toNanos(1);
    private boolean loggedFailure;
    private long lastLoggedAt;

    public boolean shouldLogFailure(long now) {
        if (loggedFailure && now - lastLoggedAt < INTERVAL) {
            return false;
        }
        loggedFailure = true;
        lastLoggedAt = now;
        return true;
    }
}
