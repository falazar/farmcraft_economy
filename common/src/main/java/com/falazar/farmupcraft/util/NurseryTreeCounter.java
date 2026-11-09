package com.falazar.farmupcraft.util;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

public final class NurseryTreeCounter {
    private NurseryTreeCounter() {}

    public record Counts(int total, Map<String, Integer> byType) {
        public Counts {
            byType = Map.copyOf(byType);
        }
    }

    public static <P> Counts count(Map<P, Set<String>> logs, Function<P, Iterable<P>> neighbors) {
        var remaining = new HashSet<>(logs.keySet());
        var counts = new TreeMap<String, Integer>();
        var pending = new ArrayDeque<P>();
        int total = 0;
        for (P root : logs.keySet()) {
            if (!remaining.remove(root)) continue;
            pending.add(root);
            var fruits = new HashSet<String>();
            while (!pending.isEmpty()) {
                P log = pending.removeFirst();
                fruits.addAll(logs.get(log));
                for (P neighbor : neighbors.apply(log)) {
                    if (remaining.remove(neighbor)) pending.addLast(neighbor);
                }
            }
            if (!fruits.isEmpty()) {
                total++;
                for (String fruit : fruits) counts.merge(fruit, 1, Integer::sum);
            }
        }
        return new Counts(total, counts);
    }
}
