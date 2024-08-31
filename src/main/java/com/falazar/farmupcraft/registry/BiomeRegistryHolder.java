package com.falazar.farmupcraft.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;

public class BiomeRegistryHolder {
    public static Registry<Biome> BIOME_REGISTRY;
    private static final ResourceLocation EMPTY_RL = new ResourceLocation("b", "empty");
    private static final ResourceKey<Biome> EMPTY_RK = ResourceKey.create(Registries.BIOME, EMPTY_RL);
    public static void setupBiomeRegistry(MinecraftServer server) {
        BIOME_REGISTRY = server.registryAccess().registry(Registries.BIOME).get();
    }

    public static ResourceLocation convertToRL(int id) {
        if (id == -1) {
            return EMPTY_RL;
        }
        return BIOME_REGISTRY.getHolder(id).get().key().location();
    }

    public static ResourceKey<Biome> convertToRK(int id) {
        if (id == -1) {
            return EMPTY_RK;
        }
        return BIOME_REGISTRY.getHolder(id).get().key();
    }

    public static int convertToID(ResourceLocation biome) {
        return BIOME_REGISTRY.getId(BIOME_REGISTRY.get(biome));
    }

    public static int convertToID(ResourceKey<Biome> biome) {
        return BIOME_REGISTRY.getId(BIOME_REGISTRY.get(biome));
    }


    public static ResourceKey<Biome> convertToID(Holder<Biome> biome) {
        return biome.unwrapKey().orElse(EMPTY_RK);
    }

    public static Biome convertToBiome(ResourceKey<Biome> biome) {
        return BIOME_REGISTRY.get(biome);
    }
}