package com.falazar.farmupcraft.neoforge;

import com.falazar.farmupcraft.currency.Coin;
import com.falazar.farmupcraft.data.rules.crop.CropRules;
import com.falazar.farmupcraft.events.CropDataEvents;
import com.falazar.farmupcraft.registry.CropRulesRegistry;
import com.falazar.farmupcraft.registry.FUCRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

@Mod(FarmUpCraftNeoForge.MOD_ID)
public final class FarmUpCraftNeoForge {
    public static final String MOD_ID = "farmupcraft";

    public FarmUpCraftNeoForge(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(CropDataEvents::addReloadListeners);
        NeoForge.EVENT_BUS.addListener(CropDataEvents::tagsUpdated);
        NeoForge.EVENT_BUS.addListener(CropDataEvents::serverStarted);
        NeoForge.EVENT_BUS.addListener(CropDataEvents::serverStopped);
        FUCRegistries.CROP_RULES_TYPE_SERIALIZER.register(modEventBus);
        CropRulesRegistry.CROP_RULE.register(modEventBus);
        modEventBus.addListener((DataPackRegistryEvent.NewRegistry event) -> {
            event.dataPackRegistry(FUCRegistries.Keys.CROP_RULES, CropRules.DIRECT_CODEC, CropRules.DIRECT_CODEC);
            event.dataPackRegistry(FUCRegistries.Keys.COIN, Coin.DIRECT_CODEC, Coin.DIRECT_CODEC);
        });
    }
}
