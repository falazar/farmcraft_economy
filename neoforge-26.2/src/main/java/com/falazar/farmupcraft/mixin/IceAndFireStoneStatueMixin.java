package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.client.StoneStatueRenderCompat;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// TEMP: Workaround for Ice and Fire 2.2-mc26.2-unofficial-beta.1.
// Remove this mixin, its client registration, helper, and regression test after verifying an upstream fix.
@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.render.entity.StoneStatueEntityRenderer", remap = false)
public abstract class IceAndFireStoneStatueMixin {
    @Inject(method = "getHollowEntity", at = @At("RETURN"))
    private void farmupcraft$assignStatueModelId(@Coerce Entity statue,
            CallbackInfoReturnable<Entity> callback) {
        StoneStatueRenderCompat.assignModelId(statue, callback.getReturnValue());
    }
}
