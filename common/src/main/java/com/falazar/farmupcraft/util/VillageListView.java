package com.falazar.farmupcraft.util;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record VillageListView(int page, int total, List<Entry> entries) {
    public static final int PAGE_SIZE = 64;
    public static final Comparator<Entry> ORDER = Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Entry::id);

    public VillageListView {
        if (total < 1 || page < 1 || page > (total - 1) / PAGE_SIZE + 1
                || entries.size() != Math.min(PAGE_SIZE, total - (page - 1) * PAGE_SIZE))
            throw new IllegalArgumentException("Invalid village list page");
        entries = List.copyOf(entries);
    }

    public int pages() { return (total - 1) / PAGE_SIZE + 1; }
    public static String pageCommand(int page) { return "village list2 " + page; }

    public record Entry(UUID id, String name, int level, int x, int z, int chunks, boolean bought, boolean home) {
        public Entry {
            if (id == null || name == null || name.isBlank() || name.length() > 256 || level < 1 || chunks < 0)
                throw new IllegalArgumentException("Invalid village list entry");
        }

        public String details() {
            return "Level " + level + " | " + chunks + " chunks | " + x + ", " + z
                    + " | " + (bought ? "Owned" : "Unbought");
        }

        public String infoCommand() {
            return "village info2 \"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }
}
