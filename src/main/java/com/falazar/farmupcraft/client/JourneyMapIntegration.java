package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.FarmUpCraft;
import journeymap.client.api.ClientPlugin;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.event.ClientEvent;

/**
 * Registers this mod with JourneyMap and holds the IClientAPI for packet
 * handlers.
 */
@ClientPlugin
public class JourneyMapIntegration implements IClientPlugin {

    private static IClientAPI jmAPI;

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
}
