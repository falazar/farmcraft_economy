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
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class TreeWorldgenParityGameTests {
    private static final List<String> TREES = Arrays.asList("""
            acorn almond apple apricot avocado banana breadfruit candlenut cashew cherry chestnut
            cinnamon coconut date dragonfruit durian fig guava gooseberry grapefruit hazelnut jackfruit
            lemon lime lychee mango maple nutmeg olive orange papaya paperbark passionfruit pawpaw peach
            pear pecan peppercorn persimmon pinenut pistachio plum pomegranate rambutan soursop spiderweb
            starfruit tamarind vanillabean walnut
            """.strip().split("\\s+"));
    private static final Set<String> BARK_FRUIT = Set.of("cinnamon", "maple", "paperbark");
    private static final long ADMITTED_SEED = 5152;
    private static final long REJECTED_SEED = 0;

    private TreeWorldgenParityGameTests() {
    }

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> {
            functions.register(FarmUpCraftNeoForge.prefix("tree_worldgen_parity_loaded_bindings"),
                    TreeWorldgenParityGameTests::loadedBindings);
            functions.register(FarmUpCraftNeoForge.prefix("tree_worldgen_parity_generation"),
                    TreeWorldgenParityGameTests::generation);
        });
    }

    private static String configuredName(String tree) {
        // The installed mod and the old pack both intentionally bind guava_placed to gauva.
        return tree.equals("guava") ? "gauva" : tree;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("pamhc2trees", path);
    }

    private static ConfiguredFeature<?, ?> configured(GameTestHelper helper, String tree) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE)
                .getOrThrow(ResourceKey.create(Registries.CONFIGURED_FEATURE, id(configuredName(tree)))).value();
    }

    private static PlacedFeature placed(GameTestHelper helper, String tree) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE)
                .getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, id(tree + "_placed"))).value();
    }

    private static Block block(GameTestHelper helper, String path) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.BLOCK)
                .getOrThrow(ResourceKey.create(Registries.BLOCK, id(path))).value();
    }

    private static void loadedBindings(GameTestHelper helper) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        helper.assertTrue(TREES.size() == 50 && Set.copyOf(TREES).size() == 50, "Exact fifty-tree inventory");
        for (String tree : TREES) {
            var configured = configured(helper, tree);
            var placed = placed(helper, tree);
            var expectedConfigured = ConfiguredFeature.DIRECT_CODEC.parse(ops,
                    resource("configured_feature", configuredName(tree))).getOrThrow();
            var expectedPlaced = PlacedFeature.DIRECT_CODEC.parse(ops,
                    resource("placed_feature", tree + "_placed")).getOrThrow();
            helper.assertTrue(configured.feature() == Feature.TREE
                            && configured.config() instanceof TreeConfiguration,
                    tree + ": real registered tree feature/configuration");
            helper.assertTrue(ConfiguredFeature.DIRECT_CODEC.encodeStart(ops, configured).getOrThrow().equals(
                            ConfiguredFeature.DIRECT_CODEC.encodeStart(ops, expectedConfigured).getOrThrow()),
                    tree + ": loaded configured feature must equal the full bundled override, not installed defaults");
            helper.assertTrue(PlacedFeature.DIRECT_CODEC.encodeStart(ops, placed).getOrThrow().equals(
                            PlacedFeature.DIRECT_CODEC.encodeStart(ops, expectedPlaced).getOrThrow()),
                    tree + ": loaded placement must retain all three legacy modifiers and their ordering");
            helper.assertTrue(placed.feature().is(ResourceKey.create(
                            Registries.CONFIGURED_FEATURE, id(configuredName(tree))))
                            && placed.feature().value() == configured,
                    tree + ": placed holder is bound to the exact loaded configured registry entry");
            helper.assertTrue(placed.placement().size() == 3, tree + ": no count/in-square/biome modifiers added");
            var config = (TreeConfiguration) configured.config();
            var foliage = net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer.CODEC
                    .encodeStart(ops, config.foliagePlacer).getOrThrow().getAsJsonObject();
            String foliageName = tree.equals("pinenut") ? "pine" : tree;
            helper.assertTrue(foliage.get("type").getAsString().equals("pamhc2trees:" + foliageName + "_fruit_placer"),
                    tree + ": installed HarvestCraft fruit codec, including pine/guava aliases");
            block(helper, tree + "_sapling");
            block(helper, "pam" + tree);
            assertRarity(helper, tree, placed);
        }
        helper.succeed();
    }

    private static void assertRarity(GameTestHelper helper, String tree, PlacedFeature placed) {
        var origin = helper.absolutePos(BlockPos.ZERO);
        var context = new PlacementContext(helper.getLevel(), helper.getLevel().getChunkSource().getGenerator(),
                Optional.of(placed));
        boolean accepted = false;
        boolean rejected = false;
        for (int i = 0; i < 1024; i++) {
            long seed = i == 0 ? ADMITTED_SEED : i * 0x9E3779B97F4A7C15L;
            boolean expected = RandomSource.create(seed).nextFloat() < 1.0F / 300;
            var positions = placed.placement().get(2).getPositions(context, RandomSource.create(seed), origin).toList();
            helper.assertTrue(positions.equals(expected ? List.of(origin) : List.of()),
                    tree + ": exact seeded 1/300 rarity decision, seed=" + seed);
            accepted |= expected;
            rejected |= !expected;
        }
        helper.assertTrue(accepted && rejected, tree + ": rarity must have both positive and negative witnesses");
    }

    private static void generation(GameTestHelper helper) {
        var level = helper.getLevel();
        var generator = level.getChunkSource().getGenerator();
        // Work only in this throwaway GameTest level, above other empty test structures, restoring every state.
        var origin = helper.absolutePos(new BlockPos(0, 32, 0));
        var saved = new LinkedHashMap<BlockPos, BlockState>();
        BlockPos.betweenClosedStream(origin.offset(-6, -2, -6), origin.offset(6, 20, 6))
                .forEach(pos -> saved.put(pos.immutable(), level.getBlockState(pos)));
        try {
            for (String tree : TREES) {
                var configured = configured(helper, tree);
                var placed = placed(helper, tree);
                var config = (TreeConfiguration) configured.config();
                reset(helper, saved, origin, Blocks.GRASS_BLOCK);
                assertLegacyGroundProvider(helper, config, origin.below(), tree);
                reset(helper, saved, origin, Blocks.GRASS_BLOCK);
                helper.assertTrue(configured.place(level, generator, RandomSource.create(0), origin),
                        tree + ": real configured generation succeeds on clear grass");
                assertTree(helper, tree, config, origin, RandomSource.create(0));

                reset(helper, saved, origin, Blocks.GRASS_BLOCK);
                helper.assertTrue(!placed.place(level, generator, RandomSource.create(REJECTED_SEED), origin.above(7))
                                && level.getBlockState(origin).isAir(),
                        tree + ": complete placed pipeline rejects an exact rarity miss without generating");

                reset(helper, saved, origin, Blocks.GRASS_BLOCK);
                var context = new PlacementContext(level, generator, Optional.of(placed));
                helper.assertTrue(placed.placement().get(0)
                                .getPositions(context, RandomSource.create(0), origin.above(7)).toList()
                                .equals(List.of(origin)),
                        tree + ": MOTION_BLOCKING heightmap projects to the prepared surface");
                helper.assertTrue(placed.placement().get(1)
                                .getPositions(context, RandomSource.create(0), origin).count() == 1,
                        tree + ": exact loaded sapling survival predicate accepts grass");
                helper.assertTrue(placed.place(level, generator, RandomSource.create(ADMITTED_SEED), origin.above(7)),
                        tree + ": complete loaded placed feature generates through the real 1/300 filter");
                var placedRandom = RandomSource.create(ADMITTED_SEED);
                placedRandom.nextFloat();
                assertTree(helper, tree, config, origin, placedRandom);

                reset(helper, saved, origin, Blocks.STONE);
                helper.assertTrue(placed.placement().get(1)
                                .getPositions(context, RandomSource.create(0), origin).findAny().isEmpty(),
                        tree + ": exact loaded sapling survival predicate rejects stone");
                helper.assertTrue(!placed.place(level, generator, RandomSource.create(ADMITTED_SEED), origin)
                                && level.getBlockState(origin).isAir(),
                        tree + ": even a rarity hit cannot generate on invalid substrate");

                reset(helper, saved, origin, Blocks.GRASS_BLOCK);
                for (int y = 0; y <= 16; y++) {
                    level.setBlock(origin.above(y), Blocks.BEDROCK.defaultBlockState(), 2);
                }
                helper.assertTrue(!configured.place(level, generator, RandomSource.create(0), origin),
                        tree + ": configured tree generation rejects an obstructed trunk");
                helper.assertTrue(level.getBlockState(origin).is(Blocks.BEDROCK),
                        tree + ": failed generation cannot replace the obstruction");
            }
        } finally {
            saved.forEach((pos, state) -> level.setBlock(pos, state, 2));
        }
        helper.succeed();
    }

    private static void reset(GameTestHelper helper, Map<BlockPos, BlockState> saved, BlockPos origin, Block soil) {
        saved.keySet().forEach(pos -> helper.getLevel().setBlock(pos,
                pos.getY() == origin.getY() - 1 ? soil.defaultBlockState()
                        : pos.getY() < origin.getY() - 1 ? Blocks.DIRT.defaultBlockState()
                        : Blocks.AIR.defaultBlockState(), 2));
    }

    private static void assertLegacyGroundProvider(
            GameTestHelper helper, TreeConfiguration config, BlockPos pos, String tree) {
        for (Block ground : List.of(Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL,
                Blocks.GRASS_BLOCK, Blocks.MYCELIUM, Blocks.FARMLAND, Blocks.DIRT_PATH,
                Blocks.MUD, Blocks.MOSS_BLOCK, Blocks.STONE)) {
            var state = ground.defaultBlockState();
            helper.getLevel().setBlock(pos, state, 2);
            boolean replace = !state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM);
            var supplied = config.belowTrunkProvider.getOptionalState(helper.getLevel(), RandomSource.create(0), pos);
            helper.assertTrue(replace ? supplied != null && supplied.is(Blocks.DIRT) : supplied == null,
                    tree + ": exact legacy force_dirt=false behavior for " + state);
            helper.assertTrue(config.belowTrunkProvider.getState(helper.getLevel(), RandomSource.create(0), pos)
                            .equals(replace ? Blocks.DIRT.defaultBlockState() : state),
                    tree + ": no fallback may overwrite already accepted dirt states");
        }
    }

    private static void assertTree(
            GameTestHelper helper, String tree, TreeConfiguration config, BlockPos origin, RandomSource random) {
        int base = tree.equals("pinenut") ? 7 : 4;
        int randA = tree.equals("pinenut") ? 2 : 1;
        int randB = tree.equals("pinenut") ? 3 : BARK_FRUIT.contains(tree) ? 1 : 2;
        int height = base + random.nextInt(randA + 1) + random.nextInt(randB + 1);
        var fruit = block(helper, "pam" + tree);
        var trunk = config.trunkProvider.getState(helper.getLevel(), RandomSource.create(0), origin).getBlock();
        var leaves = config.foliageProvider.getState(helper.getLevel(), RandomSource.create(0), origin).getBlock();
        for (int y = 0; y < height; y++) {
            var state = helper.getLevel().getBlockState(origin.above(y));
            helper.assertTrue(state.is(trunk) || BARK_FRUIT.contains(tree) && state.is(fruit),
                    tree + ": generated straight trunk has its exact seeded legacy height at y=" + y);
        }
        int fruitCount = 0;
        int leafCount = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-6, 0, -6), origin.offset(6, 20, 6))) {
            var state = helper.getLevel().getBlockState(pos);
            if (state.is(fruit)) {
                fruitCount++;
            }
            if (state.is(leaves)) {
                leafCount++;
            }
        }
        helper.assertTrue(fruitCount > 0, tree + ": registered HarvestCraft placer must generate its own real fruit");
        helper.assertTrue(leafCount > 0, tree + ": generation must include the exact legacy oak/jungle/spruce foliage");
    }

    private static JsonElement resource(String kind, String name) {
        String path = "data/pamhc2trees/worldgen/" + kind + "/" + name + ".json";
        try (var stream = ModList.get().getModFileById(FarmUpCraftNeoForge.MOD_ID).getFile()
                .getContents().openFile(path);
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
