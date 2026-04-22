package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.command.MarketCommand;
import com.falazar.farmupcraft.command.VillageCommand;
import com.falazar.farmupcraft.data.WorldData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Handles hourly and daily scheduled tasks for the world.
 *
 * Tick math: 20 ticks/sec * 60 sec * 60 min = 72,000 ticks per hour.
 */
@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WorldScheduler {

    public static final CustomLogger LOGGER = new CustomLogger(WorldScheduler.class.getSimpleName());

    private static final int TICKS_PER_HOUR = 72000; // 20 * 60 * 60
    private static int tickCounter = 0;

    /** Returns ticks remaining until the next hourly run. */
    public static int getTicksUntilNextRun() {
        return TICKS_PER_HOUR - tickCounter;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;

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
}
