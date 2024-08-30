package com.falazar.farmupcraft.datagen;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ItemTagsGenerator extends ItemTagsProvider {
    public ItemTagsGenerator(PackOutput p_275343_, CompletableFuture<HolderLookup.Provider> p_275729_,
                             CompletableFuture<TagLookup<Block>> p_275322_, @Nullable ExistingFileHelper existingFileHelper) {
        super(p_275343_, p_275729_, p_275322_, FarmUpCraft.MODID, existingFileHelper);
    }
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    protected void addTags(HolderLookup.Provider pProvider) {
        this.tag(FUCTags.VANILLA_CROPS).add(Items.POTATO).add(Items.WHEAT_SEEDS).add(Items.CARROT).add(Items.BEETROOT_SEEDS);

        for(Item object : ForgeRegistries.ITEMS.getValues()) {
            if(object.getDescriptionId().contains("pamhc2crops")) {
                ResourceLocation location = ForgeRegistries.ITEMS.getKey(object);
                this.tag(FUCTags.MODDED_CROPS).addOptional(location);
            }
            if ((object.getDescriptionId().contains("pamhc2foodcore") || object.getDescriptionId().contains("pamhc2foodextended"))
                    && object.isEdible()) {
                ResourceLocation location = ForgeRegistries.ITEMS.getKey(object);
                this.tag(FUCTags.MARKET_FOODS).addOptional(location);
            }
        }
    }

    @Override
    public String getName() { return FarmUpCraft.MODID + " Item Tags";}
}
