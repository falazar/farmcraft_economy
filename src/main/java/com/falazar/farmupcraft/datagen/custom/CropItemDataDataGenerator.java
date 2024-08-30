
package com.falazar.farmupcraft.datagen.custom;

import com.falazar.farmupcraft.data.CropBlockData;
import com.falazar.farmupcraft.data.CropItemData;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BiomeTags;
import net.minecraftforge.common.Tags;

import java.util.function.Consumer;

import static com.falazar.farmupcraft.FarmUpCraft.prefix;

public class CropItemDataDataGenerator extends CropItemDataProvider {
    public CropItemDataDataGenerator(PackOutput pOutput, String modid) {
        super(pOutput, modid);
    }

    @Override
    protected void buildCropData(Consumer<CropItemDataConsumer> pWriter) {
        pWriter.accept(new CropItemDataConsumer(prefix("test_crops"), new CropItemData(FUCTags.VANILLA_CROPS, Tags.Biomes.IS_DESERT, true)));
        pWriter.accept(new CropItemDataConsumer(prefix("test_modded_crops"), new CropItemData(FUCTags.MODDED_CROPS, Tags.Biomes.IS_DESERT, true)));

    }
}
