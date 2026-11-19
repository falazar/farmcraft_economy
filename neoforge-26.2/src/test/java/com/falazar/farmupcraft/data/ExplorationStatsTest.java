package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.data.ExplorationStats.Area;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ExplorationStatsTest {
    @Test
    void refreshesAfterTwentyFourHoursOrBorderChangeAndHandlesClockRollback() {
        var area = new Area(-16, -16, 16, 16);
        long time = 1_000_000;
        var stats = new ExplorationStats(area, 1, time);
        long day = Duration.ofDays(1).toMillis();

        assertTrue(stats.isFresh(area, time + day - 1));
        assertFalse(stats.isFresh(area, time + day));
        assertFalse(stats.isFresh(new Area(-32, -16, 16, 16), time));
        assertFalse(stats.isFresh(new Area(0, -16, 32, 16), time));
        assertFalse(stats.isFresh(area, time - 1));
    }

    @Test
    void countsEdgeChunksWithoutOverflowAndRoundsPercentage() {
        var rimfog = new Area(-5010, -5010, 5011, 5011);
        assertEquals(394384, rimfog.totalChunks());
        assertEquals(14_062_500_000_000L, new Area(-30_000_000, -30_000_000, 30_000_000, 30_000_000).totalChunks());
        var stats = new ExplorationStats(new Area(0, 0, 48, 16), 1, 0);
        assertEquals("Explored (JourneyMap, overworld): 1 / 3 chunks (33%)", stats.summary());
        assertThrows(IllegalArgumentException.class, () -> new ExplorationStats(rimfog, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ExplorationStats(rimfog, rimfog.totalChunks() + 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Area(1, 1, 0, 16));
    }
}
