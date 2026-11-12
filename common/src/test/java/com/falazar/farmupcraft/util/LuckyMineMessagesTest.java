package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LuckyMineMessagesTest {
    @Test
    void containsTwentyDistinctNonBlankLuckyFeelingVariations() {
        assertEquals(20, LuckyMineMessages.MESSAGES.size());
        assertEquals(20, Set.copyOf(LuckyMineMessages.MESSAGES).size());
        assertTrue(LuckyMineMessages.MESSAGES.stream().allMatch(s -> s != null && !s.isBlank()));
    }
}
