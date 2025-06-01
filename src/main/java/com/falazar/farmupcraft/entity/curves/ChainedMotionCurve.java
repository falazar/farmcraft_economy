package com.falazar.farmupcraft.entity.curves;

import com.falazar.farmupcraft.entity.MotionCurve;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

public class ChainedMotionCurve implements MotionCurve {

    public record Segment(MotionCurve curve, double weight) {}

    private final List<Segment> segments = new ArrayList<>();
    private double totalWeight = 0.0;

    public ChainedMotionCurve addSegment(MotionCurve curve, double weight) {
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

        // Clamp to last segment if t > 1
        return segments.get(segments.size() - 1).curve.compute(1.0);
    }
}
