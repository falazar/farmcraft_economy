package com.falazar.farmupcraft.database.message;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;
import java.util.function.Function;

final class WaypointPayloadValidation {
    static final int MAX_NAME_LENGTH = 1024;
    static final int MAX_DIMENSION_LENGTH = 256;
    static final int MAX_FILTER_LENGTH = 256;

    private WaypointPayloadValidation() {
    }

    static String name(String name) {
        Objects.requireNonNull(name, "Waypoint name");
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Waypoint name exceeds " + MAX_NAME_LENGTH + " characters");
        }
        return name;
    }

    static int color(int color) {
        if ((color & 0xFF000000) != 0) {
            throw new IllegalArgumentException("Waypoint color must be a 24-bit RGB value");
        }
        return color;
    }

    static <T> StreamCodec<FriendlyByteBuf, T> codec(
            net.minecraft.network.codec.StreamEncoder<FriendlyByteBuf, T> encoder,
            Function<FriendlyByteBuf, T> decoder) {
        return StreamCodec.of(encoder, buffer -> {
            try {
                return decoder.apply(buffer);
            } catch (IllegalArgumentException | NullPointerException error) {
                throw new DecoderException("Invalid FarmUpCraft waypoint payload", error);
            }
        });
    }
}
