package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.util.PlayerExperienceBonus;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID)
public final class PlayerExperienceEvents {
    private PlayerExperienceEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void gainExperience(PlayerXpEvent.XpChange event) {
        var player = event.getEntity();
        if (player.level().isClientSide()) return;
        try {
            event.setAmount(PlayerExperienceBonus.apply(player.getGameProfile().getName(), event.getAmount()));
        } catch (ArithmeticException error) {
            event.setCanceled(true);
            FarmUpCraft.LOGGER.error("XP bonus overflow for {}: {} XP", player.getGameProfile().getName(), event.getAmount(), error);
            player.sendSystemMessage(Component.literal("Could not grant XP: the doubled amount is too large."));
        }
    }
}
