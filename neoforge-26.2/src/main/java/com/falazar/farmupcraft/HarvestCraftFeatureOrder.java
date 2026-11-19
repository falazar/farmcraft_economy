package com.falazar.farmupcraft;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

public record HarvestCraftFeatureOrder() implements BiomeModifier {
    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, FarmUpCraftNeoForge.MOD_ID);
    private static final Supplier<MapCodec<HarvestCraftFeatureOrder>> CODEC =
            SERIALIZERS.register("harvestcraft_feature_order", () -> MapCodec.unit(new HarvestCraftFeatureOrder()));

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
                String namespace = key.identifier().getNamespace();
                return namespace.equals("pamhc2trees") || namespace.equals("pamhc2crops");
            }).orElse(false);
            if (matches) harvestcraft.add(feature);
            return matches;
        });
        // Shared features must have the same relative order in every biome to avoid generation cycles.
        harvestcraft.sort(Comparator.comparing(feature -> feature.unwrapKey().orElseThrow().identifier().toString()));
        features.addAll(harvestcraft);
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC.get();
    }
}
