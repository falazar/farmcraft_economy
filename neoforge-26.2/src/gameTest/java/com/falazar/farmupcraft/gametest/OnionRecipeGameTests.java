package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class OnionRecipeGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("onion_bag_recipe"), helper -> {
                    var level = helper.getLevel();
                    var manager = level.getServer().getRecipeManager();
                    var stacks = new ArrayList<ItemStack>(Collections.nCopies(9, ItemStack.EMPTY));
                    for (int slot : new int[]{1, 3, 5, 6, 7, 8}) stacks.set(slot, new ItemStack(Items.PAPER));
                    for (String name : List.of("lunchbag", "food_book")) {
                        var id = Identifier.parse("solonion:" + name);
                        var recipeId = Identifier.parse("farmupcraft:solonion_" + name);
                        var recipe = manager.byKey(ResourceKey.create(Registries.RECIPE, recipeId)).orElseThrow().value();
                        var input = name.equals("lunchbag") ? CraftingInput.of(3, 3, stacks)
                                : CraftingInput.of(2, 1, List.of(new ItemStack(Items.BOOK), new ItemStack(Items.POTATO)));
                        var selected = manager.getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
                        helper.assertValueEqual(selected.id().identifier(), recipeId, "Crafting manager selects compatibility recipe");
                        var result = selected.value().assemble(input);
                        helper.assertTrue(result.is(BuiltInRegistries.ITEM.getValue(id)) && result.getCount() == 1,
                                "Correct single Onion item crafted");
                        if (name.equals("lunchbag")) {
                            stacks.set(1, ItemStack.EMPTY);
                            helper.assertTrue(!((net.minecraft.world.item.crafting.ShapedRecipe) recipe)
                                    .matches(CraftingInput.of(3, 3, stacks), level), "Bag still requires all six paper");
                        }
                    }
                    helper.succeed();
                }));
    }
}
