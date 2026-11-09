package com.falazar.farmupcraft.util;

import java.util.List;

public final class GeneralMenu {
    public static final List<Entry> ENTRIES = List.of(
            new Entry("Village overview", "Village details and progression", "village info2"),
            new Entry("Village list", "Browse villages and open their dashboards", "village list2"),
            new Entry("Plot info", "Details and abilities for your current plot", "plot info2"),
            new Entry("Market", "Browse prices and sell your items", "market show"),
            new Entry("Village structures", "Browse structures and set waypoints", "village structures2"),
            new Entry("Player info / wallet", "Show your player details and coins in chat", "player info"));

    private GeneralMenu() {}

    public record Entry(String label, String description, String command) {}
}
