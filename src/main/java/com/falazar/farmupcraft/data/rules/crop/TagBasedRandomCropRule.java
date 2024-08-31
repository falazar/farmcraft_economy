package com.falazar.farmupcraft.data.rules.crop;

import com.falazar.farmupcraft.data.BiomeRulesDataJsonManager;
import com.falazar.farmupcraft.registry.BiomeRegistryHolder;
import com.falazar.farmupcraft.registry.CropRulesRegistry;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

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
    public static final CustomLogger LOGGER = new CustomLogger(TagBasedRandomCropRule.class.getSimpleName());

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
        return List.of();
    }

    @Override
    public List<Item> getCropItemsWithRandomId(ServerLevel level, int id) {
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
        Random random = new Random(level.getSeed() + id);

        // Create a copy of the allowed crops list to shuffle and select from
        List<Item> shuffledCrops = new ArrayList<>(items);

        // Shuffle the list of crops using the provided seed for consistent randomness
        Collections.shuffle(shuffledCrops, random);

        // Determine the limit to ensure we do not exceed the number of available crops
        int limit = Math.min(randomCropCount, shuffledCrops.size());

        // Return a sublist of the shuffled crops based on the computed limit

       List<Item> randomList = shuffledCrops.subList(0, limit);


       //pamh2crops do some stupid fuckery where they have a seed and a normal item that can both be placed so we put
        // them both in here so it cant be used.
        if (ModList.get().isLoaded("pamhc2crops")) {
            List<Item> additionalItems = new ArrayList<>();

            for (Item item : randomList) {
                ResourceLocation location = ForgeRegistries.ITEMS.getKey(item);
                if (location == null) continue;
                if (!location.toString().contains("pamhc2crops")) continue;

                // Determine the modified string based on whether the location contains "seed" or "seeds"
                String locationString = location.toString();
                String modifiedString;

                if (locationString.contains("seed")) {
                    // Split the string by "seed"
                    String[] parts = locationString.split("seed");

                    if (parts.length == 3) {
                        // If splitting results in exactly 3 parts, remove the second "seed"
                        modifiedString = parts[0] + "seed" + parts[1] +   parts[2];
                    } else if (parts.length == 2) {
                        // If splitting results in exactly 2 parts, remove the first "seed"
                        modifiedString = parts[0] + parts[1];
                    } else {
                        // If no "seed" or other cases, just keep the original
                        modifiedString = locationString;
                    }



                } else {
                    // For strings without "seed", handle default case
                    modifiedString = locationString.replace("item", "seeditem");
                }
                if(locationString.equals("pamhc2crops:sesameseedsseeditem")) {
                    modifiedString = "pamhc2crops:sesameseedsitem";
                }
                if(locationString.equals("pamhc2crops:mustardseedsitem")) {
                    modifiedString = "pamhc2crops:mustardseedsseeditem";
                }
                if(locationString.equals("pamhc2crops:sesameseedsitem")) {
                    modifiedString = "pamhc2crops:sesameseedsseeditem";
                }
                // Add the item if found
                //LOGGER.info("Original: " + locationString + " | Modified: " + modifiedString);

                addItemIfValid(additionalItems, modifiedString);
            }

            randomList.addAll(additionalItems);
        }



        return randomList;
    }

    private void addItemIfValid(List<Item> list, String locationString) {
        ResourceLocation loc = new ResourceLocation(locationString);
        Item itemExtra = ForgeRegistries.ITEMS.getValue(loc);
        if (itemExtra != null && !itemExtra.getDefaultInstance().is(Items.AIR)) {
            list.add(itemExtra);
        } else {
            LOGGER.warn("Tried to add a non existing item for {}", loc);
        }
    }
    @Override
    public CropRulesType<? extends CropRules> type() {
        return CropRulesRegistry.TAG_BASED_RANDOM_CROP_RULE.get();
    }
}
