package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.client.overlay.PlayerDataOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("player_data", PlayerDataOverlay.HUD_PLAYER_DATA);
    }
}
