package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.database.DataBaseManager;
import com.falazar.farmupcraft.database.message.DataBaseEntryS2C;
import com.falazar.farmupcraft.database.message.DataBaseFullS2C;
import com.falazar.farmupcraft.database.message.DataBasePayloadHandler;
import com.falazar.farmupcraft.database.message.AddJMWaypointPacket;
import com.falazar.farmupcraft.database.message.RemoveModWaypointsPacket;
import com.falazar.farmupcraft.database.message.ScanWaypointsPacket;
import com.falazar.farmupcraft.database.message.HighlightChunkPacket;
import com.falazar.farmupcraft.database.message.ShowVillageChunksPacket;
import com.falazar.farmupcraft.database.message.OpenJeiRecipePacket;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID, value = Dist.CLIENT)
public final class ClientDatabaseEvents {
    private ClientDatabaseEvents() {
    }

    @SubscribeEvent
    public static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(DataBaseEntryS2C.TYPE, DataBasePayloadHandler::handleEntry);
        event.register(DataBaseFullS2C.TYPE, DataBasePayloadHandler::handleFull);
        event.register(AddJMWaypointPacket.TYPE, WaypointPayloadHandler::handleAdd);
        event.register(RemoveModWaypointsPacket.TYPE, WaypointPayloadHandler::handleRemove);
        event.register(ScanWaypointsPacket.TYPE, WaypointPayloadHandler::handleScan);
        event.register(HighlightChunkPacket.TYPE, CommandPayloadHandler::handleHighlight);
        event.register(ShowVillageChunksPacket.TYPE, CommandPayloadHandler::handleVillage);
        event.register(OpenJeiRecipePacket.TYPE, CommandPayloadHandler::handleRecipe);
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        DataBaseManager.clearClientCaches();
        WaypointPayloadHandler.loggedOut();
        CommandPayloadHandler.loggedOut();
    }
}
