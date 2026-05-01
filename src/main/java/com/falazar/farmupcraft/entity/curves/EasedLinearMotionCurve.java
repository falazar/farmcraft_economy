package com.falazar.farmupcraft.entity.curves;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

public class EasedLinearMotionCurve implements SerializableMotionCurve {
    private final Vec3 start;
    private final Vec3 end;
    private final Easing easing;

    public EasedLinearMotionCurve(Vec3 start, Vec3 end, Easing easing) {
        this.start = start;
        this.end = end;
        this.easing = easing;
    }

    public static final Codec<EasedLinearMotionCurve> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Vec3.CODEC.fieldOf("start").forGetter(c -> c.start),
                    Vec3.CODEC.fieldOf("end").forGetter(c -> c.end),
                    Easing.CODEC.fieldOf("easing").forGetter(c -> c.easing)
            ).apply(instance, EasedLinearMotionCurve::new)
    );

    static {
        MotionCurves.register("eased_linear", CODEC);
    }

    @Override
    public Vec3 compute(double t) {
        float eased = easing.clamped((float) t, 0f, 1f);
        return start.lerp(end, eased);
    }

    @Override
    public Codec<? extends SerializableMotionCurve> codec() {
        return CODEC;
    }

    @Override
    public String getRegistryName() {
        return "eased_linear";
    }
}

