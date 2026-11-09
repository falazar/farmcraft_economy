package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DifficultyNoticePlayersTest {
    @Test
    void excludesBotSuffixRegardlessOfCaseButNotNamesContainingBotElsewhere() {
        for (String name : List.of("bridgebot", "BridgeBot", "BOT", "helperbOt")) {
            assertFalse(DifficultyNoticePlayers.includes(name), name);
        }
        for (String name : List.of("Robin", "LazzyMars", "Botanical", "bot_player", "robotics")) {
            assertTrue(DifficultyNoticePlayers.includes(name), name);
        }
    }

    @Test
    void countsAndRecipientsOnlyIncludeHumanNames() {
        assertEquals(List.of("Robin"), List.of("Robin", "BridgeBot").stream()
                .filter(DifficultyNoticePlayers::includes).toList());
        assertEquals(List.of("Robin", "LazzyMars"), List.of("Robin", "BridgeBot", "LazzyMars", "HelperBOT")
                .stream().filter(DifficultyNoticePlayers::includes).toList());
        assertTrue(List.of("BridgeBot", "HelperBOT").stream().filter(DifficultyNoticePlayers::includes).toList().isEmpty());
    }
}
