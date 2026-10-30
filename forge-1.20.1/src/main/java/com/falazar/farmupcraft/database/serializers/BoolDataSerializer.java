package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataBase;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public class BoolDataSerializer implements DataBase.DataSerializer<Boolean> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BOOL_VALUE_TAG = "bool_value";

    @Override
    public CompoundTag serialize(Boolean value) {

        CompoundTag tag = new CompoundTag();
        tag.putBoolean(BOOL_VALUE_TAG, value);

        return tag;
    }

    @Override
    public Boolean deserialize(CompoundTag tag, Level level) {
        boolean value = tag.getBoolean(BOOL_VALUE_TAG);
        return value;
    }
}
