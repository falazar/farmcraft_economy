package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

public class LongDataSerializer implements DataSerializer<Long> {
    @Override
    public CompoundTag serialize(Long value, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("long_val", value);
        return tag;
    }

    @Override
    public Long deserialize(CompoundTag tag, HolderLookup.Provider registries) {
        return NumericFields.longInteger(tag.get("long_val"), "long_val");
    }
}
