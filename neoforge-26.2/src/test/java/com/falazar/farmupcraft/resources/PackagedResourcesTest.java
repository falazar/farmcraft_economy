package com.falazar.farmupcraft.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PackagedResourcesTest {
    private static final String DATA = "/data/farmupcraft/";

    @Test
    void farmlandRestrictionUsesAPackagedNonemptyModernBlockTag() throws IOException {
        var values = resource("tags/block/farmland.json").getAsJsonArray("values");
        assertEquals(1, values.size());
        assertEquals("minecraft:farmland", values.get(0).getAsString());
        assertNull(getClass().getResource(DATA + "tags/blocks/farmland.json"));
    }

    @Test
    void packagedCropTemplatesReferenceExistingTagsWithTheCodecFieldNames() throws IOException {
        for (String name : List.of("test_crops", "test_crops_modded")) {
            var definition = resource("farmupcraft/crop_block_data/" + name + ".json");
            assertEquals(10.0, definition.get("growth_succes_rate").getAsDouble());
            assertTrue(definition.get("populate_blocks").getAsBoolean());
            assertTagExists("block", definition.getAsJsonObject("crop").get("tag").getAsString());
        }
        for (String name : List.of("test_crops", "test_modded_crops")) {
            var definition = resource("farmupcraft/crop_item_data/" + name + ".json");
            assertEquals("c:is_desert", definition.get("allowed_biomes").getAsString());
            assertTrue(definition.get("populate_items").getAsBoolean());
            assertTagExists("item", definition.getAsJsonObject("crop").get("tag").getAsString());
        }
    }

    @Test
    void biomeTemplatesAndFallbackTagsArePackaged() throws IOException {
        var overworld = resource("farmupcraft/biome_rules/overworld_rules.json");
        assertEquals("minecraft:is_overworld", overworld.get("biome").getAsString());
        assertEquals("minecraft:is_ocean", overworld.get("biome_filter").getAsString());
        var bop = resource("farmupcraft/biome_rules/bop_rules.json");
        assertTagExists("worldgen/biome", bop.get("biome").getAsString());
        for (var definition : List.of(overworld, bop)) {
            var rule = definition.getAsJsonObject("crop_rules");
            assertEquals("farmupcraft:tag_based_random_crop_rule", rule.get("type").getAsString());
            assertEquals(15, rule.get("random_crop_count").getAsInt());
            assertTagExists("item", rule.get("tag").getAsString());
        }
        assertTrue(resource("tags/worldgen/biome/empty_biome_tag.json").getAsJsonArray("values").isEmpty());
        var noOcean = resource("tags/worldgen/biome/overworld_no_ocean.json");
        assertEquals("#minecraft:is_overworld", noOcean.getAsJsonArray("values").get(0).getAsString());
        assertEquals("#minecraft:is_ocean", noOcean.getAsJsonArray("remove").get(0).getAsString());
        var bopTag = resource("tags/worldgen/biome/biomes_o_plenty_biomes.json")
                .getAsJsonArray("values").get(0).getAsJsonObject();
        assertEquals("#farmupcraft:biomes_o_plenty_overworld_biomes", bopTag.get("id").getAsString());
        assertFalse(bopTag.get("required").getAsBoolean());
    }

    @Test
    void cropUnionDoesNotLeaveDanglingFarmupcraftTagReferences() throws IOException {
        var union = resource("tags/item/vanilla_and_modded_crops.json").getAsJsonArray("values");
        assertFalse(union.isEmpty());
        for (var value : union) {
            String id = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
            if (id.startsWith("#farmupcraft:")) {
                assertTagExists("item", id.substring(1));
            }
        }
    }

    private void assertTagExists(String registry, String id) throws IOException {
        assertTrue(id.startsWith("farmupcraft:"), id);
        resource("tags/" + registry + "/" + id.substring("farmupcraft:".length()) + ".json");
    }

    private JsonObject resource(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream(DATA + path)) {
            assertNotNull(stream, "Missing packaged resource: " + path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
