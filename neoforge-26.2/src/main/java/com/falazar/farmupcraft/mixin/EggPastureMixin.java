package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.AnimalsManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;

@Pseudo
@Mixin(targets = {
        "net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg",
        "com.crispytwig.naturalist.world.entity.projectile.throwableitemprojectile.ThrownDuckEgg"
}, remap = false)
public abstract class EggPastureMixin {
    @Inject(method = "onHit", at = @At("HEAD"))
    private void farmupcraft$warnFullPasture(HitResult hit, CallbackInfo ci) {
        AnimalsManager.warnFullPastureEgg((Projectile) (Object) this);
    }

    @Redirect(method = "onHit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean farmupcraft$hatchOnlyInPasture(Level level, Entity hatchling) {
        return AnimalsManager.addEggHatchling(level, hatchling);
    }
}
