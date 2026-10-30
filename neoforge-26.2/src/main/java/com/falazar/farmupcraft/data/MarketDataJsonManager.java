package com.falazar.farmupcraft.data;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/** Legacy market loader; intentionally not registered as a reload listener. */
public class MarketDataJsonManager extends SimpleJsonResourceReloadListener<MarketData> {
    public static final Logger LOGGER = LogUtils.getLogger();
    protected static Map<Identifier, MarketData> marketEntries = new HashMap<>();
    private final String folderName;

    public MarketDataJsonManager() {
        this("farmupcraft/market", new Gson());
    }

    public MarketDataJsonManager(String folderName, Gson gson) {
        super(MarketData.CODEC, FileToIdConverter.json(folderName));
        this.folderName = folderName;
    }

    public static void clearEntries() {
        marketEntries.clear();
    }

    public static Map<Identifier, MarketData> getMarketEntries() {
        return marketEntries;
    }

    @Override
    protected void apply(Map<Identifier, MarketData> entries, ResourceManager manager, ProfilerFiller profiler) {
        clearEntries();
        marketEntries = new HashMap<>(entries);
        LOGGER.info("Data loader for {} loaded {} jsons", folderName, marketEntries.size());
    }
}
