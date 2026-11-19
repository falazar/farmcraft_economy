package com.falazar.farmupcraft.mixin;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "de.maxhenkel.gravestone.blocks.GraveStoneBlock", remap = false)
public abstract class GravestoneOverflowMixin {
    @Redirect(method = "fillPlayerInventory", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean farmupcraft$dropWhatDidNotFit(Inventory inventory, ItemStack stack) {
        // Gravestone drops a stack only when add() returns false. Vanilla returns true for a partial insert,
        // and in creative it discards a stack that has no room, so both cases lost items.
        if (stack.isEmpty()) {
            return true;
        }
        if (!farmupcraft$hasRoom(inventory, stack)) {
            return false;
        }
        return inventory.add(stack) && stack.isEmpty();
    }

    private static boolean farmupcraft$hasRoom(Inventory inventory, ItemStack stack) {
        for (ItemStack slot : inventory.getNonEquipmentItems()) {
            if (slot.isEmpty()
                    || (ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize())) {
                return true;
            }
        }
        return false;
    }
}
