package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.NpcManager;
import com.falazar.farmupcraft.VillagerFollowManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Brain.class)
public abstract class VillagerConversationBrainMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void farmupcraft$pauseConversationBehaviors(ServerLevel level, LivingEntity entity,
                                                       CallbackInfo callback) {
        if (entity instanceof Villager villager && (NpcManager.holdConversation(villager)
                || VillagerFollowManager.tickFollowing(villager))) {
            callback.cancel();
        }
    }
}
