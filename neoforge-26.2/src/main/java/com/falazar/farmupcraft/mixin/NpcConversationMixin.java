package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.NpcManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class NpcConversationMixin {
    @Shadow
    protected abstract void customServerAiStep(ServerLevel level);

    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void farmupcraft$pauseConversationAi(CallbackInfo callback) {
        Mob mob = (Mob) (Object) this;
        if (NpcManager.holdConversation(mob)) {
            // Keep merchant timers running; the separate brain hook pauses villager behaviors.
            if (mob instanceof Villager) customServerAiStep((ServerLevel) mob.level());
            callback.cancel();
        }
    }
}
