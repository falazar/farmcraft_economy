package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.command.MarketCommand;
import com.falazar.farmupcraft.command.VillageCommand;
import com.falazar.farmupcraft.data.WorldData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.fileformats.FileFormat;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.nio.file.Path;
import java.util.List;

/**
 * Handles hourly and daily scheduled tasks for the world.
 *
 * Tick math: 20 ticks/sec * 60 sec * 60 min = 72,000 ticks per hour.
 */
@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WorldScheduler {

    public static final CustomLogger LOGGER = new CustomLogger(WorldScheduler.class.getSimpleName());

    private static final int TICKS_PER_HOUR = 72000; // 20 * 60 * 60
    private static final int TICKS_PER_5_MIN = 6000;  // 20 * 60 * 5
    private static final String HARD_MODE_PLAYER = "BossPanda96366";

    private static int tickCounter = 0;
    private static int fiveMinCounter = 0;

    /** Returns ticks remaining until the next hourly run. */
    public static int getTicksUntilNextRun() {
        return TICKS_PER_HOUR - tickCounter;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;

        // 5-minute hard mode reminder.
        fiveMinCounter++;
        if (fiveMinCounter >= TICKS_PER_5_MIN) {
            fiveMinCounter = 0;
            checkHardMode();
        }

        tickCounter++;
        if (tickCounter < TICKS_PER_HOUR)
            return;
        tickCounter = 0;

        LOGGER.info("WorldScheduler: hourly tick fired.");
        runHourlyChecks();
    }

    public static void runHourlyChecks() {
        try {
            DataBase<Integer, WorldData> worldDb = ModEvents.getWorldDataDatabase();
            WorldData worldData = worldDb.getOrCreate(0, WorldData::new);

            // Daily market refresh - runs once per real-world day
            if (!worldData.hasMarketRanTodayAlready()) {
                LOGGER.info("WorldScheduler: running daily market refresh.");
                runDailyMarketRefresh();
                worldData.markMarketDailyRanToday();
                worldDb.putData(0, worldData);
            }

            // Hourly village upkeep - runs every hour, only for online players' villages
            runHourlyVillageUpkeep();

            // TODO: add more daily/hourly checks here as needed

        } catch (Exception ex) {
            LOGGER.error("WorldScheduler: exception in hourly checks - " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static void runDailyMarketRefresh() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            LOGGER.error("WorldScheduler: server is null, cannot run daily market refresh.");
            return;
        }
        MarketCommand.runDailyTask(server.createCommandSourceStack());
        LOGGER.info("WorldScheduler: daily market refresh complete.");
    }

    private static void runHourlyVillageUpkeep() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            LOGGER.error("WorldScheduler: server is null, cannot run hourly village upkeep.");
            return;
        }
        int count = VillageCommand.runOnlinePlayerVillageUpkeep(server);
        LOGGER.info("WorldScheduler: hourly village upkeep ran for {} online player villages.", count);
    }

    // Checks every 5 minutes if the target player is online solo in non-hard mode.
    private static void checkHardMode() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.size() != 1) return; // Only fire when exactly one player is online.

        ServerPlayer player = players.get(0);
        if (!player.getName().getString().equals(HARD_MODE_PLAYER)) return;

        Difficulty difficulty = server.overworld().getDifficulty();
        if (difficulty != Difficulty.HARD) {
            player.displayClientMessage(
                    Component.literal("⚠ Difficulty is " + difficulty.getKey().toUpperCase()
                            + " — switch to HARD mode!")
                            .withStyle(ChatFormatting.RED),
                    false);
            LOGGER.warn("Hard mode check: {} is playing on {} difficulty.", HARD_MODE_PLAYER, difficulty.getKey());
        }
    }

    // Export village and player databases as human-readable JSON whenever the
    // overworld is saved. Files land in run/farmupcraft-data/.
    @SubscribeEvent
    public static void onLevelSave(LevelEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel))
            return;
        if (!serverLevel.dimension().equals(Level.OVERWORLD))
            return;

        try {
            MinecraftServer server = serverLevel.getServer();
            // Resolve relative to the server's run directory.
            Path exportDir = server.getServerDirectory().toPath().resolve("farmupcraft-data");

            ModEvents.getVillageDatabase().exportDataBase(exportDir.resolve("villages"), FileFormat.JSON);
            ModEvents.getPlayerDatabase().exportDataBase(exportDir.resolve("players"), FileFormat.JSON);
            ModEvents.getChunkDataDatabase().exportDataBase(exportDir.resolve("chunks"), FileFormat.JSON);
        } catch (Exception ex) {
            LOGGER.error("WorldScheduler: failed to export JSON data - " + ex.getMessage());
        }
    }
}
