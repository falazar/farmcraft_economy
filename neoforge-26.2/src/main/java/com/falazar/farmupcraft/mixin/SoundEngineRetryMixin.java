package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.client.SoundRetryCooldown;
import com.mojang.logging.LogUtils;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class SoundEngineRetryMixin {
    @Shadow
    private boolean loaded;
    @Unique
    private final SoundRetryCooldown farmupcraft$failureLogCooldown = new SoundRetryCooldown();

    @Inject(method = "shouldChangeDevice", at = @At("HEAD"), cancellable = true)
    private void farmupcraft$deferAutomaticRetry(CallbackInfoReturnable<Boolean> ci) {
        if (!farmupcraft$failureLogCooldown.allowsAutomaticRetry(System.nanoTime()))
            ci.setReturnValue(false);
    }

    @Inject(method = "loadLibrary", at = @At("RETURN"))
    private void farmupcraft$recordAudioAttempt(CallbackInfo ci) {
        long now = System.nanoTime();
        farmupcraft$failureLogCooldown.recordAttempt(loaded, now);
        if (!loaded && farmupcraft$failureLogCooldown.shouldLogFailure(now)) {
            LogUtils.getLogger()
                    .warn("FarmUpCraft: sound initialization failed; automatic retries delayed for five minutes. "
                            + "Manual sound/resource reload can retry immediately.");
        }
    }
}
