package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeneralMenuTest {
    @Test
    void providesDashboardShortcutsIncludingVillageDirectory() {
        assertEquals(List.of("Village overview", "Village list", "Plot info", "Market", "Village structures", "Player info / wallet"),
                GeneralMenu.ENTRIES.stream().map(GeneralMenu.Entry::label).toList());
        assertEquals(List.of("village info2", "village list2", "plot info2", "market show", "village structures2", "player info"),
                GeneralMenu.ENTRIES.stream().map(GeneralMenu.Entry::command).toList());
        assertTrue(GeneralMenu.ENTRIES.stream().allMatch(entry -> !entry.description().isBlank()));
        assertThrows(UnsupportedOperationException.class, () -> GeneralMenu.ENTRIES.clear());
    }
}
