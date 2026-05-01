package com.falazar.farmupcraft.util;

import com.falazar.farmupcraft.data.VillageData;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministically assigns two grain/hay requirements per farm animal type
 * for a village, keyed off the village's chunk XZ position.
 *
 * <p>
 * Animal types covered: cow, sheep, pig, chicken.
 * </p>
 * <p>
 * Grain pool is drawn from PAM's HarvestCraft 2 Crops and vanilla.
 * </p>
 */
public class AnimalGrainAssigner {

    public static final List<String> ANIMAL_TYPES = Arrays.asList("cow", "sheep", "pig", "chicken");

    /**
     * All available grain and hay items.
     * Each animal type gets 2 consecutive entries (with wrap-around),
     * offset by the village XZ hash so different villages get different combos.
     */
    public static final List<String> GRAIN_POOL = Arrays.asList(
            "pamhc2crops:cornitem",
            "minecraft:wheat",
            "pamhc2crops:alfalfaitem",
            "pamhc2crops:milletitem",
            "pamhc2crops:ryeitem",
            "pamhc2crops:oatsitem",
            "pamhc2crops:riceitem",
            "pamhc2crops:barleyitem");

    /**
     * Assigns two grains per farm animal type based on the village's chunk
     * position,
     * saves the result into the village's animalGrains field.
     *
     * @param village the village to assign grains for
     */
    public static void assignGrains(VillageData village) {
        int pool = GRAIN_POOL.size();
        // Deterministic hash from village chunk XZ — same position always gives same
        // grains.
        int hash = Math.abs(village.getPosition().x * 31 + village.getPosition().z) % pool;

        Map<String, List<String>> map = new LinkedHashMap<>();
        for (int i = 0; i < ANIMAL_TYPES.size(); i++) {
            String grain1 = GRAIN_POOL.get((hash + i * 2) % pool);
            String grain2 = GRAIN_POOL.get((hash + i * 2 + 1) % pool);
            map.put(ANIMAL_TYPES.get(i), Arrays.asList(grain1, grain2));
        }
        village.setAnimalGrainsFromMap(map);
    }

    /**
     * Returns a human-readable display name for a grain item ID.
     * Strips the mod namespace and "item" suffix.
     */
    public static String displayName(String itemId) {
        // e.g. "pamhc2crops:cornitem" -> "Corn"
        String name = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        if (name.endsWith("item"))
            name = name.substring(0, name.length() - 4);
        // Capitalise first letter
        if (!name.isEmpty())
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        return name;
    }
}
