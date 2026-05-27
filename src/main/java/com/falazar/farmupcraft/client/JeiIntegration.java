package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.FarmUpCraft;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers this mod with JEI and stores the runtime for client command use.
 */
@JeiPlugin
public class JeiIntegration implements IModPlugin {

    private static IJeiRuntime jeiRuntime;

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(FarmUpCraft.MODID, "jei_plugin");
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
