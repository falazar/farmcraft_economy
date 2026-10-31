package com.falazar.farmupcraft.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// TEMP: Remove after Ice and Fire fixes client-side prey bite completion.
@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.entity.DragonBaseEntity", remap = false)
public abstract class IceAndFireDragonPreyMixin {
    @Inject(method = "updatePreyInMouth", at = @At(value = "INVOKE",
            target = "Lcom/iafenvoy/iceandfire/entity/DragonBaseEntity;level()Lnet/minecraft/world/level/Level;",
            ordinal = 0), cancellable = true)
    private void farmupcraft$serverOnlyPreyBite(Entity prey, CallbackInfo callback) {
        // Inject before the cast, not hurtServer: its arguments already cast the world.
        if (((Entity) (Object) this).level().isClientSide()) callback.cancel();
    }
}
