package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerExperienceBonusTest {
    @Test
    void doublesOnlyExactNamedPlayersIgnoringCase() {
        for (String name : new String[]{"LazzyMars", "BossPanda96366", "lazzymars", "BOSSPANDA96366"}) {
            assertEquals(2, PlayerExperienceBonus.apply(name, 1));
            assertEquals(100, PlayerExperienceBonus.apply(name, 50));
        }
        for (String name : new String[]{"robin", "LazzyMars2", "BossPanda", "BossPanda96366 ", ""}) {
            assertEquals(50, PlayerExperienceBonus.apply(name, 50));
        }
    }

    @Test
    void lossesZeroAndOverflowAreNotTurnedIntoGains() {
        assertEquals(0, PlayerExperienceBonus.apply("LazzyMars", 0));
        assertEquals(-10, PlayerExperienceBonus.apply("BossPanda96366", -10));
        assertEquals(Integer.MIN_VALUE, PlayerExperienceBonus.apply("LazzyMars", Integer.MIN_VALUE));
        assertThrows(ArithmeticException.class, () -> PlayerExperienceBonus.apply("LazzyMars", Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, PlayerExperienceBonus.apply("Other", Integer.MAX_VALUE));
    }
}
