package com.falazar.farmupcraft.mixin;

import net.minecraft.world.food.FoodProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "team.creative.solonion.common.SOLOnionConfig", remap = false)
public abstract class OnionFoodScoringMixin {
    @Redirect(method = "getDiversity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/food/FoodProperties;saturation()F"))
    private float farmupcraft$legacySaturationScore(FoodProperties food) {
        // Old modifier = saturation / (2 * nutrition); scale by 10 for the modern standard of 6.
        return food.nutrition() == 0 ? 0 : food.saturation() * 5 / food.nutrition();
    }
}
