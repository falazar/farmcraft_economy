package com.falazar.farmupcraft.util;

public record VillageLevelUpPayment(int villageCoins, int playerCoins) {
    public static VillageLevelUpPayment calculate(int cost, int treasury) {
        if (cost < 0) throw new IllegalArgumentException("Negative level-up cost");
        int villageCoins = Math.min(cost, Math.max(0, treasury));
        return new VillageLevelUpPayment(villageCoins, cost - villageCoins);
    }
}
