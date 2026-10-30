package com.falazar.farmupcraft.util;

import java.util.Set;

public final class CropEatingAnimals {
    private static final Set<String> VANILLA = Set.of("cow", "mooshroom", "sheep", "pig", "chicken",
            "goat", "horse", "donkey", "mule", "llama", "trader_llama", "rabbit", "turtle",
            "panda", "camel", "sniffer");
    private static final Set<String> NATURALIST = Set.of("duck", "ostrich", "elephant", "mammoth",
            "deer", "boar", "capybara", "zebra", "giraffe", "hippo", "rhino", "tortoise", "turkey", "rat");

    private CropEatingAnimals() {
    }

    public static boolean includes(String id) {
        int colon = id.indexOf(':');
        if (colon < 0) return false;
        String namespace = id.substring(0, colon);
        String species = id.substring(colon + 1);
        return ("minecraft".equals(namespace) && VANILLA.contains(species))
                || ("naturalist".equals(namespace) && NATURALIST.contains(species));
    }

    public static boolean replacesGoal(String className) {
        return className.equals("net.minecraft.world.entity.animal.Rabbit$RaidGardenGoal")
                || className.equals("net.minecraft.world.entity.animal.rabbit.Rabbit$RaidGardenGoal")
                || className.equals("com.crispytwig.naturalist.world.entity.ai.goal.RatHarvestCropsGoal");
    }
}
