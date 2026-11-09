package com.falazar.farmupcraft.util;

public final class PlayerExperienceBonus {
    private PlayerExperienceBonus() {
    }

    public static int apply(String playerName, int amount) {
        if (amount <= 0 || !(playerName.equalsIgnoreCase("LazzyMars")
                || playerName.equalsIgnoreCase("BossPanda96366"))) {
            return amount;
        }
        return Math.multiplyExact(amount, 2);
    }
}
