package com.falazar.farmupcraft.datagen.custom;

import com.falazar.farmupcraft.data.CropBlockData;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

public class CropBlockDataDataGenerator extends CropBlockDataProvider {
    public CropBlockDataDataGenerator(PackOutput output, String modid) {
        super(output, modid);
    }

    @Override
    protected void buildCropData(Consumer<CropBlockDataConsumer> writer) {
        writer.accept(new CropBlockDataConsumer(id("test_crops"), new CropBlockData(FUCTags.VANILLA_CROPS_BLOCKS, 10D, true)));
        writer.accept(new CropBlockDataConsumer(id("test_crops_modded"), new CropBlockData(FUCTags.MODDED_CROPS_BLOCKS, 10D, true)));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, path);
    }
}
