package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

public class StringDataSerializer implements DataSerializer<String> {
    @Override
    public CompoundTag serialize(String value, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("string_value", value);
        return tag;
    }

    @Override
    public String deserialize(CompoundTag tag, HolderLookup.Provider registries) {
        return tag.getString("string_value")
                .orElseThrow(() -> new IllegalArgumentException("Expected string field 'string_value'"));
    }
}
