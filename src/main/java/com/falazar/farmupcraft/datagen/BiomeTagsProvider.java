package com.falazar.farmupcraft.datagen;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public class BiomeTagsProvider extends net.minecraft.data.tags.BiomeTagsProvider {
    public BiomeTagsProvider(PackOutput pOutput, CompletableFuture<HolderLookup.Provider> pPro, ExistingFileHelper existingFileHelper) {
        super(pOutput,pPro , FarmUpCraft.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider pProvider) {
        this.addTags();
    }

    protected void addTags() {
    this.tag(FUCTags.EMPTY_BIOME_TAG);
    }
}
