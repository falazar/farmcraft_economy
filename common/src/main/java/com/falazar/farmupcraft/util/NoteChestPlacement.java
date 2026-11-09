package com.falazar.farmupcraft.util;

import java.util.List;
import java.util.random.RandomGenerator;

public final class NoteChestPlacement {
    public record Spot(int x, int y, int z) {
        public long distanceSquared(Spot center) {
            long dx = (long) x - center.x, dy = (long) y - center.y, dz = (long) z - center.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private NoteChestPlacement() {}

    public static Spot choose(List<Spot> valid, Spot center, RandomGenerator random) {
        if (valid.isEmpty()) return null;
        long nearest = valid.stream().mapToLong(p -> p.distanceSquared(center)).min().orElseThrow();
        // Randomize nearby candidates, never a remote chest while central candidates exist.
        var nearby = valid.stream().filter(p -> p.distanceSquared(center) <= nearest + 16).toList();
        return nearby.get(random.nextInt(nearby.size()));
    }
}
