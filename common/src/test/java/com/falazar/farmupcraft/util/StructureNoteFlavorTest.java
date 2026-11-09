package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class StructureNoteFlavorTest {
    @Test
    void producesManyDistinctPromptsAndFallbacksWithinBookLimits() {
        var random = new Random(123);
        var prompts = new HashSet<String>();
        var bodies = new HashSet<String>();
        var titles = new HashSet<String>();
        for (int i = 0; i < 1000; i++) {
            prompts.add(StructureNoteFlavor.prompt("Tower", random));
            var fallback = StructureNoteFlavor.fallback(random);
            assertEquals(2, fallback.length);
            assertTrue(fallback[0].length() <= 32);
            assertTrue(fallback[1].length() <= 600);
            assertFalse(fallback[1].isBlank());
            titles.add(fallback[0]);
            bodies.add(fallback[1]);
        }
        assertTrue(prompts.size() >= 130);
        assertTrue(bodies.size() >= 130);
        assertEquals(12, titles.size());
        var batch = StructureNoteFlavor.batchPrompt("Tower", 5, random);
        assertTrue(batch.contains("Produce 5 DISTINCT notes"));
        assertTrue(batch.contains("exactly ---NEXT---"));
        assertTrue(batch.contains("do not promise rewards"));
    }
}
