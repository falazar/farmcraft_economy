package com.falazar.farmupcraft;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record HarvestCraftFeatureOrder() implements BiomeModifier {
    private static final DeferredRegister<Codec<? extends BiomeModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, FarmUpCraft.MODID);
    private static final RegistryObject<Codec<HarvestCraftFeatureOrder>> CODEC =
            SERIALIZERS.register("harvestcraft_feature_order", () -> Codec.unit(new HarvestCraftFeatureOrder()));

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.AFTER_EVERYTHING) return;
        var features = builder.getGenerationSettings().getFeatures(GenerationStep.Decoration.VEGETAL_DECORATION);
        List<Holder<PlacedFeature>> harvestcraft = new ArrayList<>();
        features.removeIf(feature -> {
            boolean matches = feature.unwrapKey().map(key -> {
                String namespace = key.location().getNamespace();
                return namespace.equals("pamhc2trees") || namespace.equals("pamhc2crops");
            }).orElse(false);
            if (matches) harvestcraft.add(feature);
            return matches;
        });
        // Shared features must have the same relative order in every biome to avoid generation cycles.
        harvestcraft.sort(Comparator.comparing(feature -> feature.unwrapKey().orElseThrow().location()));
        features.addAll(harvestcraft);
    }

    @Override
    public Codec<? extends BiomeModifier> codec() {
        return CODEC.get();
    }
}
