package com.falazar.farmupcraft.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PastureEntryCooldown {
    public static final long COOLDOWN_MS = 600_000L;
    private record Key(UUID player, String dimension, long chunk) {}
    private final Map<Key, Long> shownAt = new HashMap<>();

    public boolean shouldShow(UUID player, String dimension, long chunk, long now) {
        shownAt.values().removeIf(time -> now - time >= COOLDOWN_MS);
        var key = new Key(player, dimension, chunk);
        if (shownAt.containsKey(key)) return false;
        shownAt.put(key, now);
        return true;
    }

    public void clear() {
        shownAt.clear();
    }
}
