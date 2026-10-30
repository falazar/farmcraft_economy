package com.falazar.farmupcraft.datagen;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

import java.util.concurrent.CompletableFuture;

public class BlockTagsGenerator extends BlockTagsProvider {
    public BlockTagsGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, FarmUpCraftNeoForge.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        tag(FUCTags.FARMLAND).add(BuiltInRegistries.BLOCK.getResourceKey(Blocks.FARMLAND).orElseThrow());
        var vanilla = tag(FUCTags.VANILLA_CROPS_BLOCKS);
        for (var block : new net.minecraft.world.level.block.Block[] {
                Blocks.WHEAT, Blocks.CARROTS, Blocks.BEETROOTS, Blocks.POTATOES}) {
            vanilla.add(BuiltInRegistries.BLOCK.getResourceKey(block).orElseThrow());
        }
        var modded = tag(FUCTags.MODDED_CROPS_BLOCKS);
        BuiltInRegistries.BLOCK.entrySet().forEach(entry -> {
            if (entry.getKey().identifier().getNamespace().equals("pamhc2crops")
                    && entry.getValue() instanceof CropBlock) {
                modded.add(entry.getKey());
            }
        });
    }
}
