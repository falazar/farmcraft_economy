package com.falazar.farmupcraft.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

public class CropItemData {

    public static final Codec<CropItemData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.either(BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").codec(), TagKey.codec(Registries.ITEM).fieldOf("tag").codec()).fieldOf("crop").forGetter(predicate ->
                            predicate.item != null ? Either.left(predicate.item) : Either.right(predicate.itemTagKey)
                    ),
                    TagKey.codec(Registries.BIOME).fieldOf("allowed_biomes").forGetter(CropItemData::getAllowedBiomes),
                    Codec.BOOL.fieldOf("populate_items").forGetter(CropItemData::getPopulateItems)
            ).apply(instance, CropItemData::new)
    );

    private final Item item;
    private final TagKey<Item> itemTagKey;
    private final Either<Item, TagKey<Item>> either;
    private final TagKey<Biome> allowedBiomes;
    private List<Holder<Biome>> allowedBiomesList = new ArrayList<>();

    private final boolean populateItems;
    public CropItemData(Either<Item, TagKey<Item>> either, TagKey<Biome> allowedBiomes, boolean populateItems) {
        this.item = either.left().orElse(null);
        this.itemTagKey = either.right().orElse(null);
        this.either = either;
        this.allowedBiomes = allowedBiomes;
        this.populateItems = populateItems;
    }


    public CropItemData(TagKey<Item> tagKey, TagKey<Biome> allowedBiomes, boolean populateItems) {
        this.item = null;
        this.itemTagKey = tagKey;
        this.either = null;
        this.allowedBiomes = allowedBiomes;
        this.populateItems = populateItems;
    }

    public CropItemData(Item item, TagKey<Biome> allowedBiomes, boolean populateItems) {
        this.item = item;
        this.itemTagKey = null;
        this.either = null;
        this.allowedBiomes = allowedBiomes;
        this.populateItems = populateItems;
    }

    public boolean containsBiome(Holder<Biome> biome) {
        return allowedBiomesList.contains(biome);
    }

    public void setAllowedBiomesList(List<Holder<Biome>> allowedBiomesList) {
        this.allowedBiomesList = allowedBiomesList;
    }

    public TagKey<Biome> getAllowedBiomes() {
        return allowedBiomes;
    }

    public Item getItem() {
        return item;
    }

    public TagKey<Item> getItems() {
        return itemTagKey;
    }

    public boolean getPopulateItems() {
        return populateItems;
    }
}
