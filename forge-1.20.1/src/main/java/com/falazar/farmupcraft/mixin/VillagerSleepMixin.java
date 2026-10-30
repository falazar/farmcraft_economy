package com.falazar.farmupcraft.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class VillagerSleepMixin {
    @Inject(method = "startSleeping", at = @At("TAIL"))
    private void farmupcraft$healSleepingVillager(BlockPos bed, CallbackInfo callback) {
        if ((Object) this instanceof Villager villager && !villager.level().isClientSide()
                && villager.isAlive() && villager.isSleeping()) {
            villager.setHealth(villager.getMaxHealth());
        }
    }
}
