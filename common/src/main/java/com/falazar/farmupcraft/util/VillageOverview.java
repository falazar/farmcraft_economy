package com.falazar.farmupcraft.util;

import java.util.List;

public final class VillageOverview {
    public static final int GOLD = 0xEAC879;
    public static final int MUTED = 0xA7BEC7;
    public static final int WHITE = 0xEDF4ED;
    private static final List<String> QUOTES = List.of(
            "Small seeds. A bright beginning.",
            "Strong roots. Good neighbors.",
            "New fields, new stories.",
            "A crossroads becomes a home.",
            "A place for everyone at the table.",
            "Sturdy roots. Ambitious towers.",
            "Every road brings possibility.",
            "Many stories. One thriving home.",
            "A beacon for those who wander.",
            "Built in stone. Remembered for its people.");

    public interface Canvas {
        void fill(int x, int y, int width, int height, int color);
        void text(String text, int x, int y, int width, int color);
    }

    private VillageOverview() {}

    public static String quote(int level) {
        if (level < 1 || level > 10) throw new IllegalArgumentException("Invalid village level");
        return QUOTES.get(level - 1);
    }

    public static VillageView.Entry field(VillageView view, String label) {
        return view.entries().stream().filter(e -> e.label().equals(label)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing village field: " + label));
    }

    public static int level(VillageView view) {
        int level = Integer.parseInt(field(view, "Village level").value());
        quote(level);
        return level;
    }

    public static int height(int width) { return width >= 360 ? 420 : 678; }

    public record ActionBounds(int x, int y, int width, int height) {}

    public static ActionBounds levelUpBounds(int width) {
        boolean wide = width >= 360;
        int columnWidth = wide ? (width - 8) / 2 : width;
        int progressY = wide ? 116 : 374;
        return new ActionBounds((wide ? columnWidth + 8 : 0) + 10, progressY + 160,
                Math.min(136, columnWidth - 20), 20);
    }

    public static int height(VillageView view, int width) {
        return view.hasManagementPanels() ? height(width) : (width >= 360 ? 228 : 294);
    }

    public static void render(Canvas c, VillageView view, int width) {
        int level = level(view);
        boolean wide = width >= 360;
        int artWidth = wide ? Math.min(172, width / 3) : width - 16;
        int heroHeight = wide ? 108 : 174;
        c.fill(0, 0, width, heroHeight, 0x203D3B);
        c.fill(0, 0, 3, heroHeight, GOLD);
        int infoWidth = wide ? width - artWidth - 28 : width - 24;
        c.text(view.name(), 12, 10, infoWidth, GOLD);
        c.text("LEVEL " + level + "  /  " + (level == 10 ? "A flourishing capital" : "Growing together"),
                12, 26, infoWidth, 0xB4E5AE);
        c.text(value(view, "Purchased plots") + " plots  |  " + value(view, "Farms") + " farms  |  "
                + value(view, "Village range") + " range", 12, 42, infoWidth, WHITE);
        c.text("\"" + quote(level) + "\"", 12, 60, infoWidth, 0xDFD6B2);
        c.text("Founder: " + value(view, "Founder"), 12, 79, infoWidth, MUTED);
        int artX = wide ? width - artWidth - 8 : 8;
        int artY = wide ? 8 : 94;
        illustration(c, artX, artY, artWidth, wide ? 80 : 64, level);
        c.text("Village illustration - level " + level, artX, artY + (wide ? 84 : 68), artWidth, MUTED);

        int sectionY = heroHeight + 8;
        int communityY = sectionY;
        if (view.hasManagementPanels()) {
            int columnWidth = wide ? (width - 8) / 2 : width;
            panel(c, 0, sectionY, columnWidth, 184, "TREASURY & LAND", 0xEAC879);
            stat(c, view, "Village treasury", "Treasury", 10, sectionY + 27, columnWidth - 20);
            stat(c, view, "Daily income", "Earn / day", 10, sectionY + 45, columnWidth - 20);
            stat(c, view, "Daily upkeep", "Upkeep / day", 10, sectionY + 61, columnWidth - 20);
            stat(c, view, "Net daily", "Net / day", 10, sectionY + 79, columnWidth - 20);
            c.fill(10, sectionY + 94, columnWidth - 20, 1, 0x456064);
            stat(c, view, "Next plot cost", "Buy next plot", 10, sectionY + 102, columnWidth - 20);
            c.text("Structures: " + value(view, "Claimed structures") + " claimed / "
                    + value(view, "Unclaimed structures") + " unclaimed", 10, sectionY + 121, columnWidth - 20, 0xB4E5AE);
            c.text(value(view, "Claimed chunks") + " claimed chunks", 10, sectionY + 137, columnWidth - 20, MUTED);

            int progressX = wide ? columnWidth + 8 : 0;
            int progressY = wide ? sectionY : sectionY + 192;
            panel(c, progressX, progressY, columnWidth, 184, level == 10 ? "A LASTING LEGACY" : "THE NEXT CHAPTER", 0x91CEED);
            if (level == 10) {
                c.text("Maximum village level reached", progressX + 10, progressY + 29, columnWidth - 20, GOLD);
                c.text("Keep building your story.", progressX + 10, progressY + 49, columnWidth - 20, WHITE);
                c.text("Welcome neighbors. Connect the land.", progressX + 10, progressY + 70, columnWidth - 20, MUTED);
            } else {
                c.text("Level " + level + " -> " + (level + 1) + " requirements", progressX + 10, progressY + 27, columnWidth - 20, GOLD);
                c.text("Cost: " + level * 300 + " coins", progressX + 10, progressY + 45, columnWidth - 20, WHITE);
                int villagers = Integer.parseInt(value(view, "Nearby villagers").split(" ", 2)[0].replace(",", ""));
                int claimed = Integer.parseInt(value(view, "Claimed structures").replace(",", ""));
                requirement(c, progressX + 10, progressY + 64, columnWidth - 20, "Villagers", villagers, (level + 1) * 2);
                requirement(c, progressX + 10, progressY + 88, columnWidth - 20, "Structures", claimed, (level + 1) * 2 - 2);
                c.text("Range: " + (2 + 3 * (level + 1)) + " chunks (+3)",
                        progressX + 10, progressY + 117, columnWidth - 20, 0xB4E5AE);
                int currentCap = Math.min(4, level / 2), nextCap = Math.min(4, (level + 1) / 2);
                String unlock = level + 1 == 7 ? "Unlock: 2x stone/dirt mining in bought plots"
                        : nextCap > currentCap ? "Unlock: plot upgrades to level " + nextCap
                        : "Plot upgrade cap unchanged (" + currentCap + ")";
                c.text(unlock, progressX + 10, progressY + 132, columnWidth - 20, MUTED);
                c.text("Upkeep: -100/day extra", progressX + 10, progressY + 144, columnWidth - 20, 0xFFA500);
            }
            communityY = (wide ? sectionY : progressY) + 192;
        }
        community(c, view, width, communityY);
    }

    private static void community(Canvas c, VillageView view, int width, int y) {
        panel(c, 0, y, width, 104, "OUR COMMUNITY", 0xB4E5AE);
        var members = view.entries().stream().filter(e -> e.label().startsWith("Members (")).findFirst().orElseThrow();
        c.text("Villagers: " + value(view, "Nearby villagers") + "  |  Breeding: "
                + value(view, "Breeding animal"), 10, y + 27, width - 20, 0xB4E5AE);
        c.text(members.label() + ": " + members.value(), 10, y + 45, width - 20, WHITE);
        c.text("Center: " + value(view, "Village center"), 10, y + 63, width - 20, MUTED);
        if (!view.hasManagementPanels()) {
            c.text(value(view, "Claimed structures") + " claimed / " + value(view, "Unclaimed structures")
                    + " unclaimed structures", 10, y + 83, width - 20, MUTED);
        }
    }

    private static String value(VillageView view, String label) { return field(view, label).value(); }

    private static void panel(Canvas c, int x, int y, int w, int h, String title, int accent) {
        c.fill(x, y, w, h, 0x22343E);
        c.fill(x, y, w, 20, 0x2B4650);
        c.fill(x, y, 3, 20, accent);
        c.text(title, x + 10, y + 6, w - 20, accent);
    }

    private static void stat(Canvas c, VillageView view, String key, String label, int x, int y, int width) {
        var entry = field(view, key);
        int labelWidth = Math.min(87, width / 2);
        c.text(label, x, y, labelWidth, MUTED);
        c.text(entry.value(), x + labelWidth + 4, y, width - labelWidth - 4, entry.color());
    }

    private static void requirement(Canvas c, int x, int y, int width, String label, int have, int need) {
        int color = have >= need ? 0xB4E5AE : GOLD;
        c.text(label + ": " + have + " / " + need, x, y, width, color);
        c.fill(x, y + 12, width, 3, 0x14252D);
        c.fill(x, y + 12, (int) (width * Math.min(1.0, (double) have / need)), 3, color);
    }

    private static void illustration(Canvas c, int x, int y, int w, int h, int level) {
        c.fill(x, y, w, h, 0x7197A3);
        c.fill(x + w - 25, y + 7, 12, 12, 0xF5D888);
        c.fill(x, y + h / 2, w, h / 2, 0x506D45);
        c.fill(x, y + h * 2 / 3, w, h / 3, 0x668651);
        c.fill(x + w / 2 - 5, y + h / 2, 10, h / 2, 0xC0AB78);
        c.fill(x + 5, y + h - 17, w / 4, 12, 0xA5A04C);
        for (int i = 0; i < 4; i++) c.fill(x + 7, y + h - 15 + i * 3, w / 4 - 4, 1, 0xD5BA61);
        int houses = Math.min(7, level + 1);
        for (int i = 0; i < houses; i++) {
            int hx = x + 8 + i % 4 * (w - 30) / 4;
            int hy = y + h / 2 - 6 + i / 4 * 19;
            c.fill(hx, hy, 17, 15, 0xDBC598);
            for (int roof = 0; roof < 5; roof++)
                c.fill(hx - 3 + roof * 2, hy - roof - 1, 23 - roof * 4, 1, 0x865147);
            c.fill(hx + 3, hy + 4, 4, 4, 0xF3DB8A);
            c.fill(hx + 11, hy + 7, 4, 8, 0x624E3A);
        }
        if (level >= 4) {
            c.fill(x + w - 28, y + 20, 16, h / 2, 0xB3BAB2);
            c.fill(x + w - 31, y + 17, 22, 5, 0x687D80);
            c.fill(x + w - 23, y + 27, 5, 7, 0xF3DB8A);
        }
        if (level >= 7) {
            c.fill(x + w - 45, y + h / 2, 6, 18, 0x746248);
            c.fill(x + w - 51, y + h / 2 - 8, 18, 13, 0x345D43);
        }
        if (level >= 8) c.fill(x + 4, y + h - 4, w - 8, 3, 0xB3BAB2);
        if (level >= 9) c.fill(x + w - 10, y + 20, 5, h / 2, 0xB3BAB2);
        if (level == 10) {
            c.fill(x + w - 21, y + 4, 1, 13, 0xDED5A5);
            c.fill(x + w - 20, y + 4, 9, 5, GOLD);
        }
    }
}
