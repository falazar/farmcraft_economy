package com.falazar.farmupcraft;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FruitHarvestSkill {
    public static final String DATA_KEY = "farmupcraft_fruit_skill_penalties";
    private static final Set<String> EXCLUDED = Set.of("cinnamonitem", "maplesyrupitem");

    private FruitHarvestSkill() {}

    public static boolean isFruitOrNut(String item) {
        if (item.equals("minecraft:apple")) return true;
        if (!item.startsWith("pamhc2trees:")) return false;
        String path = item.substring("pamhc2trees:".length());
        return path.endsWith("item") && !EXCLUDED.contains(path);
    }

    public static int effectiveCount(int netPickups, int penalty) {
        return (int) Math.max(0L, (long) netPickups - penalty);
    }

    public static boolean shouldReduce(int roll) {
        if (roll < 0 || roll >= 100) throw new IllegalArgumentException("Roll must be 0..99");
        return roll < 50;
    }

    public static List<String> candidates(Map<String, Integer> progress, String harvested) {
        return progress.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(harvested) && isFruitOrNut(entry.getKey())
                        && entry.getValue() > 0)
                .map(Map.Entry::getKey).sorted().toList();
    }
}
