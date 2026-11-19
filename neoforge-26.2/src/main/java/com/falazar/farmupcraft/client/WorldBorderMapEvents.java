package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID, value = Dist.CLIENT)
public final class WorldBorderMapEvents {
    private static int ticks;

    private WorldBorderMapEvents() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (++ticks % 20 != 0 || !ModList.get().isLoaded("journeymap")) {
            return;
        }
        JourneyMapWorldBorder.update(Minecraft.getInstance().level);
    }
}
