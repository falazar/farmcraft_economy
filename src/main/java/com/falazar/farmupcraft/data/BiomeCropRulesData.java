package com.falazar.farmupcraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
/**
 * Represents crop rules associated with a biome, including allowed crops and the number of random crops to be selected.
 * <p>
 * This class provides functionality to serialize and deserialize crop rules data using JSON codecs.
 */
public class BiomeCropRulesData {

    public static final Codec<BiomeCropRulesData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.list(BuiltInRegistries.ITEM.byNameCodec()).fieldOf("allowed_crops").forGetter(BiomeCropRulesData::getAllowedCrops),
                    Codec.INT.fieldOf("random_crop_count").forGetter(BiomeCropRulesData::getRandomCropCount)
            ).apply(instance, BiomeCropRulesData::new)
    );

    private final List<Item> allowedCrops;
    private final int randomCropCount;

    // Default empty instance with no crops and zero count
    public static final BiomeCropRulesData EMPTY = new BiomeCropRulesData(new ArrayList<>(), 0);
    /**
     * Constructs a new BiomeCropRulesData instance with the specified allowed crops and a default random crop count of 15.
     *
     * @param allowedCrops The list of crops that are allowed.
     */
    public BiomeCropRulesData(List<Item> allowedCrops) {
      this(allowedCrops, 15);
    }

    /**
     * Constructs a new BiomeCropRulesData instance with the specified allowed crops and random crop count.
     *
     * @param allowedCrops The list of crops that are allowed.
     * @param randomCropCount The number of crops to randomly select.
     */
    public BiomeCropRulesData(List<Item> allowedCrops, int randomCropCount) {
        this.allowedCrops = allowedCrops;
        this.randomCropCount = randomCropCount;
    }

    /**
     * Retrieves the list of allowed crops.
     *
     * @return The list of allowed crops.
     */
    public List<Item> getAllowedCrops() {
        return allowedCrops;
    }

    /**
     * Retrieves the number of crops to randomly select.
     *
     * @return The number of crops to randomly select.
     */
    public int getRandomCropCount() {
        return randomCropCount;
    }

    /**
     * Gets a random selection of crops based on the world seed and the random crop count.
     * <p>
     * The method shuffles the list of allowed crops and then selects a subset of crops based on the `randomCropCount`.
     * If the number of allowed crops is less than the specified count, it will return all available crops.
     *
     * @param seed The seed of the world to ensure consistent randomness.
     * @return A list of randomly selected crops.
     */
    public List<Item> getRandomCrops(long seed) {
        Random random = new Random(seed);

        // Create a copy of the allowed crops list to shuffle and select from
        List<Item> shuffledCrops = new ArrayList<>(allowedCrops);

        // Shuffle the list of crops using the provided seed for consistent randomness
        Collections.shuffle(shuffledCrops, random);

        // Determine the limit to ensure we do not exceed the number of available crops
        int limit = Math.min(randomCropCount, shuffledCrops.size());

        // Return a sublist of the shuffled crops based on the computed limit
        return shuffledCrops.subList(0, limit);
    }
}
