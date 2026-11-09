package com.falazar.farmupcraft;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FruitHarvestSkillTest {
    @Test void exactlyFiftyOfOneHundredRollsReduceProgress() {
        int reductions = 0;
        for (int roll = 0; roll < 100; roll++) {
            if (FruitHarvestSkill.shouldReduce(roll)) reductions++;
        }
        assertEquals(50, reductions);
        assertThrows(IllegalArgumentException.class, () -> FruitHarvestSkill.shouldReduce(100));
    }

    @Test void targetsOnlyDifferentFruitOrNutsWithPositiveProgress() {
        assertEquals(List.of("minecraft:apple", "pamhc2trees:walnutitem"),
                FruitHarvestSkill.candidates(Map.of(
                        "pamhc2trees:lemonitem", 49, "minecraft:apple", 4,
                        "pamhc2trees:walnutitem", 38, "pamhc2trees:orangeitem", 0,
                        "pamhc2trees:peachitem", -1, "pamhc2trees:maplesyrupitem", 9,
                        "pamhc2trees:cinnamonitem", 9, "minecraft:paper", 9,
                        "pamhc2trees:pamlemon", 2), "pamhc2trees:lemonitem"));
        assertTrue(FruitHarvestSkill.candidates(Map.of("minecraft:apple", 4), "minecraft:apple").isEmpty());
    }

    @Test void penaltiesReduceOnlyEffectiveProgressAndFloorAtZero() {
        assertEquals(202, FruitHarvestSkill.effectiveCount(202, 0));
        assertEquals(201, FruitHarvestSkill.effectiveCount(202, 1));
        assertEquals(0, FruitHarvestSkill.effectiveCount(1, 1));
        assertEquals(0, FruitHarvestSkill.effectiveCount(0, 5));
        assertEquals(0, FruitHarvestSkill.effectiveCount(-1, 5));
        assertEquals(1, FruitHarvestSkill.effectiveCount(6, 5));
        assertEquals(99, FruitHarvestSkill.effectiveCount(100, 1));
    }
}
