package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlotSpawnExemptionsTest {
    @Test
    void onlyDucksAndNonLycanitesBreedingAreExempt() {
        assertTrue(PlotSpawnExemptions.allows("naturalist:duck", false));
        assertTrue(PlotSpawnExemptions.allows("naturalist:duck", true));
        for (String id : new String[]{"naturalist:deer", "naturalist:lion", "other:hostile", "other:duck"}) {
            assertFalse(PlotSpawnExemptions.allows(id, false), id);
            assertTrue(PlotSpawnExemptions.allows(id, true), id);
        }
        for (String id : new String[]{"lycanitesmobs:jengu", "lycanitesmobs:vespidqueen",
                "lycanitesmobs:concapedesegment", "lycanitesmobs:duck", "lycanitesmobs:spriggan"}) {
            assertFalse(PlotSpawnExemptions.allows(id, false), id);
            assertFalse(PlotSpawnExemptions.allows(id, true), id);
        }
    }
}
