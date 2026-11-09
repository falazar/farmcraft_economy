package com.falazar.farmupcraft.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class StructureDiscoveryCooldown {
    public static final long COOLDOWN_MS = 120_000L;
    private record Key(UUID player, String dimension, long structure) {}
    private final Map<Key, Long> shownAt = new HashMap<>();

    public boolean shouldShow(UUID player, String dimension, long structure, boolean inVillage, long now) {
        shownAt.values().removeIf(time -> now - time >= COOLDOWN_MS);
        if (!inVillage) return true;
        var key = new Key(player, dimension, structure);
        if (shownAt.containsKey(key)) return false;
        shownAt.put(key, now);
        return true;
    }

    public void clear() {
        shownAt.clear();
    }
}
