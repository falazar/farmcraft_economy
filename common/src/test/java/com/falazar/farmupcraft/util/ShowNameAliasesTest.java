package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShowNameAliasesTest {
    @Test
    void cropsUseReadableNamesAndResolveToExactIds() {
        assertEquals(Map.of("onion", "pamhc2crops:onionitem", "wheat", "minecraft:wheat_seeds",
                "beetroot", "minecraft:beetroot_seeds", "carrot", "minecraft:carrot"),
                ShowNameAliases.create(List.of("pamhc2crops:onionitem", "minecraft:wheat_seeds",
                        "minecraft:beetroot_seeds", "minecraft:carrot"), true));
    }

    @Test
    void biomesFromAnyNamespaceUsePathsButCollisionsStayQualified() {
        assertEquals(Map.of("plains", "minecraft:plains", "orchard", "biomesoplenty:orchard",
                "one:forest", "one:forest", "two:forest", "two:forest"),
                ShowNameAliases.create(List.of("minecraft:plains", "biomesoplenty:orchard",
                        "one:forest", "two:forest", "one:forest"), false));
    }

    @Test
    void cropAndSeedPairsSuggestExactlyOneCropName() {
        assertEquals(Map.of("onion", "pamhc2crops:onionitem",
                        "wheat", "minecraft:wheat_seeds", "beetroot", "minecraft:beetroot_seeds",
                        "tomato", "pamhc2crops:tomatoseeditem"),
                ShowNameAliases.create(List.of("pamhc2crops:onionitem", "pamhc2crops:onionseeditem",
                        "minecraft:wheat", "minecraft:wheat_seeds", "minecraft:beetroot",
                        "minecraft:beetroot_seeds", "pamhc2crops:tomatoseeditem"), true));
    }

    @Test
    void edibleSeedsKeepTheirNameAndOnlyLoseTheExtraPlantingSeedSuffix() {
        assertEquals(Map.of("mustardseeds", "pamhc2crops:mustardseedsitem",
                        "sesameseeds", "pamhc2crops:sesameseedsitem",
                        "coffeebean", "pamhc2crops:coffeebeanitem",
                        "peas", "pamhc2crops:peasitem"),
                ShowNameAliases.create(List.of("pamhc2crops:mustardseedsitem",
                        "pamhc2crops:mustardseedsseeditem", "pamhc2crops:sesameseedsitem",
                        "pamhc2crops:sesameseedsseeditem", "pamhc2crops:coffeebeanitem",
                        "pamhc2crops:coffeebeanseeditem", "pamhc2crops:peasitem",
                        "pamhc2crops:peasseeditem"), true));
    }

    @Test
    void seedOnlyEdibleSeedCropsKeepTheirFullCropName() {
        assertEquals(Map.of("mustardseeds", "pamhc2crops:mustardseedsseeditem",
                        "sesameseeds", "pamhc2crops:sesameseedsseeditem"),
                ShowNameAliases.create(List.of("pamhc2crops:mustardseedsseeditem",
                        "pamhc2crops:sesameseedsseeditem"), true));
    }

    @Test
    void cropAliasCollisionsStayQualified() {
        assertEquals(Map.of("pamhc2crops:onionitem", "pamhc2crops:onionitem", "other:onion", "other:onion"),
                ShowNameAliases.create(List.of("pamhc2crops:onionitem", "other:onion"), true));
    }
}
