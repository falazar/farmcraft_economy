package com.falazar.farmupcraft.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TreeWorldgenParityTest {
    private static final String TREES = """
            acorn almond apple apricot avocado banana breadfruit candlenut cashew cherry chestnut
            cinnamon coconut date dragonfruit durian fig guava gooseberry grapefruit hazelnut jackfruit
            lemon lime lychee mango maple nutmeg olive orange papaya paperbark passionfruit pawpaw peach
            pear pecan peppercorn persimmon pinenut pistachio plum pomegranate rambutan soursop spiderweb
            starfruit tamarind vanillabean walnut
            """;
    private static final Set<String> OAK = Set.of(
            "acorn", "apple", "avocado", "candlenut", "cherry", "chestnut", "gooseberry",
            "hazelnut", "lemon", "nutmeg", "orange", "pawpaw", "peach", "pear", "plum",
            "soursop", "spiderweb", "walnut");

    @TestFactory
    Stream<DynamicTest> allOneHundredLegacyDefinitionsRetainEveryActiveField() {
        return names().flatMap(tree -> Stream.of(
                DynamicTest.dynamicTest("configured_feature/" + configuredName(tree),
                        () -> assertEquals(expectedConfigured(tree),
                                resource("configured_feature", configuredName(tree)))),
                DynamicTest.dynamicTest("placed_feature/" + tree + "_placed",
                        () -> assertEquals(expectedPlaced(tree),
                                resource("placed_feature", tree + "_placed")))));
    }

    @Test
    void exactLegacyInventoryContainsFiftyConfiguredAndFiftyPlacedFeatures() throws IOException {
        var configured = names().map(TreeWorldgenParityTest::configuredName).collect(Collectors.toSet());
        var placed = names().map(name -> name + "_placed").collect(Collectors.toSet());
        assertEquals(50, configured.size());
        assertEquals(50, placed.size());
        assertEquals(configured, inventory("configured_feature", "acorn"));
        assertEquals(placed, inventory("placed_feature", "acorn_placed"));
    }

    private static Set<String> inventory(String kind, String anchor) throws IOException {
        String prefix = "data/pamhc2trees/worldgen/" + kind + "/";
        var resource = TreeWorldgenParityTest.class.getResource("/" + prefix + anchor + ".json");
        assertNotNull(resource, prefix + anchor + ".json");
        if (resource.getProtocol().equals("file")) {
            try (var files = Files.list(Path.of(URI.create(resource.toExternalForm())).getParent())) {
                return files.filter(Files::isRegularFile)
                        .map(file -> file.getFileName().toString())
                        .filter(name -> name.endsWith(".json"))
                        .map(name -> name.substring(0, name.length() - 5))
                        .collect(Collectors.toSet());
            }
        }
        if (resource.getProtocol().equals("jar")) {
            var connection = (JarURLConnection) resource.openConnection();
            connection.setUseCaches(false);
            try (var jar = connection.getJarFile()) {
                return jar.stream().filter(entry -> !entry.isDirectory())
                        .map(entry -> entry.getName())
                        .filter(name -> name.startsWith(prefix))
                        .map(name -> name.substring(prefix.length()))
                        .filter(name -> name.endsWith(".json") && !name.contains("/"))
                        .map(name -> name.substring(0, name.length() - 5))
                        .collect(Collectors.toSet());
            }
        }
        throw new IOException("Unsupported bundled worldgen resource protocol: " + resource);
    }

    private static Stream<String> names() {
        return Arrays.stream(TREES.strip().split("\\s+"));
    }

    private static String configuredName(String tree) {
        // This is the actual legacy registry key, not the correctly spelled sapling/placer key.
        return tree.equals("guava") ? "gauva" : tree;
    }

    private static JsonObject expectedConfigured(String tree) {
        String wood = OAK.contains(tree) ? "oak"
                : Set.of("maple", "pinenut").contains(tree) ? "spruce" : "jungle";
        String placer = tree.equals("pinenut") ? "pine" : tree;
        int base = tree.equals("pinenut") ? 7 : 4;
        int randA = tree.equals("pinenut") ? 2 : 1;
        int randB = tree.equals("pinenut") ? 3
                : Set.of("cinnamon", "maple", "paperbark").contains(tree) ? 1 : 2;
        // 1.20.1 setDirtAt(force_dirt=false): keep dirt-tag states EXCEPT grass/mycelium.
        // 26.2 removes both old keys; a no-fallback rule preserves that exact predicate.
        return JsonParser.parseString("""
                {
                  "type": "minecraft:tree",
                  "config": {
                    "decorators": [],
                    "below_trunk_provider": {
                      "type": "minecraft:rule_based_state_provider",
                      "rules": [{
                        "if_true": {
                          "type": "minecraft:any_of",
                          "predicates": [
                            {"type": "minecraft:not", "predicate": {
                              "type": "minecraft:matching_block_tag", "tag": "minecraft:dirt"
                            }},
                            {"type": "minecraft:matching_blocks",
                             "blocks": ["minecraft:grass_block", "minecraft:mycelium"]}
                          ]
                        },
                        "then": {"type": "minecraft:simple_state_provider",
                                 "state": {"Name": "minecraft:dirt"}}
                      }]
                    },
                    "foliage_placer": {"type": "pamhc2trees:%s_fruit_placer"},
                    "foliage_provider": {
                      "type": "minecraft:simple_state_provider",
                      "state": {"Name": "minecraft:%s_leaves", "Properties": {
                        "distance": "7", "persistent": "false", "waterlogged": "false"
                      }}
                    },
                    "ignore_vines": false,
                    "minimum_size": {"type": "minecraft:two_layers_feature_size",
                                     "limit": 1, "lower_size": 0, "upper_size": 2},
                    "trunk_placer": {"type": "minecraft:straight_trunk_placer",
                                     "base_height": %d, "height_rand_a": %d, "height_rand_b": %d},
                    "trunk_provider": {
                      "type": "minecraft:simple_state_provider",
                      "state": {"Name": "minecraft:%s_log", "Properties": {"axis": "y"}}
                    }
                  }
                }
                """.formatted(placer, wood, base, randA, randB, wood)).getAsJsonObject();
    }

    private static JsonObject expectedPlaced(String tree) {
        return JsonParser.parseString("""
                {
                  "feature": "pamhc2trees:%s",
                  "placement": [
                    {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"},
                    {"type": "minecraft:block_predicate_filter", "predicate": {
                      "type": "minecraft:would_survive",
                      "state": {"Name": "pamhc2trees:%s_sapling", "Properties": {"stage": "0"}}
                    }},
                    {"type": "minecraft:rarity_filter", "chance": 300}
                  ]
                }
                """.formatted(configuredName(tree), tree)).getAsJsonObject();
    }

    private static JsonObject resource(String kind, String name) throws IOException {
        String path = "/data/pamhc2trees/worldgen/" + kind + "/" + name + ".json";
        var stream = TreeWorldgenParityTest.class.getResourceAsStream(path);
        assertNotNull(stream, path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
