package com.falazar.farmupcraft.resources;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegacyLootParityTest {
    private static final Map<String, String> CROPS = Map.of(
            "aridgarden", "agave cactusfruit cassava chickpea lentil sisal wolfberry barrelcactus chia nopales yucca papyrus",
            "frostgarden", "broccoli cabbage cauliflower celery cotton huckleberry juniperberry kohlrabi oats peas quinoa raspberry rutabaga rye spinach cloudberry",
            "shadedgarden", "bean blackberry garlic jute leek radish rhubarb scallion spiceleaf sweetpotato tealeaf tomato turnip whitemushroom zucchini truffle nettles",
            "soggygarden", "asparagus blueberry brusselsprout candleberry cotton cranberry greengrape jicama kale millet mulberry okra rice spiceleaf tomatillo waterchestnut lotus cattail",
            "tropicalgarden", "arrowroot cantaloupe coffeebean eggplant grape kenaf kiwi pineapple sesameseeds soybean taro ginger sorghum aloe calabash bokchoy guarana",
            "windygarden", "amaranth artichoke barley bellpepper chilipepper corn cucumber elderberry flax lettuce mustardseeds onion parsnip peanut strawberry wintersquash alfalfa canola sunchoke");

    @TestFactory
    Stream<DynamicTest> gardenOverridesPreserveEveryLegacyEntryWeightAndFunction() {
        return CROPS.entrySet().stream().map(garden -> DynamicTest.dynamicTest(garden.getKey(), () -> {
            var expected = JsonParser.parseString("""
                    {
                      "type": "minecraft:block",
                      "pools": [{
                        "rolls": 1,
                        "functions": [
                          {
                            "function": "minecraft:apply_bonus",
                            "enchantment": "minecraft:fortune",
                            "formula": "minecraft:uniform_bonus_count",
                            "parameters": { "bonusMultiplier": 2 }
                          },
                          { "function": "minecraft:explosion_decay" }
                        ]
                      }]
                    }
                    """).getAsJsonObject();
            var pool = expected.getAsJsonArray("pools").get(0).getAsJsonObject();
            pool.addProperty("name", garden.getKey() + "0");
            var entries = new JsonArray();
            entries.add(item("minecraft:wheat_seeds", 100));
            for (String crop : garden.getValue().split(" ")) {
                entries.add(item("pamhc2crops:" + crop + "item", 1));
            }
            pool.add("entries", entries);
            assertEquals(expected, resource("pamhc2crops", garden.getKey()));
        }));
    }

    @Test
    void wheatRetainsBothIndependentFortuneBonusesAndOnlyMatureConditions() throws IOException {
        var expected = JsonParser.parseString("""
                {
                  "type": "minecraft:block",
                  "pools": [
                    {
                      "rolls": 1,
                      "bonus_rolls": 0,
                      "entries": [{
                        "type": "minecraft:alternatives",
                        "children": [
                          {
                            "type": "minecraft:item",
                            "name": "minecraft:wheat",
                            "functions": [{
                              "function": "minecraft:apply_bonus",
                              "enchantment": "minecraft:fortune",
                              "formula": "minecraft:binomial_with_bonus_count",
                              "parameters": { "extra": 3, "probability": 0.5714286 }
                            }],
                            "conditions": [{
                              "condition": "minecraft:block_state_property",
                              "block": "minecraft:wheat",
                              "properties": { "age": "7" }
                            }]
                          },
                          { "type": "minecraft:item", "name": "minecraft:wheat_seeds" }
                        ]
                      }]
                    },
                    {
                      "rolls": 1,
                      "bonus_rolls": 0,
                      "entries": [{
                        "type": "minecraft:item",
                        "name": "minecraft:wheat_seeds",
                        "functions": [{
                          "function": "minecraft:apply_bonus",
                          "enchantment": "minecraft:fortune",
                          "formula": "minecraft:binomial_with_bonus_count",
                          "parameters": { "extra": 3, "probability": 0.5714286 }
                        }]
                      }],
                      "conditions": [{
                        "condition": "minecraft:block_state_property",
                        "block": "minecraft:wheat",
                        "properties": { "age": "7" }
                      }]
                    }
                  ],
                  "functions": [{ "function": "minecraft:explosion_decay" }],
                  "random_sequence": "minecraft:blocks/wheat"
                }
                """);
        assertEquals(expected, resource("minecraft", "wheat"));
    }

    private static JsonObject item(String name, int weight) {
        var entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", name);
        entry.addProperty("weight", weight);
        return entry;
    }

    private JsonObject resource(String namespace, String name) throws IOException {
        String root = "/data/" + namespace + "/";
        assertNull(getClass().getResource(root + "loot_tables/blocks/" + name + ".json"),
                "The old plural directory must not shadow a missing modern override");
        String path = root + "loot_table/blocks/" + name + ".json";
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, "Missing packaged override " + path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
