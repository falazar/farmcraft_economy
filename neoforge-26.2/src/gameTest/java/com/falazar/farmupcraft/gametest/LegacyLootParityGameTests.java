package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class LegacyLootParityGameTests {
    private static final Map<String, String> CROPS = Map.of(
            "aridgarden", "agave cactusfruit cassava chickpea lentil sisal wolfberry barrelcactus chia nopales yucca papyrus",
            "frostgarden", "broccoli cabbage cauliflower celery cotton huckleberry juniperberry kohlrabi oats peas quinoa raspberry rutabaga rye spinach cloudberry",
            "shadedgarden", "bean blackberry garlic jute leek radish rhubarb scallion spiceleaf sweetpotato tealeaf tomato turnip whitemushroom zucchini truffle nettles",
            "soggygarden", "asparagus blueberry brusselsprout candleberry cotton cranberry greengrape jicama kale millet mulberry okra rice spiceleaf tomatillo waterchestnut lotus cattail",
            "tropicalgarden", "arrowroot cantaloupe coffeebean eggplant grape kenaf kiwi pineapple sesameseeds soybean taro ginger sorghum aloe calabash bokchoy guarana",
            "windygarden", "amaranth artichoke barley bellpepper chilipepper corn cucumber elderberry flax lettuce mustardseeds onion parsnip peanut strawberry wintersquash alfalfa canola sunchoke");

    private LegacyLootParityGameTests() {
    }

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> {
            functions.register(FarmUpCraftNeoForge.prefix("loot_parity_wheat_fortune"),
                    LegacyLootParityGameTests::wheat);
            CROPS.forEach((name, crops) -> functions.register(
                    FarmUpCraftNeoForge.prefix("loot_parity_" + name), helper -> garden(helper, name, crops)));
        });
    }

    private static void wheat(GameTestHelper helper) {
        var table = loadedTable(helper, "minecraft", "wheat", Blocks.WHEAT.defaultBlockState());
        for (int fortune : new int[]{0, 1, 3}) {
            var tool = tool(helper, fortune);
            for (int age = 0; age <= 7; age++) {
                var state = Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, age);
                for (Float radius : new Float[]{null, 1.0F, 2.0F}) {
                    for (long seed = 1; seed <= 64; seed++) {
                        var random = RandomSource.create(seed);
                        var expected = new HashMap<Item, Integer>();
                        if (age == 7) {
                            add(expected, Items.WHEAT, decay(random, binomial(random, fortune), radius));
                            add(expected, Items.WHEAT_SEEDS, decay(random, binomial(random, fortune), radius));
                        } else {
                            add(expected, Items.WHEAT_SEEDS, decay(random, 1, radius));
                        }
                        assertLoot(helper, table, state, tool, radius, seed, expected,
                                "wheat age=" + age + " Fortune=" + fortune + " radius=" + radius);
                    }
                }
            }
            // A different block with no age property must fail both wheat maturity predicates.
            assertLoot(helper, table, Blocks.STONE.defaultBlockState(), tool, null, 17,
                    Map.of(Items.WHEAT_SEEDS, 1), "non-wheat state");
            wheatBoundaries(helper, table, fortune, tool);
        }
        assertLoot(helper, table, Blocks.WHEAT.defaultBlockState(), ItemStack.EMPTY, null, 19,
                Map.of(Items.WHEAT_SEEDS, 1), "immature empty-hand wheat");
        var emptyHandRandom = RandomSource.create(19);
        assertLoot(helper, table, Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7),
                ItemStack.EMPTY, null, 19,
                Map.of(Items.WHEAT, binomial(emptyHandRandom, 0),
                        Items.WHEAT_SEEDS, binomial(emptyHandRandom, 0)),
                "mature empty-hand wheat still has three base binomial trials per pool");
        helper.succeed();
    }

    private static void wheatBoundaries(GameTestHelper helper, LootTable table, int fortune, ItemStack tool) {
        var mature = Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7);
        for (boolean grain : new boolean[]{true, false}) {
            for (int boundary : new int[]{1, fortune + 4}) {
                boolean found = false;
                for (long seed = 1; seed <= 100_000; seed++) {
                    var random = RandomSource.create(seed);
                    int wheat = binomial(random, fortune);
                    int seeds = binomial(random, fortune);
                    if ((grain ? wheat : seeds) == boundary) {
                        assertLoot(helper, table, mature, tool, null, seed,
                                Map.of(Items.WHEAT, wheat, Items.WHEAT_SEEDS, seeds),
                                "mature " + (grain ? "wheat" : "seed") + " boundary=" + boundary
                                        + " Fortune=" + fortune);
                        found = true;
                        break;
                    }
                }
                helper.assertTrue(found, "Missing deterministic wheat yield-boundary witness");
            }
        }
    }

    private static void garden(GameTestHelper helper, String name, String cropNames) {
        var blocks = helper.getLevel().registryAccess().lookupOrThrow(Registries.BLOCK);
        var block = blocks.getOrThrow(ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath("pamhc2crops", name))).value();
        var state = block.defaultBlockState();
        var table = loadedTable(helper, "pamhc2crops", name, state);
        String[] crops = cropNames.split(" ");
        Item[] items = new Item[crops.length + 1];
        items[0] = Items.WHEAT_SEEDS;
        var registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ITEM);
        for (int i = 0; i < crops.length; i++) {
            // getOrThrow deliberately rejects missing current items, never silently substitutes air.
            items[i + 1] = registry.getOrThrow(ResourceKey.create(Registries.ITEM,
                    Identifier.fromNamespaceAndPath("pamhc2crops", crops[i] + "item"))).value();
        }
        int totalWeight = 100 + crops.length;
        for (int entry = 0; entry < items.length; entry++) {
            for (int fortune : new int[]{0, 1, 3}) {
                // Find exact seeded witnesses for every item and both Fortune yield boundaries.
                for (int bonus : new int[]{0, 2 * fortune}) {
                    long seed = witnessSeed(totalWeight, entry, fortune, bonus);
                    for (Float radius : new Float[]{null, 1.0F, 2.0F}) {
                        var random = RandomSource.create(seed);
                        int selection = random.nextInt(totalWeight);
                        int selected = selection < 100 ? 0 : selection - 99;
                        int count = 1 + random.nextInt(2 * fortune + 1);
                        helper.assertValueEqual(selected, entry, "deterministic selected garden entry");
                        helper.assertValueEqual(count, 1 + bonus, "deterministic garden count boundary");
                        count = decay(random, count, radius);
                        var expected = new HashMap<Item, Integer>();
                        add(expected, items[entry], count);
                        assertLoot(helper, table, state, tool(helper, fortune), radius, seed, expected,
                                name + " entry=" + entry + " Fortune=" + fortune + " radius=" + radius);
                    }
                    assertLoot(helper, table, state, ItemStack.EMPTY, null, seed,
                            Map.of(items[entry], 1), name + " empty-hand single roll");
                    if (fortune == 0) {
                        var silk = new ItemStack(Items.DIAMOND_HOE);
                        silk.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                                .getOrThrow(Enchantments.SILK_TOUCH), 1);
                        assertLoot(helper, table, state, silk, null, seed, Map.of(items[entry], 1),
                                name + " Silk Touch must not replace the legacy crop/seed pool");
                    }
                }
            }
        }
        helper.succeed();
    }

    private static long witnessSeed(int totalWeight, int entry, int fortune, int bonus) {
        for (long seed = 1; seed <= 100_000; seed++) {
            var random = RandomSource.create(seed);
            int selection = random.nextInt(totalWeight);
            int selected = selection < 100 ? 0 : selection - 99;
            if (selected == entry && random.nextInt(2 * fortune + 1) == bonus) {
                return seed;
            }
        }
        throw new AssertionError("No deterministic garden witness: weight=" + totalWeight
                + " entry=" + entry + " Fortune=" + fortune + " bonus=" + bonus);
    }

    private static LootTable loadedTable(GameTestHelper helper, String namespace, String name, BlockState state) {
        var key = ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(namespace, "blocks/" + name));
        helper.assertValueEqual(state.getBlock().getLootTable().orElseThrow(), key, "block loot table binding");
        var table = helper.getLevel().getServer().reloadableRegistries().getLootTable(key);
        helper.assertTrue(table != LootTable.EMPTY, "Missing or rejected loaded loot table " + key);
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        String path = "/data/" + namespace + "/loot_table/blocks/" + name + ".json";
        try (var stream = LegacyLootParityGameTests.class.getResourceAsStream(path)) {
            helper.assertTrue(stream != null, "Missing packaged loot override " + path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                var expected = LootTable.DIRECT_CODEC.parse(ops, JsonParser.parseReader(reader)).getOrThrow();
                helper.assertValueEqual(LootTable.DIRECT_CODEC.encodeStart(ops, table).getOrThrow(),
                        LootTable.DIRECT_CODEC.encodeStart(ops, expected).getOrThrow(),
                        "actual reloaded table must match the complete packaged override");
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return table;
    }

    private static ItemStack tool(GameTestHelper helper, int fortune) {
        var tool = new ItemStack(Items.DIAMOND_HOE);
        if (fortune > 0) {
            tool.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.FORTUNE), fortune);
        }
        return tool;
    }

    private static int binomial(RandomSource random, int fortune) {
        int count = 1;
        for (int i = 0; i < fortune + 3; i++) {
            if (random.nextFloat() < 0.5714286F) {
                count++;
            }
        }
        return count;
    }

    private static int decay(RandomSource random, int count, Float radius) {
        if (radius == null) {
            return count;
        }
        int survived = 0;
        for (int i = 0; i < count; i++) {
            if (random.nextFloat() <= 1.0F / radius) {
                survived++;
            }
        }
        return survived;
    }

    private static void add(Map<Item, Integer> counts, Item item, int count) {
        if (count > 0) {
            counts.merge(item, count, Integer::sum);
        }
    }

    private static void assertLoot(GameTestHelper helper, LootTable table, BlockState state, ItemStack tool,
                                   Float radius, long seed, Map<Item, Integer> expected, String description) {
        var params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(
                        new BlockPos(1, 1, 1))))
                .withParameter(LootContextParams.TOOL, tool)
                .withOptionalParameter(LootContextParams.EXPLOSION_RADIUS, radius)
                .create(LootContextParamSets.BLOCK);
        var actual = new HashMap<Item, Integer>();
        var evaluated = table.getRandomItems(params, RandomSource.create(seed));
        int expectedEmissions = state.is(Blocks.WHEAT)
                && state.getValue(BlockStateProperties.AGE_7) == 7 ? 2 : 1;
        helper.assertValueEqual(evaluated.size(), expectedEmissions,
                description + " must evaluate one entry per eligible pool");
        int emptyEmissions = 0;
        for (var stack : evaluated) {
            // Even non-raw getRandomItems retains zero-count explosion results in 26.2.
            // Block.popResource, not the loot evaluator, excludes these from world drops.
            if (stack.isEmpty()) {
                helper.assertTrue(radius != null && radius > 1.0F,
                        description + " must not emit empty stacks without destructive explosion decay");
                helper.assertValueEqual(stack.getCount(), 0, "explosion-depleted entry count");
                emptyEmissions++;
            } else {
                helper.assertTrue(stack.getCount() > 0 && stack.getCount() <= stack.getMaxStackSize(),
                        description + " must emit valid positive stacks for surviving loot");
                add(actual, stack.getItem(), stack.getCount());
            }
        }
        helper.assertValueEqual(emptyEmissions, expectedEmissions - expected.size(),
                description + " exact number of independently explosion-depleted pools seed=" + seed);
        helper.assertValueEqual(actual, expected, description + " seed=" + seed);
    }
}
