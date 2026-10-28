package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LongDataSerializer implements DataBase.DataSerializer<Long> {
    private static final String LONG_VAL = "long_val";

    @Override
    public @Nullable CompoundTag serialize(Long value) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(LONG_VAL, value);
        return tag;
    }

    @Override
    public @Nullable Long deserialize(CompoundTag tag, Level level) {
        return tag.getLong(LONG_VAL);
    }
}
