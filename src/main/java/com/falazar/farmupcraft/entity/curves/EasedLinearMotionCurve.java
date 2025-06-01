package com.falazar.farmupcraft.entity.curves;

import com.falazar.farmupcraft.entity.MotionCurve;
import net.minecraft.world.phys.Vec3;

public class EasedLinearMotionCurve implements MotionCurve {
    private final Vec3 start;
    private final Vec3 end;
    private final Easing easing;

    public EasedLinearMotionCurve(Vec3 start, Vec3 end, Easing easing) {
        this.start = start;
        this.end = end;
        this.easing = easing;
    }

    @Override
    public Vec3 compute(double t) {
        float eased = easing.clamped((float) t, 0f, 1f);
        return start.lerp(end, eased);
    }
}
