package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.GameZone;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TesterCommandTest {
    @Test
    void structureBoundsIncludeAllFacesAndTheSingleBlockCase() {
        var positions = new ArrayList<BlockPos>();
        TesterCommand.blocksInBounds(new BoundingBox(-2, -1, 3, 0, 0, 4))
                .forEach(pos -> positions.add(pos.immutable()));
        assertEquals(12, positions.size());
        assertEquals(12, Set.copyOf(positions).size());
        assertTrue(positions.contains(new BlockPos(-2, -1, 3)));
        assertTrue(positions.contains(new BlockPos(0, 0, 4)));
        positions.clear();
        TesterCommand.blocksInBounds(new BoundingBox(7, -64, 9, 7, -64, 9))
                .forEach(pos -> positions.add(pos.immutable()));
        assertEquals(List.of(new BlockPos(7, -64, 9)), positions);
    }

    @Test
    void rectangularFloodFillCombinesLandColorsAndConvertsNegativeBlockCoordinates() {
        int[][] grid = {
                {0, 0, 0, 0, 0, 0},
                {0, 1, 2, 3, 0, 0},
                {0, 0, 0, 0, 0, 0}};
        var regions = TesterCommand.regions(grid, new BlockPos(-4, 64, -4), 1, 16);
        assertEquals(2, regions.size());
        var water = regions.stream().filter(TesterCommand.Region::water).findFirst().orElseThrow();
        var land = regions.stream().filter(region -> !region.water()).findFirst().orElseThrow();
        assertTrue(water.touchesEdge());
        assertFalse(water.reportable());
        assertEquals(15, water.size());
        assertFalse(land.touchesEdge());
        assertEquals(3, land.size());
        assertEquals(List.of(new ChunkPos(-1, -1), new ChunkPos(0, -1), new ChunkPos(1, -1)), land.chunks());
        assertTrue(land.reportable());
        assertTrue(land.persistable());
        assertEquals(18, regions.stream().mapToInt(TesterCommand.Region::size).sum());
    }

    @Test
    void lakesNeedThreeUniqueChunksToPersistButTwoToReport() {
        for (int length = 1; length <= 3; length++) {
            int[][] grid = new int[3][length + 2];
            for (int[] row : grid) Arrays.fill(row, 1);
            for (int x = 1; x <= length; x++) grid[1][x] = 0;
            var lake = TesterCommand.regions(grid, BlockPos.ZERO, 1, 16).stream()
                    .filter(TesterCommand.Region::water).findFirst().orElseThrow();
            assertEquals(length, lake.size());
            assertEquals(length, lake.chunks().size());
            assertEquals(length >= 2, lake.reportable());
            assertEquals(length >= 3, lake.persistable());
        }
        var tiny = new TesterCommand.Region(false, 4, false, List.of(ChunkPos.ZERO));
        assertFalse(tiny.reportable());
        assertFalse(tiny.persistable());
        var twoChunkIsland = new TesterCommand.Region(false, 2, false, List.of(ChunkPos.ZERO, new ChunkPos(1, 0)));
        assertTrue(twoChunkIsland.persistable());
    }

    @Test
    void diagonalWaterIsNotConnectedAndEdgeRegionsAreNeverZones() {
        int[][] grid = {{0, 1, 1}, {1, 0, 1}, {1, 1, 1}};
        var water = TesterCommand.regions(grid, BlockPos.ZERO, 1, 16).stream()
                .filter(TesterCommand.Region::water).toList();
        assertEquals(2, water.size());
        assertTrue(water.getFirst().touchesEdge());
        assertFalse(water.getLast().touchesEdge());
        assertEquals(1, water.getLast().size());
        var edge = new TesterCommand.Region(true, 20, true, List.of(ChunkPos.ZERO, new ChunkPos(1, 0), new ChunkPos(2, 0)));
        assertFalse(edge.reportable());
        assertFalse(edge.persistable());
    }

    @Test
    void multipleEightBlockSamplesInOneChunkDoNotCreateATinyIsland() {
        int[][] grid = new int[5][5];
        grid[2][2] = 2;
        grid[2][3] = 3;
        var island = TesterCommand.regions(grid, new BlockPos(4, 64, 4), 1, 8).stream()
                .filter(region -> !region.water()).findFirst().orElseThrow();
        assertEquals(2, island.size());
        assertEquals(List.of(ChunkPos.ZERO), island.chunks());
        assertFalse(island.reportable());
        assertFalse(island.persistable());
    }

    @Test
    void invalidBoundsAndMalformedGridFailInsteadOfSilentlyClassifyingWater() {
        assertThrows(IllegalArgumentException.class, () -> TesterCommand.validateScan(0, 8));
        assertThrows(IllegalArgumentException.class, () -> TesterCommand.validateScan(101, 8));
        assertThrows(IllegalArgumentException.class, () -> TesterCommand.validateScan(1, 0));
        assertThrows(IllegalArgumentException.class, () -> TesterCommand.validateScan(1, 17));
        assertDoesNotThrow(() -> TesterCommand.validateScan(100, 8));
        assertThrows(IllegalArgumentException.class, () -> TesterCommand.regions(new int[0][], BlockPos.ZERO, 1, 8));
        assertThrows(IllegalArgumentException.class, () -> TesterCommand.regions(new int[][]{{0}, {0, 1}}, BlockPos.ZERO, 1, 8));
        assertThrows(IllegalArgumentException.class, () -> TesterCommand.regions(new int[][]{{0}, null}, BlockPos.ZERO, 1, 8));
        assertThrows(IllegalArgumentException.class, () -> TesterCommand.regions(new int[][]{{4}}, BlockPos.ZERO, 1, 8));
    }

    @Test
    void duplicateZonesAreDetectedByCenterRegardlessOfTypeOrName() {
        var chunks = List.of(new ChunkPos(-2, 1), new ChunkPos(-1, 1));
        var lake = GameZone.fromChunks(chunks, GameZone.ZoneType.LAKE);
        var island = GameZone.fromChunks(chunks, GameZone.ZoneType.ISLAND);
        assertNotEquals(lake.getUuid(), island.getUuid());
        assertTrue(TesterCommand.duplicateZone(List.of(lake), island));
        assertFalse(TesterCommand.duplicateZone(List.of(), island));
        assertFalse(TesterCommand.duplicateZone(List.of(lake),
                GameZone.fromChunks(List.of(new ChunkPos(10, 10)), GameZone.ZoneType.LAKE)));
    }

}
