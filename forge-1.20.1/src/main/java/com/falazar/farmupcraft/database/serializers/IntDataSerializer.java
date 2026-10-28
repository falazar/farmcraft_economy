package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IntDataSerializer implements DataBase.DataSerializer<Integer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(IntDataSerializer.class);
    private static final String INT_VALUE_TAG = "int_value";

    @Override
    public CompoundTag serialize(Integer value) {

        CompoundTag tag = new CompoundTag();
        tag.putInt(INT_VALUE_TAG, value);

        return tag;
    }

    @Override
    public Integer deserialize(CompoundTag tag, Level level) {
        int value = tag.getInt(INT_VALUE_TAG);

        return value;
    }
}
