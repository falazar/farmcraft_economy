package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.ChunkManager;
import com.falazar.farmupcraft.IslandScanManager;
import com.falazar.farmupcraft.NpcManager;
import com.falazar.farmupcraft.StructureManager;
import com.falazar.farmupcraft.VillagerManager;
import com.falazar.farmupcraft.command.NpcCommand;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.OllamaService;
import com.falazar.farmupcraft.util.AsyncLocator;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerLevel;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class GameplayEvents {
    private GameplayEvents() {
    }

    @SubscribeEvent
    public static void serverAboutToStart(ServerAboutToStartEvent event) {
        AsyncLocator.handleServerAboutToStartEvent();
    }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        clearCaches();
        WorldScheduler.start(event.getServer());
        if (event.getServer().overworld() != null) {
            OllamaService.start();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void serverStopping(ServerStoppingEvent event) {
        AsyncLocator.handleServerStoppingEvent();
        IslandScanManager.shutdown(event.getServer());
        NpcCommand.shutdown(event.getServer());
        NpcManager.shutdown(event.getServer());
        clearCaches();
        OllamaService.shutdown();
    }

    @SubscribeEvent
    public static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            IslandScanManager.cancelWorld(level);
        }
    }

    @SubscribeEvent
    public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        NpcManager.clearConversation(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        NpcManager.clearConversation(event.getEntity().getUUID());
    }

    private static void clearCaches() {
        ForgeEvents.resetCounters();
        ChunkManager.clearTransientState();
        StructureManager.clearCaches();
        VillagerManager.clearCaches();
    }
}
