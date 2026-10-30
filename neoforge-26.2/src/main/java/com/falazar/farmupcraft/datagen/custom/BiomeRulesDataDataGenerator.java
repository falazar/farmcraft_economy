package com.falazar.farmupcraft.datagen.custom;

import com.falazar.farmupcraft.data.BiomeRulesData;
import com.falazar.farmupcraft.data.rules.crop.TagBasedRandomCropRule;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;

import java.util.Optional;
import java.util.function.Consumer;

public class BiomeRulesDataDataGenerator extends BiomeRulesDataDataProvider {
    public BiomeRulesDataDataGenerator(PackOutput output, String modid) {
        super(output, modid);
    }

    @Override
    protected void buildBiomeRuleData(Consumer<BiomeRulesDataConsumer> writer) {
        writer.accept(new BiomeRulesDataConsumer(id("overworld_rules"), new BiomeRulesData(
                BiomeTags.IS_OVERWORLD, Optional.of(BiomeTags.IS_OCEAN),
                new TagBasedRandomCropRule(FUCTags.VANILLA_AND_MODDED_CROPS, 15))));
        writer.accept(new BiomeRulesDataConsumer(id("bop_rules"), new BiomeRulesData(
                FUCTags.BIOMES_O_PLENTY_OVERWORLD_TAG, Optional.empty(),
                new TagBasedRandomCropRule(FUCTags.VANILLA_AND_MODDED_CROPS, 15))));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, path);
    }
}
