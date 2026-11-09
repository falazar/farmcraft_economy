package com.falazar.farmupcraft.util;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class BuildPreviewGeometry {
    public static final int MAX_BLOCKS = 6_400;
    public static final int SOURCE_TOP_COLOR = 0x50FF4444;
    public static final int SOURCE_SIDE_COLOR = 0x40D93636;
    public static final int SOURCE_BOTTOM_COLOR = 0x30B02A2A;
    public static final int DEST_TOP_ALPHA = 0x88000000;
    public static final int DEST_SIDE_ALPHA = 0x70000000;
    public static final int DEST_BOTTOM_ALPHA = 0x58000000;

    public record Entry(int x, int y, int z, int rgb) {}

    public record Quad(double x1, double y1, double z1,
                       double x2, double y2, double z2,
                       double x3, double y3, double z3,
                       double x4, double y4, double z4,
                       int argb) {}

    private BuildPreviewGeometry() {}

    public static void validate(String dimension, List<Entry> source, List<Entry> destination, long expiresGameTime) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        if (!dimension.isEmpty() && dimension.length() > 256) {
            throw new IllegalArgumentException("Dimension identifier is too long");
        }
        if (source.size() > MAX_BLOCKS || destination.size() > MAX_BLOCKS) {
            throw new IllegalArgumentException("Build preview exceeds max block count: " + MAX_BLOCKS);
        }
        if (expiresGameTime < 0 && (!source.isEmpty() || !destination.isEmpty())) {
            throw new IllegalArgumentException("Negative preview expiration: " + expiresGameTime);
        }
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    public static List<Quad> buildQuads(List<Entry> source, List<Entry> destination) {
        validate("", source, destination, 0L);
        Set<Long> sourcePositions = new java.util.HashSet<>(source.size() * 2);
        for (Entry e : source) {
            sourcePositions.add(pack(e.x(), e.y(), e.z()));
        }
        Set<Long> destPositions = new java.util.HashSet<>(destination.size() * 2);
        for (Entry e : destination) {
            destPositions.add(pack(e.x(), e.y(), e.z()));
        }
        List<Quad> quads = new java.util.ArrayList<>((source.size() + destination.size()) * 6);
        for (Entry e : source) {
            if (destPositions.contains(pack(e.x(), e.y(), e.z()))) {
                continue;
            }
            addExposedFaces(quads, e.x(), e.y(), e.z(), -0.002, sourcePositions,
                    SOURCE_TOP_COLOR, SOURCE_SIDE_COLOR, SOURCE_BOTTOM_COLOR);
        }
        for (Entry e : destination) {
            int rgb = e.rgb() & 0xFFFFFF;
            if (rgb == 0) {
                rgb = 0x55FF88;
            }
            int sideRgb = shade(rgb, 0.84f);
            int bottomRgb = shade(rgb, 0.68f);
            addExposedFaces(quads, e.x(), e.y(), e.z(), 0.002, destPositions,
                    DEST_TOP_ALPHA | rgb, DEST_SIDE_ALPHA | sideRgb, DEST_BOTTOM_ALPHA | bottomRgb);
        }
        return List.copyOf(quads);
    }

    static int shade(int rgb, float factor) {
        int r = Math.max(0, Math.min(255, Math.round(((rgb >> 16) & 0xFF) * factor)));
        int g = Math.max(0, Math.min(255, Math.round(((rgb >> 8) & 0xFF) * factor)));
        int b = Math.max(0, Math.min(255, Math.round((rgb & 0xFF) * factor)));
        return (r << 16) | (g << 8) | b;
    }

    private static void addExposedFaces(List<Quad> out, int bx, int by, int bz, double inset,
                                        Set<Long> occupied, int topColor, int sideColor, int bottomColor) {
        double x0 = bx + inset;
        double y0 = by + inset;
        double z0 = bz + inset;
        double x1 = bx + 1.0 - inset;
        double y1 = by + 1.0 - inset;
        double z1 = bz + 1.0 - inset;
        // UP (+Y)
        if (!occupied.contains(pack(bx, by + 1, bz))) {
            out.add(new Quad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, topColor));
        }
        // DOWN (-Y)
        if (!occupied.contains(pack(bx, by - 1, bz))) {
            out.add(new Quad(x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, bottomColor));
        }
        // NORTH (-Z)
        if (!occupied.contains(pack(bx, by, bz - 1))) {
            out.add(new Quad(x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, sideColor));
        }
        // SOUTH (+Z)
        if (!occupied.contains(pack(bx, by, bz + 1))) {
            out.add(new Quad(x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, sideColor));
        }
        // WEST (-X)
        if (!occupied.contains(pack(bx - 1, by, bz))) {
            out.add(new Quad(x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, sideColor));
        }
        // EAST (+X)
        if (!occupied.contains(pack(bx + 1, by, bz))) {
            out.add(new Quad(x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, sideColor));
        }
    }
}
