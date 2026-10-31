package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.ChunkManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ThrownEgg.class)
public abstract class EggPastureMixin {
    @Redirect(method = "onHit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean farmupcraft$hatchOnlyInPasture(Level level, Entity hatchling) {
        return "pasture".equals(ChunkManager.getPlotType(hatchling.blockPosition(), level))
                && level.addFreshEntity(hatchling);
    }
}
