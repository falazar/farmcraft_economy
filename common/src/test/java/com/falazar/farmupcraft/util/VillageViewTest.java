package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VillageViewTest {
    @Test
    void summaryPreservesStatsAndColorsBalances() {
        var view = VillageView.summary("Rimfog", 4, 5, -200, "10, 64, -20",
                81, 12, 3, 380, 350, 2, 5, 9, "Robin", "cow", List.of("LazzyMars", "BossPanda96366"));
        assertEquals("Rimfog", view.name());
        assertEquals(17, view.entries().size());
        assertEquals(new VillageView.Entry("Village treasury", "-200 coins", VillageView.NEGATIVE), view.entries().get(1));
        assertEquals(new VillageView.Entry("Daily income", "200 coins", VillageView.POSITIVE), view.entries().get(2));
        assertEquals(new VillageView.Entry("Daily upkeep", "-350 coins", 0xFFA500), view.entries().get(3));
        assertEquals(new VillageView.Entry("Net daily", "-150 coins", VillageView.NEGATIVE), view.entries().get(4));
        assertEquals("10, 64, -20", view.entries().get(15).value());
        assertEquals("LazzyMars, BossPanda96366", view.entries().get(16).value());
    }

    @Test
    void handlesEmptyCommunityAndPositiveNet() {
        var view = VillageView.summary("Test", 1, 1, 0, "0, 0, 0",
                9, 0, 0, 200, 100, 2, 0, 0, "", "", List.of());
        assertEquals("+100 coins", view.entries().get(4).value());
        assertEquals(VillageView.POSITIVE, view.entries().get(4).color());
        assertEquals("Not set", view.entries().get(13).value());
        assertEquals("Not set", view.entries().get(14).value());
        assertEquals("None", view.entries().get(16).value());
    }

    @Test
    void boundsAndImmutabilityAreEnforced() {
        var source = new java.util.ArrayList<>(List.of(new VillageView.Entry("Level", "1", VillageView.NORMAL)));
        var view = new VillageView("Test", source);
        source.clear();
        assertEquals(1, view.entries().size());
        assertThrows(UnsupportedOperationException.class, () -> view.entries().clear());
        assertThrows(IllegalArgumentException.class, () -> new VillageView.Entry("x", "x".repeat(VillageView.MAX_TEXT + 1), 0));
        assertThrows(IllegalArgumentException.class, () -> new VillageView.Entry("x", "x", -1));
        assertThrows(IllegalArgumentException.class, () -> new VillageView("x", java.util.Collections.nCopies(
                VillageView.MAX_ENTRIES + 1, new VillageView.Entry("x", "x", 0))));
    }
}
