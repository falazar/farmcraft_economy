package com.falazar.farmupcraft.client;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.util.List;

public record ItemUseRecipe(String sourceCategory, boolean exactMatch, List<List<ItemStack>> inputSlots,
                            List<List<ItemStack>> outputSlots) {
    public ItemUseRecipe {
        inputSlots = copySlots(inputSlots);
        outputSlots = copySlots(outputSlots);
    }

    public static boolean hasExactInput(Recipe<?> recipe, ItemStack target) {
        return recipe.getIngredients().stream().anyMatch(ingredient -> !containsTag(ingredient.toJson())
                && ingredient.getItems().length > 0
                && java.util.Arrays.stream(ingredient.getItems()).allMatch(stack -> stack.is(target.getItem())));
    }

    private static boolean containsTag(com.google.gson.JsonElement ingredient) {
        if (ingredient.isJsonObject()) return ingredient.getAsJsonObject().has("tag");
        if (ingredient.isJsonArray()) {
            for (var entry : ingredient.getAsJsonArray()) if (containsTag(entry)) return true;
        }
        return false;
    }

    private static List<List<ItemStack>> copySlots(List<List<ItemStack>> slots) {
        return slots.stream().map(slot -> slot.stream().map(ItemStack::copy).toList()).toList();
    }
}
