package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.database.message.AddJMWaypointPacket;
import com.falazar.farmupcraft.database.message.RemoveModWaypointsPacket;
import com.falazar.farmupcraft.database.message.ScanWaypointsPacket;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

public final class WaypointPayloadHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private WaypointPayloadHandler() {
    }

    public static void handleAdd(AddJMWaypointPacket payload, IPayloadContext context) {
        if (available()) {
            JourneyMapIntegration.addWaypoint(payload);
        }
    }

    public static void handleRemove(RemoveModWaypointsPacket payload, IPayloadContext context) {
        if (available()) {
            JourneyMapIntegration.removeWaypoints(payload, context.player()::sendSystemMessage);
        }
    }

    public static void handleScan(ScanWaypointsPacket payload, IPayloadContext context) {
        if (available()) {
            JourneyMapIntegration.scanWaypoints(payload);
        }
    }

    public static void loggedOut() {
        if (ModList.get().isLoaded("journeymap")) {
            JourneyMapIntegration.loggedOut();
        }
    }

    private static boolean available() {
        if (ModList.get().isLoaded("journeymap")) {
            return true;
        }
        LOGGER.warn("Cannot process FarmUpCraft waypoint payload: JourneyMap is not installed");
        return false;
    }
}
