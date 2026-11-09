package com.falazar.farmupcraft.util;

public final class StructureDisplayNames {
    private StructureDisplayNames() {
    }

    public static String villageStructure(String name) {
        String prefix = "exclusives/";
        return name.startsWith(prefix) ? name.substring(prefix.length()) : name;
    }
}
