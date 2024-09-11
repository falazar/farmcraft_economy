package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.data.BiomeRulesDataJsonManager;
import com.falazar.farmupcraft.data.CropBlockDataJsonManager;
import com.falazar.farmupcraft.data.CropItemDataJsonManager;
import com.falazar.farmupcraft.data.MarketDataJsonManager;
import com.falazar.farmupcraft.registry.BiomeRegistryHolder;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.DataBaseAccess;
import com.falazar.farmupcraft.database.DataBaseManager;
import com.falazar.farmupcraft.database.message.DataBaseFullS2C;
import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.util.AsyncLocator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.CompletableFuture;

/**
 * Handles various Forge events related to server lifecycle and resource reloads.
 * <p>
 * This class is subscribed to Forge's event bus and contains methods that react to server
 * start, stop, and resource reload events. It is part of the FarmUpCraft mod.
 */
@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEvents {

    /**
     * Called when the server is about to start.
     * <p>
     * This method handles tasks that need to be done just before the server starts.
     * Currently, it manages asynchronous tasks related to server startup.
     *
     * @param event The event containing server startup details.
     */
    @SubscribeEvent
    public static void serverAboutToStart(final ServerAboutToStartEvent event) {
        AsyncLocator.handleServerAboutToStartEvent();
        BiomeRegistryHolder.setupBiomeRegistry(event.getServer());
    }

    /**
     * Called when the server has started.
     * <p>
     * This method populates crop block and crop item data after the server starts.
     * It uses the server's overworld to initialize data for crop blocks and items.
     *
     * @param event The event containing server start details.
     */
    @SubscribeEvent
    public static void serverStarted(final ServerStartedEvent event) {
        CropBlockDataJsonManager.populateCropBlockEntries(event.getServer().overworld());
        CropItemDataJsonManager.populateCropItemEntries(event.getServer().overworld());
        BiomeRulesDataJsonManager.populateBiomeRulesInstances(event.getServer().overworld());
    }

    /**
     * Called when the server is stopping.
     * <p>
     * This method handles cleanup tasks when the server is about to stop, such as
     * clearing cached entries and handling asynchronous tasks.
     *
     * @param event The event containing server stopping details.
     */
    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event) {
        AsyncLocator.handleServerStoppingEvent();
        CropBlockDataJsonManager.clearEntries();
        CropItemDataJsonManager.clearEntries();
        BiomeRulesDataJsonManager.clearEntries();


    }
    @SubscribeEvent
    public static void serverStopped(final ServerStoppedEvent event) {
        DataBaseManager.shutDownDataBases(event.getServer().overworld());
    }

    /**
     * Called when the reload listeners are being registered.
     * <p>
     * This method registers new reload listeners for handling JSON resource data related
     * to crop blocks, crop items, and the market. These listeners will be triggered
     * when the resources are reloaded.
     *
     * @param event The event containing reload listener registration details.
     */
    @SubscribeEvent
    public static void onRegisterReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CropBlockDataJsonManager());
        event.addListener(new CropItemDataJsonManager());
        event.addListener(new MarketDataJsonManager());
        event.addListener(new BiomeRulesDataJsonManager());
    }


    @SubscribeEvent
    public static void onLoginEvent(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            for (ResourceLocation dataBaseName : DataBaseManager.getDataBasesToSync()) {
                DataBaseAccess<?, ?> dataBaseAccess = DataBaseManager.getDataBaseAccess(dataBaseName);

                // Run saving operation asynchronously
                CompletableFuture.runAsync(() -> {
                    DataBase<?, ?> dataBase = dataBaseAccess.get(level);

                    // Perform the save operation (this may take time)
                    CompoundTag tag = dataBase.save(new CompoundTag());

                    // Send the saved data back to the main thread for sending to the player
                    level.getServer().execute(() -> {
                        EDBMessages.sendToPlayer(new DataBaseFullS2C<>(tag, dataBase.getDatabaseName()), (ServerPlayer) event.getEntity());
                    });
                });
            }
        }
    }



}
