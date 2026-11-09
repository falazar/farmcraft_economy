package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StableBiomeCropsTest {
    @Test
    void selectionUsesBiomeNameAndNotRegistryOrTagOrder() {
        List<String> crops = List.of("pam:onion", "pam:garlic", "pam:tomato", "pam:rice", "pam:corn");
        var reversed = new ArrayList<>(crops);
        Collections.reverse(reversed);
        var first = StableBiomeCrops.select(crops, item -> item, 123, "bop:tundra", 3);
        assertEquals(first, StableBiomeCrops.select(reversed, item -> item, 123, "bop:tundra", 3));
        // Inserting an unrelated biome cannot affect this name's allocation.
        StableBiomeCrops.select(crops, item -> item, 123, "other:new_biome", 3);
        assertEquals(first, StableBiomeCrops.select(crops, item -> item, 123, "bop:tundra", 3));
        assertEquals(3, first.size());
        assertEquals(3, first.stream().distinct().count());
        assertEquals(List.of("pam:corn", "pam:rice", "pam:tomato", "pam:garlic", "pam:onion"), reversed);
    }

    @Test
    void selectionCapsAtAvailableItemsAndHandlesZero() {
        assertEquals(2, StableBiomeCrops.select(List.of("a", "b"), item -> item, 5, "test:biome", 15).size());
        assertTrue(StableBiomeCrops.select(List.of("a", "b"), item -> item, 5, "test:biome", 0).isEmpty());
        assertTrue(StableBiomeCrops.select(List.<String>of(), item -> item, 5, "test:biome", 15).isEmpty());
    }
}
