package com.falazar.farmupcraft.data;

import com.mojang.logging.LogUtils;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class CropBlockDataJsonManager extends SimpleJsonResourceReloadListener<CropBlockData> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Map<Identifier, CropBlockData> cropDataEntries = new HashMap<>();
    private static Map<Block, CropBlockData> cropBlockDataEntries = new HashMap<>();
    private final String folderName;

    public CropBlockDataJsonManager() {
        this("farmupcraft/crop_block_data");
    }

    public CropBlockDataJsonManager(String folderName) {
        super(CropBlockData.CODEC, FileToIdConverter.json(folderName));
        this.folderName = folderName;
    }

    public static Map<Identifier, CropBlockData> getCropDataEntries() {
        return cropDataEntries;
    }

    public static Map<Block, CropBlockData> getCropBlockDataEntries() {
        return cropBlockDataEntries;
    }

    public static void clearEntries() {
        cropDataEntries = new HashMap<>();
        cropBlockDataEntries = new HashMap<>();
    }

    public static void populateCropBlockEntries(ServerLevel level) {
        populateCropBlockEntries(level.registryAccess());
    }

    public static void populateCropBlockEntries(RegistryAccess registries) {
        Map<Block, CropBlockData> populated = new HashMap<>();
        var blocks = registries.lookupOrThrow(Registries.BLOCK);
        for (CropBlockData data : cropDataEntries.values()) {
            if (!data.getPopulateBlocks()) continue;
            if (data.getBlock() != null) {
                putBlock(populated, data.getBlock(), data);
            } else {
                for (var holder : blocks.getTagOrEmpty(data.getBlocks())) {
                    putBlock(populated, holder.value(), data);
                }
            }
        }
        cropBlockDataEntries = populated;
    }

    private static void putBlock(Map<Block, CropBlockData> populated, Block block, CropBlockData data) {
        if (populated.put(block, data) != null) {
            LOGGER.warn("Block {} has multiple crop data definitions; replacing previous definition", block);
        }
    }

    @Override
    protected void apply(Map<Identifier, CropBlockData> entries, ResourceManager manager, ProfilerFiller profiler) {
        clearEntries();
        cropDataEntries = new HashMap<>(entries);
        LOGGER.info("Data loader for {} loaded {} jsons", folderName, entries.size());
    }
}
