package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildPreviewGeometryTest {
    @Test
    void singleSourceAndDestinationEmitSixExposedFacesEachAndCullInternalSharedFaces() {
        var source = List.of(new BuildPreviewGeometry.Entry(10, 64, 20, 0x808080));
        var dest = List.of(
                new BuildPreviewGeometry.Entry(12, 64, 20, 0xA07040),
                new BuildPreviewGeometry.Entry(13, 64, 20, 0xA07040));
        var quads = BuildPreviewGeometry.buildQuads(source, dest);
        // 1 isolated source cube = 6 faces; 2 adjacent destination cubes = 10 faces (shared face culled)
        assertEquals(16, quads.size());
    }

    @Test
    void overlappingSourceAndDestinationPositionRendersDestinationGhostOnly() {
        var source = List.of(
                new BuildPreviewGeometry.Entry(0, 64, 0, 0x707070),
                new BuildPreviewGeometry.Entry(0, 64, 1, 0x707070));
        var dest = List.of(
                new BuildPreviewGeometry.Entry(0, 64, 1, 0x707070),
                new BuildPreviewGeometry.Entry(0, 64, 2, 0x707070));
        var quads = BuildPreviewGeometry.buildQuads(source, dest);
        // Source at (0,64,0) has 5 faces (its +Z neighbor (0,64,1) is in sourcePositions).
        // Source at (0,64,1) is skipped because it is also in destPositions.
        // Dest at (0,64,1) and (0,64,2) form a 2-block volume = 10 faces.
        assertEquals(15, quads.size());
    }

    @Test
    void validationRejectsOversizedOrNegativeExpirationPayloads() {
        assertThrows(IllegalArgumentException.class, () -> BuildPreviewGeometry.validate(
                "minecraft:overworld",
                List.of(new BuildPreviewGeometry.Entry(0, 64, 0, 0)),
                List.of(),
                -1L));
    }
}
