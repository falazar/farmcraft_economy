package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataBase;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public class VersionedDataSerializer<T> implements DataBase.DataSerializer<T> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String VERSION_TAG = "version";
    private static final String VERSIONED = "versioned";
    private final DataBase.DataSerializer<T> newSerializer;
    private final DataBase.DataSerializer<T> oldSerializer;
    private final int formatVersion;

    public VersionedDataSerializer(DataBase.DataSerializer<T> newSerializer, DataBase.DataSerializer<T> oldSerializer, int formatVersion) {
        this.newSerializer = newSerializer;
        this.oldSerializer = oldSerializer;
        this.formatVersion = formatVersion;
    }

    @Override
    public CompoundTag serialize(T value) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_TAG, formatVersion); // Indicate that the new format is used

        try {
            CompoundTag serializedData = newSerializer.serialize(value);
            tag.put(VERSIONED, serializedData);
        } catch (Exception e) {
            LOGGER.error("Failed to serialize new format for value {}: {}", value, e);
        }

        return tag;
    }

    @Override
    public T deserialize(CompoundTag tag, Level level) {
        int version = tag.getInt(VERSION_TAG);

        if (version == formatVersion) {
            // New format
            if(checkVersioned(tag)) {
                CompoundTag newFormatTag = tag.getCompound(VERSIONED);
                try {
                    return newSerializer.deserialize(newFormatTag, level);
                } catch (Exception e) {
                    LOGGER.error("Failed to deserialize new format due to: {}", e);
                    return null;
                }
            }
        } else if(version == 0) {
            try {
                //try both with versioned and and unversioned since people might have implemented something without
                // this first and then changed to this
                if(checkVersioned(tag)) {
                    CompoundTag formatTag = tag.getCompound(VERSIONED);
                    return oldSerializer.deserialize(formatTag, level);
                } else {
                    return oldSerializer.deserialize(tag, level);
                }

            } catch (Exception e) {
                LOGGER.error("Failed to deserialize old format due to: {}", e);
                return null;
            }

        }
        return null;
    }

    public boolean checkVersioned(CompoundTag tag) {
        if (!tag.contains(VERSIONED)) {
            //LOGGER.error("Missing '{}' tag in CompoundTag for new format", VERSIONED);
            return false;
        }
        return true;
    }
}
