package com.falazar.farmupcraft.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

public final class PlotOverview {
    private static final Set<String> HEADER = Set.of("Plot type", "Owner", "Plot level", "Plot abilities");
    private static final Set<String> LAND = Set.of("Visitors", "Protection height", "Biomes");
    private static final int GOLD = 0xEAC879;
    private static final int GREEN = 0xB4E5AE;
    private static final int WHITE = 0xEDF4ED;

    public record Panel(int x, int y, int width, int height, int color, int accent) {}
    public record Text(String value, int x, int y, int width, int color) {}
    public record Layout(int height, List<Panel> panels, List<Text> texts) {
        public Layout {
            panels = List.copyOf(panels);
            texts = List.copyOf(texts);
        }

        public void render(VillageOverview.Canvas canvas) {
            for (var p : panels) {
                canvas.fill(p.x(), p.y(), p.width(), p.height(), p.color());
                canvas.fill(p.x(), p.y(), 3, p.height(), p.accent());
            }
            for (var t : texts) canvas.text(t.value(), t.x(), t.y(), t.width(), t.color());
        }
    }

    private PlotOverview() {}

    public static Layout layout(PlotView plot, int width, BiFunction<String, Integer, List<String>> wrap) {
        if (width < 120) throw new IllegalArgumentException("Plot panel too narrow");
        var panels = new ArrayList<Panel>();
        var texts = new ArrayList<Text>();
        boolean wide = width >= 360;
        int column = wide ? (width - 8) / 2 : width;
        var entries = plot.details().entries();
        String owner = entries.stream().filter(e -> e.label().equals("Owner")).map(VillageView.Entry::value)
                .findFirst().orElse("Unclaimed");
        boolean noVisitors = entries.stream().anyMatch(e -> e.label().equals("Visitors")
                && e.value().equalsIgnoreCase("None"));
        int y = add(texts, wrap, plot.type().toUpperCase(java.util.Locale.ROOT) + "  |  Owner: " + owner
                        + (noVisitors ? "  |  Visitors: None" : ""),
                12, 12, column - 24, GOLD);
        y = add(texts, wrap, plot.details().name(), 12, y + 3, column - 24, 0xA7BEC7);
        for (var entry : entries) {
            if (entry.label().equals("Plot level") && !Set.of("plot", "village", "wilderness").contains(plot.type()))
                y = add(texts, wrap, "Level " + entry.value(), 12, y + 3, column - 24, GREEN);
        }
        int headerHeight = Math.max(100, y + 12);
        int abilityX = wide ? column + 8 : 0;
        int abilityY = wide ? 0 : headerHeight + 8;
        int abilityWidth = width - abilityX;
        int end = add(texts, wrap, "PLOT ABILITIES", abilityX + 12, abilityY + 12, abilityWidth - 24, GREEN);
        String abilities = entries.stream().filter(e -> e.label().equals("Plot abilities"))
                .map(VillageView.Entry::value).findFirst().orElseThrow();
        end = add(texts, wrap, abilities, abilityX + 12, end + 7, abilityWidth - 24, WHITE);
        int abilityHeight = Math.max(100, end - abilityY + 12);
        if (wide) headerHeight = Math.max(headerHeight, abilityHeight);
        panels.add(new Panel(0, 0, column, headerHeight, 0x203D3B, GOLD));
        panels.add(new Panel(abilityX, abilityY, abilityWidth, wide ? headerHeight : abilityHeight, 0x283D49, GREEN));
        y = wide ? headerHeight + 8 : abilityY + abilityHeight + 8;

        var land = entries.stream().filter(e -> LAND.contains(e.label())
                && !(noVisitors && e.label().equals("Visitors"))).toList();
        if (!land.isEmpty()) {
            int start = y;
            y = add(texts, wrap, "LAND & ACCESS", 12, y + 12, width - 24, GOLD);
            String compact = String.join("   |   ", land.stream().filter(e -> !e.label().equals("Biomes"))
                    .map(e -> e.label() + ": " + e.value()).toList());
            if (!compact.isEmpty()) y = add(texts, wrap, compact, 12, y + 7, width - 24, WHITE);
            for (var entry : land) {
                if (entry.label().equals("Biomes"))
                    y = add(texts, wrap, "Biomes: " + entry.value(), 12, y + 5, width - 24, GREEN);
            }
            panels.add(new Panel(0, start, width, y + 12 - start, 0x22343E, GOLD));
            y += 20;
        }
        var special = entries.stream().filter(e -> !HEADER.contains(e.label()) && !LAND.contains(e.label())).toList();
        if (!special.isEmpty()) {
            int start = y;
            y = add(texts, wrap, "LIFE ON THIS " + plot.type().toUpperCase(java.util.Locale.ROOT), 12, y + 12, width - 24, GREEN);
            for (int i = 0; i < special.size(); i++) {
                var entry = special.get(i);
                String line = entry.label() + ": " + entry.value();
                while (i + 1 < special.size()) {
                    var next = special.get(i + 1);
                    String combined = line + "   |   " + next.label() + ": " + next.value();
                    if (next.color() != entry.color() || wrap.apply(combined, width - 24).size() != 1) break;
                    line = combined;
                    i++;
                }
                y = add(texts, wrap, line, 12, y + 7, width - 24, entry.color());
            }
            panels.add(new Panel(0, start, width, y + 12 - start, 0x233B35, GREEN));
            y += 20;
        }
        return new Layout(y, panels, texts);
    }

    private static int add(List<Text> texts, BiFunction<String, Integer, List<String>> wrap,
            String value, int x, int y, int width, int color) {
        for (String line : wrap.apply(value, width)) {
            texts.add(new Text(line, x, y, width, color));
            y += 12;
        }
        return y;
    }
}
