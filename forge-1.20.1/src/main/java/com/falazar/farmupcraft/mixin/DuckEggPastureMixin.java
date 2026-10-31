package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.ChunkManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.starfish_studios.naturalist.common.entity.core.projectile.ThrownDuckEgg", remap = false)
public abstract class DuckEggPastureMixin {
    // Naturalist overrides the mapped Minecraft impact method.
    @Redirect(method = {"onHit", "m_6532_"}, remap = false, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean farmupcraft$hatchOnlyInPasture(Level level, Entity hatchling) {
        return "pasture".equals(ChunkManager.getPlotType(hatchling.blockPosition(), level))
                && level.addFreshEntity(hatchling);
    }
}
