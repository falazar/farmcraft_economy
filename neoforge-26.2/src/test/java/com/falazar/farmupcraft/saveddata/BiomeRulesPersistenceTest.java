package com.falazar.farmupcraft.saveddata;

import com.falazar.farmupcraft.data.BiomeRulesData;
import com.falazar.farmupcraft.data.rules.crop.ItemListCropRule;
import com.falazar.farmupcraft.util.FUCTags;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class BiomeRulesPersistenceTest {
    private final List<Path> directories = new ArrayList<>();

    @AfterEach
    void cleanStorageFixtures() throws IOException {
        for (Path directory : directories) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    @Test
    void legacyBiomeDataFieldsAndOptionalFilterRoundTrip(MinecraftServer server) {
        for (boolean filter : List.of(false, true)) {
            JsonObject json = legacyRules(filter);
            BiomeRulesData decoded = BiomeRulesData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            assertEquals(FUCTags.OVERWORLD_NO_OCEAN, decoded.getBiome());
            assertEquals(filter, decoded.getBiomeFilter().isPresent());
            assertEquals(json, BiomeRulesData.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow());
        }
        assertEquals("farmupcraft:empty_biome_tag", FUCTags.EMPTY_BIOME_TAG.location().toString());
        assertTrue(BiomeRulesData.EMPTY.getBiomeFilter().isEmpty());
        assertSame(ItemListCropRule.EMPTY, BiomeRulesData.EMPTY.getCropRules());
        JsonObject tagged = legacyRules(false);
        tagged.add("crop_rules", JsonParser.parseString("""
                {"type":"farmupcraft:tag_based_random_crop_rule",
                 "tag":"farmupcraft:vanilla_crops","random_crop_count":3}
                """));
        BiomeRulesData decoded = BiomeRulesData.CODEC.parse(JsonOps.INSTANCE, tagged).getOrThrow();
        assertEquals(tagged, BiomeRulesData.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow());
    }

    @Test
    void legacyInstanceNbtRetainsSelectedCropsAndTransientSaveFlag(MinecraftServer server) {
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        CompoundTag payload = legacyPayload();
        CompoundTag instanceTag = (CompoundTag) ((CompoundTag) ((CompoundTag)
                payload.getListOrEmpty("biome_rules").getFirst()).get("rules")).get("rules_instance");
        BiomeRulesInstance instance = BiomeRulesInstance.CODEC.parse(ops, instanceTag).getOrThrow();
        assertEquals(List.of(Items.CARROT, Items.WHEAT), instance.getCrops(null));
        assertEquals(List.of(Items.CARROT, Items.WHEAT), instance.getCropsWithRandomId(null, 999));
        assertTrue(instance.biomeHasCrops(Items.CARROT.getDefaultInstance()));
        assertFalse(instance.biomeHasCrops(Items.POTATO.getDefaultInstance()));
        instance.setShouldSave(false);
        assertEquals(instanceTag, BiomeRulesInstance.CODEC.encodeStart(ops, instance).getOrThrow());
        assertTrue(BiomeRulesInstance.CODEC.parse(ops, instanceTag).getOrThrow().shouldSave());
    }

    @Test
    void legacyManagerPayloadRoundTripsWithoutChangingNbtLayout(MinecraftServer server) {
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        CompoundTag legacy = legacyPayload();
        BiomeRulesManager manager = BiomeRulesManager.CODEC.parse(ops, legacy).getOrThrow();
        Holder<Biome> plains = biome(server, "minecraft:plains");
        assertEquals("farmupcraft_biome_rules_manager", BiomeRulesManager.DATA_NAME);
        assertTrue(manager.hasRules());
        assertTrue(manager.hasItems());
        assertEquals(List.of(plains), manager.getBiomesForItem(Items.WHEAT));
        assertEquals(List.of(Items.CARROT, Items.WHEAT), manager.getBiomeRules(plains).getCrops(null));
        assertEquals(legacy, BiomeRulesManager.CODEC.encodeStart(ops, manager).getOrThrow());
    }

    @Test
    void actualStoragePersistsAcrossCloseAndReopenUsingServerRegistries(MinecraftServer server) throws IOException {
        // The official fixture has registries but no levels. Exercise the supported nullable-level
        // storage constructor, not a stub ServerLevel; get(Level) and seeded generation remain untested.
        assertNull(server.overworld());
        Path directory = storageDirectory();
        Holder<Biome> plains = biome(server, "minecraft:plains");
        try (SavedDataStorage storage = storage(server, directory)) {
            BiomeRulesManager manager = storage.computeIfAbsent(BiomeRulesManager.TYPE);
            assertSame(manager, storage.computeIfAbsent(BiomeRulesManager.TYPE));
            manager.setBiomeRules(plains, selectedRules());
            manager.setItemBiomeList(Items.WHEAT, plains);
            storage.saveAndJoin();
            assertFalse(manager.isDirty());
        }
        Path file = BiomeRulesManager.TYPE.id().withSuffix(".dat").resolveAgainst(directory);
        assertTrue(Files.isRegularFile(file));
        CompoundTag disk = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        assertEquals(legacyPayload(), disk.get("data"));
        assertTrue(disk.contains("DataVersion"));
        try (SavedDataStorage reopened = storage(server, directory)) {
            BiomeRulesManager loaded = reopened.get(BiomeRulesManager.TYPE);
            assertNotNull(loaded);
            assertFalse(loaded.isDirty());
            assertEquals(List.of(Items.CARROT, Items.WHEAT), loaded.getBiomeRules(plains).getCrops(null));
            assertEquals(List.of(plains), loaded.getBiomesForItem(Items.WHEAT));
            loaded.removeBiomeRule(plains);
            assertTrue(loaded.isDirty());
            reopened.saveAndJoin();
        }
        try (SavedDataStorage reopened = storage(server, directory)) {
            assertFalse(reopened.get(BiomeRulesManager.TYPE).containsBiome(plains));
        }
    }

    @Test
    void separateWorldStoresDoNotShareRulesItemIndexesOrMissingRuleInstances(MinecraftServer server) throws IOException {
        Path first = storageDirectory();
        Path second = storageDirectory();
        Holder<Biome> plains = biome(server, "minecraft:plains");
        Holder<Biome> desert = biome(server, "minecraft:desert");
        try (SavedDataStorage a = storage(server, first); SavedDataStorage b = storage(server, second)) {
            BiomeRulesManager worldA = a.computeIfAbsent(BiomeRulesManager.TYPE);
            BiomeRulesManager worldB = b.computeIfAbsent(BiomeRulesManager.TYPE);
            assertNotSame(worldA, worldB);
            worldA.setBiomeRules(plains, selectedRules());
            worldA.setItemBiomeList(Items.WHEAT, plains);
            assertFalse(worldB.hasRules());
            assertFalse(worldB.hasItems());
            BiomeRulesInstance missingA = worldA.getBiomeRules(desert);
            BiomeRulesInstance missingB = worldB.getBiomeRules(desert);
            assertNotSame(missingA, missingB);
            assertNotSame(BiomeRulesInstance.EMPTY, missingA);
            assertFalse(missingA.shouldSave());
            missingA.setShouldSave(true);
            assertFalse(missingB.shouldSave());
            assertTrue(BiomeRulesInstance.EMPTY.shouldSave());
            worldB.setBiomeRules(desert, new BiomeRulesInstance(BiomeRulesData.EMPTY, List.of(Items.POTATO)));
            worldB.setItemBiomeList(Items.POTATO, desert);
            a.saveAndJoin();
            b.saveAndJoin();
            worldA.clear();
            assertTrue(worldB.containsBiome(desert));
            assertEquals(List.of(desert), worldB.getBiomesForItem(Items.POTATO));
        }
        try (SavedDataStorage a = storage(server, first); SavedDataStorage b = storage(server, second)) {
            assertFalse(a.get(BiomeRulesManager.TYPE).hasRules());
            assertFalse(a.get(BiomeRulesManager.TYPE).hasItems());
            assertFalse(b.get(BiomeRulesManager.TYPE).containsBiome(plains));
            assertEquals(List.of(Items.POTATO), b.get(BiomeRulesManager.TYPE).getBiomeRules(desert).getCrops(null));
        }
    }

    @Test
    void missingRulesAreOmittedAndLazySelectionMarksTheOwnerDirty(MinecraftServer server) {
        Holder<Biome> plains = biome(server, "minecraft:plains");
        BiomeRulesManager manager = new BiomeRulesManager();
        manager.getBiomeRules(plains);
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        CompoundTag encoded = (CompoundTag) BiomeRulesManager.CODEC.encodeStart(ops, manager).getOrThrow();
        assertTrue(encoded.getListOrEmpty("biome_rules").isEmpty());
        BiomeRulesInstance instance = new BiomeRulesInstance(
                new BiomeRulesData(FUCTags.OVERWORLD_NO_OCEAN, Optional.empty(),
                        new ItemListCropRule(List.of(Items.WHEAT))));
        manager.setBiomeRules(plains, instance);
        manager.setDirty(false);
        assertTrue(instance.getCrops(null).isEmpty());
        assertFalse(manager.isDirty());
        assertEquals(List.of(Items.WHEAT), instance.getCrops(null, true));
        assertTrue(manager.isDirty());
        manager.setDirty(false);
        assertEquals(List.of(Items.WHEAT), instance.getCropsWithRandomId(null, 5));
        assertFalse(manager.isDirty());
    }

    @Test
    void malformedAndUnknownRegistryEntriesReportErrorsInsteadOfSilentDefaults(MinecraftServer server) {
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        JsonObject legacy = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, legacyPayload()).getAsJsonObject();
        for (String invalid : List.of("minecraft:not_a_real_biome", "INVALID ID")) {
            JsonObject broken = legacy.deepCopy();
            broken.getAsJsonArray("biome_rules").get(0).getAsJsonObject().addProperty("biome", invalid);
            assertTrue(BiomeRulesManager.CODEC.parse(ops, broken).error().isPresent(), invalid);
        }
        JsonObject unknownItem = legacy.deepCopy();
        unknownItem.getAsJsonArray("item_to_biome_list").get(0).getAsJsonObject()
                .addProperty("item", "farmupcraft:not_a_real_item");
        assertTrue(BiomeRulesManager.CODEC.parse(ops, unknownItem).error().isPresent());
        JsonObject missingRules = legacy.deepCopy();
        missingRules.getAsJsonArray("biome_rules").get(0).getAsJsonObject()
                .getAsJsonObject("rules").remove("rules_instance");
        assertTrue(BiomeRulesManager.CODEC.parse(ops, missingRules).error().isPresent());
        JsonObject badFilter = legacyRules(true);
        badFilter.addProperty("biome_filter", "INVALID ID");
        assertTrue(BiomeRulesData.CODEC.parse(ops, badFilter).error().isPresent());
        JsonObject badCrop = legacy.deepCopy();
        badCrop.getAsJsonArray("biome_rules").get(0).getAsJsonObject()
                .getAsJsonObject("rules").getAsJsonObject("rules_instance")
                .getAsJsonArray("random_crops").add("farmupcraft:not_a_real_item");
        assertTrue(BiomeRulesManager.CODEC.parse(ops, badCrop).error().isPresent());
        assertTrue(BiomeRulesManager.CODEC.parse(ops, new JsonObject()).error().isPresent());
    }

    private Path storageDirectory() throws IOException {
        Path path = Path.of("build", "biome-rule-test-storage", UUID.randomUUID().toString());
        Files.createDirectories(path);
        directories.add(path);
        return path;
    }

    private static SavedDataStorage storage(MinecraftServer server, Path directory) {
        return new SavedDataStorage(null, directory, DataFixers.getDataFixer(), server.registryAccess());
    }

    private static Holder<Biome> biome(MinecraftServer server, String id) {
        return server.registryAccess().lookupOrThrow(Registries.BIOME)
                .getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse(id)));
    }

    private static BiomeRulesInstance selectedRules() {
        return new BiomeRulesInstance(BiomeRulesData.CODEC.parse(JsonOps.INSTANCE, legacyRules(true)).getOrThrow(),
                List.of(Items.CARROT, Items.WHEAT));
    }

    private static JsonObject legacyRules(boolean filter) {
        return JsonParser.parseString("""
                {"biome":"farmupcraft:overworld_no_ocean",%s
                 "crop_rules":{"type":"farmupcraft:item_list_crop_rule",
                               "allowed_crops":["minecraft:wheat","minecraft:carrot"],"random_crop_count":15}}
                """.formatted(filter ? "\"biome_filter\":\"minecraft:is_overworld\"," : "")).getAsJsonObject();
    }

    private static CompoundTag legacyPayload() {
        var json = JsonParser.parseString("""
                {"biome_rules":[{"biome":"minecraft:plains","rules":{"rules_instance":{
                    "rules_data":%s,"random_crops":["minecraft:carrot","minecraft:wheat"]}}}],
                 "item_to_biome_list":[{"item":"minecraft:wheat","biomes":[{"biome":"minecraft:plains"}]}]}
                """.formatted(legacyRules(true)));
        CompoundTag tag = (CompoundTag) JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, json);
        // JsonOps narrows small numbers to bytes; the legacy Codec.INT persisted an int tag.
        CompoundTag instance = (CompoundTag) ((CompoundTag) ((CompoundTag)
                tag.getListOrEmpty("biome_rules").getFirst()).get("rules")).get("rules_instance");
        ((CompoundTag) ((CompoundTag) instance.get("rules_data")).get("crop_rules"))
                .putInt("random_crop_count", 15);
        return tag;
    }
}
