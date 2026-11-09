package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PlayerOnboardingTest {
    @Test
    void menuReminderIncludesCommandAndDefaultShortcut() {
        assertEquals("Use /menu or press Alt+F to see all the options!", PlayerOnboarding.MENU_REMINDER);
    }

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
        assertFalse(PlayerOnboarding.shouldWelcome( first.toEpochDay(), later));
        assertTrue(PlayerOnboarding.shouldWelcome(later.toEpochDay(), later));
    }

    @Test
    void twentyDistinctMarketplaceRemindersIncludeCheckMarketplaceAndMarketShow() {
        assertEquals(20, PlayerOnboarding.MARKET_REMINDERS.size());
        assertEquals(20, java.util.Set.copyOf(PlayerOnboarding.MARKET_REMINDERS).size());
        assertEquals(900_000L, PlayerOnboarding.MARKET_REMINDER_DELAY_MS);
        assertEquals(18_000L, PlayerOnboarding.MARKET_REMINDER_DELAY_TICKS);
        for (int i = 0; i < 20; i++) {
            String msg = PlayerOnboarding.marketReminder(i);
            assertTrue(msg.toLowerCase(java.util.Locale.ROOT).contains("check the marketplace"), msg);
            assertFalse(msg.toLowerCase(java.util.Locale.ROOT).contains("gold"), msg);
            assertTrue(msg.contains("/market show"), msg);
        }
    }
}
