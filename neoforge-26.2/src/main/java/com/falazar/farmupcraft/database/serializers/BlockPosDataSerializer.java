package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

public class BlockPosDataSerializer implements DataSerializer<BlockPos> {
    @Override
    public CompoundTag serialize(BlockPos value, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", value.getX());
        tag.putInt("y", value.getY());
        tag.putInt("z", value.getZ());
        return tag;
    }

    @Override
    public BlockPos deserialize(CompoundTag tag, HolderLookup.Provider registries) {
        return new BlockPos(coordinate(tag, "x"), coordinate(tag, "y"), coordinate(tag, "z"));
    }

    private static int coordinate(CompoundTag tag, String name) {
        return NumericFields.integer(tag.get(name), name);
    }
}
