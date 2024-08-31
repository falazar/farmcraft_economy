package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.util.CustomLogger;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CropItemDataJsonManager extends SimpleJsonResourceReloadListener {
    private static final Gson STANDARD_GSON = new Gson();
    private final String folderName;
    public static final CustomLogger LOGGER = new CustomLogger(CropItemDataJsonManager.class.getSimpleName());


    protected static Map<ResourceLocation, CropItemData> cropDataEntries = new HashMap<>();
    //protected static Map<Item, CropData> cropItemDataEntries = new HashMap<>();
    protected static Map<Item, CropItemData> cropItemDataEntries = new HashMap<>();


    public static Map<ResourceLocation, CropItemData> getCropDataEntries() {
        return cropDataEntries;
    }


    public static void clearEntries() {
        cropDataEntries.clear();
        cropItemDataEntries.clear();
        //cropItemDataEntries.clear();
    }


    //public static Map<Item, CropData> getCropItemDataEntries() {
    //    return cropItemDataEntries;
    //}


    public static Map<Item, CropItemData> getCropItemDataEntries() {
        return cropItemDataEntries;
    }

    public static void populateCropItemEntries(ServerLevel level) {
        if(!getCropItemDataEntries().isEmpty()) return;
        for (CropItemData data : getCropDataEntries().values()) {
            if(!data.getPopulateItems()) continue;
            TagKey<Item> items = data.getItems();


            TagKey<Biome> biomes = data.getAllowedBiomes();
            if(biomes != null) {
                level.registryAccess().registry(Registries.BIOME).ifPresent(reg -> {
                    Iterable<Holder<Biome>> holders = reg.getTagOrEmpty(biomes);
                    List<Holder<Biome>> biomesList = new ArrayList<>();
                    for (Holder<Biome> biomeHolder : holders) {
                        biomesList.add(biomeHolder);
                    }
                    data.setAllowedBiomesList(biomesList);
                });
            }


            if(items == null) {
                Item item = data.getItem();
                if(cropDataEntries.containsKey(item)) {
                    LOGGER.warn("block {} already has crop data defined, skipping!", item);
                }
                cropItemDataEntries.put(item, data);
            }

            level.registryAccess().registry(Registries.ITEM).ifPresent(reg -> {
                Iterable<Holder<Item>> holders = reg.getTagOrEmpty(items);
                for (Holder<Item> blockHolder : holders) {
                    if(cropDataEntries.containsKey(blockHolder)) {
                        LOGGER.warn("item {} already has crop data defined, skipping!", blockHolder.get());
                    }
                    cropItemDataEntries.put(blockHolder.get(), data);
                }
            });
        }
    }

    public CropItemDataJsonManager() {
        this("farmupcraft/crop_item_data", STANDARD_GSON);
    }

    public CropItemDataJsonManager(String folderName, Gson gson) {
        super(gson, folderName);
        this.folderName = folderName;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        clearEntries();
        Map<ResourceLocation, CropItemData> cropData = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            ResourceLocation key = entry.getKey();
            JsonElement element = entry.getValue();
            CropItemData.CODEC.decode(JsonOps.INSTANCE, element)
                    .get()
                    .ifLeft(result -> {
                        CropItemData codec = result.getFirst();
                        cropData.put(key, codec);
                    })
                    .ifRight(partial -> LOGGER.error("Failed to parse crop item data json {} due to: {}", key, partial.message()));


        }

        this.cropDataEntries = cropData;
        LOGGER.info("Data loader for {} loaded {} jsons", this.folderName, this.cropDataEntries.size());
    }
}
