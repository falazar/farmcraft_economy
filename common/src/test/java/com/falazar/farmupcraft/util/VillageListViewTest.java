package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class VillageListViewTest {
    private VillageListView.Entry entry(String name) {
        return new VillageListView.Entry(UUID.randomUUID(), name, 2, -32, 48, 3, true, false);
    }

    @Test
    void alphabeticalOrderingAndQuotedDashboardCommands() {
        var entries = List.of(entry("Zulu"), entry("alpha"), entry("Beta")).stream()
                .sorted(VillageListView.ORDER).toList();
        assertEquals(List.of("alpha", "Beta", "Zulu"), entries.stream().map(VillageListView.Entry::name).toList());
        assertEquals("village info2 \"A \\\"quoted\\\" \\\\ village\"", entry("A \"quoted\" \\ village").infoCommand());
        assertEquals("Level 2 | 3 chunks | -32, 48 | Owned", entry("Test").details());
        assertEquals("village list2 2", VillageListView.pageCommand(2));
    }

    @Test
    void pagesAreBoundedWithoutDroppingLargerDirectories() {
        var full = IntStream.range(0, 64).mapToObj(i -> entry("Village " + i)).toList();
        var first = new VillageListView(1, 65, full);
        var second = new VillageListView(2, 65, List.of(entry("Last")));
        assertEquals(2, first.pages());
        assertEquals(2, second.pages());
        assertEquals(1, new VillageListView(1, 64, full).pages());
        assertThrows(UnsupportedOperationException.class, () -> first.entries().clear());
        assertThrows(IllegalArgumentException.class, () -> new VillageListView(0, 65, full));
        assertThrows(IllegalArgumentException.class, () -> new VillageListView(3, 65, full));
        assertThrows(IllegalArgumentException.class, () -> new VillageListView(1, 65, List.of(entry("Incomplete"))));
        assertThrows(IllegalArgumentException.class, () -> new VillageListView(1, 0, List.of()));
    }
}
