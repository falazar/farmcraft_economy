package com.falazar.farmupcraft.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class CropDataTest {
    @Test
    void blockCodecsRetainBothSelectorsAndLegacyMisspelledField() {
        JsonElement direct = json("""
                {"crop":{"block":"minecraft:wheat"},"growth_succes_rate":10.0,"populate_blocks":true}
                """);
        var decoded = CropBlockData.CODEC.parse(JsonOps.INSTANCE, direct).getOrThrow();
        assertSame(Blocks.WHEAT, decoded.getBlock());
        assertNull(decoded.getBlocks());
        assertEquals(10.0, decoded.getGrowthSuccesRate());
        assertTrue(decoded.getPopulateBlocks());
        assertEquals(direct, CropBlockData.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow());

        JsonElement tagged = json("""
                {"crop":{"tag":"farmupcraft:vanilla_crops"},"growth_succes_rate":0.5,"populate_blocks":false}
                """);
        var tagDecoded = CropBlockData.CODEC.parse(JsonOps.INSTANCE, tagged).getOrThrow();
        assertNull(tagDecoded.getBlock());
        assertEquals(id("farmupcraft:vanilla_crops"), tagDecoded.getBlocks().location());
        assertEquals(tagged, CropBlockData.CODEC.encodeStart(JsonOps.INSTANCE, tagDecoded).getOrThrow());
        assertEquals(tagged, CropBlockData.CODEC.encodeStart(JsonOps.INSTANCE,
                new CropBlockData(tagDecoded.getBlocks(), 0.5, false)).getOrThrow());
        assertEquals(direct, CropBlockData.CODEC.encodeStart(JsonOps.INSTANCE,
                new CropBlockData(Blocks.WHEAT, null, 10.0, true)).getOrThrow());
    }

    @Test
    void itemCodecsRetainDirectAndTagSelectorsWithRequiredBiomeAndPopulationFields() {
        JsonElement direct = json("""
                {"crop":{"item":"minecraft:wheat_seeds"},"allowed_biomes":"c:is_desert","populate_items":true}
                """);
        var decoded = CropItemData.CODEC.parse(JsonOps.INSTANCE, direct).getOrThrow();
        assertSame(Items.WHEAT_SEEDS, decoded.getItem());
        assertNull(decoded.getItems());
        assertTrue(decoded.getPopulateItems());
        assertEquals(direct, CropItemData.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow());
        assertEquals(direct, CropItemData.CODEC.encodeStart(JsonOps.INSTANCE,
                new CropItemData(Items.WHEAT_SEEDS, decoded.getAllowedBiomes(), true)).getOrThrow());

        JsonElement tagged = json("""
                {"crop":{"tag":"farmupcraft:vanilla_crops"},"allowed_biomes":"c:is_desert","populate_items":false}
                """);
        var tagDecoded = CropItemData.CODEC.parse(JsonOps.INSTANCE, tagged).getOrThrow();
        assertNull(tagDecoded.getItem());
        assertEquals(tagged, CropItemData.CODEC.encodeStart(JsonOps.INSTANCE,
                new CropItemData(tagDecoded.getItems(), tagDecoded.getAllowedBiomes(), false)).getOrThrow());
    }

    @Test
    void malformedOrUnknownCropDefinitionsFailInsteadOfProducingDefaults() {
        for (String invalid : new String[]{
                """
                {"crop":{"block":"minecraft:wheat"},"growth_success_rate":10.0,"populate_blocks":true}
                """,
                """
                {"crop":{"block":"farmupcraft:missing_block"},"growth_succes_rate":10.0,"populate_blocks":true}
                """,
                """
                {"crop":{"tag":"farmupcraft:vanilla_crops"},"growth_succes_rate":10.0}
                """}) {
            assertTrue(CropBlockData.CODEC.parse(JsonOps.INSTANCE, json(invalid)).error().isPresent(), invalid);
        }
        for (String invalid : new String[]{
                """
                {"crop":{"item":"farmupcraft:missing_item"},"allowed_biomes":"c:is_desert","populate_items":true}
                """,
                """
                {"crop":{"item":"minecraft:wheat_seeds"},"populate_items":true}
                """,
                """
                {"crop":{},"allowed_biomes":"c:is_desert","populate_items":true}
                """}) {
            assertTrue(CropItemData.CODEC.parse(JsonOps.INSTANCE, json(invalid)).error().isPresent(), invalid);
        }
    }

    @Test
    void allMigratedDefinitionsAndSupportingTagsLoadThroughRegisteredListeners(MinecraftServer server) {
        assertEquals(2, CropBlockDataJsonManager.getCropDataEntries().size());
        assertEquals(2, CropItemDataJsonManager.getCropDataEntries().size());
        assertEquals(2, BiomeRulesDataJsonManager.getBiomeRules().size());

        var registries = server.registryAccess();
        var blocks = registries.lookupOrThrow(Registries.BLOCK);
        var items = registries.lookupOrThrow(Registries.ITEM);
        var biomes = registries.lookupOrThrow(Registries.BIOME);
        var vanillaBlocks = blocks.get(TagKey.create(Registries.BLOCK, id("farmupcraft:vanilla_crops"))).orElseThrow();
        var moddedBlocks = blocks.get(TagKey.create(Registries.BLOCK, id("farmupcraft:modded_crops"))).orElseThrow();
        assertEquals(4, vanillaBlocks.size());
        assertTrue(moddedBlocks.size() > 80);
        var expectedBlocks = new HashSet<>();
        vanillaBlocks.forEach(holder -> expectedBlocks.add(holder.value()));
        moddedBlocks.forEach(holder -> expectedBlocks.add(holder.value()));
        assertEquals(expectedBlocks, CropBlockDataJsonManager.getCropBlockDataEntries().keySet());

        var vanillaItems = items.get(TagKey.create(Registries.ITEM, id("farmupcraft:vanilla_crops"))).orElseThrow();
        var moddedItems = items.get(TagKey.create(Registries.ITEM, id("farmupcraft:modded_crops"))).orElseThrow();
        assertEquals(4, vanillaItems.size());
        assertTrue(moddedItems.size() > 80);
        var expectedItems = new HashSet<>();
        vanillaItems.forEach(holder -> expectedItems.add(holder.value()));
        moddedItems.forEach(holder -> expectedItems.add(holder.value()));
        assertEquals(expectedItems, CropItemDataJsonManager.getCropItemDataEntries().keySet());

        var desert = biomes.get(id("minecraft:desert")).orElseThrow();
        var plains = biomes.get(id("minecraft:plains")).orElseThrow();
        for (var definition : CropItemDataJsonManager.getCropDataEntries().values()) {
            assertTrue(definition.containsBiome(desert));
            assertFalse(definition.containsBiome(plains));
        }
        assertTrue(items.get(TagKey.create(Registries.ITEM, id("farmupcraft:vanilla_and_modded_crops")))
                .orElseThrow().size() > 80);
        assertTrue(biomes.get(TagKey.create(Registries.BIOME, id("farmupcraft:biomes_o_plenty_overworld_biomes")))
                .orElseThrow().size() > 0);
    }

    @Test
    void directItemPopulationNeverRequestsANullTagAndReloadClearsStaleCaches(MinecraftServer server) {
        var loader = new CropItemDataJsonManager();
        var direct = new CropItemData(Items.WHEAT_SEEDS,
                TagKey.create(Registries.BIOME, id("c:is_desert")), true);
        var disabled = new CropItemData(Items.CARROT, direct.getAllowedBiomes(), false);
        var original = Map.copyOf(CropItemDataJsonManager.getCropDataEntries());
        try {
            loader.apply(Map.of(id("farmupcraft:direct"), direct, id("farmupcraft:disabled"), disabled), null, null);
            assertTrue(CropItemDataJsonManager.getCropItemDataEntries().isEmpty());
            CropItemDataJsonManager.populateCropItemEntries(server.registryAccess());
            assertEquals(Map.of(Items.WHEAT_SEEDS, direct), CropItemDataJsonManager.getCropItemDataEntries());
            assertTrue(direct.containsBiome(server.registryAccess().lookupOrThrow(Registries.BIOME)
                    .get(id("minecraft:desert")).orElseThrow()));

            loader.apply(Map.of(), null, null);
            CropItemDataJsonManager.populateCropItemEntries(server.registryAccess());
            assertTrue(CropItemDataJsonManager.getCropDataEntries().isEmpty());
            assertTrue(CropItemDataJsonManager.getCropItemDataEntries().isEmpty());
        } finally {
            loader.apply(original, null, null);
            CropItemDataJsonManager.populateCropItemEntries(server.registryAccess());
        }
    }

    @Test
    void blockPopulationHonorsDisabledEntriesAndReloadReplacesItsIndex(MinecraftServer server) {
        var loader = new CropBlockDataJsonManager();
        var enabled = new CropBlockData(Blocks.WHEAT, null, 10.0, true);
        var disabled = new CropBlockData(Blocks.CARROTS, null, 10.0, false);
        var original = Map.copyOf(CropBlockDataJsonManager.getCropDataEntries());
        try {
            loader.apply(Map.of(id("farmupcraft:direct"), enabled, id("farmupcraft:disabled"), disabled), null, null);
            assertTrue(CropBlockDataJsonManager.getCropBlockDataEntries().isEmpty());
            CropBlockDataJsonManager.populateCropBlockEntries(server.registryAccess());
            assertEquals(Map.of(Blocks.WHEAT, enabled), CropBlockDataJsonManager.getCropBlockDataEntries());
            loader.apply(Map.of(), null, null);
            CropBlockDataJsonManager.populateCropBlockEntries(server.registryAccess());
            assertTrue(CropBlockDataJsonManager.getCropBlockDataEntries().isEmpty());
        } finally {
            loader.apply(original, null, null);
            CropBlockDataJsonManager.populateCropBlockEntries(server.registryAccess());
        }
    }

    private static Identifier id(String id) {
        return Identifier.parse(id);
    }

    private static JsonElement json(String json) {
        return JsonParser.parseString(json);
    }
}
