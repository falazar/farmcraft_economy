package com.falazar.farmupcraft.database.message;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamEncoder;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.function.Function;

final class CommandPayloadValidation {
    static final int MAX_IDENTIFIER_LENGTH = 256;
    static final int MAX_CHUNK_COORDINATE = 1_875_000;

    private CommandPayloadValidation() {
    }

    static String identifier(String value) {
        Objects.requireNonNull(value, "Identifier");
        Identifier id = Identifier.tryParse(value);
        if (id == null || id.getPath().isEmpty() || id.toString().length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException("Invalid command payload identifier: " + value);
        }
        return id.toString();
    }

    static int chunk(int coordinate) {
        if (coordinate < -MAX_CHUNK_COORDINATE || coordinate >= MAX_CHUNK_COORDINATE) {
            throw new IllegalArgumentException("Chunk coordinate outside world bounds: " + coordinate);
        }
        return coordinate;
    }

    static <T> StreamCodec<FriendlyByteBuf, T> codec(StreamEncoder<FriendlyByteBuf, T> encoder,
                                                    Function<FriendlyByteBuf, T> decoder) {
        return StreamCodec.of(encoder, buffer -> {
            try {
                return decoder.apply(buffer);
            } catch (IllegalArgumentException | NullPointerException | IndexOutOfBoundsException error) {
                throw new DecoderException("Invalid FarmUpCraft command payload", error);
            }
        });
    }
}
