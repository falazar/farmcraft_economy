package com.falazar.farmupcraft.entity.curves;

import com.falazar.farmupcraft.entity.MotionCurve;
import net.minecraft.world.phys.Vec3;

public class BurstCurve implements MotionCurve {
    private final Vec3 origin;
    private final Vec3 direction;
    private final double distance;

    public BurstCurve(Vec3 origin, Vec3 direction, double distance) {
        this.origin = origin;
        this.direction = direction.normalize();
        this.distance = distance;
    }

    @Override
    public Vec3 compute(double t) {
        double scale = 1.0 - Math.pow(1 - t, 2); // slow-down curve
        return origin.add(direction.scale(scale * distance));
    }
}
