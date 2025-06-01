package com.falazar.farmupcraft.entity.curves;

import com.falazar.farmupcraft.entity.MotionCurve;
import net.minecraft.world.phys.Vec3;

public class SpiralCurve implements MotionCurve {
    private final Vec3 center;
    private final double height;
    private final double radius;

    public SpiralCurve(Vec3 center, double height, double radius) {
        this.center = center;
        this.height = height;
        this.radius = radius;
    }

    @Override
    public Vec3 compute(double t) {
        double angle = t * 4 * Math.PI; // two full spirals
        double x = center.x + radius * Math.cos(angle);
        double z = center.z + radius * Math.sin(angle);
        double y = center.y + height * t;
        return new Vec3(x, y, z);
    }
}
