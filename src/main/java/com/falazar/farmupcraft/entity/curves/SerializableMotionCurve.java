package com.falazar.farmupcraft.entity.curves;

import com.mojang.serialization.Codec;
import net.minecraft.world.phys.Vec3;

public interface SerializableMotionCurve {
    Vec3 compute(double t);

    Codec<? extends SerializableMotionCurve> codec();

    String getRegistryName();
}
