package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VillageLevelUpPaymentTest {
    @Test
    void treasuryPaysFirstWithoutSpendingDebtOrOvercharging() {
        assertEquals(new VillageLevelUpPayment(600, 0), VillageLevelUpPayment.calculate(600, 900));
        assertEquals(new VillageLevelUpPayment(600, 0), VillageLevelUpPayment.calculate(600, 600));
        assertEquals(new VillageLevelUpPayment(250, 350), VillageLevelUpPayment.calculate(600, 250));
        assertEquals(new VillageLevelUpPayment(0, 600), VillageLevelUpPayment.calculate(600, 0));
        assertEquals(new VillageLevelUpPayment(0, 600), VillageLevelUpPayment.calculate(600, -200));
        assertEquals(new VillageLevelUpPayment(0, 600), VillageLevelUpPayment.calculate(600, Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> VillageLevelUpPayment.calculate(-1, 200));
    }
}
