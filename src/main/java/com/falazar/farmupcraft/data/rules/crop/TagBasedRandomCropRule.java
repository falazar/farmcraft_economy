package com.falazar.farmupcraft.data.rules.crop;

import com.falazar.farmupcraft.registry.CropRulesRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Manages random crop rules based on item tags.
 * <p>
 * This class handles selecting a random number of items from a specified tag of items.
 */
public class TagBasedRandomCropRule implements CropRules{

    // Codec for serializing and deserializing TagBasedRandomCropRules
    public static final Codec<TagBasedRandomCropRule> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    TagKey.codec(Registries.ITEM).fieldOf("tag").forGetter(TagBasedRandomCropRule::getCropTag),
                    Codec.INT.fieldOf("random_crop_count").forGetter(TagBasedRandomCropRule::getRandomCropCount)
            ).apply(instance, TagBasedRandomCropRule::new)
    );
    public static final CropRulesType<TagBasedRandomCropRule> TYPE = new CropRulesType<TagBasedRandomCropRule>() {
        @Override
        public Codec<TagBasedRandomCropRule> codec() {
            return CODEC;
        }

    };
    private final TagKey<Item> cropTag; // Tag for categorizing crops
    private final int randomCropCount;  // Number of crops to randomly select
    private final List<Item> items = new ArrayList<>();
    /**
     * Creates a new instance with the specified tag and count.
     *
     * @param cropTag          The tag for categorizing crops.
     * @param randomCropCount  The number of crops to randomly select.
     */
    public TagBasedRandomCropRule(TagKey<Item> cropTag, int randomCropCount) {
        this.cropTag = cropTag;
        this.randomCropCount = randomCropCount;
    }

    /**
     * Retrieves the tag for categorizing crops.
     *
     * @return The tag used for crops.
     */
    public TagKey<Item> getCropTag() {
        return cropTag;
    }

    /**
     * Retrieves the number of crops to randomly select.
     *
     * @return The number of crops to randomly select.
     */
    public int getRandomCropCount() {
        return randomCropCount;
    }

    @Override
    public List<Item> getCropItems(ServerLevel level) {
        if(items.isEmpty()) {
            level.registryAccess().registry(Registries.ITEM).ifPresent(reg -> {
                Iterable<Holder<Item>> holders = reg.getTagOrEmpty(getCropTag());
                for (Holder<Item> itemHolder : holders) {
                    items.add(itemHolder.value());
                }
            });
        }


        //different way to get a random element from a tag
        //ForgeRegistries.ITEMS.tags().getTag(getCropTag()).getRandomElement(random);

        Random random = new Random(level.getSeed());

        // Create a copy of the allowed crops list to shuffle and select from
        List<Item> shuffledCrops = new ArrayList<>(items);

        // Shuffle the list of crops using the provided seed for consistent randomness
        Collections.shuffle(shuffledCrops, random);

        // Determine the limit to ensure we do not exceed the number of available crops
        int limit = Math.min(randomCropCount, shuffledCrops.size());

        // Return a sublist of the shuffled crops based on the computed limit
        return shuffledCrops.subList(0, limit);
    }

    @Override
    public CropRulesType<? extends CropRules> type() {
        return CropRulesRegistry.TAG_BASED_RANDOM_CROP_RULE.get();
    }
}
