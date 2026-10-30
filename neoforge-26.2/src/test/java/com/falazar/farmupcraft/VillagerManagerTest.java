package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.ChunkData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VillagerManagerTest {
    private static final UUID VILLAGE = new UUID(0, 0);

    @AfterEach
    void clearCaches() {
        VillagerManager.clearCaches();
    }

    @Test
    void warnsOncePerVillagerPerCooldownAndClearsAtWorldStop() {
        UUID villager = UUID.randomUUID();
        long now = 10_000_000;
        assertTrue(VillagerManager.shouldWarnAboutBell(villager, now));
        assertFalse(VillagerManager.shouldWarnAboutBell(villager, now + 30 * 60 * 1000));
        assertTrue(VillagerManager.shouldWarnAboutBell(villager, now + 30 * 60 * 1000 + 1));
        assertTrue(VillagerManager.markAttackAlert(villager));
        assertFalse(VillagerManager.markAttackAlert(villager));
        VillagerManager.clearCaches();
        assertTrue(VillagerManager.markAttackAlert(villager));
        assertTrue(VillagerManager.shouldWarnAboutBell(villager, now));
    }

    @Test
    void dailyResetClearsAlertsOnlyWhenDateChanges() {
        LocalDate today = LocalDate.now();
        VillagerManager.clearDailyCaches(today);
        UUID id = UUID.randomUUID();
        assertTrue(VillagerManager.markAttackAlert(id));
        VillagerManager.clearDailyCaches(today);
        assertFalse(VillagerManager.markAttackAlert(id));
        VillagerManager.clearDailyCaches(today.plusDays(1));
        assertTrue(VillagerManager.markAttackAlert(id));
    }

    @Test
    void stuckDetectionHonorsThresholdCooldownMovementAndMessageLimit() {
        var state = new VillagerManager.StuckRecord(BlockPos.ZERO);
        long now = 10_000_000;
        for (int i = 0; i < 11; i++) {
            assertFalse(state.check(BlockPos.ZERO, now));
        }
        assertTrue(state.check(BlockPos.ZERO, now));
        assertFalse(state.check(BlockPos.ZERO, now + 15 * 60 * 1000 - 1));
        assertTrue(state.check(BlockPos.ZERO, now + 15 * 60 * 1000));
        assertTrue(state.check(BlockPos.ZERO, now + 30 * 60 * 1000));
        assertFalse(state.check(BlockPos.ZERO, now + 45 * 60 * 1000));
        assertFalse(state.check(BlockPos.ZERO.east(), now + 60 * 60 * 1000));
        assertEquals(0, state.ticksAtPos);
        assertEquals(3, state.messagesSent);
    }

    @Test
    void plotContextPrefersOwnSpecializationThenNeighborThenUnclaimed() {
        ChunkPos center = new ChunkPos(-2, 3);
        ChunkData village = new ChunkData("village", -1, VILLAGE);
        ChunkData kitchen = new ChunkData("kitchen", -1, VILLAGE);
        Map<Long, ChunkData> chunks = Map.of(center.pack(), village, ChunkPos.pack(-1, 3), kitchen);
        assertEquals("near:kitchen", VillagerManager.plotContext(center, chunks::get));
        assertEquals("kitchen", VillagerManager.plotContext(center, key -> key == center.pack() ? kitchen : null));
        assertEquals("village", VillagerManager.plotContext(center, key -> key == center.pack() ? village : null));
        assertEquals("unclaimed", VillagerManager.plotContext(center, key -> null));
    }

    @Test
    void foreverChildAssignmentIsStableAndUsesBothIdentities() {
        UUID village = new UUID(0, 42);
        int assigned = 0;
        for (long i = 0; i < 100; i++) {
            UUID villager = new UUID(0, i);
            boolean result = VillagerManager.isForeverKid(villager, village);
            assertEquals(result, VillagerManager.isForeverKid(villager, village));
            if (result) {
                assigned++;
            }
        }
        assertEquals(20, assigned);
        assertNotEquals(VillagerManager.isForeverKid(new UUID(0, 0), village),
                VillagerManager.isForeverKid(new UUID(0, 0), new UUID(1, 42)));
    }
}
