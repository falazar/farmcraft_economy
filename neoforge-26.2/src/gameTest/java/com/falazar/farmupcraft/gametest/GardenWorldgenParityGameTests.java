package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class GardenWorldgenParityGameTests {
    private record Binding(String placed, String garden, String biomeTag) {
    }

    private static final List<Binding> BINDINGS = List.of(
            new Binding("arid_garden_placed", "arid", "c:is_dry/overworld"),
            new Binding("frost_garden_placed", "frost", "c:is_tree/coniferous"),
            new Binding("shaded_garden_placed", "shaded", "minecraft:is_forest"),
            new Binding("soggy_garden_placed", "soggy", "c:is_swamp"),
            new Binding("tropical_garden_placed_jungle", "tropical", "minecraft:is_jungle"),
            new Binding("tropical_garden_placed_ocean", "tropical", "minecraft:is_ocean"),
            new Binding("windy_garden_placed_plains", "windy", "c:is_plains"),
            new Binding("windy_garden_placed_savanna", "windy", "minecraft:is_savanna"));

    private GardenWorldgenParityGameTests() {
    }

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> {
            functions.register(FarmUpCraftNeoForge.prefix("garden_worldgen_parity_loaded_bindings"),
                    GardenWorldgenParityGameTests::loadedBindings);
            functions.register(FarmUpCraftNeoForge.prefix("garden_worldgen_parity_seeded_generation"),
                    GardenWorldgenParityGameTests::seededGeneration);
        });
    }

    private static void loadedBindings(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var configured = access.lookupOrThrow(Registries.CONFIGURED_FEATURE);
        var placed = access.lookupOrThrow(Registries.PLACED_FEATURE);
        var biomes = access.lookupOrThrow(Registries.BIOME);
        var ops = RegistryOps.create(JsonOps.INSTANCE, access);
        for (String garden : BINDINGS.stream().map(Binding::garden).distinct().toList()) {
            var loaded = configured.getOrThrow(ResourceKey.create(Registries.CONFIGURED_FEATURE,
                    id(garden + "_garden"))).value();
            var expected = ConfiguredFeature.DIRECT_CODEC.parse(ops,
                    resource("configured_feature/" + garden + "_garden.json")).getOrThrow();
            helper.assertTrue(ConfiguredFeature.DIRECT_CODEC.encodeStart(ops, expected).getOrThrow().equals(
                            ConfiguredFeature.DIRECT_CODEC.encodeStart(ops, loaded).getOrThrow()),
                    "Loaded configured feature differs from packaged override: " + garden);
        }
        for (var binding : BINDINGS) {
            var key = ResourceKey.create(Registries.PLACED_FEATURE, id(binding.placed()));
            var loaded = placed.getOrThrow(key).value();
            var expected = PlacedFeature.DIRECT_CODEC.parse(ops,
                    resource("placed_feature/" + binding.placed() + ".json")).getOrThrow();
            helper.assertTrue(PlacedFeature.DIRECT_CODEC.encodeStart(ops, expected).getOrThrow().equals(
                            PlacedFeature.DIRECT_CODEC.encodeStart(ops, loaded).getOrThrow()),
                    "Loaded placed feature differs from packaged override: " + binding.placed());
            helper.assertTrue(loaded.feature().is(ResourceKey.create(Registries.CONFIGURED_FEATURE,
                            id(binding.garden() + "_garden"))),
                    "Broken placed -> configured holder binding: " + binding.placed());
            var tag = TagKey.create(Registries.BIOME, Identifier.parse(binding.biomeTag()));
            helper.assertTrue(biomes.getOrThrow(tag).size() > 0, "Empty installed biome tag: " + tag);
            for (var biome : biomes.listElements().toList()) {
                var steps = biome.value().getGenerationSettings().features();
                int step = GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
                long occurrences = steps.size() > step
                        ? steps.get(step).stream().filter(feature -> feature.is(key)).count() : 0;
                helper.assertTrue(occurrences == (biome.is(tag) ? 1 : 0),
                        "Installed biome modifier is absent, duplicated or attached outside its tag: "
                                + binding.placed() + " in " + biome.key().identifier());
            }
        }
        helper.succeed();
    }

    private static void seededGeneration(GameTestHelper helper) {
        var level = helper.getLevel();
        var access = level.registryAccess();
        var features = access.lookupOrThrow(Registries.PLACED_FEATURE);
        var blocks = access.lookupOrThrow(Registries.BLOCK);
        var fixture = helper.absolutePos(BlockPos.ZERO);
        // Keep this deliberately wide patch arena above the small, unrelated empty-template tests.
        var origin = new BlockPos(fixture.getX(), 281, fixture.getZ());
        int[] outcomes = {0, 0};
        for (var binding : BINDINGS) {
            var feature = features.getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE,
                    id(binding.placed()))).value();
            var block = blocks.getOrThrow(ResourceKey.create(Registries.BLOCK,
                    id(binding.garden() + "garden"))).value();
            int accepted = 0;
            boolean rejected = false;
            for (long seed = 0; seed < 100_000 && (accepted < 3 || !rejected); seed++) {
                var random = RandomSource.create(seed);
                boolean passesRarity = random.nextFloat() < 1.0F / 50;
                if (passesRarity ? accepted >= 3 : rejected) {
                    continue;
                }
                var expected = originalPatch(random, origin, passesRarity);
                if (passesRarity && expected.isEmpty()) {
                    continue;
                }
                for (int x = -7; x <= 7; x++) {
                    for (int z = -7; z <= 7; z++) {
                        level.setBlock(origin.offset(x, -2, z), Blocks.STONE.defaultBlockState(), 2);
                        level.setBlock(origin.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                        for (int y = 0; y <= 3; y++) {
                            level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
                helper.assertTrue(block.defaultBlockState().canSurvive(level, origin),
                        "Grass fixture does not support real HC block " + binding.garden());
                // Supply the wrong Y deliberately: only the loaded MOTION_BLOCKING modifier can find the bed.
                boolean generated = feature.place(level, level.getChunkSource().getGenerator(),
                        RandomSource.create(seed), origin.below(20));
                var actual = new HashSet<BlockPos>();
                for (int x = -6; x <= 6; x++) {
                    for (int z = -6; z <= 6; z++) {
                        for (int y = -2; y <= 2; y++) {
                            var pos = origin.offset(x, y, z);
                            var state = level.getBlockState(pos);
                            if (state.is(block)) {
                                actual.add(pos);
                            } else if (y >= 0) {
                                helper.assertTrue(state.isAir(), "Wrong garden block generated at " + pos);
                            }
                        }
                    }
                }
                helper.assertTrue(generated == !expected.isEmpty() && actual.equals(expected),
                        "Original 32-try triangular spread/height/1-in-50 output mismatch for "
                                + binding.placed() + " seed=" + seed + " expected=" + expected + " actual=" + actual);
                if (passesRarity) {
                    accepted++;
                    outcomes[0]++;
                } else {
                    rejected = true;
                    outcomes[1]++;
                }
            }
            helper.assertTrue(accepted == 3 && rejected, "Missing accepted/rejected rarity witnesses");
        }
        helper.assertTrue(outcomes[0] == 24 && outcomes[1] == 8,
                "Every loaded placed feature must generate three seeded patches and reject one");
        helper.succeed();
    }

    private static Set<BlockPos> originalPatch(RandomSource random, BlockPos origin, boolean passesRarity) {
        var positions = new HashSet<BlockPos>();
        if (passesRarity) {
            for (int i = 0; i < 32; i++) {
                int x = random.nextInt(7) - random.nextInt(7);
                int y = random.nextInt(3) - random.nextInt(3);
                int z = random.nextInt(7) - random.nextInt(7);
                if (y == 0) {
                    positions.add(origin.offset(x, 0, z));
                }
            }
        }
        return positions;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("pamhc2crops", path);
    }

    private static JsonElement resource(String path) {
        try (var stream = GardenWorldgenParityGameTests.class.getResourceAsStream(
                "/data/pamhc2crops/worldgen/" + path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing packaged garden worldgen " + path);
            }
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
