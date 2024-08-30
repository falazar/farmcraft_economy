package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.data.CropBlockDataJsonManager;
import com.falazar.farmupcraft.data.CropItemDataJsonManager;
import com.falazar.farmupcraft.util.AsyncLocator;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEvents {
    @SubscribeEvent
    public static void serverAboutToStart(final ServerAboutToStartEvent event) {
        AsyncLocator.handleServerAboutToStartEvent();
    }
    @SubscribeEvent
    public static void serverStarted(final ServerStartedEvent event) {
        CropBlockDataJsonManager.populateCropBlockEntries(event.getServer().overworld());
        CropItemDataJsonManager.populateCropItemEntries(event.getServer().overworld());

    }

    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event) {
        AsyncLocator.handleServerStoppingEvent();
        CropBlockDataJsonManager.clearEntries();
        CropItemDataJsonManager.clearEntries();
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CropBlockDataJsonManager());
        event.addListener(new CropItemDataJsonManager());
    }
}
