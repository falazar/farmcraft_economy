package com.falazar.farmupcraft.client;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecartSoundVolumeTest {
    @Test
    void halvesRollingAndPassengerSoundsIncludingUnderwaterWithoutCompounding() {
        for (String path : List.of("entity.minecart.riding", "entity.minecart.inside",
                "entity.minecart.inside.underwater")) {
            Identifier sound = Identifier.withDefaultNamespace(path);
            assertEquals(0.35F, MinecartSoundVolume.adjust(sound, 0.7F));
            assertEquals(0.35F, MinecartSoundVolume.adjust(sound, 0.7F));
            assertEquals(0.0F, MinecartSoundVolume.adjust(sound, 0.0F));
        }
    }

    @Test
    void leavesOtherSoundsAndOtherNamespacesUnchanged() {
        for (String id : List.of("minecraft:entity.boat.paddle_water", "minecraft:weather.rain",
                "minecraft:block.rail.place", "othermod:entity.minecart.riding")) {
            assertEquals(0.8F, MinecartSoundVolume.adjust(Identifier.parse(id), 0.8F));
        }
    }
}
