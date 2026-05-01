package com.falazar.farmupcraft.structure.rotation;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;

public class RandomRotation implements RotationElement {
    private final Rotation[] options;

    public RandomRotation(Rotation... options) {
        this.options = options.length > 0 ? options : Rotation.values();
    }

    @Override
    public Rotation resolve(RandomSource random) {
        return options[random.nextInt(options.length)];
    }
}
