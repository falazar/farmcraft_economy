package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PlotOverviewTest {
    @Test
    void nurseryCensusAppearsInScrollableLayoutAtNarrowAndWideSizes() {
        var base = plot("nursery");
        var entries = new ArrayList<>(base.details().entries());
        var census = PlotSpecialInfo.nursery(new NurseryTreeCounter.Counts(5, java.util.Map.of("Almond", 2, "Apple", 3)));
        entries.addAll(census);
        var nursery = new PlotView(new VillageView(base.details().name(), entries), base.dimension(),
                base.x(), base.z(), base.type(), base.buyCost(), base.changeCost(),
                base.canBuy(), base.canChange(), base.types());
        for (int width : List.of(240, 614)) {
            var layout = PlotOverview.layout(nursery, width, PlotOverviewTest::wrap);
            String rendered = String.join("", layout.texts().stream().map(PlotOverview.Text::value).toList());
            for (var entry : census) assertTrue(rendered.contains(entry.label() + ": " + entry.value()));
        }
    }

    private static List<String> wrap(String text, int width) {
        int chars = Math.max(1, width / 6);
        var lines = new ArrayList<String>();
        for (int i = 0; i < text.length(); i += chars)
            lines.add(text.substring(i, Math.min(text.length(), i + chars)));
        return lines;
    }

    private static PlotView plot(String type) {
        return new PlotView(new VillageView("Chunk -2, 3 | Y 64", List.of(
                new VillageView.Entry("Plot type", type, VillageView.NORMAL),
                new VillageView.Entry("Owner", "Robin", VillageView.NORMAL),
                new VillageView.Entry("Plot level", "2 (max upgrade: 4)", VillageView.NORMAL),
                new VillageView.Entry("Plot abilities", "Long ability description ".repeat(30), VillageView.NORMAL),
                new VillageView.Entry("Visitors", "None", VillageView.NORMAL),
                new VillageView.Entry("Protection height", "Y >= 64 (Creative bypass)", VillageView.NORMAL),
                new VillageView.Entry("Biomes", "Plains, Forest", VillageView.NORMAL),
                new VillageView.Entry("Breeding species", "cow", VillageView.NORMAL),
                new VillageView.Entry("Breeding feed", "wheat", VillageView.NORMAL),
                new VillageView.Entry("Breeding rules", "Adults ready; own village pasture; ".repeat(15), VillageView.NORMAL))),
                "minecraft:overworld", -2, 3, type, 215, 108, false, true, List.of("plot", "pasture"));
    }

    @Test
    void wrapsEveryValueWithinPanelsAtNarrowAndWideSizes() {
        for (int width : List.of(240, 320, 359, 360, 480, 614)) {
            var plot = plot("pasture");
            var layout = PlotOverview.layout(plot, width, PlotOverviewTest::wrap);
            for (var panel : layout.panels()) {
                assertTrue(panel.x() >= 0 && panel.y() >= 0);
                assertTrue(panel.x() + panel.width() <= width);
                assertTrue(panel.y() + panel.height() <= layout.height());
            }
            for (var text : layout.texts()) {
                assertTrue(text.value().length() * 6 <= text.width());
                assertTrue(layout.panels().stream().anyMatch(p -> text.x() >= p.x() && text.y() >= p.y()
                        && text.x() + text.width() <= p.x() + p.width() && text.y() + 9 <= p.y() + p.height()));
            }
            String rendered = String.join("", layout.texts().stream().map(PlotOverview.Text::value).toList());
            for (var entry : plot.details().entries())
                assertTrue(rendered.contains(entry.value()), "Must preserve full value for " + entry.label());
            var ability = layout.panels().get(1);
            assertTrue(ability.height() >= 100);
            assertEquals(width >= 360 ? 0 : layout.panels().get(0).height() + 8, ability.y());
            assertEquals(width >= 360 ? (width - 8) / 2 + 8 : 0, ability.x());
        }
    }

    @Test
    void keepsOwnerInlineAndHidesUnupgradeableLevels() {
        for (String type : List.of("plot", "village", "wilderness")) {
            var layout = PlotOverview.layout(plot(type), 614, PlotOverviewTest::wrap);
            assertTrue(layout.texts().get(0).value().contains("Owner: Robin"));
            assertTrue(layout.texts().stream().noneMatch(t -> t.value().startsWith("Level ")));
        }
        var layout = PlotOverview.layout(plot("pasture"), 614, PlotOverviewTest::wrap);
        assertTrue(layout.texts().stream().anyMatch(t -> t.value().startsWith("Level 2")));
        assertTrue(layout.texts().stream().anyMatch(t -> t.value().contains("cow   |   Breeding feed: wheat")));
    }

    @Test
    void movesEmptyVisitorsIntoHeaderButKeepsNamedVisitorsInLandPanel() {
        var empty = plot("plot");
        var layout = PlotOverview.layout(empty, 614, PlotOverviewTest::wrap);
        assertTrue(layout.texts().get(0).value().contains("Owner: Robin  |  Visitors: None"));
        assertEquals(1, layout.texts().stream().filter(t -> t.value().contains("Visitors:")).count());
        var entries = empty.details().entries().stream().map(e -> e.label().equals("Visitors")
                ? new VillageView.Entry("Visitors", "Alex, Steve", e.color()) : e).toList();
        var named = new PlotView(new VillageView(empty.details().name(), entries), empty.dimension(),
                empty.x(), empty.z(), empty.type(), empty.buyCost(), empty.changeCost(),
                empty.canBuy(), empty.canChange(), empty.types());
        layout = PlotOverview.layout(named, 614, PlotOverviewTest::wrap);
        assertFalse(layout.texts().get(0).value().contains("Visitors:"));
        assertTrue(layout.texts().stream().anyMatch(t -> t.value().contains("Visitors: Alex, Steve")));
    }
}
