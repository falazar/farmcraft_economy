package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CropBiomeQueryTest {
    @Test
    void acceptsOneToThreeSpaceSeparatedCropNames() {
        assertEquals(List.of("carrot"), CropBiomeQuery.crops("carrot"));
        assertEquals(List.of("carrot", "potato"), CropBiomeQuery.crops(" carrot   potato "));
        assertEquals(List.of("carrot", "potato", "minecraft:wheat_seeds"),
                CropBiomeQuery.crops("carrot potato minecraft:wheat_seeds"));
        assertThrows(IllegalArgumentException.class, () -> CropBiomeQuery.crops(""));
        assertThrows(IllegalArgumentException.class, () -> CropBiomeQuery.crops("a b c d"));
    }

    @Test
    void intersectsEveryCropWithoutMutatingRulesOrSkippingInvalidLaterCrops() {
        var rules = Map.of("a", Set.of("plains", "forest"), "b", Set.of("forest", "swamp"),
                "c", Set.of("forest"), "d", Set.<String>of());
        assertEquals(rules.get("a"), CropBiomeQuery.shared(List.of("a"), rules::get));
        assertEquals(Set.of("forest"), CropBiomeQuery.shared(List.of("a", "b"), rules::get));
        assertEquals(Set.of("forest"), CropBiomeQuery.shared(List.of("a", "b", "c"), rules::get));
        assertTrue(CropBiomeQuery.shared(List.of("a", "d"), rules::get).isEmpty());
        assertEquals(Set.of("plains", "forest"), rules.get("a"));
        assertThrows(IllegalArgumentException.class, () -> CropBiomeQuery.shared(List.of("d", "missing"), crop -> {
            if (!rules.containsKey(crop)) throw new IllegalArgumentException("Invalid crop");
            return rules.get(crop);
        }));
    }
}
