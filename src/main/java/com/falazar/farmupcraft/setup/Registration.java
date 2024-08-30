package com.falazar.farmupcraft.setup;

import com.falazar.farmupcraft.registry.CropRulesRegistry;
import com.falazar.farmupcraft.registry.FUCRegistries;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;


public class Registration {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static void init(){

        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        FUCRegistries.CROP_RULES_TYPE_SERIALIZER.register(bus);
        CropRulesRegistry.CROP_RULE.register(bus);

    }
}
