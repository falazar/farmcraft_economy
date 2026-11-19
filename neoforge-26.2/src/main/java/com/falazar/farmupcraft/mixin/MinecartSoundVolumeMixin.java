package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.client.MinecartSoundVolume;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSoundInstance.class)
public abstract class MinecartSoundVolumeMixin {
    @Shadow
    @Final
    protected Identifier identifier;

    @Inject(method = "getVolume", at = @At("RETURN"), cancellable = true)
    private void farmupcraft$quieterMinecarts(CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(MinecartSoundVolume.adjust(identifier, callback.getReturnValue()));
    }
}
