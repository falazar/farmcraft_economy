package com.falazar.farmupcraft.data.rules.crop;

import com.falazar.farmupcraft.registry.CropRulesRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

public class ItemListCropRule implements CropRules {
    public static final MapCodec<ItemListCropRule> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.list(BuiltInRegistries.ITEM.byNameCodec()).fieldOf("allowed_crops").forGetter(ItemListCropRule::getAllowedCrops),
                    Codec.INT.fieldOf("random_crop_count").forGetter(ItemListCropRule::getRandomCropCount)
            ).apply(instance, ItemListCropRule::new));
    public static final CropRulesType<ItemListCropRule> TYPE = () -> CODEC;
    public static final ItemListCropRule EMPTY = new ItemListCropRule(new ArrayList<>(), 0);

    private final List<Item> allowedCrops;
    private final int randomCropCount;

    public ItemListCropRule(List<Item> allowedCrops) {
        this(allowedCrops, 15);
    }

    public ItemListCropRule(List<Item> allowedCrops, int randomCropCount) {
        this.allowedCrops = allowedCrops;
        this.randomCropCount = randomCropCount;
    }

    public List<Item> getAllowedCrops() {
        return allowedCrops;
    }

    public int getRandomCropCount() {
        return randomCropCount;
    }

    @Override
    public List<Item> getCropItems(ServerLevel level, int id) {
        return getAllowedCrops();
    }

    @Override
    public CropRulesType<? extends CropRules> type() {
        return CropRulesRegistry.ITEM_LIST_CROP_RULE.get();
    }
}
