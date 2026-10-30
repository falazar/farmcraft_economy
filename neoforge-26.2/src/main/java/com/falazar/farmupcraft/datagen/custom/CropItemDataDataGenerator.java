package com.falazar.farmupcraft.datagen.custom;

import com.falazar.farmupcraft.data.CropItemData;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.Tags;

import java.util.function.Consumer;

public class CropItemDataDataGenerator extends CropItemDataProvider {
    public CropItemDataDataGenerator(PackOutput output, String modid) {
        super(output, modid);
    }

    @Override
    protected void buildCropData(Consumer<CropItemDataConsumer> writer) {
        writer.accept(new CropItemDataConsumer(id("test_crops"), new CropItemData(FUCTags.VANILLA_CROPS, Tags.Biomes.IS_DESERT, true)));
        writer.accept(new CropItemDataConsumer(id("test_modded_crops"), new CropItemData(FUCTags.MODDED_CROPS, Tags.Biomes.IS_DESERT, true)));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, path);
    }
}
