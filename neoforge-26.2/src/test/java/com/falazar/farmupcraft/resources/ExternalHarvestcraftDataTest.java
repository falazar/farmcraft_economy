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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ExternalHarvestcraftDataTest {
    @TestFactory
    Stream<DynamicTest> fishTagsPreserveEveryLegacyMemberWithoutRequiringCompanionMods() {
        return Map.of(
                "fishes", List.of("aquaculture:fish_fillet_raw", "alexsmobs:raw_catfish"),
                "rawfish", List.of("aquaculture:fish_fillet_cooked", "aquaculture:fish_fillet_raw",
                        "alexsmobs:raw_catfish"),
                "rawmeats", List.of("aquaculture:fish_fillet_raw", "alexsmobs:raw_catfish")
        ).entrySet().stream().map(tag -> DynamicTest.dynamicTest(tag.getKey(), () -> {
            var expected = new JsonObject();
            expected.addProperty("replace", false);
            var values = new JsonArray();
            for (String id : tag.getValue()) {
                var member = new JsonObject();
                member.addProperty("id", id);
                member.addProperty("required", false);
                values.add(member);
            }
            expected.add("values", values);
            assertEquals(expected, resource("c/tags/item/" + tag.getKey() + ".json"));
        }));
    }

    @Test
    void oilRetainsTwoCanolaOneRollerAndEightOutputsDespiteTheLegacyFilename() throws IOException {
        assertEquals(JsonParser.parseString("""
                {
                  "type": "minecraft:crafting_shapeless",
                  "ingredients": ["#c:tool_roller", "pamhc2crops:canolaitem", "pamhc2crops:canolaitem"],
                  "result": { "id": "pamhc2foodcore:cookingoilitem", "count": 8 }
                }
                """), resource("pamhc2crops/recipe/cookingoil_x4_canola_x2.json"));
    }

    @TestFactory
    Stream<DynamicTest> pipeRecipesKeepTheOriginalPatternIngredientsAndOneOutput() {
        return Map.of("fluid_pipe", "minecraft:bucket", "item_pipe", "minecraft:dropper")
                .entrySet().stream().map(pipe -> DynamicTest.dynamicTest(pipe.getKey(), () -> {
                    var expected = JsonParser.parseString("""
                            {
                              "neoforge:conditions": [{"type":"neoforge:mod_loaded","modid":"pipez"}],
                              "type": "minecraft:crafting_shaped",
                              "pattern": ["III", "BRB", "III"],
                              "key": {"B":"minecraft:bucket","I":"#c:ingots/iron","R":"#c:dusts/redstone"},
                              "result": {"id":"pipez:fluid_pipe","count":1}
                            }
                            """).getAsJsonObject();
                    if (pipe.getKey().equals("item_pipe")) {
                        expected.getAsJsonArray("pattern").set(1, JsonParser.parseString("\"DRD\""));
                        expected.getAsJsonObject("key").remove("B");
                        expected.getAsJsonObject("key").addProperty("D", pipe.getValue());
                    }
                    expected.getAsJsonObject("result").addProperty("id", "pipez:" + pipe.getKey());
                    assertEquals(expected, resource("pipez/recipe/" + pipe.getKey() + ".json"));
                }));
    }

    private static JsonObject resource(String path) throws IOException {
        try (var stream = ExternalHarvestcraftDataTest.class.getResourceAsStream("/data/" + path)) {
            assertNotNull(stream, "Missing external datapack conversion: " + path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
