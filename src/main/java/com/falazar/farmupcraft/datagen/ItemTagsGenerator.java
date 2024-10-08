package com.falazar.farmupcraft.datagen;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class ItemTagsGenerator extends ItemTagsProvider {
    public ItemTagsGenerator(PackOutput p_275343_, CompletableFuture<HolderLookup.Provider> p_275729_,
                             CompletableFuture<TagLookup<Block>> p_275322_, @Nullable ExistingFileHelper existingFileHelper) {
        super(p_275343_, p_275729_, p_275322_, FarmUpCraft.MODID, existingFileHelper);
    }
    public static final CustomLogger LOGGER = new CustomLogger(ItemTagsGenerator.class.getSimpleName());

    @Override
    protected void addTags(HolderLookup.Provider pProvider) {
        this.tag(FUCTags.VANILLA_CROPS).add(Items.POTATO).add(Items.WHEAT_SEEDS).add(Items.CARROT).add(Items.BEETROOT_SEEDS);

        for(Item object : ForgeRegistries.ITEMS.getValues()) {
            if(object.getDescriptionId().contains("pamhc2crops") && object.getDescriptionId().contains("seed")) {
                ResourceLocation location = ForgeRegistries.ITEMS.getKey(object);
                this.tag(FUCTags.MODDED_SEEDS).addOptional(location);
            } else if(object.getDescriptionId().contains("pamhc2crops")) {
                if(object instanceof BlockItem item) {
                    ResourceLocation location = ForgeRegistries.ITEMS.getKey(object);
                    this.tag(FUCTags.MODDED_CROPS).addOptional(location);
                }
            }
            if ((object.getDescriptionId().contains("pamhc2foodcore") || object.getDescriptionId().contains("pamhc2foodextended"))
                    && object.isEdible()) {
                ResourceLocation location = ForgeRegistries.ITEMS.getKey(object);
                this.tag(FUCTags.MARKET_FOODS).addOptional(location);
            }
        }
        //this.copy(FUCTags.MODDED_CROPS_BLOCKS,FUCTags.MODDED_CROPS);
        this.tag(FUCTags.VANILLA_AND_MODDED_CROPS).addTag(FUCTags.VANILLA_CROPS).addOptionalTag(FUCTags.MODDED_SEEDS.location());
    }

    @Override
    public String getName() { return FarmUpCraft.MODID + " Item Tags";}
}
