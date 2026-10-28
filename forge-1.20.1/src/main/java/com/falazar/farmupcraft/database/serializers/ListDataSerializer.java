package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataBase;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ListDataSerializer<T> implements DataBase.DataSerializer<List<T>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LIST_TAG = "list";
    private static final String DATABASE_NAME_TAG = "database_name";

    private final DataBase.DataSerializer<T> elementSerializer;

    public ListDataSerializer(DataBase.DataSerializer<T> elementSerializer) {
        this.elementSerializer = elementSerializer;
    }

    @Override
    public CompoundTag serialize(List<T> value) {
        CompoundTag listTag = new CompoundTag();
        ListTag nbtList = new ListTag();

        for (T element : value) {
            try {
                CompoundTag elementTag = elementSerializer.serialize(element);
                nbtList.add(elementTag);
            } catch (Exception e) {
                LOGGER.error("Error serializing element: {}", element, e);
            }
        }
        listTag.put(LIST_TAG, nbtList);
        return listTag;
    }

    @Override
    public List<T> deserialize(CompoundTag tag, Level level) {
        ListTag nbtList = tag.getList(LIST_TAG, Tag.TAG_COMPOUND);
        List<T> result = new ArrayList<>();
        String databaseName = tag.getString(DATABASE_NAME_TAG);

        for (Tag elementTag : nbtList) {
            try {
                T element = elementSerializer.deserialize((CompoundTag) elementTag, level);
                if (element != null) {
                    result.add(element);
                } else {
                    LOGGER.warn("[{}] Deserialized element is null, skipping...", databaseName);
                }
            } catch (Exception e) {
                LOGGER.error("[{}] Error deserializing element: {} due to {}",databaseName ,elementTag, e);
            }
        }
        return result;
    }

}
