package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.data.rules.crop.CropRules;
import com.falazar.farmupcraft.data.rules.crop.ItemListCropRule;
import com.falazar.farmupcraft.util.FUCTags;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Represents data associated with biome rules, including biome tags and crop rules.
 * <p>
 * This class provides functionality to serialize and deserialize biome rules data using JSON codecs.
 */
public class BiomeRulesData {

    // Codec for serializing and deserializing BiomeRulesData instances
    public static final Codec<BiomeRulesData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    TagKey.codec(Registries.BIOME).fieldOf("biome").forGetter(BiomeRulesData::getBiome), // Biome tag
                    CropRules.DIRECT_CODEC.fieldOf("crop_rules").forGetter(BiomeRulesData::getCropRules) // Crop rules data
                    // Add more fields here for other types of rules if needed
            ).apply(instance, BiomeRulesData::new)
    );

    private final TagKey<Biome> biome; // Tag for the biome
    private final CropRules cropRules; // Data related to crop rules in the biome

    // Empty instance with default values
    public static final BiomeRulesData EMPTY = new BiomeRulesData(FUCTags.EMPTY_BIOME_TAG, ItemListCropRule.EMPTY);

    /**
     * Constructs a new BiomeRulesData instance.
     *
     * @param biome The biome tag associated with this rules data.
     * @param cropRules The crop rules data associated with this biome.
     */
    public BiomeRulesData(TagKey<Biome> biome, CropRules cropRules) {
        this.biome = biome;
        this.cropRules = cropRules;
    }

    /**
     * Retrieves the biome tag for this rules data.
     *
     * @return The biome tag.
     */
    public TagKey<Biome> getBiome() {
        return biome;
    }

    /**
     * Retrieves the crop rules data for this biome.
     *
     * @return The crop rules data.
     */
    public CropRules getCropRules() {
        return cropRules;
    }
}
