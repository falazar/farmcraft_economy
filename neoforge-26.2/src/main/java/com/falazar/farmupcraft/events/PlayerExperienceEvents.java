package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.PlayerExperienceBonus;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class PlayerExperienceEvents {
    private PlayerExperienceEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void gainExperience(PlayerXpEvent.XpChange event) {
        var player = event.getEntity();
        if (player.level().isClientSide()) return;
        try {
            event.setAmount(PlayerExperienceBonus.apply(player.getGameProfile().name(), event.getAmount()));
        } catch (ArithmeticException error) {
            event.setCanceled(true);
            ModEvents.LOGGER.error("XP bonus overflow for {}: {} XP", player.getGameProfile().name(), event.getAmount(), error);
            player.sendSystemMessage(Component.literal("Could not grant XP: the doubled amount is too large."));
        }
    }
}
