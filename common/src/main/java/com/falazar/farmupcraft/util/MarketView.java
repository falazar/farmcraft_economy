package com.falazar.farmupcraft.util;

import java.util.Comparator;
import java.util.List;

public record MarketView(String category, int minimumPrice, int coins, List<Entry> entries) {
    public static final List<String> CATEGORIES = List.of("general", "food", "wood", "stone", "high", "favorites");
    public static final int MAX_ENTRIES = 4096;

    public MarketView {
        if (!CATEGORIES.contains(category) || minimumPrice < 1 || entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid market view");
        }
        entries = entries.stream().sorted(Comparator.comparingInt(Entry::price).reversed()
                .thenComparing(Entry::itemId)).toList();
    }

    public String refreshCommand() {
        return "market show " + category + (category.equals("high") ? " " + minimumPrice : "");
    }

    public String minimumPriceCommand(int delta) {
        if (!category.equals("high") || (delta != -1 && delta != 1))
            throw new IllegalArgumentException("Expected a high-market price step of -1 or 1");
        long price = (long) minimumPrice + delta;
        return "market show high " + Math.max(1L, Math.min(Integer.MAX_VALUE, price));
    }

    public record Entry(String itemId, int price, int count, boolean favorite) {
        public Entry(String itemId, int price, int count) {
            this(itemId, price, count, false);
        }

        public String favoriteCommand() {
            return "market favorite \"" + itemId + "\" " + !favorite;
        }

        public Entry {
            if (!itemId.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") || itemId.length() > 256 || price < 0 || count < 0) {
                throw new IllegalArgumentException("Invalid market entry");
            }
        }

        public String sellCommand() {
            return "market sell item \"" + itemId + "\"";
        }

        public String recipeCommand() {
            return "frecipe " + itemId;
        }
    }
}
