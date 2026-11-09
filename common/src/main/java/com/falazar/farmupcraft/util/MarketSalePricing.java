package com.falazar.farmupcraft.util;

public final class MarketSalePricing {
    private MarketSalePricing() {
    }

    public record Quote(int coins, int finalCost) {
    }

    /** Prices drop after each complete group of 32 lifetime sales, never below zero. */
    public static Quote quote(int initialCost, int amountSold, int count) {
        if (initialCost < 0 || amountSold < 0 || count < 0)
            throw new IllegalArgumentException("Price, amount sold and count must be non-negative");
        int firstBoundary = 32 - amountSold % 32;
        int firstCount = Math.min(count, firstBoundary);
        long coins = (long) initialCost * firstCount;
        int cost = firstCount == firstBoundary ? Math.max(0, initialCost - 1) : initialCost;
        int remaining = count - firstCount;
        int groups = Math.min(remaining / 32, cost);
        coins += 16L * groups * (2L * cost - groups + 1);
        cost -= groups;
        remaining -= groups * 32;
        if (cost > 0) coins += (long) cost * remaining;
        return new Quote(Math.toIntExact(coins), cost);
    }
}
