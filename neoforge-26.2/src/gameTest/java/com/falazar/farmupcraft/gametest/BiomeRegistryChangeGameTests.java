package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.data.BiomeRulesData;
import com.falazar.farmupcraft.data.BiomeRulesDataJsonManager;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.falazar.farmupcraft.saveddata.BiomeRulesManager;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.util.datafix.DataFixers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class BiomeRegistryChangeGameTests {
    private BiomeRegistryChangeGameTests() {}

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("biome_registry_changes"), BiomeRegistryChangeGameTests::registryChanges));
    }

    private static void registryChanges(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = BiomeRulesManager.get(level);
        var registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        var tundra = registry.getOrThrow(ResourceKey.create(Registries.BIOME,
                Identifier.parse("biomesoplenty:tundra")));
        var previous = manager.getBiomeRules(tundra);
        Map<Holder<Biome>, List<Item>> oldSelections = new HashMap<>();
        for (var biome : manager.getBiomeKeys()) {
            if (!biome.equals(tundra) && manager.getBiomeRules(biome).shouldSave()) {
                oldSelections.put(biome, List.copyOf(manager.getBiomeRules(biome).getCrops(level)));
            }
        }
        try {
            manager.removeBiomeRule(tundra);
            manager.rebuildItemIndex();
            manager.getBiomeRules(tundra); // A lookup of a new biome must not block its initialization.
            BiomeRulesDataJsonManager.populateBiomeRulesInstances(level);
            helper.assertTrue(manager.containsBiome(tundra), "New eligible biome receives real rules");
            List<Item> selected = List.copyOf(manager.getBiomeRules(tundra).getCrops(level));
            helper.assertTrue(selected.size() >= 15, "New biome receives the 15-crop selection plus seed pairs");
            oldSelections.forEach((biome, crops) -> helper.assertValueEqual(
                    manager.getBiomeRules(biome).getCrops(level), crops, "Existing selections never change"));
            for (Item item : selected) {
                helper.assertTrue(manager.getBiomesForItem(item).contains(tundra),
                        "New biome is indexed for crop lookup");
            }
            BiomeRulesDataJsonManager.populateBiomeRulesInstances(level);
            helper.assertValueEqual(manager.getBiomeRules(tundra).getCrops(level), selected,
                    "Repeated initialization does not reroll");
            for (Item item : selected) {
                helper.assertValueEqual(manager.getBiomesForItem(item).stream().filter(tundra::equals).count(),
                        1L, "Repeated initialization does not duplicate lookup entries");
            }

            var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            var standalone = new BiomeRulesManager();
            standalone.setBiomeRules(tundra, new BiomeRulesInstance(BiomeRulesData.EMPTY,
                    List.of(Items.CARROT, Items.WHEAT)));
            standalone.rebuildItemIndex();
            JsonObject json = BiomeRulesManager.CODEC.encodeStart(ops, standalone).getOrThrow().getAsJsonObject();
            String missing = "missing:temporarily_removed";
            json.getAsJsonArray("biome_rules").get(0).getAsJsonObject().addProperty("biome", missing);
            for (var entry : json.getAsJsonArray("item_to_biome_list")) {
                entry.getAsJsonObject().getAsJsonArray("biomes").get(0).getAsJsonObject().addProperty("biome", missing);
            }
            var loaded = BiomeRulesManager.CODEC.parse(ops, json).getOrThrow();
            helper.assertTrue(!loaded.hasRules(), "Unavailable biome has no active rules");
            helper.assertTrue(loaded.getBiomesForItem(Items.CARROT).isEmpty(),
                    "Unavailable biome excluded from crop searches");
            JsonObject saved = BiomeRulesManager.CODEC.encodeStart(ops, loaded).getOrThrow().getAsJsonObject();
            helper.assertValueEqual(saved.getAsJsonArray("biome_rules"), json.getAsJsonArray("biome_rules"),
                    "Removed biome's named rules and exact selections survive saving");
            verifyDiskPersistence(helper, loaded, saved);
            saved.getAsJsonArray("biome_rules").get(0).getAsJsonObject()
                    .addProperty("biome", tundra.unwrapKey().orElseThrow().identifier().toString());
            var restored = BiomeRulesManager.CODEC.parse(ops, saved).getOrThrow();
            restored.rebuildItemIndex();
            helper.assertValueEqual(restored.getBiomeRules(tundra).getCrops(level),
                    List.of(Items.CARROT, Items.WHEAT), "Reintroduced biome recovers its original crops");
            helper.assertTrue(restored.getBiomesForItem(Items.CARROT).contains(tundra),
                    "Restored biome returns to crop searches");
            saved.getAsJsonArray("biome_rules").get(0).getAsJsonObject().addProperty("biome", "INVALID ID");
            helper.assertTrue(BiomeRulesManager.CODEC.parse(ops, saved).error().isPresent(),
                    "Malformed IDs still fail explicitly");
        } finally {
            manager.setBiomeRules(tundra, previous);
            manager.rebuildItemIndex();
        }
        helper.succeed();
    }

    private static void verifyDiskPersistence(GameTestHelper helper, BiomeRulesManager removed, JsonObject expected) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("farmupcraft-biome-change-");
            var level = helper.getLevel();
            try (var storage = new SavedDataStorage(null, directory, DataFixers.getDataFixer(), level.registryAccess())) {
                storage.set(BiomeRulesManager.TYPE, removed);
                removed.setDirty();
                storage.saveAndJoin();
            }
            try (var storage = new SavedDataStorage(null, directory, DataFixers.getDataFixer(), level.registryAccess())) {
                var loaded = storage.get(BiomeRulesManager.TYPE);
                helper.assertTrue(loaded != null, "Removed biome state reloads from actual SavedDataStorage");
                var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                helper.assertValueEqual(BiomeRulesManager.CODEC.encodeStart(ops, loaded).getOrThrow()
                                .getAsJsonObject().getAsJsonArray("biome_rules"),
                        expected.getAsJsonArray("biome_rules"), "Disk reload retains unavailable biome's original crops");
            }
        } catch (IOException error) {
            throw new IllegalStateException("Biome persistence fixture failed", error);
        } finally {
            if (directory != null) {
                try (var paths = Files.walk(directory)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                } catch (IOException error) {
                    throw new IllegalStateException("Cannot clean biome persistence fixture", error);
                }
            }
        }
    }
}
