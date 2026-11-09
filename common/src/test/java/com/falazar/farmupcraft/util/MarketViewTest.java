package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MarketViewTest {
    @Test
    void favoritesExposeExplicitAddAndRemoveCommands() {
        assertTrue(MarketView.CATEGORIES.contains("favorites"));
        assertEquals("market favorite \"minecraft:apple\" true",
                new MarketView.Entry("minecraft:apple", 5, 1).favoriteCommand());
        assertEquals("market favorite \"minecraft:apple\" false",
                new MarketView.Entry("minecraft:apple", 5, 1, true).favoriteCommand());
        assertEquals("market show favorites", new MarketView("favorites", 10, 0, List.of()).refreshCommand());
    }

    @Test
    void sortsByDescendingPriceThenIdAndPreservesServerValues() {
        var input = new ArrayList<>(List.of(
                new MarketView.Entry("minecraft:carrot", 4, 3),
                new MarketView.Entry("minecraft:apple", 4, 2),
                new MarketView.Entry("minecraft:diamond", 50, 0)));
        var view = new MarketView("food", 10, -5, input);
        input.clear();
        assertEquals(List.of("minecraft:diamond", "minecraft:apple", "minecraft:carrot"),
                view.entries().stream().map(MarketView.Entry::itemId).toList());
        assertEquals(-5, view.coins());
        assertEquals(2, view.entries().get(1).count());
        assertThrows(UnsupportedOperationException.class, () -> view.entries().clear());
    }

    @Test
    void commandsPreserveCategoryThresholdAndQuoteSaleIds() {
        for (String category : MarketView.CATEGORIES) {
            assertEquals("market show " + category + (category.equals("high") ? " 23" : ""),
                    new MarketView(category, 23, 100, List.of()).refreshCommand());
        }
        var entry = new MarketView.Entry("minecraft:apple", 0, 0);
        assertEquals("market sell item \"minecraft:apple\"", entry.sellCommand());
        assertEquals("frecipe minecraft:apple", entry.recipeCommand());
    }

    @Test
    void highPriceStepsRefreshExactlyOneCoinAndClampAtBothBounds() {
        var high = new MarketView("high", 10, 100, List.of());
        assertEquals("market show high 9", high.minimumPriceCommand(-1));
        assertEquals("market show high 11", high.minimumPriceCommand(1));
        assertEquals("market show high 1", new MarketView("high", 1, 0, List.of()).minimumPriceCommand(-1));
        assertEquals("market show high 2147483647",
                new MarketView("high", Integer.MAX_VALUE, 0, List.of()).minimumPriceCommand(1));
        assertThrows(IllegalArgumentException.class, () -> high.minimumPriceCommand(0));
        assertThrows(IllegalArgumentException.class,
                () -> new MarketView("food", 10, 0, List.of()).minimumPriceCommand(1));
    }

    @Test
    void rejectsInvalidAndOversizedSnapshots() {
        assertThrows(IllegalArgumentException.class, () -> new MarketView("other", 10, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MarketView("food", 0, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MarketView.Entry("minecraft:apple\"", 5, 1));
        assertThrows(IllegalArgumentException.class, () -> new MarketView.Entry("minecraft:apple", -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MarketView.Entry("minecraft:apple", 1, -1));
        assertThrows(IllegalArgumentException.class, () -> new MarketView("food", 10, 0,
                java.util.Collections.nCopies(MarketView.MAX_ENTRIES + 1,
                        new MarketView.Entry("minecraft:apple", 1, 0))));
    }
}
