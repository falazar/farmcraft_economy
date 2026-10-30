package com.falazar.farmupcraft.networktest;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class ProbeDataTest {
    @Test
    void snapshotExceedsOneMiBWithoutExceedingAnyNbtStringLimit() {
        int bytes = 0;
        var unique = new HashSet<String>();
        for (int i = 0; i < ProbeData.ROWS; i++) {
            String value = ProbeData.row(i);
            assertEquals(ProbeData.ROW_LENGTH, value.length());
            assertEquals(value, ProbeData.row(i));
            int encoded = value.getBytes(StandardCharsets.UTF_8).length;
            assertTrue(encoded < 65_536);
            assertTrue(unique.add(value));
            bytes += encoded;
        }
        assertTrue(bytes > 1_048_576);
        assertNotEquals(0, ProbeData.largeDigest());
    }

    @Test
    void digestCoversEveryRowInOrder() {
        int expected = 1;
        for (int i = 0; i < ProbeData.ROWS; i++) expected = 31 * expected + ProbeData.row(i).hashCode();
        assertEquals(expected, ProbeData.largeDigest());
        int reversed = 1;
        for (int i = ProbeData.ROWS - 1; i >= 0; i--) reversed = 31 * reversed + ProbeData.row(i).hashCode();
        assertNotEquals(reversed, expected);
    }
}
