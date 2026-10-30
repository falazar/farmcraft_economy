package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.data.BiomeRulesDataJsonManager;
import com.falazar.farmupcraft.data.CropBlockDataJsonManager;
import com.falazar.farmupcraft.data.CropItemDataJsonManager;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class CropDataEvents {
    private CropDataEvents() {
    }

    public static void addReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(id("crop_block_data"), new CropBlockDataJsonManager());
        event.addListener(id("crop_item_data"), new CropItemDataJsonManager());
        event.addListener(id("biome_rules"), new BiomeRulesDataJsonManager());
    }

    public static void tagsUpdated(TagsUpdatedEvent.ServerDataLoad event) {
        CropBlockDataJsonManager.populateCropBlockEntries(event.getRegistries());
        CropItemDataJsonManager.populateCropItemEntries(event.getRegistries());
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.overworld() != null) {
            BiomeRulesDataJsonManager.populateBiomeRulesInstances(server.overworld());
        }
    }

    public static void serverStarted(ServerStartedEvent event) {
        var server = event.getServer();
        CropBlockDataJsonManager.populateCropBlockEntries(server.registryAccess());
        CropItemDataJsonManager.populateCropItemEntries(server.registryAccess());
        var level = server.overworld();
        if (level != null) {
            BiomeRulesDataJsonManager.populateBiomeRulesInstances(level);
        } else {
            LogUtils.getLogger().debug("No overworld available; world-dependent biome rule initialization postponed");
        }
    }

    public static void serverStopped(ServerStoppedEvent event) {
        CropBlockDataJsonManager.clearEntries();
        CropItemDataJsonManager.clearEntries();
        BiomeRulesDataJsonManager.clearEntries();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, path);
    }
}
