package com.falazar.farmupcraft.entity.curves;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class ArcCurve implements MotionCurve {
    private final Vec3 start;
    private final Vec3 end;
    private final double arcHeight;

    public ArcCurve(Vec3 start, Vec3 end, double arcHeight) {
        this.start = start;
        this.end = end;
        this.arcHeight = arcHeight;
    }

    @Override
    public Vec3 compute(double t) {
        t = Mth.clamp(t, 0.0, 1.0);

        double x = Mth.lerp(t, start.x, end.x);
        double z = Mth.lerp(t, start.z, end.z);

        double y = Mth.lerp(t, start.y, end.y);
        double heightOffset = Math.sin(Math.PI * t) * arcHeight; // smooth arc
        y += heightOffset;

        return new Vec3(x, y, z);
    }
}

