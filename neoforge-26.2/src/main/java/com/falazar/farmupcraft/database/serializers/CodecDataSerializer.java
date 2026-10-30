package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataSerializer;
import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.Objects;

public class CodecDataSerializer<T> implements DataSerializer<T> {
    private static final String CODEC_VALUE_TAG = "codec_value";
    private final Codec<T> codec;

    public CodecDataSerializer(Codec<T> codec) {
        this.codec = Objects.requireNonNull(codec);
    }

    @Override
    public CompoundTag serialize(T value, HolderLookup.Provider registries) {
        Tag encoded = codec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), value)
                .getOrThrow(message -> new IllegalArgumentException("Failed to serialize codec value: " + message));
        var tag = new CompoundTag();
        tag.put(CODEC_VALUE_TAG, encoded);
        return tag;
    }

    @Override
    public T deserialize(CompoundTag tag, HolderLookup.Provider registries) {
        Tag value = tag.get(CODEC_VALUE_TAG);
        if (value == null) {
            throw new IllegalArgumentException("Missing '" + CODEC_VALUE_TAG + "'");
        }
        return codec.parse(registries.createSerializationContext(NbtOps.INSTANCE), value)
                .getOrThrow(message -> new IllegalArgumentException("Failed to deserialize codec value: " + message));
    }
}
