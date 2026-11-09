package com.falazar.farmupcraft.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

public final class StableBiomeCrops {
    private StableBiomeCrops() {}

    public static <T> List<T> select(List<T> crops, Function<T, String> itemId,
                                     long worldSeed, String biomeId, int count) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < biomeId.length(); i++) {
            hash = (hash ^ biomeId.charAt(i)) * 0x100000001b3L;
        }
        List<T> sorted = new ArrayList<>(crops);
        sorted.sort(Comparator.comparing(itemId));
        Collections.shuffle(sorted, new Random(worldSeed ^ hash));
        return new ArrayList<>(sorted.subList(0, Math.min(count, sorted.size())));
    }
}
