package com.falazar.farmupcraft.datagen;

import biomesoplenty.api.biome.BOPBiomes;
import com.falazar.farmupcraft.CropsManager;
import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.FUCTags;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

public class BiomeTagsProvider extends net.minecraft.data.tags.BiomeTagsProvider {
    public BiomeTagsProvider(PackOutput pOutput, CompletableFuture<HolderLookup.Provider> pPro, ExistingFileHelper existingFileHelper) {
        super(pOutput, pPro, FarmUpCraft.MODID, existingFileHelper);
    }
    public static final CustomLogger LOGGER = new CustomLogger(BiomeTagsProvider.class.getSimpleName());

    @Override
    protected void addTags(HolderLookup.Provider pProvider) {
        this.addTags();
    }

    protected void addTags() {
        this.tag(FUCTags.EMPTY_BIOME_TAG);
        for(ResourceKey<Biome> biomesResourceKey : BOPBiomes.getOverworldBiomes()) {
            this.tag(FUCTags.BIOMES_O_PLENTY_OVERWORLD_TAG).addOptional(biomesResourceKey.location());
        }

        LOGGER.error("Tag has {} entries", BOPBiomes.getOverworldBiomes().size() );
        this.tag(FUCTags.BIOMES_O_PLENTY_TAG)
                .addOptional(FUCTags.BIOMES_O_PLENTY_OVERWORLD_TAG.location());
        this.tag(FUCTags.OVERWORLD_NO_OCEAN).addTags(BiomeTags.IS_OVERWORLD).remove(BiomeTags.IS_OCEAN);
    }
}
