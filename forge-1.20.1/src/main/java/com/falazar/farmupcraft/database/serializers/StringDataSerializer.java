package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

public class StringDataSerializer implements DataBase.DataSerializer<String> {
    @Override
    public CompoundTag serialize(String value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("string_value", value);
        return tag;
    }

    @Override
    public String deserialize(CompoundTag tag, Level level) {
        return tag.getString("string_value");
    }

}