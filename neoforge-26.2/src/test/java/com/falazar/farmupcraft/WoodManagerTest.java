package com.falazar.farmupcraft;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class WoodManagerTest {
    @Test
    void nurseryAndBirchBonusesRetainTheirScopeAndCaseSensitivity() {
        assertEquals(40, WoodManager.successRate(0, null, "birch_forest"));
        assertEquals(47, WoodManager.successRate(7, "farm", "birch_forest"));
        assertEquals(60, WoodManager.successRate(0, "nursery", "forest"));
        assertEquals(80, WoodManager.successRate(0, "nursery", "old_growth_birch_forest"));
        assertEquals(105, WoodManager.successRate(25, "nursery", "birch_forest"));
        assertEquals(40, WoodManager.successRate(0, "Nursery", "birch_forest"));
        assertEquals(60, WoodManager.successRate(0, "nursery", "unknown"));
    }

    @Test
    void bonusUsesInclusiveIntegerDividedThreshold() {
        assertTrue(WoodManager.earnsBonus(0, 0));
        assertTrue(WoodManager.earnsBonus(6, 40));
        assertFalse(WoodManager.earnsBonus(7, 40));
        assertTrue(WoodManager.earnsBonus(10, 60));
        assertFalse(WoodManager.earnsBonus(11, 60));
        assertTrue(WoodManager.earnsBonus(13, 80));
        assertFalse(WoodManager.earnsBonus(14, 80));
    }

    @Test
    void logFailureRunsBeforeTheLowPriorityNurseryBonusAndSkipsCanceledBreaks() throws Exception {
        assertEquals("farmupcraft", WoodManager.class.getAnnotation(EventBusSubscriber.class).modid());
        SubscribeEvent handler = WoodManager.class.getMethod("onBreakLogs", BreakBlockEvent.class)
                .getAnnotation(SubscribeEvent.class);
        assertNotNull(handler);
        assertEquals(EventPriority.NORMAL, handler.priority());
        assertFalse(handler.receiveCanceled());
        SubscribeEvent nurseryBonus = TreeManager.class.getMethod("onLogBreakNurseryBonus", BreakBlockEvent.class)
                .getAnnotation(SubscribeEvent.class);
        assertEquals(EventPriority.LOW, nurseryBonus.priority());
        assertFalse(nurseryBonus.receiveCanceled());
    }
}
