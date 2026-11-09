package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CropHarvestRulesTest {
    @Test
    void failureRatesAreExactlyThirtyTwentyTenAndZero() {
        for (int level = 1; level <= 10; level++) {
            int failures = 0;
            for (int roll = 0; roll < 100; roll++) {
                if (roll < CropHarvestRules.failureChance(level)) failures++;
            }
            assertEquals(level == 1 ? 30 : level == 2 ? 20 : level == 3 ? 10 : 0, failures);
        }
    }
}
