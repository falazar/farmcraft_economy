package com.falazar.farmupcraft;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScanFeatureRulesTest {
    @Test void displayOmitsSizeSuffixWithoutChangingOtherNameParts() {
        assertEquals("Frost", ScanFeatureRules.displayName("Frost-1089"));
        assertEquals("Mist", ScanFeatureRules.displayName("Mist-50"));
        assertEquals("Storm-Cove", ScanFeatureRules.displayName("Storm-Cove-100"));
        assertEquals("Mist", ScanFeatureRules.displayName("Mist"));
        assertEquals("Area51", ScanFeatureRules.displayName("Area51"));
    }
    @Test void exactInclusiveLakeAndIslandThresholds() {
        assertFalse(ScanFeatureRules.largeEnough("lake", 49));
        assertTrue(ScanFeatureRules.largeEnough("lake", 50));
        assertFalse(ScanFeatureRules.largeEnough("island", 39));
        assertTrue(ScanFeatureRules.largeEnough("island", 40));
        assertFalse(ScanFeatureRules.largeEnough("lake", 19));
        assertFalse(ScanFeatureRules.largeEnough("lake", 27));
        assertTrue(ScanFeatureRules.largeEnough("island", 1089));
        assertThrows(IllegalArgumentException.class, () -> ScanFeatureRules.largeEnough("other", 100));
    }
    @Test void villageScanRadiusGrowsWithVillageLevelWellBeyondGreenArea() {
        assertEquals(30, ScanFeatureRules.villageScanRadiusChunks(1));
        assertEquals(35, ScanFeatureRules.villageScanRadiusChunks(2));
        assertEquals(60, ScanFeatureRules.villageScanRadiusChunks(7));
        assertEquals(75, ScanFeatureRules.villageScanRadiusChunks(10));
        assertThrows(IllegalArgumentException.class, () -> ScanFeatureRules.villageScanRadiusChunks(0));
        assertThrows(IllegalArgumentException.class, () -> ScanFeatureRules.villageScanRadiusChunks(11));
    }
}
