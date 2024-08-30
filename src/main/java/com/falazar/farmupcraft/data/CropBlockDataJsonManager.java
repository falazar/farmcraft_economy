package com.falazar.farmupcraft.data;

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
import net.minecraft.world.level.block.Block;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class CropBlockDataJsonManager extends SimpleJsonResourceReloadListener {
    private static final Gson STANDARD_GSON = new Gson();
    private final String folderName;
    public static final Logger LOGGER = LogManager.getLogger();


    protected static Map<ResourceLocation, CropBlockData> cropDataEntries = new HashMap<>();
    //protected static Map<Item, CropData> cropItemDataEntries = new HashMap<>();
    protected static Map<Block, CropBlockData> cropBlockDataEntries = new HashMap<>();


    public static Map<ResourceLocation, CropBlockData> getCropDataEntries() {
        return cropDataEntries;
    }


    public static void clearEntries() {
        cropDataEntries.clear();
        cropBlockDataEntries.clear();
        //cropItemDataEntries.clear();
    }


    //public static Map<Item, CropData> getCropItemDataEntries() {
    //    return cropItemDataEntries;
    //}


    public static Map<Block, CropBlockData> getCropBlockDataEntries() {
        return cropBlockDataEntries;
    }

    public static void populateCropBlockEntries(ServerLevel level) {
        if(!cropBlockDataEntries.isEmpty()) return;
        for (CropBlockData data : getCropDataEntries().values()) {
            if(!data.getPopulateBlocks()) continue;
            TagKey<Block> blocks = data.getBlocks();
            if(blocks == null) {
                Block block = data.getBlock();
                if(cropDataEntries.containsKey(block)) {
                    LOGGER.warn("block {} already has crop data defined, skipping!", block);
                }
                cropBlockDataEntries.put(block, data);
            } else {
                level.registryAccess().registry(Registries.BLOCK).ifPresent(reg -> {
                    Iterable<Holder<Block>> holders = reg.getTagOrEmpty(blocks);
                    for (Holder<Block> blockHolder : holders) {
                        if (cropDataEntries.containsKey(blockHolder)) {
                            LOGGER.warn("block {} already has crop data defined, skipping!", blockHolder.get());
                        }
                        cropBlockDataEntries.put(blockHolder.get(), data);
                    }
                });
            }
        }
    }

    public CropBlockDataJsonManager() {
        this("farmupcraft/crop_block_data", STANDARD_GSON);
    }

    public CropBlockDataJsonManager(String folderName, Gson gson) {
        super(gson, folderName);
        this.folderName = folderName;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        clearEntries();
        Map<ResourceLocation, CropBlockData> cropData = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            ResourceLocation key = entry.getKey();
            JsonElement element = entry.getValue();
            CropBlockData.CODEC.decode(JsonOps.INSTANCE, element)
                    .get()
                    .ifLeft(result -> {
                        CropBlockData codec = result.getFirst();
                        cropData.put(key, codec);
                    })
                    .ifRight(partial -> LOGGER.error("Failed to parse crop block data json {} due to: {}", key, partial.message()));


        }

        this.cropDataEntries = cropData;
        LOGGER.info("Data loader for {} loaded {} jsons", this.folderName, this.cropDataEntries.size());
    }
}
