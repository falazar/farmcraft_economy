package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

public class VersionedDataSerializer<T> implements DataSerializer<T> {
    private static final String VERSION_TAG = "version";
    private static final String VERSIONED = "versioned";
    private final DataSerializer<T> newSerializer;
    private final DataSerializer<T> oldSerializer;
    private final int formatVersion;

    public VersionedDataSerializer(DataSerializer<T> newSerializer, DataSerializer<T> oldSerializer, int formatVersion) {
        if (formatVersion <= 0) {
            throw new IllegalArgumentException("Format version must be positive; zero denotes the legacy format");
        }
        this.newSerializer = Objects.requireNonNull(newSerializer);
        this.oldSerializer = Objects.requireNonNull(oldSerializer);
        this.formatVersion = formatVersion;
    }

    @Override
    public CompoundTag serialize(T value, HolderLookup.Provider registries) {
        CompoundTag payload = newSerializer.serialize(value, registries);
        var tag = new CompoundTag();
        tag.putInt(VERSION_TAG, formatVersion);
        tag.put(VERSIONED, payload);
        return tag;
    }

    @Override
    public T deserialize(CompoundTag tag, HolderLookup.Provider registries) {
        int version = 0;
        if (tag.contains(VERSION_TAG)) {
            version = tag.getInt(VERSION_TAG)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid '" + VERSION_TAG + "'"));
            if (tag.getDouble(VERSION_TAG).orElseThrow() != version) {
                throw new IllegalArgumentException("Format version is not an exact integer");
            }
        }
        if (version == formatVersion) {
            return newSerializer.deserialize(requirePayload(tag), registries);
        }
        if (version == 0) {
            return oldSerializer.deserialize(checkVersioned(tag) ? requirePayload(tag) : tag, registries);
        }
        throw new IllegalArgumentException("Unsupported format version: " + version);
    }

    public boolean checkVersioned(CompoundTag tag) {
        return tag.contains(VERSIONED);
    }

    private static CompoundTag requirePayload(CompoundTag tag) {
        return tag.getCompound(VERSIONED)
                .orElseThrow(() -> new IllegalArgumentException("Missing or invalid '" + VERSIONED + "'"));
    }
}
