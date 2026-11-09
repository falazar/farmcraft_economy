package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.FarmUpCraft;
import journeymap.client.api.ClientPlugin;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.display.Waypoint;
import journeymap.client.api.event.ClientEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers this mod with JourneyMap and holds the IClientAPI for packet
 * handlers.
 */
@ClientPlugin
public class JourneyMapIntegration implements IClientPlugin {

    private static IClientAPI jmAPI;
    private static final List<Waypoint> scanWaypoints = new ArrayList<>();

    @Override
    public String getModId() {
        return FarmUpCraft.MODID;
    }

    @Override
    public void initialize(IClientAPI api) {
        jmAPI = api;
    }

    @Override
    public void onEvent(ClientEvent event) {
        // no-op
    }

    public static IClientAPI getApi() {
        return jmAPI;
    }

    public static boolean openMapAt(int x, int z) {
        if (jmAPI == null) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "JourneyMap is not ready."));
            return false;
        }
        try {
            Class<?> managerType = Class.forName("journeymap.client.ui.UIManager");
            Object manager = managerType.getField("INSTANCE").get(null);
            Object map = managerType.getMethod("openFullscreenMap").invoke(manager);
            map.getClass().getMethod("centerOn", double.class, double.class).invoke(map, (double) x, (double) z);
            return true;
        } catch (ReflectiveOperationException | LinkageError error) {
            FarmUpCraft.LOGGER.error("Cannot open JourneyMap at {}, {}", x, z, error);
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Could not open JourneyMap. See the log for details."));
            return false;
        }
    }

    public static List<Waypoint> getScanWaypoints() {
        return scanWaypoints;
    }

    public static void addScanWaypoint(Waypoint wp) {
        scanWaypoints.add(wp);
    }

    public static void clearScanWaypoints() {
        scanWaypoints.clear();
    }
}
