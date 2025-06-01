package com.falazar.farmupcraft.entity.curves;

import com.falazar.farmupcraft.entity.MotionCurve;
import net.minecraft.world.phys.Vec3;

public class EasedMotionCurve implements MotionCurve {
    private final MotionCurve base;
    private final Easing easing;

    public EasedMotionCurve(MotionCurve base, Easing easing) {
        this.base = base;
        this.easing = easing;
    }

    @Override
    public Vec3 compute(double t) {
        double easedT = easing.clamped(t, 0.0, 1.0);
        return base.compute(easedT);
    }
}
