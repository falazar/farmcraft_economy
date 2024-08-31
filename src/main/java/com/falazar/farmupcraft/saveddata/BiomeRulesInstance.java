package com.falazar.farmupcraft.saveddata;

import com.falazar.farmupcraft.data.BiomeRulesData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the rules and configurations for a specific biome in terms of crops and other data.
 * <p>
 * This class holds information about crop rules and random crops for a biome, and provides
 * functionality to determine if certain items are valid crops for that biome.
 */
public class BiomeRulesInstance {

    /**
     * Codec for serializing and deserializing {@link BiomeRulesInstance} instances.
     * <p>
     * This codec is used to encode and decode {@link BiomeRulesInstance} data in NBT format.
     */
    public static final Codec<BiomeRulesInstance> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BiomeRulesData.CODEC.fieldOf("rules_data").forGetter(BiomeRulesInstance::getRulesData),
                    Codec.list(BuiltInRegistries.ITEM.byNameCodec()).fieldOf("random_crops").forGetter(BiomeRulesInstance::getRandomCrops)
            ).apply(instance, BiomeRulesInstance::new)
    );

    private final BiomeRulesData rulesData;
    private List<Item> randomCrops;
    private boolean shouldSave = true;

    /**
     * An empty instance of {@link BiomeRulesInstance} with default settings.
     * <p>
     * This instance is used as a placeholder or default value when no specific rules are defined.
     */
    public static final BiomeRulesInstance EMPTY = new BiomeRulesInstance(BiomeRulesData.EMPTY);

    /**
     * Constructs a new {@link BiomeRulesInstance} with the specified rules data and random crop seed.
     * <p>
     * The random crops list is generated based on the provided seed.
     *
     * @param rulesData The {@link BiomeRulesData} that defines the rules for the biome.
     */
    public BiomeRulesInstance(BiomeRulesData rulesData) {
        this(rulesData, Collections.emptyList());
    }

    /**
     * Constructs a new {@link BiomeRulesInstance} with the specified rules data and a predefined list of random crops.
     *
     * @param rulesData   The {@link BiomeRulesData} that defines the rules for the biome.
     * @param randomCrops A list of items representing the random crops for the biome.
     */
    public BiomeRulesInstance(BiomeRulesData rulesData, List<Item> randomCrops) {
        this.rulesData = rulesData;
        this.randomCrops = randomCrops;
    }

    /**
     * Gets the {@link BiomeRulesData} associated with this instance.
     *
     * @return The {@link BiomeRulesData} object containing rules and settings for the biome.
     */
    public BiomeRulesData getRulesData() {
        return rulesData;
    }

    /**
     * Gets the list of random crops for this biome.
     *
     * @return A list of {@link Item} objects that are considered as random crops for this biome.
     */
    private List<Item> getRandomCrops() {
        return randomCrops;
    }


    public List<Item> getCrops(ServerLevel level) {
        return getCrops(level, false);
    }

    public List<Item> getCrops(ServerLevel level, boolean newTry) {
        if (this.randomCrops.isEmpty() && newTry) {
            this.randomCrops = new ArrayList<>(this.rulesData.getCropRules().getCropItems(level));
        }
        return randomCrops;
    }

    public List<Item> getCropsWithRandomId(ServerLevel level, int id) {
        if (this.randomCrops.isEmpty()) {
            this.randomCrops = new ArrayList<>(this.rulesData.getCropRules().getCropItemsWithRandomId(level, id));
        }
        return randomCrops;
    }


    /**
     * Checks if a given item stack's item is considered a crop for this biome.
     *
     * @param itemStack The {@link ItemStack} to check.
     * @return True if the item is a valid crop for this biome, false otherwise.
     */
    public boolean biomeHasCrops(ItemStack itemStack) {
        return randomCrops.contains(itemStack.getItem());
    }

    /**
     * Sets whether this instance should be saved.
     *
     * @param shouldSave True if this instance should be saved, false otherwise.
     */
    public void setShouldSave(boolean shouldSave) {
        this.shouldSave = shouldSave;
    }

    /**
     * Checks if this instance is marked to be saved.
     *
     * @return True if this instance should be saved, false otherwise.
     */
    public boolean shouldSave() {
        return shouldSave;
    }
}
