package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataBase;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public class BlockPosDataSerializer implements DataBase.DataSerializer<BlockPos> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String X_TAG = "x";
    private static final String Y_TAG = "y";
    private static final String Z_TAG = "z";
    private static final String DATABASE_NAME_TAG = "database_name";

    @Override
    public CompoundTag serialize(BlockPos value) {

        CompoundTag tag = new CompoundTag();
        tag.putInt(X_TAG, value.getX());
        tag.putInt(Y_TAG, value.getY());
        tag.putInt(Z_TAG, value.getZ());

        LOGGER.debug("Serialization complete for BlockPos: ({}, {}, {})", value.getX(), value.getY(), value.getZ());
        return tag;
    }

    @Override
    public BlockPos deserialize(CompoundTag tag, Level level) {
        int x = tag.getInt(X_TAG);
        int y = tag.getInt(Y_TAG);
        int z = tag.getInt(Z_TAG);
        String databaseName = tag.getString(DATABASE_NAME_TAG);

        BlockPos blockPos = new BlockPos(x, y, z);

        LOGGER.debug("[{}] Deserialization complete for BlockPos: ({}, {}, {})",databaseName, blockPos.getX(), blockPos.getY(), blockPos.getZ());
        return blockPos;
    }
}
