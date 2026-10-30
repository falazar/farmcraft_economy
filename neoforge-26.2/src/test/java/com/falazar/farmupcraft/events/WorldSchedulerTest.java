package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.data.WorldData;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WorldSchedulerTest {
    @Test
    void normalModeReminderRequiresMultiplayerHardAndOperator() {
        for (var difficulty : net.minecraft.world.Difficulty.values()) {
            for (int count : new int[]{0, 1, 2, 3}) {
                for (boolean operator : new boolean[]{false, true}) {
                    assertEquals(count >= 2 && difficulty == net.minecraft.world.Difficulty.HARD && operator,
                            WorldScheduler.shouldRemindNormalMode(count, difficulty, operator));
                }
            }
        }
    }

    @Test
    void exactTickIntervalsProduceOneHourlyAndTwelveReminderRuns() {
        var schedule = new WorldScheduler.Schedule();
        assertEquals(72000, schedule.ticksUntilNextRun());
        int hours = 0;
        int reminders = 0;
        for (int tick = 1; tick <= 72000; tick++) {
            if (schedule.advanceReminder()) {
                reminders++;
                assertEquals(0, tick % 6000);
            }
            if (schedule.advanceHour()) {
                hours++;
                assertEquals(72000, tick);
            }
            if (tick < 72000) {
                assertEquals(72000 - tick, schedule.ticksUntilNextRun());
            }
        }
        assertEquals(1, hours);
        assertEquals(12, reminders);
        assertEquals(72000, schedule.ticksUntilNextRun());
    }

    @Test
    void failedMarketRefreshDoesNotMarkTheDayOrPersistSuccess() {
        WorldData world = new WorldData();
        String oldDate = world.getLastRanMarketDaily();
        AtomicInteger saves = new AtomicInteger();
        assertFalse(WorldScheduler.refreshIfDue(world, () -> 0, saves::incrementAndGet));
        assertFalse(WorldScheduler.refreshIfDue(world, () -> -1, saves::incrementAndGet));
        assertEquals(oldDate, world.getLastRanMarketDaily());
        assertEquals(0, saves.get());
    }

    @Test
    void successfulMarketRefreshRunsAndPersistsOnlyOncePerDay() {
        WorldData world = new WorldData();
        AtomicInteger refreshes = new AtomicInteger();
        AtomicInteger saves = new AtomicInteger();
        assertTrue(WorldScheduler.refreshIfDue(world, refreshes::incrementAndGet, saves::incrementAndGet));
        assertTrue(WorldScheduler.refreshIfDue(world, refreshes::incrementAndGet, saves::incrementAndGet));
        assertEquals(1, refreshes.get());
        assertEquals(1, saves.get());
        assertEquals(LocalDate.now().toString(), world.getLastRanMarketDaily());
    }

    @Test
    void unexpectedRefreshErrorsSurfaceWithoutMarkingTheDay() {
        WorldData world = new WorldData();
        String oldDate = world.getLastRanMarketDaily();
        assertThrows(IllegalStateException.class, () -> WorldScheduler.refreshIfDue(world,
                () -> { throw new IllegalStateException("Refresh failed"); },
                () -> fail("A failed refresh must not be persisted")));
        assertEquals(oldDate, world.getLastRanMarketDaily());
    }
}
