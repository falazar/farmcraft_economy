package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PastureRangeTest {
    @Test
    void includesSixBlocksEachWayButNotSeven() {
        for (int base : new int[]{-64, 0, 60, 80, 319}) {
            assertFalse(PastureRange.contains(base, base - 7));
            for (int offset = -6; offset <= 6; offset++)
                assertTrue(PastureRange.contains(base, base + offset));
            assertFalse(PastureRange.contains(base, base + 7));
        }
        assertFalse(PastureRange.contains(Integer.MAX_VALUE, Integer.MIN_VALUE));
        assertTrue(PastureRange.contains(Integer.MIN_VALUE, Integer.MIN_VALUE + 6));
        assertEquals("Y 74 through Y 86 inclusive; purchase base Y 80 (animal feet)",
                PastureRange.description(80));
    }
}
