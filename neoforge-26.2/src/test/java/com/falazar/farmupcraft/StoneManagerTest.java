package com.falazar.farmupcraft;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

/** Uses the official registry fixture, not a simulated level or player. */
@ExtendWith(EphemeralTestServerProvider.class)
class StoneManagerTest {
    @Test
    void onlyOverworldStoneNetherStoneAndDirtAreAffected(MinecraftServer server) {
        assertNotNull(server.registryAccess());
        for (var block : new net.minecraft.world.level.block.Block[]{
                Blocks.STONE, Blocks.DEEPSLATE, Blocks.GRANITE, Blocks.NETHERRACK,
                Blocks.BASALT, Blocks.BLACKSTONE, Blocks.DIRT, Blocks.COARSE_DIRT}) {
            assertTrue(StoneManager.isAffectedBlock(block.defaultBlockState()), block.toString());
        }
        for (var block : new net.minecraft.world.level.block.Block[]{
                Blocks.GRASS_BLOCK, Blocks.COBBLESTONE, Blocks.SAND, Blocks.OAK_LOG, Blocks.OAK_LEAVES, Blocks.DIAMOND_ORE}) {
            assertFalse(StoneManager.isAffectedBlock(block.defaultBlockState()), block.toString());
        }
    }

    @Test
    void experienceAndMiningBonusesKeepTheirIntegerThresholdsAndUncappedRate() {
        assertEquals(30, StoneManager.successRate(0, 0));
        assertEquals(30, StoneManager.successRate(0, 9_999));
        assertEquals(31, StoneManager.successRate(0, 10_000));
        assertEquals(32, StoneManager.successRate(1, 0));
        assertEquals(92, StoneManager.successRate(30, 20_000));
        assertEquals(100, StoneManager.successRate(35, 0));
        assertEquals(130, StoneManager.successRate(50, 0));
    }

    @Test
    void subscribesToTheCancellableAttemptRatherThanAnAfterBreakPhase() throws Exception {
        assertEquals("farmupcraft", StoneManager.class.getAnnotation(EventBusSubscriber.class).modid());
        assertTrue(ICancellableEvent.class.isAssignableFrom(BreakBlockEvent.class));
        SubscribeEvent handler = StoneManager.class.getMethod("onBreakStone", BreakBlockEvent.class)
                .getAnnotation(SubscribeEvent.class);
        assertNotNull(handler);
        assertEquals(EventPriority.NORMAL, handler.priority());
        assertFalse(handler.receiveCanceled());
    }
}
