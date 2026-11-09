package com.falazar.farmupcraft.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcSpeechBubblesTest {
    @Test
    void previewsNormalizeAndBoundLongReplies() {
        assertEquals("Hello there!", NpcSpeechBubbles.preview(" Hello\n there! "));
        assertEquals("Hello", NpcSpeechBubbles.preview("Hello"));
        String text = NpcSpeechBubbles.preview("A lovely day in the village. ".repeat(20));
        assertTrue(text.length() <= 100);
        assertTrue(text.endsWith("..."));
        assertEquals(100, NpcSpeechBubbles.preview("W".repeat(300)).length());
    }

    @Test
    void fadesOnlyAtEndOrBeyondTwelveBlocksWithoutOpacityWraparound() {
        assertEquals(255, NpcSpeechBubbles.opacity(0, 3));
        assertEquals(255, NpcSpeechBubbles.opacity(160, 12));
        assertEquals(255, NpcSpeechBubbles.opacity(300, 12));
        assertEquals(128, NpcSpeechBubbles.opacity(310, 12));
        assertEquals(128, NpcSpeechBubbles.opacity(10, 14));
        assertEquals(64, NpcSpeechBubbles.opacity(310, 14));
        assertEquals(4, NpcSpeechBubbles.opacity(320, 16));
    }
}
