package com.falazar.farmupcraft.entity.curves;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class VerticalHopCurve implements SerializableMotionCurve {
    private final Vec3 start;
    private final Vec3 end;
    private final double height;

    public VerticalHopCurve(Vec3 start, Vec3 end, double height) {
        this.start = start;
        this.end = end;
        this.height = height;
    }

    public static final Codec<VerticalHopCurve> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Vec3.CODEC.fieldOf("start").forGetter(c -> c.start),
                    Vec3.CODEC.fieldOf("end").forGetter(c -> c.end),
                    Codec.DOUBLE.fieldOf("height").forGetter(c -> c.height)
            ).apply(instance, VerticalHopCurve::new)
    );

    static {
        MotionCurves.register("vertical_hop", CODEC);
    }

    @Override
    public Vec3 compute(double t) {
        double x = Mth.lerp(t, start.x, end.x);
        double z = Mth.lerp(t, start.z, end.z);
        double baseY = Mth.lerp(t, start.y, end.y);
        double arc = height * 4 * t * (1 - t);
        return new Vec3(x, baseY + arc, z);
    }

    @Override
    public Codec<? extends SerializableMotionCurve> codec() {
        return CODEC;
    }

    @Override
    public String getRegistryName() {
        return "vertical_hop";
    }
}

