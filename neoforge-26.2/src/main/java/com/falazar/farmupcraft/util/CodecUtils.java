package com.falazar.farmupcraft.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.ChunkPos;

public class CodecUtils {
    public static final Codec<Long> LONG_CODEC = Codec.STRING.comapFlatMap(CodecUtils::parseLong, Object::toString);

    private static DataResult<Long> parseLong(String value) {
        try {
            return DataResult.success(Long.valueOf(value));
        } catch (NumberFormatException e) {
            return DataResult.error(e::getMessage);
        }
    }

    // Keep the legacy packed-long shape, not ChunkPos.CODEC's coordinate array.
    public static final Codec<ChunkPos> CHUNK_POS_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(Codec.LONG.fieldOf("chunk_long").forGetter(ChunkPos::pack))
                    .apply(instance, ChunkPos::unpack));

    public static final Codec<ChunkPos> CHUNK_POS_CODEC_STRING =
            LONG_CODEC.xmap(ChunkPos::unpack, ChunkPos::pack);
}
