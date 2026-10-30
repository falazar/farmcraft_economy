package com.falazar.farmupcraft.util;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.stream.Collectors;

public final class ShowNameAliases {
    private ShowNameAliases() {
    }

    public static Map<String, String> create(Collection<String> ids, boolean crops) {
        Map<String, String> aliases = new TreeMap<>();
        var candidates = new HashSet<>(ids);
        if (crops) {
            for (String id : ids) {
                if (id.startsWith("pamhc2crops:") && id.endsWith("seeditem")) {
                    String produce = id.substring(0, id.length() - "seeditem".length()) + "item";
                    if (candidates.contains(produce)) candidates.remove(id);
                }
            }
            if (candidates.contains("minecraft:wheat_seeds")) candidates.remove("minecraft:wheat");
            if (candidates.contains("minecraft:beetroot_seeds")) candidates.remove("minecraft:beetroot");
        }
        var groups = candidates.stream().collect(Collectors.groupingBy(id -> shortName(id, crops)));
        groups.forEach((name, matches) -> {
            if (matches.size() == 1) aliases.put(name, matches.get(0));
            else matches.forEach(id -> aliases.put(id, id));
        });
        return aliases;
    }

    private static String shortName(String id, boolean crops) {
        String path = id.substring(id.indexOf(':') + 1);
        if (crops && id.startsWith("pamhc2crops:") && path.endsWith("item")) {
            if (path.endsWith("seeditem")) return path.substring(0, path.length() - "seeditem".length());
            return path.substring(0, path.length() - 4);
        }
        if (crops && id.equals("minecraft:wheat_seeds")) return "wheat";
        if (crops && id.equals("minecraft:beetroot_seeds")) return "beetroot";
        return path;
    }
}
