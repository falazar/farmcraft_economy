package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VillageOverviewTest {
    private static VillageView view(int level) {
        return VillageView.summary("Mykos", level, 2 + level * 3, 0, "0, 64, 0",
                169, 17, 3, 455, level * 100 + 255, 2, 3, 5, "Robin", "cow", List.of("First", "Second"));
    }

    private static final class Recording implements VillageOverview.Canvas {
        final List<String> text = new ArrayList<>();
        final List<String> fills = new ArrayList<>();
        final int width, height;
        Recording(int width) { this(width, VillageOverview.height(width)); }
        Recording(int width, int height) { this.width = width; this.height = height; }
        @Override
        public void fill(int x, int y, int w, int h, int color) {
            assertTrue(x >= 0 && y >= 0 && w >= 0 && h >= 0 && x + w <= width && y + h <= height,
                    "Rectangle must stay within the scrollable content: " + x + "," + y + "," + w + "," + h);
            fills.add(x + "," + y + "," + w + "," + h + "," + color);
        }
        @Override
        public void text(String value, int x, int y, int w, int color) {
            assertTrue(x >= 0 && y >= 0 && w > 0 && x + w <= width && y + 9 <= height);
            text.add(value);
        }
    }

    @Test
    void layoutFitsNarrowAndWideScreensAcrossAllVillageLevels() {
        for (int width : List.of(240, 320, 359, 360, 400, 614)) {
            for (int level = 1; level <= 10; level++) {
                var c = new Recording(width);
                VillageOverview.render(c, view(level), width);
                assertTrue(c.text.contains("Treasury"));
                assertTrue(c.text.contains("Buy next plot"));
                assertTrue(c.text.contains("Structures: 2 claimed / 3 unclaimed"));
                assertTrue(c.text.contains("Members (2): First, Second"));
                assertTrue(c.text.contains("Villagers: 5  |  Breeding: cow"));
                assertTrue(c.text.stream().noneMatch(s -> s.contains("snapshot") || s.contains("from your wallet")));
                assertTrue(c.text.contains("Center: 0, 64, 0"));
                assertTrue(c.text.contains("\"" + VillageOverview.quote(level) + "\""));
                var button = VillageOverview.levelUpBounds(width);
                int progressY = width >= 360 ? 116 : 374;
                assertTrue(button.x() >= (width >= 360 ? (width - 8) / 2 + 8 : 0));
                assertTrue(button.x() + button.width() <= width);
                assertTrue(button.y() > progressY + 153);
                assertTrue(button.y() + button.height() <= progressY + 184);
                assertTrue(button.y() + button.height() < progressY + 192);
            }
        }
    }

    @Test
    void progressionShowsActualRequirementsWalletCostAndSpecificUnlocks() {
        var c = new Recording(500);
        VillageOverview.render(c, view(2), 500);
        assertTrue(c.text.contains("Level 2 -> 3 requirements"));
        assertTrue(c.text.contains("Cost: 600 coins"));
        assertTrue(c.text.contains("Villagers: 5 / 6"));
        assertTrue(c.text.contains("Structures: 2 / 4"));
        assertTrue(c.text.contains("Range: 11 chunks (+3)"));
        assertTrue(c.text.contains("Upkeep: -100/day extra"));
        var next = new Recording(500);
        VillageOverview.render(next, view(3), 500);
        assertTrue(next.text.contains("Unlock: plot upgrades to level 2"));
        var six = new Recording(500);
        VillageOverview.render(six, view(6), 500);
        assertTrue(six.text.contains("Unlock: 2x stone/dirt mining in bought plots"));
        var max = new Recording(500);
        VillageOverview.render(max, view(10), 500);
        assertTrue(max.text.contains("Maximum village level reached"));
        assertTrue(max.text.stream().noneMatch(s -> s.contains("Cost:") || s.contains("->  11")));
    }

    @Test
    void otherVillageViewsHideManagementPanelsAndFinancialFieldsWithoutEmptySpace() {
        for (int width : List.of(240, 359, 360, 614)) {
            for (int level : List.of(2, 10)) {
                var publicView = view(level).publicOverview();
                assertFalse(publicView.hasManagementPanels());
                assertThrows(IllegalArgumentException.class,
                        () -> VillageOverview.field(publicView, "Village treasury"));
                assertThrows(IllegalArgumentException.class,
                        () -> VillageOverview.field(publicView, "Next plot cost"));
                var c = new Recording(width, VillageOverview.height(publicView, width));
                VillageOverview.render(c, publicView, width);
                assertTrue(c.text.contains("OUR COMMUNITY"));
                assertTrue(c.text.contains("2 claimed / 3 unclaimed structures"));
                assertFalse(c.text.contains("TREASURY & LAND"));
                assertFalse(c.text.contains("THE NEXT CHAPTER"));
                assertFalse(c.text.contains("A LASTING LEGACY"));
                assertTrue(c.text.stream().noneMatch(s -> s.startsWith("Cost:") || s.equals("Treasury")));
                assertTrue(VillageOverview.height(publicView, width) < VillageOverview.height(width));
            }
        }
    }

    @Test
    void eachLevelHasDistinctQuoteAndArtworkAndMissingFieldsFailExplicitly() {
        var quotes = new HashSet<String>();
        var drawings = new HashSet<List<String>>();
        for (int level = 1; level <= 10; level++) {
            quotes.add(VillageOverview.quote(level));
            var c = new Recording(500);
            VillageOverview.render(c, view(level), 500);
            drawings.add(List.copyOf(c.fills));
        }
        assertEquals(10, quotes.size());
        assertEquals(10, drawings.size());
        assertThrows(IllegalArgumentException.class, () -> VillageOverview.quote(0));
        assertThrows(IllegalArgumentException.class, () -> VillageOverview.quote(11));
        assertThrows(IllegalArgumentException.class, () -> VillageOverview.level(new VillageView("Empty", List.of())));
    }
}
