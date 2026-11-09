package com.falazar.farmupcraft.util;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class CropBiomeQuery {
    private CropBiomeQuery() {}

    public static List<String> crops(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Specify one to three crops.");
        var crops = Arrays.asList(input.trim().split("\\s+"));
        if (crops.size() > 3) throw new IllegalArgumentException("Specify at most three crops.");
        return List.copyOf(crops);
    }

    public static <T> Set<T> shared(List<String> crops, Function<String, Set<T>> biomes) {
        Set<T> result = null;
        for (String crop : crops) {
            Set<T> next = biomes.apply(crop);
            if (result == null) result = new LinkedHashSet<>(next);
            else result.retainAll(next);
        }
        if (result == null) throw new IllegalArgumentException("Specify one to three crops.");
        return result;
    }
}
