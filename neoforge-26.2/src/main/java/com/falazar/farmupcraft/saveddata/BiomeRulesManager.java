package com.falazar.farmupcraft.saveddata;

import com.falazar.farmupcraft.data.BiomeRulesData;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.logging.LogUtils;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Overworld-owned biome state. The 26.2 saved-data codec retains the legacy payload:
 * biome_rules[{biome,rules:{rules_instance}}], item_to_biome_list[{item,biomes:[{biome}]}].
 * SavedDataStorage now stores it under a namespaced path; legacy file relocation is not done here.
 */
public class BiomeRulesManager extends SavedData {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String DATA_NAME = FarmUpCraftNeoForge.MOD_ID + "_biome_rules_manager";

    private static final Codec<Holder<Biome>> BIOME_CODEC = RegistryFixedCodec.create(Registries.BIOME);
    private static final Codec<Identifier> BIOME_ENTRY_CODEC = Identifier.CODEC.fieldOf("biome").codec();
    private static final Codec<BiomeRulesInstance> RULES_CODEC =
            BiomeRulesInstance.CODEC.fieldOf("rules_instance").codec();

    private record BiomeEntry(Identifier biome, BiomeRulesInstance rules) {
        private static final Codec<BiomeEntry> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Identifier.CODEC.fieldOf("biome").forGetter(BiomeEntry::biome),
                        RULES_CODEC.fieldOf("rules").forGetter(BiomeEntry::rules)
                ).apply(instance, BiomeEntry::new));
    }

    private record ItemEntry(Item item, List<Identifier> biomes) {
        private static final Codec<ItemEntry> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(ItemEntry::item),
                        BIOME_ENTRY_CODEC.listOf().fieldOf("biomes").forGetter(ItemEntry::biomes)
                ).apply(instance, ItemEntry::new));
    }

    private record Payload(List<BiomeEntry> biomes, List<ItemEntry> items) {}

    private static final Codec<Payload> PAYLOAD_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BiomeEntry.CODEC.listOf().fieldOf("biome_rules").forGetter(Payload::biomes),
                    ItemEntry.CODEC.listOf().fieldOf("item_to_biome_list").forGetter(Payload::items)
            ).apply(instance, Payload::new));

    public static final Codec<BiomeRulesManager> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<BiomeRulesManager, T>> decode(DynamicOps<T> ops, T input) {
            return PAYLOAD_CODEC.decode(ops, input)
                    .map(pair -> Pair.of(new BiomeRulesManager(pair.getFirst(), ops), pair.getSecond()))
                    .mapError(BiomeRulesManager::logCodecError);
        }

        @Override
        public <T> DataResult<T> encode(BiomeRulesManager input, DynamicOps<T> ops, T prefix) {
            return PAYLOAD_CODEC.encode(new Payload(input.biomeEntries(), input.itemEntries()), ops, prefix)
                    .mapError(BiomeRulesManager::logCodecError);
        }
    };

    private static String logCodecError(String error) {
        LOGGER.error("Failed to load or save biome rules: {}", error);
        return error;
    }

    public static final SavedDataType<BiomeRulesManager> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, DATA_NAME),
            BiomeRulesManager::new, CODEC);

    // No strong references to disconnected client worlds, nor any shared server state.
    private static final Map<Level, BiomeRulesManager> CLIENT_MANAGERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Holder<Biome>, BiomeRulesInstance> biomeRules = new ConcurrentHashMap<>();
    private final Map<Item, List<Holder<Biome>>> itemToBiomeList = new ConcurrentHashMap<>();
    private final Map<Identifier, BiomeRulesInstance> unavailableBiomeRules = new ConcurrentHashMap<>();

    public BiomeRulesManager() {}

    private <T> BiomeRulesManager(Payload payload, DynamicOps<T> ops) {
        for (BiomeEntry entry : payload.biomes()) {
            var resolved = BIOME_CODEC.parse(ops, ops.createString(entry.biome().toString()));
            if (resolved.result().isPresent()) {
                putBiomeRules(resolved.result().orElseThrow(), entry.rules());
            } else {
                LOGGER.warn("Biome {} is unavailable; retaining its saved crop selections for a future restore: {}",
                        entry.biome(), resolved.error().orElseThrow().message());
                unavailableBiomeRules.put(entry.biome(), entry.rules());
            }
        }
        for (ItemEntry entry : payload.items()) {
            List<Holder<Biome>> biomes = new ArrayList<>();
            for (Identifier id : entry.biomes()) {
                var resolved = BIOME_CODEC.parse(ops, ops.createString(id.toString()));
                if (resolved.result().isPresent()) {
                    biomes.add(resolved.result().orElseThrow());
                } else {
                    LOGGER.warn("Excluding unavailable biome {} from crop lookup for {}", id,
                            BuiltInRegistries.ITEM.getKey(entry.item()));
                }
            }
            itemToBiomeList.put(entry.item(), biomes);
        }
    }

    public static BiomeRulesManager get(Level level) {
        Objects.requireNonNull(level, "level");
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
        }
        return CLIENT_MANAGERS.computeIfAbsent(level, ignored -> new BiomeRulesManager());
    }

    private List<BiomeEntry> biomeEntries() {
        List<BiomeEntry> entries = new ArrayList<>(biomeRules.entrySet().stream()
                .filter(entry -> entry.getValue().shouldSave())
                .map(entry -> new BiomeEntry(entry.getKey().unwrapKey().orElseThrow().identifier(), entry.getValue())).toList());
        unavailableBiomeRules.forEach((id, rules) -> entries.add(new BiomeEntry(id, rules)));
        return entries;
    }

    private List<ItemEntry> itemEntries() {
        return itemToBiomeList.entrySet().stream()
                .map(entry -> new ItemEntry(entry.getKey(), entry.getValue().stream()
                        .map(biome -> biome.unwrapKey().orElseThrow().identifier()).toList())).toList();
    }

    public boolean hasRules() {
        return !biomeRules.isEmpty();
    }

    public void clear() {
        biomeRules.clear();
        itemToBiomeList.clear();
        unavailableBiomeRules.clear();
        setDirty();
    }

    public void removeBiomeRule(Holder<Biome> biome) {
        if (biomeRules.remove(biome) != null) {
            setDirty();
        }
    }

    public boolean containsBiome(Holder<Biome> biome) {
        return biomeRules.containsKey(biome) && biomeRules.get(biome).shouldSave();
    }

    public BiomeRulesInstance getBiomeRules(Holder<Biome> biome) {
        return biomeRules.computeIfAbsent(biome, ignored -> {
            // Never mutate/reuse the global EMPTY instance for a world's missing rule.
            BiomeRulesInstance empty = new BiomeRulesInstance(BiomeRulesData.EMPTY);
            empty.setShouldSave(false);
            empty.onChanged(this::setDirty);
            return empty;
        });
    }

    private void putBiomeRules(Holder<Biome> biome, BiomeRulesInstance rules) {
        rules.onChanged(this::setDirty);
        biomeRules.put(biome, rules);
    }

    public void setBiomeRules(Holder<Biome> biome, BiomeRulesInstance rules) {
        var restored = unavailableBiomeRules.remove(biome.unwrapKey().orElseThrow().identifier());
        putBiomeRules(biome, restored == null ? rules : restored);
        setDirty();
    }

    public void rebuildItemIndex() {
        Map<Item, List<Holder<Biome>>> rebuilt = new java.util.HashMap<>();
        biomeRules.forEach((biome, rules) -> {
            if (rules.shouldSave()) {
                for (Item item : rules.getCrops(null)) {
                    var biomes = rebuilt.computeIfAbsent(item, ignored -> new ArrayList<>());
                    if (!biomes.contains(biome)) biomes.add(biome);
                }
            }
        });
        if (!itemToBiomeList.equals(rebuilt)) {
            itemToBiomeList.clear();
            itemToBiomeList.putAll(rebuilt);
            setDirty();
        }
    }

    public Collection<BiomeRulesInstance> getBiomeRules() {
        return biomeRules.values();
    }

    public Set<Holder<Biome>> getBiomeKeys() {
        return biomeRules.keySet();
    }

    public List<Holder<Biome>> getBiomesForItem(Item item) {
        return itemToBiomeList.computeIfAbsent(item, ignored -> new ArrayList<>());
    }

    public Set<Item> getItems() {
        return itemToBiomeList.keySet();
    }

    public void setItemBiomeList(Item item, Holder<Biome> biome) {
        getBiomesForItem(item).add(biome);
        setDirty();
    }

    public Map<Holder<Biome>, BiomeRulesInstance> getData() {
        return biomeRules;
    }

    public boolean hasItems() {
        return !itemToBiomeList.isEmpty();
    }
}
