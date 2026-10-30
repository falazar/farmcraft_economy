package com.falazar.farmupcraft.networktest;

import java.util.Random;

/** Deterministic, poorly compressible ASCII values below the NBT UTF string limit. */
public final class ProbeData {
    public static final int ROWS = 24;
    public static final int ROW_LENGTH = 60_000;

    private ProbeData() {}

    public static String row(int index) {
        Random random = new Random(0x4e45544cL + index);
        StringBuilder text = new StringBuilder(ROW_LENGTH);
        for (int i = 0; i < ROW_LENGTH; i++) text.append((char) ('!' + random.nextInt(90)));
        return text.toString();
    }

    public static int largeDigest() {
        int digest = 1;
        for (int i = 0; i < ROWS; i++) digest = 31 * digest + row(i).hashCode();
        return digest;
    }
}
