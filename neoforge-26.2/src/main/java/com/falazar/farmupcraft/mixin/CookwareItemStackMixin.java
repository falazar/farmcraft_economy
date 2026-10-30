package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.CookwareManager;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class CookwareItemStackMixin implements ItemInstance {
    @Override
    public @Nullable ItemStackTemplate getCraftingRemainder() {
        ItemStack stack = (ItemStack) (Object) this;
        // Keep the ingredient intact until recipe lookup; only suppress its returned remainder.
        return CookwareManager.isExhausted(stack) ? null : stack.getItem().getCraftingRemainder(stack);
    }

    @Inject(method = "isBarVisible", at = @At("HEAD"), cancellable = true)
    private void farmupcraft$isBarVisible(CallbackInfoReturnable<Boolean> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        if (CookwareManager.isCookware(stack)) {
            callback.setReturnValue(CookwareManager.getRemainingUses(stack) < CookwareManager.getMaxUsesForDisplay(stack));
        }
    }

    @Inject(method = "getBarWidth", at = @At("HEAD"), cancellable = true)
    private void farmupcraft$getBarWidth(CallbackInfoReturnable<Integer> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        if (CookwareManager.isCookware(stack)) {
            callback.setReturnValue(Mth.clamp(Math.round(13.0f * CookwareManager.getRemainingUses(stack)
                    / CookwareManager.getMaxUsesForDisplay(stack)), 0, 13));
        }
    }

    @Inject(method = "getBarColor", at = @At("HEAD"), cancellable = true)
    private void farmupcraft$getBarColor(CallbackInfoReturnable<Integer> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        if (CookwareManager.isCookware(stack)) {
            float fraction = (float) CookwareManager.getRemainingUses(stack) / CookwareManager.getMaxUsesForDisplay(stack);
            callback.setReturnValue(Mth.hsvToRgb(fraction / 3.0f, 1.0f, 1.0f));
        }
    }
}
