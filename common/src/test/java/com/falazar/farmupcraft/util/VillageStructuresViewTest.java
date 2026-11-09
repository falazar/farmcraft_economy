package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VillageStructuresViewTest {
    private static VillageStructuresView.Entry entry(long id, int distance, boolean claimed, boolean visited) {
        return new VillageStructuresView.Entry(id, "Ruin", "test:ruin", -10, 64, 20, distance, claimed, visited);
    }

    @Test
    void sortsNearestFirstAndFiltersWithoutLosingIds() {
        var view = new VillageStructuresView("Village",
                List.of(entry(3, 30, false, false), entry(2, 10, false, true), entry(1, 10, true, true)));
        assertEquals(List.of(1L, 2L, 3L), view.filtered("all").stream().map(VillageStructuresView.Entry::id).toList());
        assertEquals(List.of(1L), view.filtered("claimed").stream().map(VillageStructuresView.Entry::id).toList());
        assertEquals(List.of(2L, 3L), view.filtered("unclaimed").stream().map(VillageStructuresView.Entry::id).toList());
        assertEquals(List.of("all", "claimed", "unclaimed", "unvisited"), VillageStructuresView.FILTERS);
        assertEquals(List.of(3L), view.filtered("unvisited").stream().map(VillageStructuresView.Entry::id).toList());
        assertThrows(IllegalArgumentException.class, () -> view.filtered("unknown"));
        assertThrows(UnsupportedOperationException.class, () -> view.entries().clear());
    }

    @Test
    void labelsStatusesAndBuildsStableWaypointCommand() {
        assertEquals("CLAIMED", entry(1, 1, true, true).status());
        assertEquals("VISITED / UNCLAIMED", entry(2, 2, false, true).status());
        assertEquals("UNVISITED", entry(3, 3, false, false).status());
        assertEquals("-10, 64, 20", entry(1, 1, true, true).coordinates());
        assertEquals("village structures waypoint -9", entry(-9, 1, false, false).waypointCommand());
        assertEquals(VillageView.POSITIVE, entry(1, 1, true, true).color());
    }

    @Test
    void compassBearingsUseMinecraftNorthAndAllEightDirections() {
        int[][] offsets = {{0,-10},{10,-10},{10,0},{10,10},{0,10},{-10,10},{-10,0},{-10,-10}};
        String[] expected = {"N","NE","E","SE","S","SW","W","NW"};
        for (int i = 0; i < offsets.length; i++) {
            var e = new VillageStructuresView.Entry(1, "Ruin", "test:ruin",
                    100 + offsets[i][0], 64, -100 + offsets[i][1], 135, false, false);
            assertEquals(expected[i], e.bearing(100, -100));
            assertEquals("HERE", e.bearing(e.x(), e.z()));
        }
    }

    @Test
    void supportsEmptyAndLargeListsWithoutSilentTruncation() {
        assertTrue(new VillageStructuresView("Empty", List.of()).entries().isEmpty());
        var entries = java.util.Collections.nCopies(VillageStructuresView.MAX_ENTRIES, entry(1, 1, false, false));
        assertEquals(VillageStructuresView.MAX_ENTRIES, new VillageStructuresView("Full", entries).entries().size());
        assertThrows(IllegalArgumentException.class, () -> new VillageStructuresView("Too large",
                java.util.Collections.nCopies(VillageStructuresView.MAX_ENTRIES + 1, entry(1, 1, false, false))));
        assertThrows(IllegalArgumentException.class, () -> entry(1, -1, false, false));
    }
}
