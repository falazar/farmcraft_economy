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

@Mixin(SoundEngine.class)
public abstract class SoundEngineRetryMixin {
    @Shadow
    private boolean loaded;
    @Unique
    private final SoundRetryCooldown farmupcraft$failureLogCooldown = new SoundRetryCooldown();

    @Inject(method = "loadLibrary", at = @At("RETURN"))
    private void farmupcraft$recordAudioAttempt(CallbackInfo ci) {
        if (!loaded && farmupcraft$failureLogCooldown.shouldLogFailure(System.nanoTime())) {
            LogUtils.getLogger()
                    .warn("FarmUpCraft: sound initialization failed; automatic retry checks remain enabled. "
                            + "Manual sound/resource reload can retry immediately.");
        }
    }
}
