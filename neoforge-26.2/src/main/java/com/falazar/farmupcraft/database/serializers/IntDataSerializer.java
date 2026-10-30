package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

public class IntDataSerializer implements DataSerializer<Integer> {
    @Override
    public CompoundTag serialize(Integer value, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("int_value", value);
        return tag;
    }

    @Override
    public Integer deserialize(CompoundTag tag, HolderLookup.Provider registries) {
        return NumericFields.integer(tag.get("int_value"), "int_value");
    }
}
