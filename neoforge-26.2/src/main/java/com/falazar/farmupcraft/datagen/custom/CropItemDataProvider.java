package com.falazar.farmupcraft.datagen.custom;

import com.falazar.farmupcraft.data.CropItemData;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class CropItemDataProvider extends CodecDataProvider<CropItemData> {
    public CropItemDataProvider(PackOutput output, String modid) {
        super(output, modid, "crop_item_data", "Crop Item Data", CropItemData.CODEC);
    }

    public record CropItemDataConsumer(Identifier resourceLocation, CropItemData data) {}

    @Override
    protected final void buildEntries(BiConsumer<Identifier, CropItemData> writer) {
        buildCropData(entry -> writer.accept(entry.resourceLocation(), entry.data()));
    }

    protected abstract void buildCropData(Consumer<CropItemDataConsumer> writer);
}
