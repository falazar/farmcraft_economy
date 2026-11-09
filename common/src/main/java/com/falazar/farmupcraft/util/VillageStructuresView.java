package com.falazar.farmupcraft.util;

import java.util.Comparator;
import java.util.List;

public record VillageStructuresView(String name, List<Entry> entries) {
    public static final int MAX_ENTRIES = 512;
    public static final List<String> FILTERS = List.of("all", "claimed", "unclaimed", "unvisited");

    public VillageStructuresView {
        if (name == null || name.length() > 256 || entries.size() > MAX_ENTRIES)
            throw new IllegalArgumentException("Invalid village structures view");
        entries = entries.stream().sorted(Comparator.comparingInt(Entry::distance).thenComparingLong(Entry::id)).toList();
    }

    public List<Entry> filtered(String filter) {
        if (!FILTERS.contains(filter)) throw new IllegalArgumentException("Invalid structure filter: " + filter);
        return entries.stream().filter(e -> switch (filter) {
            case "claimed" -> e.claimed();
            case "unclaimed" -> !e.claimed();
            case "unvisited" -> !e.visited();
            default -> true;
        }).toList();
    }

    public record Entry(long id, String name, String type, int x, int y, int z, int distance,
                        boolean claimed, boolean visited) {
        public Entry {
            if (name == null || name.length() > 256 || type == null || type.length() > 256 || distance < 0)
                throw new IllegalArgumentException("Invalid village structure entry");
        }

        public String coordinates() { return x + ", " + y + ", " + z; }
        public String bearing(double fromX, double fromZ) {
            double dx = x - fromX, dz = z - fromZ;
            if (dx == 0 && dz == 0) return "HERE";
            String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
            int sector = Math.floorMod((int) Math.round(Math.atan2(dx, -dz) / (Math.PI / 4)), 8);
            return directions[sector];
        }
        public String status() { return claimed ? "CLAIMED" : visited ? "VISITED / UNCLAIMED" : "UNVISITED"; }
        public int color() { return claimed ? VillageView.POSITIVE : visited ? 0xFFD879 : VillageView.NORMAL; }
        public String waypointCommand() { return "village structures waypoint " + id; }
    }
}
