package com.falazar.farmupcraft.setup;

import com.falazar.farmupcraft.registry.CropRulesRegistry;
import com.falazar.farmupcraft.registry.FUCRegistries;
import com.falazar.farmupcraft.saveddata.BiomeRulesManager;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;


public class Registration {
    public static final CustomLogger LOGGER = new CustomLogger(Registries.class.getSimpleName());
    public static void init(){

        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        FUCRegistries.CROP_RULES_TYPE_SERIALIZER.register(bus);
        CropRulesRegistry.CROP_RULE.register(bus);

    }
}
