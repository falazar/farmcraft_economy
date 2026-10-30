package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.util.CustomLogger;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class MarketDataJsonManager extends SimpleJsonResourceReloadListener {
    private static final Gson STANDARD_GSON = new Gson();
    private final String folderName;
    public static final CustomLogger LOGGER = new CustomLogger(MarketDataJsonManager.class.getSimpleName());

    protected static Map<ResourceLocation, MarketData> marketEntries = new HashMap<>();

    public static void clearEntries() {
        marketEntries.clear();
    }


    //public static Map<Item, CropData> getCropItemDataEntries() {
    //    return cropItemDataEntries;
    //}


    public static Map<ResourceLocation, MarketData> getMarketEntries() {
        return marketEntries;
    }


    public MarketDataJsonManager() {
        this("farmupcraft/market", STANDARD_GSON);
    }

    public MarketDataJsonManager(String folderName, Gson gson) {
        super(gson, folderName);
        this.folderName = folderName;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        clearEntries();
        // Create a new list for market.
        // Loop over json and parse into our list.
        Map<ResourceLocation, MarketData> itemData = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            ResourceLocation key = entry.getKey();
            JsonElement element = entry.getValue();
            MarketData.CODEC.decode(JsonOps.INSTANCE, element)
                    .get()
                    .ifLeft(result -> {
                        MarketData codec = result.getFirst();
                        itemData.put(key, codec);
                    })
                    .ifRight(partial -> LOGGER.error("Failed to parse market data json {} due to: {}", key, partial.message()));
        }

        this.marketEntries = itemData;
        LOGGER.info("Data loader for {} loaded {} jsons", this.folderName, this.marketEntries.size());
    }
}
