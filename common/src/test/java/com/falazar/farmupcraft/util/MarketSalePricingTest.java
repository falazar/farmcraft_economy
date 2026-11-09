package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarketSalePricingTest {
    @Test
    void matchesPerItemPricesAcrossAllBoundaryOffsets() {
        for (int price = 0; price <= 12; price++) {
            for (int sold = 0; sold < 64; sold++) {
                for (int count : new int[]{0, 1, 2, 31, 32, 33, 64, 128, 1024}) {
                    int expectedCoins = 0;
                    int expectedPrice = price;
                    for (int i = 0; i < count; i++) {
                        expectedCoins += expectedPrice;
                        if ((sold + i + 1) % 32 == 0) expectedPrice = Math.max(0, expectedPrice - 1);
                    }
                    var quote = MarketSalePricing.quote(price, sold, count);
                    assertEquals(expectedCoins, quote.coins());
                    assertEquals(expectedPrice, quote.finalCost());
                }
            }
        }
        assertEquals(9, MarketSalePricing.quote(5, 31, 2).coins());
    }

    @Test
    void rejectsOverflowAndInvalidDataBeforeMutation() {
        assertThrows(ArithmeticException.class, () -> MarketSalePricing.quote(Integer.MAX_VALUE, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> MarketSalePricing.quote(-1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> MarketSalePricing.quote(1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> MarketSalePricing.quote(1, 0, -1));
    }
}
