package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class UUIDDataSerializer implements DataSerializer<UUID> {
    @Override
    public CompoundTag serialize(UUID value, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        long most = value.getMostSignificantBits();
        long least = value.getLeastSignificantBits();
        tag.putIntArray("uuid", new int[] {(int) (most >> 32), (int) most, (int) (least >> 32), (int) least});
        return tag;
    }

    @Override
    public UUID deserialize(CompoundTag tag, HolderLookup.Provider registries) {
        if (!(tag.get("uuid") instanceof CollectionTag components)) {
            throw new IllegalArgumentException("Expected numeric array or list field 'uuid'");
        }
        if (components.size() != 4) {
            throw new IllegalArgumentException("Expected four ints in field 'uuid'");
        }
        int[] value = new int[4];
        for (int i = 0; i < value.length; i++) {
            value[i] = NumericFields.integer(components.get(i), "uuid[" + i + "]");
        }
        return new UUID(((long) value[0] << 32) | (value[1] & 0xffffffffL),
                ((long) value[2] << 32) | (value[3] & 0xffffffffL));
    }
}
