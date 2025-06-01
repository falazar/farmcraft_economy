package com.falazar.farmupcraft.entity;


import net.minecraft.world.phys.Vec3;

@FunctionalInterface
public interface MotionCurve {
    Vec3 compute(double t); // t ∈ [0.0, 1.0]
}
