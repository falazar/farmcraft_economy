package com.falazar.farmupcraft.util;

import com.falazar.farmupcraft.data.VillageData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic, position-based village feed requirements using real HarvestCraft crop IDs. */
public class AnimalGrainAssigner {
    public static final List<String> ANIMAL_TYPES = List.of("cow", "sheep", "pig", "chicken", "duck");
    public static final List<String> GRAIN_POOL = List.of(
            "pamhc2crops:cornitem", "minecraft:wheat", "pamhc2crops:alfalfaitem",
            "pamhc2crops:milletitem", "pamhc2crops:ryeitem", "pamhc2crops:oatsitem",
            "pamhc2crops:riceitem", "pamhc2crops:barleyitem");

    public static void assignGrains(VillageData village) {
        int pool = GRAIN_POOL.size();
        // Keep the legacy absolute-position rotation, computing the hash without integer overflow.
        int hash = (int) (Math.abs((long) village.getPosition().x() * 31 + village.getPosition().z()) % pool);
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (int i = 0; i < 4; i++) {
            map.put(ANIMAL_TYPES.get(i), List.of(
                    GRAIN_POOL.get((hash + i * 2) % pool),
                    GRAIN_POOL.get((hash + i * 2 + 1) % pool)));
        }
        village.setAnimalGrainsFromMap(map);
        ensureDuckGrain(village);
    }

    public static boolean ensureDuckGrain(VillageData village) {
        if (!village.getGrainsForAnimal("duck").isEmpty()) return false;
        var map = village.getAnimalGrainsMap();
        int index = (int) (Math.abs((long) village.getPosition().x() * 31 + village.getPosition().z() + 17)
                % GRAIN_POOL.size());
        map.put("duck", List.of(GRAIN_POOL.get(index)));
        village.setAnimalGrainsFromMap(map);
        return true;
    }

    public static String displayName(String itemId) {
        String name = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        if (name.endsWith("item")) {
            name = name.substring(0, name.length() - 4);
        }
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
