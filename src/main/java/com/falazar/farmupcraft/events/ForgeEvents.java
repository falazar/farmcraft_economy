package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.data.*;
import com.falazar.farmupcraft.database.message.DataBaseChunkS2C;
import com.falazar.farmupcraft.registry.BiomeRegistryHolder;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.DataBaseAccess;
import com.falazar.farmupcraft.database.DataBaseManager;
import com.falazar.farmupcraft.database.message.DataBaseFullS2C;
import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.util.AsyncLocator;
import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles various Forge events related to server lifecycle and resource
 * reloads.
 * <p>
 * This class is subscribed to Forge's event bus and contains methods that react
 * to server
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
     * This method registers new reload listeners for handling JSON resource data
     * related
     * to crop blocks, crop items, and the market. These listeners will be triggered
     * when the resources are reloaded.
     *
     * @param event The event containing reload listener registration details.
     */
    @SubscribeEvent
    public static void onRegisterReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CropBlockDataJsonManager());
        event.addListener(new CropItemDataJsonManager());
        // event.addListener(new MarketDataJsonManager());
        event.addListener(new BiomeRulesDataJsonManager());
    }

    @SubscribeEvent
    public static void onLoginEvent(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) {

            DataBase<UUID, PlayerData> playerDatabase = ModEvents.getPlayerDatabase();
            UUID uuid = ((ServerPlayer) event.getEntity()).getUUID();

            if (!playerDatabase.containsKey(uuid)) {
                playerDatabase.putData(uuid, new PlayerData(event.getEntity().getId(), uuid, UUID.randomUUID(), 10));
            } else {
                // Ensure the playerUUID field is set on existing records (e.g. old saves before
                // the field was added).
                PlayerData existing = playerDatabase.getData(uuid);
                if (existing != null
                        && (existing.getPlayerUUID() == null || existing.getPlayerUUID().equals(new UUID(0L, 0L)))) {
                    existing.setPlayerUUID(uuid);
                    playerDatabase.putData(uuid, existing);
                }
            }

            for (ResourceLocation dataBaseName : DataBaseManager.getDataBasesToSync()) {
                DataBaseAccess<?, ?> dataBaseAccess = DataBaseManager.getDataBaseAccess(dataBaseName);

                CompletableFuture.runAsync(() -> {
                    DataBase<?, ?> dataBase = dataBaseAccess.get(level);
                    List<CompoundTag> chunks = dataBase.saveChunked(50); // ← send always in chunks

                    level.getServer().execute(() -> {
                        ServerPlayer player = (ServerPlayer) event.getEntity();
                        for (int i = 0; i < chunks.size(); i++) {
                            EDBMessages.sendToPlayer(new DataBaseChunkS2C<>(
                                    chunks.get(i),
                                    dataBase.getDatabaseName(),
                                    i,
                                    chunks.size()), player);
                        }
                    });
                });
            }

            // Suggest daily tasks from Rimfog/TODO.md, shuffled with a per-day seed.
            sendDailyTaskSuggestions((ServerPlayer) event.getEntity());
        }
    }

    /**
     * Reads Rimfog/TODO.md, shuffles task lines with a seed keyed to today's date
     * (so the order stays consistent all day), then sends a "Have Fun!" message
     * with the first four tasks to the player.
     */
    private static void sendDailyTaskSuggestions(ServerPlayer player) {
        try {
            // Rimfog/ lives one directory above the game run/ folder.
            Path todoFile = FMLPaths.GAMEDIR.get().getParent().resolve("Rimfog").resolve("TODO.md");
            if (!Files.exists(todoFile))
                return;

            List<String> allLines = Files.readAllLines(todoFile);

            // Collect non-empty, non-header lines as tasks.
            List<String> tasks = new ArrayList<>();
            for (String line : allLines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    // Strip leading list markers (- , * , digits+.)
                    String task = trimmed.replaceFirst("^[-*]\\s+|^\\d+\\.\\s+", "");
                    if (!task.isEmpty())
                        tasks.add(task);
                }
            }
            if (tasks.isEmpty())
                return;

            // Shuffle with today's date as seed — same order for everyone all day.
            long seed = LocalDate.now().toEpochDay();
            Collections.shuffle(tasks, new Random(seed));

            // Announce with gold "Have Fun!" header.
            player.sendSystemMessage(
                    Component.literal("Have Fun and do some of those Tasks:")
                            .withStyle(ChatFormatting.GOLD));

            int shown = Math.min(4, tasks.size());
            for (int i = 0; i < shown; i++) {
                player.sendSystemMessage(
                        Component.literal("  - " + tasks.get(i))
                                .withStyle(ChatFormatting.YELLOW));
            }
        } catch (IOException e) {
            FarmUpCraft.LOGGER.warn("Could not read Rimfog/TODO.md for daily tasks: " + e.getMessage());
        }
    }

}
