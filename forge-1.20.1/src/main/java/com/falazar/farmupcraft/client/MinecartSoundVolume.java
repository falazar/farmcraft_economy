package com.falazar.farmupcraft.client;

import net.minecraft.resources.ResourceLocation;

public final class MinecartSoundVolume {
    private MinecartSoundVolume() {}

    public static float adjust(ResourceLocation sound, float volume) {
        if (!sound.getNamespace().equals("minecraft")) return volume;
        return switch (sound.getPath()) {
            case "entity.minecart.riding", "entity.minecart.inside", "entity.minecart.inside.underwater" ->
                    volume * 0.5F;
            default -> volume;
        };
    }
}
