package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CropEatingAnimalsTest {
    @Test
    void includesGroundPlantEatersButNotFlyingBirdsPredatorsOrInsects() {
        for (String id : new String[]{"minecraft:cow", "minecraft:goat", "minecraft:turtle",
                "minecraft:rabbit", "naturalist:duck", "naturalist:ostrich", "naturalist:elephant",
                "naturalist:deer", "naturalist:tortoise", "naturalist:boar"}) {
            assertTrue(CropEatingAnimals.includes(id), id);
        }
        for (String id : new String[]{"minecraft:parrot", "minecraft:bee", "minecraft:wolf",
                "naturalist:bird", "naturalist:vulture", "naturalist:butterfly", "naturalist:lion",
                "naturalist:alligator", "other:duck"}) {
            assertFalse(CropEatingAnimals.includes(id), id);
        }
    }

    @Test
    void replacesExistingCropRaidersWithoutRemovingUnrelatedGoals() {
        assertTrue(CropEatingAnimals.replacesGoal("net.minecraft.world.entity.animal.Rabbit$RaidGardenGoal"));
        assertTrue(CropEatingAnimals.replacesGoal("net.minecraft.world.entity.animal.rabbit.Rabbit$RaidGardenGoal"));
        assertTrue(CropEatingAnimals.replacesGoal(
                "com.crispytwig.naturalist.world.entity.ai.goal.RatHarvestCropsGoal"));
        assertFalse(CropEatingAnimals.replacesGoal("net.minecraft.world.entity.ai.goal.PanicGoal"));
    }
}
