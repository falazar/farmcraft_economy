package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.GameZone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ZoneCommandTest {
    private static GameZone zone(String name, GameZone.ZoneType type, int x, int z) {
        return new GameZone(UUID.randomUUID(), name, List.of(SectionPos.of(x >> 4, 4, z >> 4)),
                type, new BlockPos(x, 64, z));
    }

    @Test
    void nearbyZonesUseInclusiveManhattanChunkDistanceCaseInsensitiveTypesAndNegativeCoordinates() {
        var origin = new ChunkPos(-1, -1);
        var here = zone("Here", GameZone.ZoneType.ISLAND, -1, -1);
        var lake = zone("Lake", GameZone.ZoneType.LAKE, -17, -1);
        var edge = zone("Edge", GameZone.ZoneType.LAKE, 16 * 24, 16 * 24);
        var outside = zone("Outside", GameZone.ZoneType.LAKE, 16 * 25, 16 * 24);
        List<GameZone> all = List.of(outside, edge, lake, here);
        assertEquals(1, ZoneCommand.chunkDistance(lake, origin));
        assertEquals(50, ZoneCommand.chunkDistance(edge, origin));
        assertEquals(List.of(here, lake, edge), ZoneCommand.nearbyZones(all, origin, 50, null));
        assertEquals(List.of(lake, edge), ZoneCommand.nearbyZones(all, origin, 50, "lAkE"));
        assertEquals(List.of(here), ZoneCommand.nearbyZones(all, origin, 0, null));
        assertTrue(ZoneCommand.nearbyZones(all, origin, 50, "unknown").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ZoneCommand.nearbyZones(all, origin, -1, null));
    }

    @Test
    void renamePreservesEveryFieldExceptNameAndRejectsMissingOrDuplicateNames() {
        var original = zone("Old Name", GameZone.ZoneType.LAKE, -17, 33);
        var other = zone("Taken", GameZone.ZoneType.ISLAND, 64, 64);
        var all = List.of(original, other);
        var renamed = ZoneCommand.renamedZone(all, "Old Name", "New Name");
        assertEquals(original.getUuid(), renamed.getUuid());
        assertEquals(original.getSections(), renamed.getSections());
        assertEquals(original.getCenter(), renamed.getCenter());
        assertEquals(original.getType(), renamed.getType());
        assertEquals("New Name", renamed.getName());
        assertEquals("Old Name", original.getName());
        assertEquals("Zone 'missing' not found.", assertThrows(IllegalArgumentException.class,
                () -> ZoneCommand.renamedZone(all, "missing", "New")).getMessage());
        assertEquals("A zone with name 'Taken' already exists.", assertThrows(IllegalArgumentException.class,
                () -> ZoneCommand.renamedZone(all, "Old Name", "Taken")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> ZoneCommand.renamedZone(all, "Old Name", "Old Name"));
        assertDoesNotThrow(() -> ZoneCommand.renamedZone(all, "Old Name", "taken"));
    }

}
