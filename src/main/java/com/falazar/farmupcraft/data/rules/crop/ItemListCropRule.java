package com.falazar.farmupcraft.data.rules.crop;

import com.falazar.farmupcraft.registry.CropRulesRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

public class ItemListCropRule implements CropRules{
    public static final Codec<ItemListCropRule> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.list(BuiltInRegistries.ITEM.byNameCodec()).fieldOf("allowed_crops").forGetter(ItemListCropRule::getAllowedCrops),
                    Codec.INT.fieldOf("random_crop_count").forGetter(ItemListCropRule::getRandomCropCount)
            ).apply(instance, ItemListCropRule::new)
    );
    public static final CropRulesType<ItemListCropRule> TYPE = new CropRulesType<ItemListCropRule>() {
        @Override
        public Codec<ItemListCropRule> codec() {
            return CODEC;
        }

    };
    private final List<Item> allowedCrops;
    private final int randomCropCount;

    // Default empty instance with no crops and zero count
    public static final ItemListCropRule EMPTY = new ItemListCropRule(new ArrayList<>(), 0);
    /**
     * Constructs a new ItemListCropRules instance with the specified allowed crops and a default random crop count of 15.
     *
     * @param allowedCrops The list of crops that are allowed.
     */
    public ItemListCropRule(List<Item> allowedCrops) {
        this(allowedCrops, 15);
    }

    /**
     * Constructs a new ItemListCropRules instance with the specified allowed crops and random crop count.
     *
     * @param allowedCrops The list of crops that are allowed.
     * @param randomCropCount The number of crops to randomly select.
     */
    public ItemListCropRule(List<Item> allowedCrops, int randomCropCount) {
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

    @Override
    public List<Item> getCropItems(ServerLevel level) {
        return getAllowedCrops();
    }

    @Override
    public CropRulesType<? extends CropRules> type() {
        return CropRulesRegistry.ITEM_LIST_CROP_RULE.get();
    }
}
