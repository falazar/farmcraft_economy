package com.falazar.farmupcraft.neoforge;

import com.falazar.farmupcraft.currency.Coin;
import com.falazar.farmupcraft.command.PlotCommand;
import com.falazar.farmupcraft.data.rules.crop.CropRules;
import com.falazar.farmupcraft.database.backup.BackupScheduler;
import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.events.CropDataEvents;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.registry.CropRulesRegistry;
import com.falazar.farmupcraft.registry.FUCRegistries;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

import java.util.Locale;

@Mod(FarmUpCraftNeoForge.MOD_ID)
public final class FarmUpCraftNeoForge {
    public static final String MOD_ID = "farmupcraft";
    public static final CustomLogger LOGGER = new CustomLogger(FarmUpCraftNeoForge.class.getSimpleName());

    public static Identifier prefix(String name) {
        return Identifier.fromNamespaceAndPath(MOD_ID, name.toLowerCase(Locale.ROOT));
    }

    public FarmUpCraftNeoForge(IEventBus modEventBus) {
        if (net.neoforged.fml.ModList.get().isLoaded("solonion")) {
            try {
                if (com.falazar.farmupcraft.util.StartupDefaults.installOnion(
                        net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get(), true)) {
                    LOGGER.info("Installed one-time Spice of Life progression defaults.");
                }
            } catch (java.io.IOException | com.google.gson.JsonParseException | IllegalStateException error) {
                throw new IllegalStateException("Could not install Spice of Life progression defaults", error);
            }
        }
        modEventBus.addListener(PlotCommand::registerTicketControllers);
        modEventBus.addListener(ModEvents::initDataBases);
        modEventBus.addListener(EDBMessages::register);
        NeoForge.EVENT_BUS.addListener(ModEvents::serverStarted);
        NeoForge.EVENT_BUS.addListener(ModEvents::serverStopping);
        NeoForge.EVENT_BUS.addListener(ModEvents::serverStopped);
        NeoForge.EVENT_BUS.addListener(BackupScheduler::onTick);
        NeoForge.EVENT_BUS.addListener(EDBMessages::playerLoggedIn);
        NeoForge.EVENT_BUS.addListener(EDBMessages::playerChangedDimension);
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
