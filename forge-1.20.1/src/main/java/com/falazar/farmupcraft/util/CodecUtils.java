package com.falazar.farmupcraft.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.ChunkPos;

public class CodecUtils {
    public static final Codec<Long> LONG_CODEC  = Codec.STRING.comapFlatMap(str -> parseLong(str), intVal ->  Long.toString(intVal));

    private static final DataResult<Long> parseLong(String instantString) {
        try {
            return DataResult.success(Long.valueOf(instantString));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> e.getMessage());
        }
    }

    public static final Codec<ChunkPos> CHUNK_POS_CODEC = RecordCodecBuilder.create((p_122642_) -> {
        return p_122642_.group(Codec.LONG.fieldOf("chunk_long").forGetter(ChunkPos::toLong)).apply(p_122642_, ChunkPos::new);
    });
    public static final Codec<ChunkPos> CHUNK_POS_CODEC_STRING  = Codec.STRING.comapFlatMap(str -> parseChunkPos(str), intVal ->  Long.toString(intVal.toLong()));

    private static final DataResult<ChunkPos> parseChunkPos(String instantString) {
        try {
            return DataResult.success(new ChunkPos(Long.parseLong(instantString)));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> e.getMessage());
        }
    }
}

