package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.client.border.BorderRenderer;
import com.falazar.farmupcraft.client.overlay.PlayerDataOverlay;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.ClientUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, "player_data"),
                PlayerDataOverlay.HUD_PLAYER_DATA);
    }

    @SubscribeEvent
    public static void extractBorders(ExtractLevelRenderStateEvent event) {
        BorderRenderer.extract(event);
    }

    @SubscribeEvent
    public static void submitBorders(SubmitCustomGeometryEvent event) {
        BorderRenderer.submit(event);
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientUtils.blockedEntityRenders.clear();
    }
}
