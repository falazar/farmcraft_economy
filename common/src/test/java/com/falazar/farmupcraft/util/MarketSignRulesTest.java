package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketSignRulesTest {
    @Test
    void matchesSingleLineAndMultiLineMarketSignsCaseInsensitively() {
        assertTrue(MarketSignRules.isMarketSignSide(List.of("market", "", "", "")));
        assertTrue(MarketSignRules.isMarketSignSide(List.of("", "MARKET", "", "")));
        assertTrue(MarketSignRules.isMarketSignSide(List.of("[Market]", "", "", "")));
        assertTrue(MarketSignRules.isMarketSignSide(List.of("Click for Market", "", "", "")));
        assertTrue(MarketSignRules.isMarketSignSide(List.of("Click for", "Market!", "", "")));
        assertTrue(MarketSignRules.isMarketSignSide(List.of("Click", "for", "market", "")));
        assertTrue(MarketSignRules.isMarketSignSide(List.of("\u00A76Market", "", "", "")));
    }

    @Test
    void rejectsUnrelatedOrResidentSigns() {
        assertFalse(MarketSignRules.isMarketSignSide(List.of("", "", "", "")));
        assertFalse(MarketSignRules.isMarketSignSide(List.of("Robin", "Farmer", "", "")));
        assertFalse(MarketSignRules.isMarketSignSide(List.of("Market Street", "", "", "")));
        assertFalse(MarketSignRules.isMarketSignSide(List.of("Old", "Market", "Road", "")));
    }
}
