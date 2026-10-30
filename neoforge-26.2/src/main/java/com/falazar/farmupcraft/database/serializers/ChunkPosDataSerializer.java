package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

public class ChunkPosDataSerializer implements DataSerializer<ChunkPos> {
    @Override
    public CompoundTag serialize(ChunkPos value, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", value.x());
        tag.putInt("z", value.z());
        return tag;
    }

    @Override
    public ChunkPos deserialize(CompoundTag tag, HolderLookup.Provider registries) {
        return new ChunkPos(coordinate(tag, "x"), coordinate(tag, "z"));
    }

    private static int coordinate(CompoundTag tag, String name) {
        return NumericFields.integer(tag.get(name), name);
    }
}
