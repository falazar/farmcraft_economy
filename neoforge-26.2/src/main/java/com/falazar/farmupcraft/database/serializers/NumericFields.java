package com.falazar.farmupcraft.database.serializers;

import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;

final class NumericFields {
    private NumericFields() {}

    static int integer(Tag tag, String name) {
        long value = longInteger(tag, name);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw invalid(name);
        }
        return (int) value;
    }

    static long longInteger(Tag tag, String name) {
        if (!(tag instanceof NumericTag number)) {
            throw invalid(name);
        }
        if (number instanceof FloatTag || number instanceof DoubleTag) {
            double value = number.doubleValue();
            // Long.MAX_VALUE rounds to +2^63 as a double, so the upper bound is exclusive.
            if (!Double.isFinite(value) || value != Math.rint(value) || value < -0x1p63 || value >= 0x1p63) {
                throw invalid(name);
            }
            return (long) value;
        }
        return number.longValue();
    }

    private static IllegalArgumentException invalid(String name) {
        return new IllegalArgumentException("Expected losslessly representable integer field '" + name + "'");
    }
}
