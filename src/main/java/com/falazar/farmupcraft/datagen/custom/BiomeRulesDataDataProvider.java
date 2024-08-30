package com.falazar.farmupcraft.datagen.custom;

import com.falazar.farmupcraft.data.BiomeRulesData;
import com.google.common.collect.Sets;
import com.mojang.serialization.JsonOps;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;


public abstract class BiomeRulesDataDataProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;
    private final String modid;
    public BiomeRulesDataDataProvider(PackOutput pOutput, String modid) {
        this.pathProvider = pOutput.createPathProvider(PackOutput.Target.DATA_PACK, new ResourceLocation(modid,"farmupcraft/biome_rules").getPath());
        this.modid = modid;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput pOutput) {
        Set<ResourceLocation> set = Sets.newHashSet();
        Set<ResourceLocation> taskSet = Sets.newHashSet();
        List<CompletableFuture<?>> list = new ArrayList<>();
        this.buildBiomeRuleData((biomeRulesData -> {
            if (!set.add(biomeRulesData.resourceLocation())) {
                throw new IllegalStateException("Duplicate Biome Rule" + biomeRulesData.resourceLocation());
            } else {

                BiomeRulesData.CODEC.encodeStart(JsonOps.INSTANCE, biomeRulesData.data)
                        .get()
                        .ifLeft(e -> list.add(DataProvider.saveStable(pOutput, e, this.pathProvider.json(biomeRulesData.resourceLocation()))))
                        .ifRight(partial -> LOGGER.error("Failed to create biome rules data {}, due to {}", biomeRulesData.resourceLocation(), partial));
            }
        }));
        return CompletableFuture.allOf(list.toArray((p_253414_) -> {
            return new CompletableFuture[p_253414_];
        }));
    }

    public record BiomeRulesDataConsumer(ResourceLocation resourceLocation, BiomeRulesData data){}

    protected abstract void buildBiomeRuleData(Consumer<BiomeRulesDataConsumer> pWriter);

    @Override
    public String getName() {
        return modid + " Biome Rules Data";
    }
}
