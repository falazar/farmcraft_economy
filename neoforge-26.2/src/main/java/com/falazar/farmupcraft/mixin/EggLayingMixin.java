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
@Mixin(targets = {
        "net.minecraft.world.entity.animal.chicken.Chicken",
        "com.crispytwig.naturalist.world.entity.animal.duck.Duck"
}, remap = false)
public abstract class EggLayingMixin {
    @Shadow(remap = false) public int eggTime;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void farmupcraft$restrictEggLaying(CallbackInfo ci) {
        eggTime = AnimalsManager.eggLayingTime((Animal) (Object) this, eggTime);
    }
}
