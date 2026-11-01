package com.falazar.farmupcraft.util;

public final class EnchantmentPricing {
    private EnchantmentPricing() {}

    public static int cost(int villageLevel, int buttonId) {
        if (buttonId < 0 || buttonId > 2) {
            throw new IllegalArgumentException("Invalid enchantment button: " + buttonId);
        }
        if (villageLevel < 0 || villageLevel > 10) {
            throw new IllegalArgumentException("Invalid village level: " + villageLevel);
        }
        int level = Math.max(1, villageLevel);
        return ((level * 100 * (buttonId + 1) + 75) / 150) * 50;
    }
}
