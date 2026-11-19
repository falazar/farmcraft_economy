package com.falazar.farmupcraft.client;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public record ItemUseRecipe(String sourceCategory, boolean exactMatch, List<List<ItemStack>> inputSlots,
        List<List<ItemStack>> outputSlots) {
    public ItemUseRecipe {
        inputSlots = copySlots(inputSlots);
        outputSlots = copySlots(outputSlots);
    }

    private static List<List<ItemStack>> copySlots(List<List<ItemStack>> slots) {
        return slots.stream()
                .map(slot -> slot.stream().map(ItemStack::copy).toList())
                .toList();
    }
}
