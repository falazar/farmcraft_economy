package com.falazar.farmupcraft.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OptionalCompatibilityResourcesTest {
    @Test
    void veswaxRecipePreservesNineToOneAndRequiresLycanites() throws IOException {
        var recipe = resource("farmupcraft/recipe/honeycomb_from_veswax");
        assertLycanitesGuard(recipe);
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
        var ingredients = recipe.getAsJsonArray("ingredients");
        assertEquals(9, ingredients.size());
        ingredients.forEach(item -> assertEquals("lycanitesmobs:veswax", item.getAsString()));
        assertEquals("minecraft:honeycomb", recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
    }

    @Test
    void veswaxLootRequiresSilkTouchUsingModernComponentPredicate() throws IOException {
        var loot = resource("lycanitesmobs/loot_table/blocks/veswax");
        assertLycanitesGuard(loot);
        var pools = loot.getAsJsonArray("pools");
        assertEquals(1, pools.size());
        var pool = pools.get(0).getAsJsonObject();
        assertEquals(1, pool.get("rolls").getAsInt());
        var entries = pool.getAsJsonArray("entries");
        assertEquals(1, entries.size());
        var entry = entries.get(0).getAsJsonObject();
        assertEquals("lycanitesmobs:veswax", entry.get("name").getAsString());
        var conditions = entry.getAsJsonArray("conditions");
        assertEquals(1, conditions.size());
        var condition = conditions.get(0).getAsJsonObject();
        assertEquals("minecraft:match_tool", condition.get("condition").getAsString());
        var enchantment = condition.getAsJsonObject("predicate").getAsJsonObject("predicates")
                .getAsJsonArray("minecraft:enchantments").get(0).getAsJsonObject();
        assertEquals("minecraft:silk_touch", enchantment.get("enchantments").getAsString());
        assertEquals(1, enchantment.getAsJsonObject("levels").get("min").getAsInt());
    }

    @Test
    void eggAndSavannaTagsExtendExistingTagsWithOnlyOptionalEntries() throws IOException {
        assertOptionalTag("c/tags/item/egg", List.of("alexsmobs:emu_egg", "naturalist:duck_egg"));
        assertOptionalTag("minecraft/tags/worldgen/biome/is_savanna",
                List.of("biomesoplenty:scrubland", "biomesoplenty:lush_savanna", "biomesoplenty:dryland"));
    }

    private void assertOptionalTag(String path, List<String> expected) throws IOException {
        var tag = resource(path);
        assertFalse(tag.get("replace").getAsBoolean());
        var values = tag.getAsJsonArray("values");
        assertEquals(expected.size(), values.size());
        for (int i = 0; i < expected.size(); i++) {
            var value = values.get(i).getAsJsonObject();
            assertEquals(expected.get(i), value.get("id").getAsString());
            assertFalse(value.get("required").getAsBoolean());
        }
    }

    private static void assertLycanitesGuard(JsonObject resource) {
        var conditions = resource.getAsJsonArray("neoforge:conditions");
        assertEquals(1, conditions.size());
        var guard = conditions.get(0).getAsJsonObject();
        assertEquals("neoforge:mod_loaded", guard.get("type").getAsString());
        assertEquals("lycanitesmobs", guard.get("modid").getAsString());
    }

    private JsonObject resource(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream("/data/" + path + ".json")) {
            assertNotNull(stream, path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
