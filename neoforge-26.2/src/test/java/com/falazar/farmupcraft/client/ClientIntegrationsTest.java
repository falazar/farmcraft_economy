package com.falazar.farmupcraft.client;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.common.JourneyMapPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ClientIntegrationsTest {
    @AfterEach
    void resetIntegrationState() {
        new JeiIntegration().onRuntimeUnavailable();
        new JourneyMapIntegration().initialize(null);
        JourneyMapIntegration.clearScanWaypoints();
    }

    @Test
    void jeiKeepsItsPluginIdentifier() {
        assertEquals("farmupcraft:jei_plugin", new JeiIntegration().getPluginUid().toString());
    }

    @Test
    void jeiStoresAndClearsItsRuntime() {
        IJeiRuntime runtime = (IJeiRuntime) Proxy.newProxyInstance(
                IJeiRuntime.class.getClassLoader(), new Class<?>[]{IJeiRuntime.class},
                (proxy, method, arguments) -> {
                    throw new UnsupportedOperationException("No JEI runtime calls expected");
                });
        JeiIntegration integration = new JeiIntegration();

        integration.onRuntimeAvailable(runtime);
        assertSame(runtime, JeiIntegration.getRuntime());

        integration.onRuntimeUnavailable();
        assertNull(JeiIntegration.getRuntime());
    }

    @Test
    void journeyMapDeclaresTheActualV2ApiVersion() {
        JourneyMapPlugin annotation = JourneyMapIntegration.class.getAnnotation(JourneyMapPlugin.class);
        assertNotNull(annotation);
        assertEquals(IClientAPI.API_VERSION, annotation.apiVersion());
        assertEquals("farmupcraft", new JourneyMapIntegration().getModId());
    }

    @Test
    void journeyMapStoresTheClientApi() {
        IClientAPI api = (IClientAPI) Proxy.newProxyInstance(
                IClientAPI.class.getClassLoader(), new Class<?>[]{IClientAPI.class},
                (proxy, method, arguments) -> {
                    throw new UnsupportedOperationException("No JourneyMap API calls expected");
                });

        new JourneyMapIntegration().initialize(api);

        assertSame(api, JourneyMapIntegration.getApi());
    }
}
