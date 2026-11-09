package com.falazar.farmupcraft.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class VillagerLadderMixin {
    @Inject(method = "handleRelativeFrictionAndCalculateMovement", at = @At("RETURN"), cancellable = true)
    private void farmupcraft$stopVillagerLadderClimbing(Vec3 input, float friction,
                                                      CallbackInfoReturnable<Vec3> callback) {
        if ((Object) this instanceof Villager villager
                && villager.level().getBlockState(villager.blockPosition()).getBlock() instanceof LadderBlock) {
            Vec3 movement = callback.getReturnValue();
            // Cancel the wall-collision climbing boost, retaining vanilla ladder sliding and gravity.
            if (movement.y > 0.0) {
                callback.setReturnValue(new Vec3(movement.x, 0.0, movement.z));
            }
        }
    }
}
