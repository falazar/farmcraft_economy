package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.data.rules.crop.CropRules;
import com.falazar.farmupcraft.data.rules.crop.ItemListCropRule;
import com.falazar.farmupcraft.util.FUCTags;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.Optional;

/** Datapack biome selection and crop rules, retaining the legacy codec fields. */
public class BiomeRulesData {
    public static final Codec<BiomeRulesData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    TagKey.codec(Registries.BIOME).fieldOf("biome").forGetter(BiomeRulesData::getBiome),
                    TagKey.codec(Registries.BIOME).optionalFieldOf("biome_filter").forGetter(BiomeRulesData::getBiomeFilter),
                    CropRules.DIRECT_CODEC.fieldOf("crop_rules").forGetter(BiomeRulesData::getCropRules)
            ).apply(instance, BiomeRulesData::new));

    public static final BiomeRulesData EMPTY =
            new BiomeRulesData(FUCTags.EMPTY_BIOME_TAG, Optional.empty(), ItemListCropRule.EMPTY);

    private final TagKey<Biome> biome;
    private final Optional<TagKey<Biome>> biomeFilter;
    private final CropRules cropRules;

    public BiomeRulesData(TagKey<Biome> biome, Optional<TagKey<Biome>> biomeFilter, CropRules cropRules) {
        this.biome = biome;
        this.biomeFilter = biomeFilter;
        this.cropRules = cropRules;
    }

    public TagKey<Biome> getBiome() {
        return biome;
    }

    public Optional<TagKey<Biome>> getBiomeFilter() {
        return biomeFilter;
    }

    public CropRules getCropRules() {
        return cropRules;
    }
}
