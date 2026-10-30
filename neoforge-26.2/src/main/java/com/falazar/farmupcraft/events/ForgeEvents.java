package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.ChunkManager;
import com.falazar.farmupcraft.command.FarmCraftCommand;
import com.falazar.farmupcraft.command.VillageCommand;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.database.message.RemoveModWaypointsPacket;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.ChoresFile;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class ForgeEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final UUID NO_UUID = new UUID(0, 0);
    private static final int DIFFICULTY_WARN_INTERVAL = 6000;
    private static final String NO_CHORES_MSG = "You should make a chores list instead";
    private static int difficultyWarnTick;

    private ForgeEvents() {
    }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        initializeWorldDefaults(event.getServer());
        Path chores = ChoresFile.getPath(FMLPaths.CONFIGDIR.get());
        try {
            if (ChoresFile.seedIfMissing(FMLPaths.CONFIGDIR.get(), FMLPaths.GAMEDIR.get())) {
                LOGGER.info("Created editable daily chores file at {}", chores);
            }

        } catch (IOException error) {
            LOGGER.error("Could not initialize daily chores at {}", chores, error);
        }
    }

    public static void initializeWorldDefaults(net.minecraft.server.MinecraftServer server) {
        try {
            if (com.falazar.farmupcraft.util.StartupDefaults.initializeWorld(
                    server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT), () -> {
                        var rules = server.overworld().getGameRules();
                        rules.set(net.minecraft.world.level.gamerules.GameRules.RANDOM_TICK_SPEED, 1, server);
                        rules.set(net.minecraft.world.level.gamerules.GameRules.PLAYERS_SLEEPING_PERCENTAGE, 50, server);
                        server.saveEverything(false, false, true);
                    })) {
                LOGGER.info("Initialized world defaults: random tick speed 1, sleeping percentage 50.");
            }
        } catch (IOException error) {
            throw new java.io.UncheckedIOException("Could not initialize world gameplay defaults", error);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLoginEvent(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var database = ModEvents.getPlayerDatabase(player.level());
        boolean firstLogin = ensurePlayerRecord(database, player.getId(), player.getUUID());
        if (firstLogin) {
            player.sendSystemMessage(Component.literal(
                    "Welcome! Your village has daily upkeep based on its level and plots. Claim structures to earn income!")
                    .withStyle(ChatFormatting.GOLD));
            EDBMessages.sendToPlayer(new RemoveModWaypointsPacket(player.getX(), player.getY(), player.getZ(),
                    128, false, ChunkManager.VILLAGER_DEATH_WAYPOINT_TAG), player);
        }
        PlayerData data = database.getData(player.getUUID());
        var today = java.time.LocalDate.now();
        if (data.getFirstWelcomeDay() == com.falazar.farmupcraft.util.PlayerOnboarding.UNSET_DAY) {
            data.setFirstWelcomeDay(today.toEpochDay());
            database.putData(player.getUUID(), data);
        }
        if (com.falazar.farmupcraft.util.PlayerOnboarding.shouldWelcome(data.getFirstWelcomeDay(), today)) {
            player.sendSystemMessage(Component.literal(com.falazar.farmupcraft.util.PlayerOnboarding.WELCOME)
                    .withStyle(ChatFormatting.GOLD));
        }
        if (data.getHomeVillageUUID() != null) {
            var village = ModEvents.getVillageDatabase(player.level()).getData(data.getHomeVillageUUID());
            if (village != null) {
                int upkeep = VillageCommand.getDailyCost(village);
                if (village.getCoins() < upkeep) {
                    player.sendSystemMessage(Component.literal("Warning: " + village.getName() + " treasury ("
                            + village.getCoins() + " coins) cannot cover today's upkeep (" + upkeep + " coins)!")
                            .withStyle(ChatFormatting.RED));
                }
            }
        }
        FarmCraftCommand.refreshVillageChunksOverlay(player);
        sendDailyTaskSuggestions(player);
    }

    static boolean ensurePlayerRecord(DataBase<UUID, PlayerData> database, int entityId, UUID playerId) {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(playerId, "playerId");
        PlayerData existing = database.getData(playerId);
        if (existing == null) {
            database.putData(playerId, new PlayerData(entityId, playerId, UUID.randomUUID(), 10));
            return true;
        }
        if (existing.getPlayerUUID() == null || NO_UUID.equals(existing.getPlayerUUID())) {
            existing.setPlayerUUID(playerId);
            database.putData(playerId, existing);
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.overworld() == null || ++difficultyWarnTick < DIFFICULTY_WARN_INTERVAL) {
            return;
        }
        difficultyWarnTick = 0;
        var players = server.getPlayerList().getPlayers();
        if (players.size() != 1) {
            return;
        }
        ServerPlayer player = players.getFirst();
        Difficulty difficulty = player.level().getDifficulty();
        if (difficulty != Difficulty.HARD) {
            player.sendSystemMessage(Component.literal("Warning: you are playing on "
                    + difficulty.getSerializedName().toUpperCase(java.util.Locale.ROOT) + " mode - consider switching to HARD!")
                    .withStyle(ChatFormatting.RED));
        }
        FarmCraftCommand.displayLanNotice(player);
    }

    public static void sendDailyTaskSuggestions(ServerPlayer player) {
        Path file = ChoresFile.getPath(FMLPaths.CONFIGDIR.get());
        if (!Files.exists(file)) {
            player.sendSystemMessage(Component.literal(NO_CHORES_MSG).withStyle(ChatFormatting.GRAY));
            return;
        }
        List<String> tasks;
        try {
            tasks = selectDailyTasks(Files.readAllLines(file, StandardCharsets.UTF_8), LocalDate.now());
        } catch (IOException error) {
            LOGGER.warn("Could not read daily chores from {}", file, error);
            player.sendSystemMessage(Component.literal("Could not read the chores file. Check the server log.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (tasks.isEmpty()) {
            player.sendSystemMessage(Component.literal(NO_CHORES_MSG).withStyle(ChatFormatting.GRAY));
            return;
        }
        player.sendSystemMessage(Component.literal("Have Fun and do some random chores:").withStyle(ChatFormatting.GOLD));
        for (String task : tasks) {
            player.sendSystemMessage(Component.literal("  - " + task).withStyle(ChatFormatting.YELLOW));
        }
    }

    static List<String> selectDailyTasks(List<String> lines, LocalDate date) {
        List<String> tasks = new ArrayList<>();
        for (String line : lines) {
            String text = line.trim();
            if (!text.isEmpty() && !text.startsWith("#")) {
                String task = text.replaceFirst("^(?:[-*]|\\d+\\.)(?:\\s+|$)", "");
                if (!task.isEmpty()) {
                    tasks.add(task);
                }
            }
        }
        Collections.shuffle(tasks, new Random(date.toEpochDay()));
        return List.copyOf(tasks.subList(0, Math.min(4, tasks.size())));
    }

    public static void resetCounters() {
        difficultyWarnTick = 0;
    }
}
