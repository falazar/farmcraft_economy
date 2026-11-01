package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.AnimalsManager;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.starfish_studios.naturalist.common.entity.Duck", remap = false)
public abstract class DuckEggLayingMixin {
    @Shadow(remap = false) public int eggTime;

    @Inject(method = {"aiStep", "m_8107_"}, at = @At("HEAD"))
    private void farmupcraft$restrictEggLaying(CallbackInfo ci) {
        eggTime = AnimalsManager.eggLayingTime((Animal) (Object) this, eggTime);
    }
}
