package com.falazar.farmupcraft.entity.curves;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

public class ChainedMotionCurve implements SerializableMotionCurve {
    public record Segment(SerializableMotionCurve curve, double weight) {}
    public static final Codec<Segment> SEGMENT_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    MotionCurves.DISPATCH_CODEC.fieldOf("curve").forGetter(Segment::curve),
                    Codec.DOUBLE.fieldOf("weight").forGetter(Segment::weight)
            ).apply(instance, Segment::new)
    );

    public static final Codec<ChainedMotionCurve> CODEC = SEGMENT_CODEC.listOf().xmap(
            list -> {
                ChainedMotionCurve chained = new ChainedMotionCurve();
                list.forEach(s -> chained.addSegment(s.curve, s.weight()));
                return chained;
            },
            ChainedMotionCurve::getSegments
    );
    private final List<Segment> segments = new ArrayList<>();
    private double totalWeight = 0.0;

    public ChainedMotionCurve addSegment(SerializableMotionCurve curve, double weight) {
        segments.add(new Segment(curve, weight));
        totalWeight += weight;
        return this;
    }

    @Override
    public Vec3 compute(double t) {
        if (segments.isEmpty()) return Vec3.ZERO;

        double accumulated = 0.0;
        for (Segment segment : segments) {
            double startT = accumulated / totalWeight;
            double endT = (accumulated + segment.weight) / totalWeight;

            if (t <= endT) {
                double localT = (t - startT) / (endT - startT);
                return segment.curve.compute(localT);
            }

            accumulated += segment.weight;
        }

        return segments.get(segments.size() - 1).curve.compute(1.0);
    }

    public List<Segment> getSegments() {
        return segments;
    }

    @Override
    public Codec<? extends SerializableMotionCurve> codec() {
        return CODEC;
    }

    @Override
    public String getRegistryName() {
        return "chained";
    }
}


