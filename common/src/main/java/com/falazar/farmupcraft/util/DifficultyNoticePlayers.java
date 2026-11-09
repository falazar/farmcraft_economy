package com.falazar.farmupcraft.util;

import java.util.Locale;

public final class DifficultyNoticePlayers {
    private DifficultyNoticePlayers() {}

    public static boolean includes(String playerName) {
        return !playerName.toLowerCase(Locale.ROOT).endsWith("bot");
    }
}
