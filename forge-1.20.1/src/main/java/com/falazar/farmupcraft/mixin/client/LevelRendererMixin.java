package com.falazar.farmupcraft.mixin.client;

import com.falazar.farmupcraft.client.border.BorderRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {


    @Inject(method = "renderSnowAndRain", at = @At(value = "HEAD"), cancellable = true)
    public void farmupcraft$renderClaimedChunk(LightTexture pLightTexture, float pPartialTick, double pCamX, double pCamY, double pCamZ, CallbackInfo ci) {
        LevelRenderer levelRenderer = (LevelRenderer) (Object) this;
        BorderRenderer.renderClaimedChunk(levelRenderer, pLightTexture, pPartialTick, pCamX, pCamY, pCamZ);
    }
}
