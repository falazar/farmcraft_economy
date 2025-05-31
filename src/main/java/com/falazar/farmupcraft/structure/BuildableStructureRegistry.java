package com.falazar.farmupcraft.structure;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class BuildableStructureRegistry {

    private static final Map<ResourceLocation, BuildableStructure> STRUCTURES = new HashMap<>();

    public static void register(ResourceLocation id, BuildableStructure structure) {
        STRUCTURES.put(id, structure);
    }

    public static Map<ResourceLocation, BuildableStructure> getStructures() {
        return STRUCTURES;
    }

    public static BuildableStructure get(ResourceLocation id) {
        return STRUCTURES.get(id);
    }

    public static boolean contains(ResourceLocation id) {
        return STRUCTURES.containsKey(id);
    }

    public static void clear() {
        STRUCTURES.clear();
    }
} 