package com.falazar.farmupcraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class NamedScanAreasTest {
    @Test void titlesOnlyShowOnEntryWithTwentySecondCooldown() {
        assertTrue(NamedScanAreas.shouldAnnounce("lake", "", 400));
        assertFalse(NamedScanAreas.shouldAnnounce("lake", "", 399));
        assertFalse(NamedScanAreas.shouldAnnounce("lake", "lake", 10000));
        assertFalse(NamedScanAreas.shouldAnnounce("", "lake", 10000));
        assertTrue(NamedScanAreas.shouldAnnounce("island", "lake", 400));
    }
    @Test void sharedFindingsChooseSmallestOverlapAndIncludeExactBoundary() {
        JsonObject large = IslandScanManager.buildResult("island",
                Set.of(IslandScanManager.pack(-20, -20), IslandScanManager.pack(20, 20)));
        large.addProperty("name", "Storm-100");
        large.addProperty("block_count", 100);
        JsonObject small = IslandScanManager.buildResult("lake",
                Set.of(IslandScanManager.pack(-2, -2), IslandScanManager.pack(2, 2)));
        small.addProperty("name", "Mist-50");
        small.addProperty("block_count", 50);
        JsonArray findings = new JsonArray();
        findings.add(large); findings.add(small);
        var areas = NamedScanAreas.parse(findings);
        assertEquals("Mist Lake", NamedScanAreas.at(areas, 2, 2).title());
        assertEquals("Storm Island", NamedScanAreas.at(areas, 3, 3).title());
        assertEquals("Mist-50", NamedScanAreas.at(areas, 2, 2).name());
        assertNull(NamedScanAreas.at(areas, 21, 0));
        assertEquals(0, areas.getFirst().x());
        assertEquals(0, areas.getFirst().z());
        assertEquals(25, areas.getFirst().size());
        assertThrows(UnsupportedOperationException.class, () -> areas.clear());
        small.addProperty("block_count", 49);
        assertEquals(1, NamedScanAreas.parse(findings).size());
        large.addProperty("block_count", 39);
        assertTrue(NamedScanAreas.parse(findings).isEmpty());
    }
}
