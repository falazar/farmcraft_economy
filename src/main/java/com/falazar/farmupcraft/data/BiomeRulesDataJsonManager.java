package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.registry.BiomeRegistryHolder;
import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.falazar.farmupcraft.saveddata.BiomeRulesManager;
import com.falazar.farmupcraft.util.CustomLogger;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
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

import java.util.HashMap;
import java.util.Map;
/**
 * Manages the loading and handling of biome rules data from JSON files.
 * <p>
 * This class extends SimpleJsonResourceReloadListener to handle JSON resource reloads
 * and store biome rules data.
 */
public class BiomeRulesDataJsonManager extends SimpleJsonResourceReloadListener {
    private static final Gson STANDARD_GSON = new Gson();
    private final String folderName;
    public static final CustomLogger LOGGER = new CustomLogger(BiomeRulesDataJsonManager.class.getSimpleName());


    protected static Map<ResourceLocation, BiomeRulesData> biomeRules = new HashMap<>();


    /**
     * Clears all entries in the biomeRules map.
     * <p>
     * This method is used to reset the data before reloading new data.
     */
    public static void clearEntries() {
        biomeRules.clear();
    }




    /**
     * Retrieves the current biome rules data.
     *
     * @return A map of biome rules data keyed by resource location.
     */
    public static Map<ResourceLocation, BiomeRulesData> getBiomeRules() {
        return biomeRules;
    }

    /**
     * Populates biome rules instances into the BiomeRulesManager for the specified server level.
     * <p>
     * This method checks if biome rules are already set and if not, it sets them based on the loaded
     * biome rules data.
     *
     * @param level The server level where the biome rules should be applied.
     */
    public static void populateBiomeRulesInstances(ServerLevel level) {
        if (biomeRules.isEmpty()) return;
        BiomeRulesManager manager = BiomeRulesManager.get(level);
        if(manager.hasRules()) return;

        for (BiomeRulesData data : getBiomeRules().values()) {
            TagKey<Biome> biomes = data.getBiome();
            if(biomes != null) {
                level.registryAccess().registry(Registries.BIOME).ifPresent(reg -> {
                    Iterable<Holder<Biome>> holders = reg.getTagOrEmpty(biomes);
                    for (Holder<Biome> biomeHolder : holders) {

                        //LOGGER.warn("Biome Pre" + biomeHolder);
                        if(!manager.containsBiome(biomeHolder)) {
                            //LOGGER.warn("Biome After" + biomeHolder);

                            manager.setBiomeRules(biomeHolder, new BiomeRulesInstance(data));
                        }
                    }
                });
            }
        }


        if(manager.hasItems()) return;
        for(Holder<Biome> biomes : manager.getBiomeKeys()){
            BiomeRulesInstance instance = manager.getBiomeRules(biomes);
            ResourceKey<Biome> biomeRk = BiomeRegistryHolder.convertToID(biomes);
            for(Item item : instance.getCropsWithRandomId(level, BiomeRegistryHolder.convertToID(biomeRk))) {
                manager.setItemBiomeList(item, biomes);
            }
            for(Item item : instance.getCrops(level)){
                manager.setItemBiomeList(item, biomes);
            }
        }
    }


    public BiomeRulesDataJsonManager() {
        this("farmupcraft/biome_rules", STANDARD_GSON);
    }

    public BiomeRulesDataJsonManager(String folderName, Gson gson) {
        super(gson, folderName);
        this.folderName = folderName;
    }
    /**
     * Applies the JSON data to the biomeRules map.
     * <p>
     * This method is called to process JSON files and load biome rules data into the map.
     *
     * @param jsons          A map of resource locations to JSON elements.
     * @param pResourceManager The resource manager.
     * @param pProfiler      The profiler used to track resource loading.
     */
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        clearEntries();
        Map<ResourceLocation, BiomeRulesData> biomeRules = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            ResourceLocation key = entry.getKey();
            JsonElement element = entry.getValue();
            BiomeRulesData.CODEC.decode(JsonOps.INSTANCE, element)
                    .get()
                    .ifLeft(result -> {
                        BiomeRulesData codec = result.getFirst();
                        biomeRules.put(key, codec);
                    })
                    .ifRight(partial -> LOGGER.error("Failed to parse biome rules data json {} due to: {}", key, partial.message()));


        }

        this.biomeRules = biomeRules;
        LOGGER.info("Data loader for {} loaded {} jsons", this.folderName, this.biomeRules.size());
    }
}
