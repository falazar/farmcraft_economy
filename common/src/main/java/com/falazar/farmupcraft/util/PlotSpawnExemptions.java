package com.falazar.farmupcraft.util;

public final class PlotSpawnExemptions {
    public static final String BRED_OFFSPRING = "farmupcraft_bred_offspring";
    public static final String SPAWNER_ORIGIN = "farmupcraft_spawner_origin";

    private PlotSpawnExemptions() {
    }

    public static boolean allows(String entityId, boolean bredOffspring) {
        if (entityId.startsWith("lycanitesmobs:")) return false;
        return "naturalist:duck".equals(entityId) || bredOffspring;
    }
}
