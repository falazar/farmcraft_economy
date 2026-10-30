package com.falazar.farmupcraft.util;

import java.util.List;

public final class PollinationCooldown {
    public static final long INTERVAL = 6000L;
    public static final int MAX_BOOSTS = 5;

    private PollinationCooldown() {
    }

    public static boolean ready(List<Long> successes, long now) {
        return successes.stream().filter(time -> isRecent(time, now)).count() < MAX_BOOSTS;
    }

    public static void recordSuccess(List<Long> successes, long now) {
        successes.removeIf(time -> !isRecent(time, now));
        successes.add(now);
    }

    private static boolean isRecent(long time, long now) {
        return now < time || now - time < INTERVAL;
    }
}
