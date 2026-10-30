package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PlayerOnboardingTest {
    @Test
    void welcomeIncludesFirstFiveCalendarDatesButNotDaySix() {
        var first = LocalDate.of(2026, 12, 30);
        for (int day = 0; day < 5; day++) {
            assertTrue(PlayerOnboarding.shouldWelcome(first.toEpochDay(), first.plusDays(day)));
        }
        assertFalse(PlayerOnboarding.shouldWelcome(first.toEpochDay(), first.plusDays(5)));
        assertFalse(PlayerOnboarding.shouldWelcome(first.toEpochDay(), first.plusYears(1)));
        assertFalse(PlayerOnboarding.shouldWelcome(first.toEpochDay(), first.minusDays(1)));
        assertFalse(PlayerOnboarding.shouldWelcome(PlayerOnboarding.UNSET_DAY, first));
    }

    @Test
    void laterArrivalsHaveTheirOwnWindowAndRepeatedLoginsDoNotExtendIt() {
        var first = LocalDate.of(2026, 10, 29);
        var later = first.plusDays(20);
        assertTrue(PlayerOnboarding.shouldWelcome(first.toEpochDay(), first));
        assertTrue(PlayerOnboarding.shouldWelcome(first.toEpochDay(), first));
        assertFalse(PlayerOnboarding.shouldWelcome(first.toEpochDay(), later));
        assertTrue(PlayerOnboarding.shouldWelcome(later.toEpochDay(), later));
    }
}
