package com.falazar.farmupcraft;

public final class ScanFeatureRules {
    public static final int MIN_LAKE_BLOCKS = 50;
    public static final int MIN_ISLAND_BLOCKS = 40;

    private ScanFeatureRules() {}

    public static int villageScanRadiusChunks(int villageLevel) {
        if (villageLevel < 1 || villageLevel > 10) {
            throw new IllegalArgumentException("Invalid village level: " + villageLevel);
        }
        return 25 + 5 * villageLevel;
    }

    public static String displayName(String name) {
        return name.replaceFirst("-\\d+$", "");
    }

    public static boolean largeEnough(String type, int blocks) {
        return blocks >= switch (type) {
            case "lake" -> MIN_LAKE_BLOCKS;
            case "island" -> MIN_ISLAND_BLOCKS;
            default -> throw new IllegalArgumentException("Unknown scan feature type: " + type);
        };
    }
}
