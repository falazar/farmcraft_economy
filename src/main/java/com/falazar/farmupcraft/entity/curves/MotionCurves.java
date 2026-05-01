package com.falazar.farmupcraft.entity.curves;

import com.mojang.serialization.Codec;

import java.util.HashMap;
import java.util.Map;

public class MotionCurves {
    public static final Map<String, Codec<? extends SerializableMotionCurve>> REGISTRY = new HashMap<>();

    public static void register(String name, Codec<? extends SerializableMotionCurve> codec) {
        REGISTRY.put(name, codec);
    }

    public static final Codec<SerializableMotionCurve> DISPATCH_CODEC = Codec.STRING.dispatchStable(
        SerializableMotionCurve::getRegistryName,
        name -> {
            Codec<? extends SerializableMotionCurve> codec = REGISTRY.get(name);
            if (codec == null) throw new IllegalArgumentException("Unknown MotionCurve type: " + name);
            return codec;
        }
    );
}
