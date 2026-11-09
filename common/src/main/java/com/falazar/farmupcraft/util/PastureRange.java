package com.falazar.farmupcraft.util;

public final class PastureRange {
    public static final int RADIUS = 6;

    private PastureRange() {}

    public static boolean contains(int boughtY, int blockY) {
        return (long) blockY >= (long) boughtY - RADIUS && (long) blockY <= (long) boughtY + RADIUS;
    }

    public static String description(int boughtY) {
        return "Y " + (boughtY - RADIUS) + " through Y " + (boughtY + RADIUS)
                + " inclusive; purchase base Y " + boughtY + " (animal feet)";
    }
}
