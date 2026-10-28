package com.falazar.farmupcraft.mixin.client;

import com.falazar.farmupcraft.CookwareManager;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side mixin on ItemStack that makes cookware items (grinder, cutting
 * board, etc.)
 * display a vanilla-style durability bar based on our custom
 * farmupcraft_cookware_uses tag.
 */
@Mixin(ItemStack.class)
public abstract class CookwareItemStackMixin {

    @Inject(method = "isBarVisible()Z", at = @At("HEAD"), cancellable = true)
    private void farmupcraft$isBarVisible(CallbackInfoReturnable<Boolean> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (!CookwareManager.isCookware(self))
            return;
        int uses = CookwareManager.getRemainingUses(self);
        int max = CookwareManager.getMaxUsesForDisplay(self);
        if (uses < max) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getBarWidth()I", at = @At("HEAD"), cancellable = true)
    private void farmupcraft$getBarWidth(CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (!CookwareManager.isCookware(self))
            return;
        int uses = CookwareManager.getRemainingUses(self);
        int max = CookwareManager.getMaxUsesForDisplay(self);
        int width = Math.round(13.0f * uses / max);
        cir.setReturnValue(Math.max(0, Math.min(13, width)));
    }

    @Inject(method = "getBarColor()I", at = @At("HEAD"), cancellable = true)
    private void farmupcraft$getBarColor(CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (!CookwareManager.isCookware(self))
            return;
        int uses = CookwareManager.getRemainingUses(self);
        int max = CookwareManager.getMaxUsesForDisplay(self);
        float fraction = (float) uses / max;
        cir.setReturnValue(Mth.hsvToRgb(fraction / 3.0f, 1.0f, 1.0f));
    }
}
