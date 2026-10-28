package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;

import java.util.ArrayList;
import java.util.List;

@JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
public class JourneyMapIntegration implements IClientPlugin {
    private static IClientAPI jmAPI;
    private static final List<Waypoint> scanWaypoints = new ArrayList<>();

    @Override
    public String getModId() {
        return FarmUpCraftNeoForge.MOD_ID;
    }

    @Override
    public void initialize(IClientAPI api) {
        jmAPI = api;
    }

    public static IClientAPI getApi() {
        return jmAPI;
    }

    public static List<Waypoint> getScanWaypoints() {
        return scanWaypoints;
    }

    public static void addScanWaypoint(Waypoint waypoint) {
        scanWaypoints.add(waypoint);
    }

    public static void clearScanWaypoints() {
        scanWaypoints.clear();
    }
}
