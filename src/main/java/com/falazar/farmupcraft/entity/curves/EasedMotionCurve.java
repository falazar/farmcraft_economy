package com.falazar.farmupcraft.entity.curves;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

public class EasedMotionCurve implements SerializableMotionCurve {
    private final SerializableMotionCurve base;
    private final Easing easing;

    public EasedMotionCurve(SerializableMotionCurve base, Easing easing) {
        this.base = base;
        this.easing = easing;
    }

    public static final Codec<EasedMotionCurve> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    MotionCurves.DISPATCH_CODEC.fieldOf("base").forGetter(c -> (SerializableMotionCurve) c.base),
                    Easing.CODEC.fieldOf("easing").forGetter(c -> c.easing)
            ).apply(instance, EasedMotionCurve::new)
    );

    static {
        MotionCurves.register("eased_motion", CODEC);
    }

    @Override
    public Vec3 compute(double t) {
        double easedT = easing.clamped(t, 0.0, 1.0);
        return base.compute(easedT);
    }

    @Override
    public Codec<? extends SerializableMotionCurve> codec() {
        return CODEC;
    }

    @Override
    public String getRegistryName() {
        return "eased_motion";
    }
}

