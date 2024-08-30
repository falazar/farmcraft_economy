package com.falazar.farmupcraft.datagen.custom;

import com.falazar.farmupcraft.data.CropBlockData;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.data.PackOutput;

import java.util.function.Consumer;

import static com.falazar.farmupcraft.FarmUpCraft.prefix;

public class CropBlockDataDataGenerator extends CropBlockDataProvider {
    public CropBlockDataDataGenerator(PackOutput pOutput, String modid) {
        super(pOutput, modid);
    }

    @Override
    protected void buildCropData(Consumer<CropBlockDataConsumer> pWriter) {
        pWriter.accept(new CropBlockDataConsumer(prefix("test_crops"), new CropBlockData(FUCTags.VANILLA_CROPS_BLOCKS, 10D,false)));
        pWriter.accept(new CropBlockDataConsumer(prefix("test_crops_modded"), new CropBlockData(FUCTags.MODDED_CROPS_BLOCKS, 10D,false)));

    }
}
