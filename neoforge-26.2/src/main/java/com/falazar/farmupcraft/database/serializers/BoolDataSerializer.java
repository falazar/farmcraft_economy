package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

public class BoolDataSerializer implements DataSerializer<Boolean> {
    @Override
    public CompoundTag serialize(Boolean value, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("bool_value", value);
        return tag;
    }

    @Override
    public Boolean deserialize(CompoundTag tag, HolderLookup.Provider registries) {
        long value = NumericFields.longInteger(tag.get("bool_value"), "bool_value");
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException("Expected numeric 0 or 1 in field 'bool_value'");
        }
        return value == 1;
    }
}
