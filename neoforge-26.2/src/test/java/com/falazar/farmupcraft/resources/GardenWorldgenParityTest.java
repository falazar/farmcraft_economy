package com.falazar.farmupcraft.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GardenWorldgenParityTest {
    private static final Map<String, String> BINDINGS = Map.of(
            "arid_garden_placed", "arid",
            "frost_garden_placed", "frost",
            "shaded_garden_placed", "shaded",
            "soggy_garden_placed", "soggy",
            "tropical_garden_placed_jungle", "tropical",
            "tropical_garden_placed_ocean", "tropical",
            "windy_garden_placed_plains", "windy",
            "windy_garden_placed_savanna", "windy");

    @Test
    void allSixConfiguredFeaturesPreserveTheEntireOriginalPatchExceptTheRemovedType() throws IOException {
        for (String garden : BINDINGS.values().stream().distinct().toList()) {
            var expected = JsonParser.parseString("""
                    {
                      "type": "pamhc2crops:original_random_patch",
                      "config": {
                        "feature": {
                          "feature": {
                            "type": "minecraft:simple_block",
                            "config": {
                              "to_place": {
                                "type": "minecraft:simple_state_provider",
                                "state": { "Name": "pamhc2crops:%sgarden" }
                              }
                            }
                          },
                          "placement": [{
                            "type": "minecraft:block_predicate_filter",
                            "predicate": { "type": "minecraft:matching_blocks", "blocks": "minecraft:air" }
                          }]
                        },
                        "tries": 32, "xz_spread": 6, "y_spread": 2
                      }
                    }
                    """.formatted(garden));
            assertEquals(expected, resource("worldgen/configured_feature/" + garden + "_garden.json"),
                    garden + ": only minecraft:random_patch -> installed HC compatibility feature may change");
        }
    }

    @Test
    void allEightPlacedFeaturesPreserveOriginalOrderHeightFrequencyAndTheSharedAridSurvivalGuard()
            throws IOException {
        for (var binding : BINDINGS.entrySet()) {
            var expected = JsonParser.parseString("""
                    {
                      "feature": "pamhc2crops:%s_garden",
                      "placement": [
                        { "type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING" },
                        {
                          "type": "minecraft:block_predicate_filter",
                          "predicate": {
                            "type": "minecraft:would_survive",
                            "state": { "Name": "pamhc2crops:aridgarden" }
                          }
                        },
                        { "type": "minecraft:rarity_filter", "chance": 50 }
                      ]
                    }
                    """.formatted(binding.getValue()));
            assertEquals(expected, resource("worldgen/placed_feature/" + binding.getKey() + ".json"),
                    binding.getKey());
        }
    }

    @Test
    void installedHarvestCraftAlreadyAttachesEveryOriginalPlacedIdAtTheOriginalStep() throws IOException {
        var tags = Map.of(
                "arid_garden_placed", "c:is_dry/overworld",
                "frost_garden_placed", "c:is_tree/coniferous",
                "shaded_garden_placed", "minecraft:is_forest",
                "soggy_garden_placed", "c:is_swamp",
                "tropical_garden_placed_jungle", "minecraft:is_jungle",
                "tropical_garden_placed_ocean", "minecraft:is_ocean",
                "windy_garden_placed_plains", "c:is_plains",
                "windy_garden_placed_savanna", "minecraft:is_savanna");
        assertEquals(BINDINGS.keySet(), tags.keySet());
        var harvestCraft = ModList.get().getModFileById("pamhc2crops");
        assertNotNull(harvestCraft, "Installed HarvestCraft crops mod file");
        var contents = harvestCraft.getFile().getContents();
        for (var binding : tags.entrySet()) {
            var expected = new JsonObject();
            expected.addProperty("type", "neoforge:add_features");
            expected.addProperty("biomes", "#" + binding.getValue());
            expected.addProperty("features", "pamhc2crops:" + binding.getKey());
            expected.addProperty("step", "vegetal_decoration");
            // FML isolates dependency resources from this test mod's classloader.
            var path = "data/pamhc2crops/neoforge/biome_modifier/" + binding.getKey() + ".json";
            assertTrue(contents.containsFile(path), "Missing installed HC biome modifier: " + path);
            try (var stream = contents.openFile(path);
                 var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                assertEquals(expected, JsonParser.parseReader(reader).getAsJsonObject(),
                        "Installed HC modifier must reference our override; do not add duplicate biome modifiers");
            }
        }
    }

    private JsonObject resource(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream("/data/pamhc2crops/" + path)) {
            assertNotNull(stream, path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
