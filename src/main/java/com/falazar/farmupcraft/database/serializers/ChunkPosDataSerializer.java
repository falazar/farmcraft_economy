package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataBase;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public class ChunkPosDataSerializer implements DataBase.DataSerializer<ChunkPos> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String X_TAG = "x";
    private static final String Z_TAG = "z";
    private static final String DATABASE_NAME_TAG = "database_name";

    @Override
    public CompoundTag serialize(ChunkPos value) {

        CompoundTag tag = new CompoundTag();
        tag.putInt(X_TAG, value.x);
        tag.putInt(Z_TAG, value.z);

        LOGGER.debug("Serialization complete for ChunkPos: ({}, {})", value.x, value.z);
        return tag;
    }

    @Override
    public ChunkPos deserialize(CompoundTag tag, Level level) {
        int x = tag.getInt(X_TAG);
        int z = tag.getInt(Z_TAG);
        String databaseName = tag.getString(DATABASE_NAME_TAG);
        ChunkPos chunkPos = new ChunkPos(x, z);

        LOGGER.debug("[{}] Deserialization complete for ChunkPos: ({}, {})", databaseName,chunkPos.x, chunkPos.z);
        return chunkPos;
    }
}
