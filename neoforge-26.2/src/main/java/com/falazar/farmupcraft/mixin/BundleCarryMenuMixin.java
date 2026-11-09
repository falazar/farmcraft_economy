package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.events.BundleCarryEvents;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerMenu.class)
public abstract class BundleCarryMenuMixin {
    @Shadow @Final public NonNullList<Slot> slots;
    @Shadow public abstract ItemStack getCarried();

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void farmupcraft$checkBundleClick(int index, int button, ContainerInput input, Player user,
                                            CallbackInfo callback) {
        if (!(user instanceof ServerPlayer player) || index < 0 || index >= slots.size()) return;
        Slot slot = slots.get(index);
        if (slot.container == player.getInventory()) return;
        if (input != ContainerInput.PICKUP && input != ContainerInput.QUICK_MOVE
                && input != ContainerInput.SWAP && input != ContainerInput.CLONE) return;
        ItemStack exchanged = input == ContainerInput.PICKUP ? getCarried()
                : input == ContainerInput.SWAP && button >= 0 && button < player.getInventory().getContainerSize()
                ? player.getInventory().getItem(button) : ItemStack.EMPTY;
        if (BundleCarryEvents.blocksAcquisition(player, slot.getItem(), exchanged)) {
            player.containerMenu.sendAllDataToRemote();
            callback.cancel();
        }
    }

    @Inject(method = "moveItemStackTo", at = @At("HEAD"), cancellable = true)
    private void farmupcraft$checkBundleQuickMove(ItemStack stack, int start, int end, boolean backwards,
                                                CallbackInfoReturnable<Boolean> callback) {
        for (int index = start; index < end; index++) {
            if (slots.get(index).container instanceof Inventory inventory
                    && inventory.player instanceof ServerPlayer player
                    && BundleCarryEvents.blocksAcquisition(player, stack, ItemStack.EMPTY)) {
                callback.setReturnValue(false);
                return;
            }
        }
    }
}
