package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.falazar.farmupcraft.saveddata.BiomeRulesManager;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class BiomeRulesDataJsonManager extends SimpleJsonResourceReloadListener<BiomeRulesData> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Map<Identifier, BiomeRulesData> biomeRules = new HashMap<>();
    private final String folderName;

    public BiomeRulesDataJsonManager() {
        this("farmupcraft/biome_rules");
    }

    public BiomeRulesDataJsonManager(String folderName) {
        super(BiomeRulesData.CODEC, FileToIdConverter.json(folderName));
        this.folderName = folderName;
    }

    public static void clearEntries() {
        biomeRules = new HashMap<>();
    }

    public static Map<Identifier, BiomeRulesData> getBiomeRules() {
        return biomeRules;
    }

    public static void populateBiomeRulesInstances(ServerLevel level) {
        if (biomeRules.isEmpty()) return;
        BiomeRulesManager manager = BiomeRulesManager.get(level);
        var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);

        if (!manager.hasRules()) {
            for (BiomeRulesData data : biomeRules.values()) {
                for (var biome : biomes.getTagOrEmpty(data.getBiome())) {
                    boolean filtered = data.getBiomeFilter().map(biome::is).orElse(false);
                    if (!manager.containsBiome(biome) && !filtered) {
                        manager.setBiomeRules(biome, new BiomeRulesInstance(data));
                    }
                }
            }
            LOGGER.info("Created a total of {} biomes with rules", manager.getBiomeKeys().size());
        }

        if (!manager.hasItems()) {
            for (var biome : manager.getBiomeKeys()) {
                var instance = manager.getBiomeRules(biome);
                for (var item : instance.getCropsWithRandomId(level, biomes.getId(biome.value()))) {
                    manager.setItemBiomeList(item, biome);
                }
            }
            // Lazy crop selection mutates the saved rule instances, including for empty item lists.
            if (manager.hasRules()) manager.setDirty();
        }
    }

    @Override
    protected void apply(Map<Identifier, BiomeRulesData> entries, ResourceManager manager, ProfilerFiller profiler) {
        biomeRules = new HashMap<>(entries);
        LOGGER.info("Data loader for {} loaded {} jsons", folderName, entries.size());
    }
}
