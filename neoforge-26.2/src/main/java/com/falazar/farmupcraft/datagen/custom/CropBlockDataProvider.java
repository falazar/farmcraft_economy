package com.falazar.farmupcraft.datagen.custom;

import com.falazar.farmupcraft.data.CropBlockData;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class CropBlockDataProvider extends CodecDataProvider<CropBlockData> {
    public CropBlockDataProvider(PackOutput output, String modid) {
        super(output, modid, "crop_block_data", "Crop Block Data", CropBlockData.CODEC);
    }

    public record CropBlockDataConsumer(Identifier resourceLocation, CropBlockData data) {}

    @Override
    protected final void buildEntries(BiConsumer<Identifier, CropBlockData> writer) {
        buildCropData(entry -> writer.accept(entry.resourceLocation(), entry.data()));
    }

    protected abstract void buildCropData(Consumer<CropBlockDataConsumer> writer);
}
