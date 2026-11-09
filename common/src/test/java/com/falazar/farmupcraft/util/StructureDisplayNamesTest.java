package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StructureDisplayNamesTest {
    @Test
    void hidesExclusiveFolderInExistingStoredNames() {
        assertEquals("village mediterranean",
                StructureDisplayNames.villageStructure("exclusives/village mediterranean"));
        assertEquals("village rustic", StructureDisplayNames.villageStructure("exclusives/village rustic"));
    }

    @Test
    void preservesOtherNamesAndPaths() {
        for (String name : new String[]{"village birch", "village birch forest", "cemetery",
                "other/village", "not exclusives/village", ""}) {
            assertEquals(name, StructureDisplayNames.villageStructure(name));
        }
    }
}
