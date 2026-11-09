package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NurseryTreeCounterTest {
    @Test
    void countsConnectedTrunkOnceEvenWithSeveralFruitsAtDifferentHeights() {
        var counts = NurseryTreeCounter.count(Map.of(
                0, Set.<String>of(), 1, Set.of("Apple"), 2, Set.of("Apple")),
                p -> List.of(p - 1, p + 1));
        assertEquals(1, counts.total());
        assertEquals(Map.of("Apple", 1), counts.byType());
    }

    @Test
    void separatesTreesOfSameSpeciesAndIgnoresLogsWithoutFruit() {
        var counts = NurseryTreeCounter.count(Map.of(
                0, Set.of("Almond"), 1, Set.<String>of(),
                10, Set.of("Almond"), 20, Set.of("Apple"), 30, Set.<String>of()),
                p -> List.of(p - 1, p + 1));
        assertEquals(3, counts.total());
        assertEquals(Map.of("Almond", 2, "Apple", 1), counts.byType());
    }

    @Test
    void mixedTreeCountsOnceInTotalAndOncePerAttachedType() {
        var counts = NurseryTreeCounter.count(Map.of(0, Set.of("Apple", "Almond"), 1, Set.of("Apple")),
                p -> List.of(p - 1, p + 1));
        assertEquals(1, counts.total());
        assertEquals(Map.of("Almond", 1, "Apple", 1), counts.byType());
    }

    @Test
    void handlesEmptyNurseryAndCyclicBranchesWithoutMutatingInput() {
        assertEquals(0, NurseryTreeCounter.count(Map.<Integer, Set<String>>of(), p -> List.of(p)).total());
        var logs = Map.of(0, Set.of("Apple"), 1, Set.<String>of(), 2, Set.of("Apple"));
        assertEquals(1, NurseryTreeCounter.count(logs, p -> List.of(0, 1, 2)).total());
        assertEquals(3, logs.size());
    }
}
