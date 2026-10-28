package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;

@JeiPlugin
public class JeiIntegration implements IModPlugin {
    private static IJeiRuntime jeiRuntime;

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        jeiRuntime = runtime;
    }

    @Override
    public void onRuntimeUnavailable() {
        jeiRuntime = null;
    }

    public static IJeiRuntime getRuntime() {
        return jeiRuntime;
    }
}
