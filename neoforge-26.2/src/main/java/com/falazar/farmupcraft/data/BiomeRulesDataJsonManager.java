package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.falazar.farmupcraft.saveddata.BiomeRulesManager;
import com.falazar.farmupcraft.util.FUCTags;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
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

        int added = 0;
        for (var entry : biomeRules.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            BiomeRulesData data = entry.getValue();
            for (var biome : biomes.listElements().toList()) {
                boolean matches = biome.is(data.getBiome());
                if (data.getBiome().equals(FUCTags.BIOMES_O_PLENTY_OVERWORLD_TAG)) {
                    matches = biome.unwrapKey().orElseThrow().identifier().getNamespace().equals("biomesoplenty")
                            && biome.is(BiomeTags.IS_OVERWORLD)
                            && !biome.is(BiomeTags.IS_NETHER) && !biome.is(BiomeTags.IS_END);
                }
                boolean filtered = data.getBiomeFilter().map(biome::is).orElse(false);
                if (matches && !manager.containsBiome(biome) && !filtered) {
                    manager.setBiomeRules(biome, new BiomeRulesInstance(data));
                    added++;
                }
            }
        }
        if (added > 0) LOGGER.info("Added crop rules for {} new biomes; existing selections preserved", added);

        for (var biome : manager.getBiomeKeys()) {
            var instance = manager.getBiomeRules(biome);
            if (instance.shouldSave()) instance.getCropsForBiome(level,
                    biome.unwrapKey().orElseThrow().identifier().toString());
        }
        manager.rebuildItemIndex();
    }

    @Override
    protected void apply(Map<Identifier, BiomeRulesData> entries, ResourceManager manager, ProfilerFiller profiler) {
        biomeRules = new HashMap<>(entries);
        LOGGER.info("Data loader for {} loaded {} jsons", folderName, entries.size());
    }
}
