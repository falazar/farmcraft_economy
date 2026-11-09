package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PlotViewTest {
    private static PlotView view(int cost) {
        return new PlotView(new VillageView("Chunk", List.of()), "minecraft:overworld", -2, 3, "plot",
                cost, cost, true, true, List.of("plot", "road"));
    }

    @Test
    void actionsCarryLocationPriceAndPreviousType() {
        assertEquals("plot gui buy \"minecraft:overworld\" -2 3 215 plot road", view(215).actionCommand(true, "road"));
        assertEquals("plot gui settype \"minecraft:overworld\" -2 3 215 plot road", view(215).actionCommand(false, "road"));
        assertEquals("plot gui settype \"minecraft:overworld\" -2 3 0 plot plot", view(215).actionCommand(false, "plot"));
        assertEquals("plot gui buy \"minecraft:overworld\" -2 3 0 plot road", view(0).actionCommand(true, "road"));
        assertThrows(IllegalArgumentException.class, () -> view(215).actionCommand(true, "road; stop"));
    }

    @Test
    void typeChangesCostHalfRoundedUpWithoutOverflow() {
        assertEquals(0, PlotView.typeChangeCost(0));
        assertEquals(1, PlotView.typeChangeCost(1));
        assertEquals(100, PlotView.typeChangeCost(200));
        assertEquals(108, PlotView.typeChangeCost(215));
        assertEquals(228, PlotView.typeChangeCost(455));
        assertEquals(1_073_741_824, PlotView.typeChangeCost(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> PlotView.typeChangeCost(-1));
    }

    @Test
    void rejectsInvalidDataAndCopiesTypes() {
        assertThrows(IllegalArgumentException.class, () -> view(-1));
        assertThrows(IllegalArgumentException.class, () -> new PlotView(new VillageView("x", List.of()),
                "bad dimension", 0, 0, "plot", 0, 0, false, false, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new PlotView(new VillageView("x", List.of()),
                "minecraft:overworld", 0, 0, "plot", 0, 0, false, false, List.of("road stop")));
        assertThrows(UnsupportedOperationException.class, () -> view(1).types().clear());
    }
}
