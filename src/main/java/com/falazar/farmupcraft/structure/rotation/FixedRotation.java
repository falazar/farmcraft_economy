package com.falazar.farmupcraft.structure.rotation;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;

public class FixedRotation implements RotationElement {
    private final Rotation rotation;

    public FixedRotation(Rotation rotation) {
        this.rotation = rotation;
    }

    @Override
    public Rotation resolve(RandomSource random) {
        return rotation;
    }
}
