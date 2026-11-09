package com.falazar.farmupcraft.mixin;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TransportItemsBetweenContainers.class)
public abstract class CopperGolemChestReachMixin {
    @Redirect(method = "isWithinTargetDistance", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/AABB;inflate(DDD)Lnet/minecraft/world/phys/AABB;"))
    private AABB farmupcraft$reachHigherChest(AABB box, double x, double y, double z,
            double distance, TransportItemsBetweenContainers.TransportItemTarget target,
            Level level, PathfinderMob mob, Vec3 fromPos) {
        double feetY = fromPos.y - mob.getBoundingBox().getYsize() / 2;
        int height = target.pos().getY() - net.minecraft.util.Mth.floor(feetY);
        if (mob instanceof CopperGolem && height == 2) {
            return new AABB(box.minX - x, box.minY - 1.5, box.minZ - z,
                    box.maxX + x, box.maxY + y, box.maxZ + z);
        }
        return box.inflate(x, y, z);
    }
}
