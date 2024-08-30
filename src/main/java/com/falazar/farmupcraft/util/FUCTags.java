package com.falazar.farmupcraft.util;

import com.falazar.farmupcraft.FarmUpCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class FUCTags {
    public static final TagKey<Item> FOOD = registerItemTag("food");
    public static final TagKey<Item> VANILLA_CROPS = registerItemTag("vanilla_crops");
    public static final TagKey<Item> MODDED_CROPS = registerItemTag("modded_crops");
    public static final TagKey<Item> MARKET_FOODS = registerItemTag("market_foods");

    public static final TagKey<Block> BARRIER = registerBlockTag("barrier");
    public static final TagKey<Block> FARMLAND = registerBlockTag("farmland");
    public static final TagKey<Block> VANILLA_CROPS_BLOCKS = registerBlockTag("vanilla_crops");
    public static final TagKey<Block> MODDED_CROPS_BLOCKS = registerBlockTag("modded_crops");


    private static TagKey<Item> registerItemTag(String name) {
        return TagKey.create(Registries.ITEM, new ResourceLocation(FarmUpCraft.MODID, name));
    }

    private static TagKey<Block> registerBlockTag(String name) {
        return TagKey.create(Registries.BLOCK, new ResourceLocation(FarmUpCraft.MODID, name));
    }
}
