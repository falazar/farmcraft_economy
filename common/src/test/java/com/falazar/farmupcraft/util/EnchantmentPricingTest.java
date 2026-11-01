package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnchantmentPricingTest {
    @Test
    void allLevelsAndOptionsMatchTheAgreedChart() {
        int[][] expected = {
                {50, 50, 100}, {50, 50, 100}, {50, 150, 200},
                {100, 200, 300}, {150, 250, 400}, {150, 350, 500},
                {200, 400, 600}, {250, 450, 700}, {250, 550, 800},
                {300, 600, 900}, {350, 650, 1000}
        };
        for (int level = 0; level <= 10; level++) {
            for (int button = 0; button < 3; button++) {
                assertEquals(expected[level][button], EnchantmentPricing.cost(level, button),
                        "level=" + level + ", button=" + button);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> EnchantmentPricing.cost(11, 0));
        assertThrows(IllegalArgumentException.class, () -> EnchantmentPricing.cost(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> EnchantmentPricing.cost(1, -1));
        assertThrows(IllegalArgumentException.class, () -> EnchantmentPricing.cost(1, 3));
    }
}
