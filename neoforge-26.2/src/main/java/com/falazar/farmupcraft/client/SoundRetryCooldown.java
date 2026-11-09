package com.falazar.farmupcraft.client;

import java.util.concurrent.TimeUnit;

public final class SoundRetryCooldown {
    private static final long INTERVAL = TimeUnit.MINUTES.toNanos(5);
    private boolean failed;
    private long failedAt;

    public void recordAttempt(boolean loaded, long now) {
        failed = !loaded;
        failedAt = now;
    }

    public boolean hasFailed() {
        return failed;
    }

    public boolean retryDue(long now) {
        return failed && now - failedAt >= INTERVAL;
    }
}
