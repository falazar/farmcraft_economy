package com.falazar.farmupcraft.entity.curves;

import com.falazar.farmupcraft.entity.MotionCurve;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class VerticalHopCurve implements MotionCurve {
    private final Vec3 start;
    private final Vec3 end;
    private final double height;

    public VerticalHopCurve(Vec3 start, Vec3 end, double height) {
        this.start = start;
        this.end = end;
        this.height = height;
    }

    public Vec3 compute(double t) {
        t = Mth.clamp(t, 0.0, 1.0); // Always within bounds

        // Linear horizontal interpolation
        double x = Mth.lerp(t, start.x, end.x);
        double z = Mth.lerp(t, start.z, end.z);

        // Parabolic height curve: peak at t = 0.5
        double baseY = Mth.lerp(t, start.y, end.y);
        double arc = height * 4 * t * (1 - t); // max at t=0.5, 0 at t=0 and t=1
        double y = baseY + arc;

        return new Vec3(x, y, z);
    }
}
