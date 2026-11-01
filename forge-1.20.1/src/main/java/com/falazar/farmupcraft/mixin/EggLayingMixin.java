package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.AnimalsManager;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Chicken.class)
public abstract class EggLayingMixin {
    @Shadow public int eggTime;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void farmupcraft$restrictEggLaying(CallbackInfo ci) {
        eggTime = AnimalsManager.eggLayingTime((Animal) (Object) this, eggTime);
    }
}
