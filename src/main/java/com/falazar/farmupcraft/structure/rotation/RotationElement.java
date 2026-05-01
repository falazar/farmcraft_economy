package com.falazar.farmupcraft.structure.rotation;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;

public interface RotationElement {
    Rotation resolve(RandomSource random);

    static RotationElement fixed() {
        return new FixedRotation(Rotation.NONE);
    }

    static RotationElement fixed(Rotation rotation) {
        return new FixedRotation(rotation);
    }

    static RotationElement random90() {
        return new RandomRotation(Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90);
    }

    static RotationElement randomAny() {
        return new RandomRotation(Rotation.values());
    }
}

