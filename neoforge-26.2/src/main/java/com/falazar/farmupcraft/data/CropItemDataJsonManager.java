package com.falazar.farmupcraft.data;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CropItemDataJsonManager extends SimpleJsonResourceReloadListener<CropItemData> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Map<Identifier, CropItemData> cropDataEntries = new HashMap<>();
    private static Map<Item, CropItemData> cropItemDataEntries = new HashMap<>();
    private final String folderName;

    public CropItemDataJsonManager() {
        this("farmupcraft/crop_item_data");
    }

    public CropItemDataJsonManager(String folderName) {
        super(CropItemData.CODEC, FileToIdConverter.json(folderName));
        this.folderName = folderName;
    }

    public static Map<Identifier, CropItemData> getCropDataEntries() {
        return cropDataEntries;
    }

    public static Map<Item, CropItemData> getCropItemDataEntries() {
        return cropItemDataEntries;
    }

    public static void clearEntries() {
        cropDataEntries = new HashMap<>();
        cropItemDataEntries = new HashMap<>();
    }

    public static void populateCropItemEntries(ServerLevel level) {
        populateCropItemEntries(level.registryAccess());
    }

    public static void populateCropItemEntries(RegistryAccess registries) {
        Map<Item, CropItemData> populated = new HashMap<>();
        var items = registries.lookupOrThrow(Registries.ITEM);
        var biomes = registries.lookupOrThrow(Registries.BIOME);
        for (CropItemData data : cropDataEntries.values()) {
            if (!data.getPopulateItems()) continue;
            var allowedBiomes = new ArrayList<Holder<Biome>>();
            biomes.getTagOrEmpty(data.getAllowedBiomes()).forEach(allowedBiomes::add);
            data.setAllowedBiomesList(allowedBiomes);

            if (data.getItem() != null) {
                putItem(populated, data.getItem(), data);
            } else {
                for (var holder : items.getTagOrEmpty(data.getItems())) {
                    putItem(populated, holder.value(), data);
                }
            }
        }
        cropItemDataEntries = populated;
    }

    private static void putItem(Map<Item, CropItemData> populated, Item item, CropItemData data) {
        if (populated.put(item, data) != null) {
            LOGGER.warn("Item {} has multiple crop data definitions; replacing previous definition", item);
        }
    }

    @Override
    protected void apply(Map<Identifier, CropItemData> entries, ResourceManager manager, ProfilerFiller profiler) {
        clearEntries();
        cropDataEntries = new HashMap<>(entries);
        LOGGER.info("Data loader for {} loaded {} jsons", folderName, entries.size());
    }
}
