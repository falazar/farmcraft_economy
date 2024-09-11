package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataBase;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public class CodecDataSerializer<T> implements DataBase.DataSerializer<T> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CODEC_VALUE_TAG = "codec_value";
    private static final String DATABASE_NAME_TAG = "database_name";
    private final Codec<T> codec;

    public CodecDataSerializer(Codec<T> codec) {
        this.codec = codec;
    }

    @Override
    public CompoundTag serialize(T value) {
        CompoundTag codecTag = new CompoundTag();
        DataResult<Tag> result = codec.encodeStart(NbtOps.INSTANCE, value);

        result.get().ifLeft(tag -> {
            codecTag.put(CODEC_VALUE_TAG, tag);
        }).ifRight(partial -> {
            LOGGER.error("Failed to serialize codec for value {}: {}", value, partial);
        });

        return codecTag;
    }

    public T deserialize(CompoundTag tag, Level level) {
        if (!tag.contains(CODEC_VALUE_TAG)) {
            LOGGER.error("Missing '{}' tag in CompoundTag", CODEC_VALUE_TAG);
            return null;
        }
        String databaseName = tag.getString(DATABASE_NAME_TAG);
        CompoundTag objectTag = tag.getCompound(CODEC_VALUE_TAG);
        DataResult<T> result = codec.parse(NbtOps.INSTANCE, objectTag);

        return result.resultOrPartial(err -> LOGGER.error("[{}] Failed to deserialize object due to: {}", databaseName, err))
                .orElse(null);
    }
}
