package com.falazar.farmupcraft.entity.curves;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

public class DefaultCurve implements SerializableMotionCurve {
    private final Vec3 pos;
    public static final Codec<DefaultCurve> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Vec3.CODEC.fieldOf("position").forGetter(c -> c.pos)
            ).apply(instance, DefaultCurve::new)
    );
    public DefaultCurve(Vec3 pos) {
        this.pos = pos;
    }

    @Override
    public Vec3 compute(double t) {
        return pos;
    }

    @Override
    public Codec<? extends SerializableMotionCurve> codec() {
        return CODEC;
    }

    @Override
    public String getRegistryName() {
        return "default";
    }
}
