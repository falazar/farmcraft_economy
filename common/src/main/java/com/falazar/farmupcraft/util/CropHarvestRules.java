package com.falazar.farmupcraft.util;

public final class CropHarvestRules {
    private CropHarvestRules() {}

    public static int failureChance(int farmLevel) {
        return farmLevel <= 1 ? 30 : farmLevel == 2 ? 20 : farmLevel == 3 ? 10 : 0;
    }
}
