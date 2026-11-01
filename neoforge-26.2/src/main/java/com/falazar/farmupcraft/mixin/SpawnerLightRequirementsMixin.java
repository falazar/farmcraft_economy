package com.falazar.farmupcraft.mixin;

import net.minecraft.world.entity.EntitySpawnReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntitySpawnReason.class)
public abstract class SpawnerLightRequirementsMixin {
    @Inject(method = "ignoresLightRequirements", at = @At("HEAD"), cancellable = true)
    private static void farmupcraft$allowLitSpawners(EntitySpawnReason reason,
                                                   CallbackInfoReturnable<Boolean> callback) {
        if (reason == EntitySpawnReason.SPAWNER) callback.setReturnValue(true);
    }
}
