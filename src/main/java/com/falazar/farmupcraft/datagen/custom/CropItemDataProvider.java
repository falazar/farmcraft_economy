package com.falazar.farmupcraft.datagen.custom;

import com.falazar.farmupcraft.data.CropBlockData;
import com.falazar.farmupcraft.data.CropItemData;
import com.google.common.collect.Sets;
import com.mojang.serialization.JsonOps;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;


public abstract class CropItemDataProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;
    private final String modid;
    public CropItemDataProvider(PackOutput pOutput, String modid) {
        this.pathProvider = pOutput.createPathProvider(PackOutput.Target.DATA_PACK, new ResourceLocation(modid,"farmupcraft/crop_item_data").getPath());
        this.modid = modid;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput pOutput) {
        Set<ResourceLocation> set = Sets.newHashSet();
        Set<ResourceLocation> taskSet = Sets.newHashSet();
        List<CompletableFuture<?>> list = new ArrayList<>();
        this.buildCropData((cropData -> {
            if (!set.add(cropData.resourceLocation())) {
                throw new IllegalStateException("Duplicate Crop Item Data " + cropData.resourceLocation());
            } else {

                CropItemData.CODEC.encodeStart(JsonOps.INSTANCE, cropData.data)
                        .get()
                        .ifLeft(e -> list.add(DataProvider.saveStable(pOutput, e, this.pathProvider.json(cropData.resourceLocation()))))
                        .ifRight(partial -> LOGGER.error("Failed to create crop item data {}, due to {}", cropData.resourceLocation(), partial));
            }
        }));
        return CompletableFuture.allOf(list.toArray((p_253414_) -> {
            return new CompletableFuture[p_253414_];
        }));
    }

    public record CropItemDataConsumer(ResourceLocation resourceLocation, CropItemData data){}

    protected abstract void buildCropData(Consumer<CropItemDataConsumer> pWriter);

    @Override
    public String getName() {
        return modid + "Crop Item Data";
    }
}
