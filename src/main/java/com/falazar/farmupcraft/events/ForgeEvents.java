package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.command.VillageCommand;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.Difficulty;
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
    private static final String NO_CHORES_MSG = "You should make a chores list instead";
    // 5 minutes at 20 TPS
    private static final int DIFFICULTY_WARN_INTERVAL = 6000;
    private static int difficultyWarnTick = 0;

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

            ServerPlayer serverPlayer = (ServerPlayer) event.getEntity();
            boolean isFirstLogin = !playerDatabase.containsKey(uuid);

            // First login: explain the upkeep system once.
            if (isFirstLogin) {
                serverPlayer.sendSystemMessage(
                        Component.literal(
                                "Welcome! Your village has a daily upkeep cost (village level x100 + plots x20 coins/day). Claim structures to earn income!")
                                .withStyle(ChatFormatting.GOLD));
            }

            // Every login: warn if village treasury is too low to cover today's upkeep.
            PlayerData loginPlayerData = playerDatabase.getData(uuid);
            if (loginPlayerData != null && loginPlayerData.getHomeVillageUUID() != null) {
                com.falazar.farmupcraft.data.VillageData loginVillage = ModEvents.getVillageDatabase()
                        .getData(loginPlayerData.getHomeVillageUUID());
                if (loginVillage != null) {
                    int upkeep = VillageCommand.getDailyCost(loginVillage);
                    if (loginVillage.getCoins() < upkeep) {
                        serverPlayer.sendSystemMessage(
                                Component
                                        .literal("Warning: " + loginVillage.getName() + " treasury ("
                                                + loginVillage.getCoins() + " coins) cannot cover today's upkeep ("
                                                + upkeep + " coins)!")
                                        .withStyle(ChatFormatting.RED));
                    }
                }
            }

            // Suggest daily tasks from Rimfog/TODO.md, shuffled with a per-day seed.
            sendDailyTaskSuggestions(serverPlayer);
        }
    }

    /**
     * Every 5 minutes, if only one player is online and the world difficulty is
     * NORMAL (not HARD), remind them to switch to Hard mode.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        difficultyWarnTick++;
        if (difficultyWarnTick < DIFFICULTY_WARN_INTERVAL)
            return;
        difficultyWarnTick = 0;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;
        var players = server.getPlayerList().getPlayers();
        if (players.size() != 1)
            return;

        ServerPlayer player = players.get(0);
        Difficulty diff = player.getCommandSenderWorld().getDifficulty();
        if (diff != Difficulty.HARD) {
            player.sendSystemMessage(
                    Component
                            .literal("⚠ You are playing on " + diff.getKey().toUpperCase()
                                    + " mode — consider switching to HARD!")
                            .withStyle(ChatFormatting.RED));
        }
    }

    /**
     * (so the order stays consistent all day), then sends a "Have Fun!" message
     * with the first four tasks to the player.
     */
    public static void sendDailyTaskSuggestions(ServerPlayer player) {
        try {
            Path taskFile = FMLPaths.GAMEDIR.get().resolve("Rimfog").resolve("chores.md");
            if (!Files.exists(taskFile)) {
                player.sendSystemMessage(Component.literal(NO_CHORES_MSG).withStyle(ChatFormatting.GRAY));
                return;
            }

            List<String> allLines = Files.readAllLines(taskFile);

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
            if (tasks.isEmpty()) {
                player.sendSystemMessage(Component.literal(NO_CHORES_MSG).withStyle(ChatFormatting.GRAY));
                return;
            }

            // Shuffle with today's date as seed — same order for everyone all day.
            long seed = LocalDate.now().toEpochDay();
            Collections.shuffle(tasks, new Random(seed));

            // Announce with gold header.
            player.sendSystemMessage(
                    Component.literal("Have Fun and do some random chores:")
                            .withStyle(ChatFormatting.GOLD));

            int shown = Math.min(4, tasks.size());
            for (int i = 0; i < shown; i++) {
                player.sendSystemMessage(
                        Component.literal("  - " + tasks.get(i))
                                .withStyle(ChatFormatting.YELLOW));
            }
        } catch (IOException e) {
            FarmUpCraft.LOGGER.warn("Could not read chores file for daily tasks: " + e.getMessage());
            player.sendSystemMessage(Component.literal(NO_CHORES_MSG).withStyle(ChatFormatting.GRAY));
        }
    }

}
