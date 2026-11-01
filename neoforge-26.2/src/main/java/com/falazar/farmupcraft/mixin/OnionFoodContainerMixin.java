package com.falazar.farmupcraft.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "team.creative.solonion.common.item.foodcontainer.FoodContainer", remap = false)
public abstract class OnionFoodContainerMixin {
    @Shadow
    public ItemStack containerItem;

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/transfer/access/ItemAccess;forPlayerSlot(Lnet/minecraft/world/entity/player/Player;I)Lnet/neoforged/neoforge/transfer/access/ItemAccess;"))
    private ItemAccess farmupcraft$accessActualContainerSlot(Player player, int menuId) {
        // Onion 1.5.5 passes the menu ID, which is unrelated to the bag's inventory slot.
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot) == containerItem) {
                return ItemAccess.forPlayerSlot(player, slot);
            }
        }
        throw new IllegalStateException("Onion lunch container is not in the player's inventory");
    }
}
