package com.falazar.farmupcraft.datagen;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class BlockTagsGenerator extends BlockTagsProvider {
    public BlockTagsGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, FarmUpCraft.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider pProvider) {
        this.tag(FUCTags.FARMLAND).add(Blocks.FARMLAND);
        this.tag(FUCTags.VANILLA_CROPS_BLOCKS).add(Blocks.WHEAT).add(Blocks.CARROTS).add(Blocks.BEETROOTS).add(Blocks.POTATOES);
    }


    @Override
    public String getName() {
        return FarmUpCraft.MODID + " Block Tags";
    }
}
