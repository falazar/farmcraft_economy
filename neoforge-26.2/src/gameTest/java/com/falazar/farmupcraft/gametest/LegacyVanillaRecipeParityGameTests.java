package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class LegacyVanillaRecipeParityGameTests {
    private record Craft(String id, Item input, String output, int count, boolean shaped) {
    }

    private record Cut(String id, Item input, Item output, int count) {
    }

    private static final List<Craft> CRAFTS = List.of(
            new Craft("bone_meal", Items.BONE, "minecraft:bone_meal", 1, false),
            new Craft("deepslate_bricks", Items.POLISHED_DEEPSLATE, "minecraft:deepslate_bricks", 1, true),
            new Craft("deepslate_tiles", Items.DEEPSLATE_BRICKS, "minecraft:deepslate_tiles", 1, true),
            new Craft("polished_deepslate", Items.DEEPSLATE, "minecraft:polished_deepslate", 1, true),
            new Craft("stone_bricks", Items.STONE, "minecraft:stone_bricks", 1, true),
            new Craft("milk_multiple", Items.MILK_BUCKET, "pamhc2foodcore:freshmilkitem", 72, false),
            new Craft("water_multiple", Items.WATER_BUCKET, "pamhc2foodcore:freshwateritem", 72, false));

    private static final List<Cut> ENABLED_CUTS = List.of(
            new Cut("stonecutter_andesite_slab", Items.ANDESITE, Items.ANDESITE_SLAB, 1),
            new Cut("stonecutter_cobblestone_slab", Items.COBBLESTONE, Items.COBBLESTONE_SLAB, 1),
            new Cut("stonecutter_cobblestone_stairs", Items.COBBLESTONE, Items.COBBLESTONE_STAIRS, 1),
            new Cut("stonecutter_diorite_slab", Items.DIORITE, Items.DIORITE_SLAB, 1),
            new Cut("stonecutter_granite_slab", Items.GRANITE, Items.GRANITE_SLAB, 1),
            new Cut("stonecutter_polished_andesite", Items.ANDESITE, Items.POLISHED_ANDESITE, 1),
            new Cut("stonecutter_polished_diorite", Items.DIORITE, Items.POLISHED_DIORITE, 1),
            new Cut("stonecutter_polished_granite", Items.GRANITE, Items.POLISHED_GRANITE, 1),
            new Cut("stonecutter_stone_bricks_from_cobble", Items.COBBLESTONE, Items.COBBLESTONE_WALL, 1));

    private static final List<Cut> DISABLED_CUTS = List.of(
            new Cut("stonecutter_chiseled_deepslate", Items.POLISHED_DEEPSLATE, Items.CHISELED_DEEPSLATE, 1),
            new Cut("stonecutter_chiseled_stone_bricks", Items.STONE, Items.CHISELED_STONE_BRICKS, 1),
            new Cut("stonecutter_deepslate_bricks", Items.POLISHED_DEEPSLATE, Items.DEEPSLATE_BRICKS, 1),
            new Cut("stonecutter_deepslate_brick_slab", Items.DEEPSLATE_BRICKS, Items.DEEPSLATE_BRICK_SLAB, 2),
            new Cut("stonecutter_deepslate_brick_stairs", Items.DEEPSLATE_BRICKS, Items.DEEPSLATE_BRICK_STAIRS, 1),
            new Cut("stonecutter_deepslate_brick_wall", Items.DEEPSLATE_BRICKS, Items.DEEPSLATE_BRICK_WALL, 1),
            new Cut("stonecutter_deepslate_tiles", Items.DEEPSLATE_BRICKS, Items.DEEPSLATE_TILES, 1),
            new Cut("stonecutter_polished_deepslate", Items.DEEPSLATE, Items.POLISHED_DEEPSLATE, 1),
            new Cut("stonecutter_stone_bricks", Items.STONE, Items.STONE_BRICKS, 1),
            new Cut("stonecutter_stone_brick_slab", Items.STONE, Items.STONE_BRICK_SLAB, 2),
            new Cut("stonecutter_stone_brick_stairs", Items.STONE, Items.STONE_BRICK_STAIRS, 1),
            new Cut("stonecutter_stone_brick_wall", Items.STONE, Items.STONE_BRICK_WALL, 1));

    private LegacyVanillaRecipeParityGameTests() {
    }

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> {
            functions.register(FarmUpCraftNeoForge.prefix("skillet_recipe"), LegacyVanillaRecipeParityGameTests::skillet);
            functions.register(FarmUpCraftNeoForge.prefix("legacy_recipe_parity_crafting"),
                    LegacyVanillaRecipeParityGameTests::crafting);
            functions.register(FarmUpCraftNeoForge.prefix("legacy_recipe_parity_stonecutting"),
                    LegacyVanillaRecipeParityGameTests::stonecutting);
            functions.register(FarmUpCraftNeoForge.prefix("legacy_recipe_parity_disabled"),
                    LegacyVanillaRecipeParityGameTests::disabled);
        });
    }

    private static void skillet(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getServer().getRecipeManager();
        var id = Identifier.fromNamespaceAndPath("pamhc2foodcore", "tool_skillet");
        var recipe = manager.byKey(ResourceKey.create(Registries.RECIPE, id)).orElseThrow().value();
        helper.assertTrue(recipe instanceof ShapedRecipe, "Skillet recipe must be shaped");
        var stacks = new ArrayList<ItemStack>(Collections.nCopies(9, ItemStack.EMPTY));
        stacks.set(0, new ItemStack(Items.COPPER_INGOT));
        stacks.set(1, new ItemStack(Items.COPPER_INGOT));
        stacks.set(4, new ItemStack(Items.STICK));
        stacks.set(8, new ItemStack(Items.STICK));
        var input = CraftingInput.of(3, 3, stacks);
        var shaped = (ShapedRecipe) recipe;
        helper.assertTrue(shaped.matches(input, level), "Image layout must craft a skillet");
        var result = shaped.assemble(input);
        helper.assertTrue(result.is(BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath("pamhc2foodcore", "skilletitem"))) && result.getCount() == 1,
                "Recipe must produce exactly one skillet");
        var selected = manager.getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
        helper.assertValueEqual(selected.id().identifier(), id, "Crafting manager selects skillet");
        stacks.set(1, ItemStack.EMPTY);
        helper.assertTrue(!shaped.matches(CraftingInput.of(3, 3, stacks), level),
                "Old one-copper spear layout must not craft a skillet");
        helper.succeed();
    }

    private static void crafting(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getServer().getRecipeManager();
        var table = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(table, Blocks.CRAFTING_TABLE.defaultBlockState(), 2);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "recipe-parity"));
        player.setPos(table.getX() + 0.5, table.getY() + 1, table.getZ() + 0.5);
        for (var expected : CRAFTS) {
            var holder = manager.byKey(key(expected.id())).orElseThrow();
            var sourcePack = level.getServer().getResourceManager().getResource(
                    Identifier.withDefaultNamespace("recipe/" + expected.id() + ".json")).orElseThrow().sourcePackId();
            helper.assertTrue(holder.value() instanceof CraftingRecipe, expected.id() + " crafting type");
            var recipe = (CraftingRecipe) holder.value();
            int ingredientCount = expected.shaped() ? 4 : expected.count() == 72 ? 9 : 1;
            if (expected.shaped()) {
                helper.assertTrue(recipe instanceof ShapedRecipe, expected.id() + " shaped serializer");
                var shaped = (ShapedRecipe) recipe;
                helper.assertValueEqual(shaped.getWidth(), 2, expected.id() + " pattern width");
                helper.assertValueEqual(shaped.getHeight(), 2, expected.id() + " pattern height");
                helper.assertValueEqual(shaped.getIngredients().size(), 4, expected.id() + " full square");
                for (var ingredient : shaped.getIngredients()) {
                    assertIngredient(helper, ingredient.orElseThrow(), expected.input(), expected.id() + " from " + sourcePack);
                }
            } else {
                helper.assertTrue(recipe instanceof ShapelessRecipe, expected.id() + " shapeless serializer");
            }
            var ingredients = recipe.placementInfo().ingredients();
            helper.assertValueEqual(ingredients.size(), ingredientCount, expected.id() + " ingredient multiplicity");
            for (var ingredient : ingredients) {
                assertIngredient(helper, ingredient, expected.input(), expected.id() + " from " + sourcePack);
            }
            int width = expected.shaped() ? 2 : ingredientCount == 9 ? 3 : 1;
            var stacks = new ArrayList<ItemStack>();
            for (int i = 0; i < ingredientCount; i++) {
                stacks.add(new ItemStack(expected.input()));
            }
            var input = CraftingInput.of(width, width, stacks);
            helper.assertTrue(recipe.matches(input, level), expected.id() + " matches real level");
            assertOutput(helper, recipe.assemble(input), expected.output(), expected.count(), expected.id());
            var selected = manager.getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
            helper.assertValueEqual(selected.id(), holder.id(), expected.id() + " actually selected by manager");
            var missing = new ArrayList<>(stacks);
            missing.set(0, ItemStack.EMPTY);
            helper.assertTrue(!recipe.matches(CraftingInput.of(width, width, missing), level),
                    expected.id() + " rejects missing ingredient");
            helper.assertTrue(!recipe.matches(CraftingInput.of(width, width,
                    Collections.nCopies(ingredientCount, new ItemStack(Items.DIRT))), level),
                    expected.id() + " rejects wrong ingredient");
            if (expected.id().equals("polished_deepslate")) {
                helper.assertTrue(!recipe.matches(CraftingInput.of(2, 2,
                        Collections.nCopies(4, new ItemStack(Items.COBBLED_DEEPSLATE))), level),
                        "Legacy polished deepslate uses deepslate, not modern vanilla cobbled deepslate");
            }

            var menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(level, table));
            for (int y = 0; y < width; y++) {
                for (int x = 0; x < width; x++) {
                    menu.getSlot(1 + x + y * 3).set(new ItemStack(expected.input()));
                }
            }
            var result = menu.getSlot(0);
            assertOutput(helper, result.getItem(), expected.output(), expected.count(), expected.id() + " menu");
            var taken = result.remove(expected.count());
            result.onTake(player, taken);
            assertOutput(helper, taken, expected.output(), expected.count(), expected.id() + " taken");
            for (int slot = 1; slot <= 9; slot++) {
                var remainder = menu.getSlot(slot).getItem();
                if (ingredientCount == 9) {
                    helper.assertTrue(remainder.is(Items.BUCKET) && remainder.getCount() == 1,
                            expected.id() + " returns all nine empty buckets");
                } else {
                    helper.assertTrue(remainder.isEmpty(), expected.id() + " consumes exactly one full recipe");
                }
            }
            helper.assertTrue(result.getItem().isEmpty(), expected.id() + " cannot craft a second output");
        }
        helper.succeed();
    }

    private static void stonecutting(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getServer().getRecipeManager();
        for (var expected : ENABLED_CUTS) {
            var holder = manager.byKey(key(expected.id())).orElseThrow();
            helper.assertTrue(holder.value() instanceof StonecutterRecipe, expected.id() + " stonecutting type");
            assertCut(helper, (StonecutterRecipe) holder.value(), expected);
            var input = new SingleRecipeInput(new ItemStack(expected.input()));
            var selected = manager.getRecipeFor(RecipeType.STONECUTTING, input, level, key(expected.id())).orElseThrow();
            helper.assertValueEqual(selected.id(), holder.id(), expected.id() + " selectable by manager");
            assertOutput(helper, selected.value().assemble(input),
                    BuiltInRegistries.ITEM.getKey(expected.output()).toString(), expected.count(), expected.id());
        }
        helper.succeed();
    }

    private static void disabled(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        for (var expected : DISABLED_CUTS) {
            helper.assertTrue(server.getRecipeManager().byKey(key(expected.id())).isEmpty(),
                    expected.id() + " must remain disabled like legacy forge:false");
            var resource = server.getResourceManager().getResource(
                    Identifier.withDefaultNamespace("recipe/" + expected.id() + ".json")).orElseThrow();
            try (var reader = resource.openAsReader()) {
                // Decode the dormant recipe without conditional ops to verify its actual recipe semantics too.
                var recipe = Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
                        JsonParser.parseReader(reader)).getOrThrow();
                helper.assertTrue(recipe instanceof StonecutterRecipe, expected.id() + " dormant serializer");
                assertCut(helper, (StonecutterRecipe) recipe, expected);
            } catch (IOException error) {
                throw new IllegalStateException("Cannot read disabled recipe " + expected.id(), error);
            }
        }
        helper.succeed();
    }

    private static void assertCut(GameTestHelper helper, StonecutterRecipe recipe, Cut expected) {
        assertIngredient(helper, recipe.input(), expected.input(), expected.id());
        var input = new SingleRecipeInput(new ItemStack(expected.input()));
        helper.assertTrue(recipe.matches(input, helper.getLevel()), expected.id() + " single input match");
        helper.assertTrue(!recipe.matches(new SingleRecipeInput(new ItemStack(Items.DIRT)), helper.getLevel()),
                expected.id() + " rejects wrong stone");
        helper.assertTrue(!recipe.matches(new SingleRecipeInput(ItemStack.EMPTY), helper.getLevel()),
                expected.id() + " rejects empty input");
        assertOutput(helper, recipe.assemble(input), BuiltInRegistries.ITEM.getKey(expected.output()).toString(),
                expected.count(), expected.id());
    }

    private static void assertIngredient(GameTestHelper helper, Ingredient ingredient, Item expected, String label) {
        var accepted = ingredient.items().toList();
        helper.assertValueEqual(accepted.size(), 1, label + " exact ingredient, not expanded tag");
        helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(accepted.getFirst().value()),
                BuiltInRegistries.ITEM.getKey(expected), label + " exact input item");
    }

    private static void assertOutput(GameTestHelper helper, ItemStack stack, String item, int count, String label) {
        helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), item, label + " output item");
        helper.assertValueEqual(stack.getCount(), count, label + " output count");
        helper.assertTrue(ItemStack.validateStrict(stack).isSuccess(), label + " valid output components/stack size");
        if (count == 72) {
            helper.assertValueEqual(stack.getMaxStackSize(), 72, label + " valid legacy bulk output above 64");
        }
    }

    private static ResourceKey<Recipe<?>> key(String path) {
        return ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace(path));
    }
}
