package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlotSpecialInfoTest {
    @Test
    void nurseryShowsTotalSortedTypesAndExplicitScanRules() {
        var entries = PlotSpecialInfo.nursery(new NurseryTreeCounter.Counts(3,
                java.util.Map.of("Apple", 1, "Almond", 2)));
        assertEquals("3", entries.get(0).value());
        assertEquals("Almond: 2, Apple: 1", entries.get(1).value());
        assertTrue(entries.get(2).value().contains("full world height"));
        assertEquals("None found", PlotSpecialInfo.nursery(
                new NurseryTreeCounter.Counts(0, java.util.Map.of())).get(1).value());
    }

    @Test
    void farmContainsCompactBonusTrampleBiomeGrowthAndScanInfo() {
        var entries = PlotSpecialInfo.farm(2, 20, true, "Wheat (8)", 64);
        assertEquals(7, entries.size());
        assertEquals("Wheat (8)", entries.get(0).value());
        assertEquals("Y 63 through 66 in this chunk", entries.get(1).value());
        assertTrue(entries.get(2).value().startsWith("20%"));
        assertTrue(entries.stream().noneMatch(e -> e.label().equals("Harvest scope")));
        assertTrue(entries.get(3).value().contains("planting location"));
        assertEquals("Protection at level 2", entries.get(4).value());
        assertEquals("No bonus", entries.get(5).value());
        assertTrue(PlotSpecialInfo.farm(3, 40, true, "", 64).get(3).value().contains("any biome"));
        assertEquals("None found", PlotSpecialInfo.farm(1, 0, false, "", 64).get(0).value());
        assertEquals("None", PlotSpecialInfo.farm(1, 0, false, "", 64).get(2).value());
        assertEquals("Biome rules disabled or bypassed", PlotSpecialInfo.farm(1, 0, false, "", 64).get(3).value());
    }

    @Test
    void pastureContainsCapacityBreakdownFeedAndAllLevelBonuses() {
        var entries = PlotSpecialInfo.pasture(30, 30, "Cow: 30", 2, 60, "cow", "Wheat");
        assertEquals(7, entries.size());
        assertEquals("30 / 30 - FULL", entries.get(0).value());
        assertEquals(VillageView.NEGATIVE, entries.get(0).color());
        assertEquals("Cow: 30", entries.get(1).value());
        assertEquals("cow", entries.get(2).value());
        assertEquals("Wheat", entries.get(3).value());
        assertEquals("Own village pasture; below animal cap; once per real day per animal", entries.get(4).value());
        assertEquals("Pasture only, once per real day", entries.get(5).value());
        assertEquals("+2 per loot stack from eligible pasture animals", entries.get(6).value());
        assertTrue(entries.stream().noneMatch(e -> java.util.Set.of("Sheep dye success", "Egg hatching",
                "Wool regrowth", "Creative exceptions").contains(e.label())));
        assertTrue(entries.stream().noneMatch(e -> e.label().equals("Animal scan height")));
        var empty = PlotSpecialInfo.pasture(0, 30, "", 0, 20, "", "Not assigned");
        assertEquals("0 / 30 - 30 spaces", empty.get(0).value());
        assertEquals(VillageView.POSITIVE, empty.get(0).color());
        assertEquals("None found", empty.get(1).value());
        assertEquals(5, empty.size());
    }

    @Test
    void pastureRulesFollowSelectedSpeciesAndActualAnimalsWithoutLocalizedNameParsing() {
        var sheep = PlotSpecialInfo.pasture(1, 30, "Localized animal: 1", 2, 60, "sheep", "Wheat");
        assertTrue(sheep.stream().anyMatch(e -> e.label().equals("Sheep dye success")));
        assertTrue(sheep.stream().anyMatch(e -> e.value().startsWith("+2 wool")));
        assertTrue(sheep.stream().noneMatch(e -> e.label().equals("Milk rules") || e.label().equals("Egg hatching")));
        for (String bird : java.util.List.of("chicken", "duck")) {
            var entries = PlotSpecialInfo.pasture(0, 30, "", 0, 20, bird, "Grain");
            assertTrue(entries.stream().anyMatch(e -> e.label().equals("Egg hatching")));
            assertTrue(entries.stream().noneMatch(e -> e.label().equals("Milk rules")
                    || e.label().equals("Sheep dye success") || e.label().equals("Pasture drop bonus")));
        }
        var mixed = PlotSpecialInfo.pasture(3, 30, "Localized names", 1, 40, "pig", "Feed",
                java.util.Set.of("cow", "sheep", "duck"));
        assertTrue(mixed.stream().anyMatch(e -> e.label().equals("Milk rules")));
        assertTrue(mixed.stream().anyMatch(e -> e.label().equals("Sheep dye success")));
        assertTrue(mixed.stream().anyMatch(e -> e.label().equals("Egg hatching")));
        assertTrue(mixed.stream().noneMatch(e -> e.label().equals("Wool regrowth")
                || e.label().equals("Creative exceptions") || e.value().contains("real-world")));
    }
}
