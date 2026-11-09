package com.falazar.farmupcraft.util;

import java.util.List;

public record VillageView(String name, List<Entry> entries) {
    public static final int MAX_ENTRIES = 32;
    public static final int MAX_TEXT = 8192;
    public static final int NORMAL = 0xFFFFFF;
    public static final int POSITIVE = 0x9EE6A4;
    public static final int NEGATIVE = 0xFF8888;

    public VillageView {
        if (name == null || name.length() > 256 || entries.size() > MAX_ENTRIES)
            throw new IllegalArgumentException("Invalid village view");
        entries = List.copyOf(entries);
    }

    public boolean hasManagementPanels() {
        return entries.stream().anyMatch(e -> e.label().equals("Village treasury"));
    }

    public VillageView publicOverview() {
        return new VillageView(name, entries.stream().filter(e -> !List.of(
                "Village treasury", "Daily income", "Daily upkeep", "Net daily", "Next plot cost",
                "Level-up action").contains(e.label())).toList());
    }

    public static VillageView summary(String name, int level, int radius, int coins, String center,
            int chunks, int plots, int farms, int plotCost, int dailyCost, int claimed, int unclaimed,
            int villagers, String founder, String animal, List<String> members) {
        var numbers = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US);
        int earned = Math.multiplyExact(claimed, 100);
        int net = Math.subtractExact(earned, dailyCost);
        return new VillageView(name, List.of(
                new Entry("Village level", numbers.format(level), NORMAL),
                new Entry("Village treasury", numbers.format(coins) + " coins", coins < 0 ? NEGATIVE : POSITIVE),
                new Entry("Daily income", numbers.format(earned) + " coins", POSITIVE),
                new Entry("Daily upkeep", numbers.format(-(long) dailyCost) + " coins", 0xFFA500),
                new Entry("Net daily", (net >= 0 ? "+" : "") + numbers.format(net) + " coins", net < 0 ? NEGATIVE : POSITIVE),
                new Entry("Next plot cost", numbers.format(plotCost) + " coins", NORMAL),
                new Entry("Claimed structures", numbers.format(claimed), POSITIVE),
                new Entry("Unclaimed structures", numbers.format(unclaimed), NORMAL),
                new Entry("Village range", radius + " chunks", NORMAL),
                new Entry("Claimed chunks", numbers.format(chunks), NORMAL),
                new Entry("Purchased plots", numbers.format(plots), NORMAL),
                new Entry("Farms", numbers.format(farms), NORMAL),
                new Entry("Nearby villagers", numbers.format(villagers), NORMAL),
                new Entry("Founder", founder.isEmpty() ? "Not set" : founder, NORMAL),
                new Entry("Breeding animal", animal.isEmpty() ? "Not set" : animal, NORMAL),
                new Entry("Village center", center, NORMAL),
                new Entry("Members (" + members.size() + ")", members.isEmpty() ? "None" : String.join(", ", members), NORMAL)));
    }

    public record Entry(String label, String value, int color) {
        public Entry {
            if (label == null || label.length() > 64 || value == null || value.length() > MAX_TEXT
                    || color < 0 || color > 0xFFFFFF)
                throw new IllegalArgumentException("Invalid village view entry");
        }
    }
}
