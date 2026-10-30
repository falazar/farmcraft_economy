package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.command.MarketCommand;
import com.falazar.farmupcraft.command.NpcCommand;
import com.falazar.farmupcraft.command.VillageCommand;
import com.falazar.farmupcraft.data.WorldData;
import com.falazar.farmupcraft.database.fileformats.FileFormat;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.IntSupplier;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class WorldScheduler {
    public static final Logger LOGGER = LogUtils.getLogger();
    private static MinecraftServer activeServer;
    private static MinecraftServer stoppingServer;
    private static Schedule schedule = new Schedule();

    private WorldScheduler() {
    }

    static final class Schedule {
        private int hourlyTicks;
        private int reminderTicks;

        boolean advanceHour() {
            if (++hourlyTicks < 72000) {
                return false;
            }
            hourlyTicks = 0;
            return true;
        }

        boolean advanceReminder() {
            if (++reminderTicks < 6000) {
                return false;
            }
            reminderTicks = 0;
            return true;
        }

        int ticksUntilNextRun() {
            return 72000 - hourlyTicks;
        }
    }

    public static void start(MinecraftServer server) {
        activeServer = Objects.requireNonNull(server, "server");
        stoppingServer = null;
        schedule = new Schedule();
    }

    public static int getTicksUntilNextRun() {
        return schedule.ticksUntilNextRun();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld() == null || server == stoppingServer) {
            return;
        }
        if (activeServer != server) {
            start(server);
        }
        if (schedule.advanceReminder()) {
            checkHardMode(server);
            NpcCommand.tickRetryQueue(server);
            NpcCommand.autoReplenishNotesIfNeeded(server);
        }
        if (schedule.advanceHour()) {
            LOGGER.info("WorldScheduler: hourly tick fired.");
            runHourlyChecks(server);
        }
    }

    public static void runHourlyChecks() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.overworld() == null) {
            throw new IllegalStateException("Hourly checks require a running server with an overworld");
        }
        runHourlyChecks(server);
    }

    public static void runHourlyChecks(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Hourly checks must run on the server thread");
        }
        var database = ModEvents.getWorldDataDatabase(server.overworld());
        WorldData world = database.getOrCreate(0, WorldData::new);
        boolean refreshed = refreshIfDue(world,
                () -> MarketCommand.runDailyTask(server.createCommandSourceStack()),
                () -> database.putData(0, world));
        if (!refreshed) {
            LOGGER.error("Daily market refresh failed; leaving its date unchanged for retry.");
        }
        int processed = VillageCommand.runOnlinePlayerVillageUpkeep(server);
        LOGGER.info("WorldScheduler: hourly village upkeep ran for {} online player villages.", processed);
    }

    static boolean refreshIfDue(WorldData world, IntSupplier refresh, Runnable persist) {
        if (world.hasMarketRanTodayAlready()) {
            return true;
        }
        if (refresh.getAsInt() <= 0) {
            return false;
        }
        world.markMarketDailyRanToday();
        persist.run();
        return true;
    }

    private static void checkHardMode(MinecraftServer server) {
        var players = server.getPlayerList().getPlayers();
        if (players.size() < 2 || server.overworld().getDifficulty() != Difficulty.HARD) {
            return;
        }
        for (var player : players) {
            if (shouldRemindNormalMode(players.size(), server.overworld().getDifficulty(),
                    player.createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))) {
                player.sendSystemMessage(Component.literal(
                        "Reminder: HARD mode is on with multiple players online. Consider /difficulty normal to make it easier for the kids.")
                        .withStyle(ChatFormatting.YELLOW));
                LOGGER.info("Multiplayer difficulty reminder sent to {} with {} players on HARD.",
                        player.getName().getString(), players.size());
            }
        }
    }

    static boolean shouldRemindNormalMode(int playerCount, Difficulty difficulty, boolean operator) {
        return playerCount >= 2 && difficulty == Difficulty.HARD && operator;
    }

    @SubscribeEvent
    public static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(Level.OVERWORLD)
                && level.getServer() != stoppingServer) {
            exportDatabases(level);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void serverStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        // Export before normal-priority database cleanup; final level saves must not recreate closed caches.
        if (server.overworld() != null) {
            exportDatabases(server.overworld());
        }
        stoppingServer = server;
        if (activeServer == server) {
            activeServer = null;
            schedule = new Schedule();
        }
    }

    private static void exportDatabases(ServerLevel level) {
        var directory = level.getServer().getServerDirectory().resolve("farmupcraft-data");
        try {
            ModEvents.getVillageDatabase(level).exportDataBase(directory.resolve("villages"), FileFormat.JSON);
            ModEvents.getPlayerDatabase(level).exportDataBase(directory.resolve("players"), FileFormat.JSON);
            ModEvents.getChunkDataDatabase(level).exportDataBase(directory.resolve("chunks"), FileFormat.JSON);
        } catch (UncheckedIOException error) {
            LOGGER.error("Failed to export economy databases to {}", directory, error);
        }
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        if (stoppingServer == event.getServer()) {
            stoppingServer = null;
        }
        if (activeServer == event.getServer()) {
            activeServer = null;
            schedule = new Schedule();
        }
    }
}
