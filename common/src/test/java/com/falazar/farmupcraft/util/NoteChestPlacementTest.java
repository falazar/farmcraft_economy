package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class NoteChestPlacementTest {
    @Test
    void centralCandidatesBeatCornersAndRoofsWithRandomNearbyChoices() {
        var center = new NoteChestPlacement.Spot(0, 64, 0);
        var nearby = List.of(center, new NoteChestPlacement.Spot(1, 64, 0),
                new NoteChestPlacement.Spot(0, 64, 4));
        var valid = new java.util.ArrayList<>(nearby);
        valid.add(new NoteChestPlacement.Spot(-5, 64, -5));
        valid.add(new NoteChestPlacement.Spot(0, 80, 0));
        var chosen = new HashSet<NoteChestPlacement.Spot>();
        var random = new Random(42);
        for (int i = 0; i < 100; i++) {
            var spot = NoteChestPlacement.choose(valid, center, random);
            assertTrue(nearby.contains(spot));
            chosen.add(spot);
        }
        assertEquals(new HashSet<>(nearby), chosen);
    }

    @Test
    void expandsToNearestValidBandAndHandlesNegativeHeightsAndEmptyList() {
        var center = new NoteChestPlacement.Spot(-100, -60, -100);
        var closest = new NoteChestPlacement.Spot(-94, -60, -100);
        var distant = new NoteChestPlacement.Spot(-80, -60, -100);
        assertEquals(closest, NoteChestPlacement.choose(List.of(distant, closest), center, new Random(1)));
        assertNull(NoteChestPlacement.choose(List.of(), center, new Random(1)));
    }
}
