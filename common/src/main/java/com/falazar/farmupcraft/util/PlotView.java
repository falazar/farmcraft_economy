package com.falazar.farmupcraft.util;

import java.util.List;

public record PlotView(VillageView details, String dimension, int x, int z, String type,
                       int buyCost, int changeCost, boolean canBuy, boolean canChange, List<String> types) {
    public PlotView {
        if (details == null || dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")
                || dimension.length() > 256 || type == null || type.length() > 64 || !type.matches("[a-z]+")
                || buyCost < 0 || changeCost < 0 || types.size() > 32
                || types.stream().anyMatch(t -> t == null || !t.matches("[a-z]+") || t.length() > 64))
            throw new IllegalArgumentException("Invalid plot view");
        types = List.copyOf(types);
    }

    public String actionCommand(boolean buy, String target) {
        if (!types.contains(target)) throw new IllegalArgumentException("Unknown plot type");
        return "plot gui " + (buy ? "buy" : "settype") + " \"" + dimension + "\" " + x + " " + z + " "
                + (buy ? buyCost : target.equals(type) ? 0 : changeCost) + " " + type + " " + target;
    }

    public static int typeChangeCost(int plotCost) {
        if (plotCost < 0) throw new IllegalArgumentException("Invalid plot cost");
        return plotCost / 2 + plotCost % 2;
    }
}
