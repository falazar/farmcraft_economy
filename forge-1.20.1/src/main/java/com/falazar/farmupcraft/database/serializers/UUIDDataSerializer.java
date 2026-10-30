package com.falazar.farmupcraft.database.serializers;

import java.util.UUID;

import com.falazar.farmupcraft.database.DataBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

public class UUIDDataSerializer implements DataBase.DataSerializer<UUID> {
    private static final String UUID_TAG = "uuid";

    @Override
    public CompoundTag serialize(UUID value) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(UUID_TAG, value);

        return tag;
    }

    @Override
    public UUID deserialize(CompoundTag tag, Level level) {
        UUID uuid = tag.getUUID(UUID_TAG);
        return uuid;
    }
}
