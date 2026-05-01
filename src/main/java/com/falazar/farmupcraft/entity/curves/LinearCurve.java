package com.falazar.farmupcraft.entity.curves;

import net.minecraft.world.phys.Vec3;

public class LinearCurve implements MotionCurve {
    private final Vec3 start;
    private final Vec3 end;

    public LinearCurve(Vec3 start, Vec3 end) {
        this.start = start;
        this.end = end;
    }

    @Override
    public Vec3 compute(double t) {
        t = Math.max(0, Math.min(1, t)); // Clamp to [0, 1]
        return start.add(end.subtract(start).scale(t));
    }
}
