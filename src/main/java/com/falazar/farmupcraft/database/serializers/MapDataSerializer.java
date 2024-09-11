package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataBase;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class MapDataSerializer<K, V> implements DataBase.DataSerializer<Map<K, V>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MAP_ENTRY_TAG = "map_entry";
    private static final String KEY_TAG = "key";
    private static final String VALUE_TAG = "value";
    private static final String DATABASE_NAME_TAG = "database_name";
    private final DataBase.DataSerializer<K> keySerializer;
    private final DataBase.DataSerializer<V> valueSerializer;

    public MapDataSerializer(DataBase.DataSerializer<K> keySerializer, DataBase.DataSerializer<V> valueSerializer) {
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
    }

    @Override
    public CompoundTag serialize(Map<K, V> value) {


        CompoundTag mapTag = new CompoundTag();
        ListTag nbtList = new ListTag();

        for (Map.Entry<K, V> entry : value.entrySet()) {
            try {
                CompoundTag entryTag = new CompoundTag();
                entryTag.put(KEY_TAG, keySerializer.serialize(entry.getKey()));
                entryTag.put(VALUE_TAG, valueSerializer.serialize(entry.getValue()));
                nbtList.add(entryTag);
            } catch (Exception e) {
                LOGGER.error("Error serializing map entry: key={}, value={}", entry.getKey(), entry.getValue(), e);
            }
        }

        mapTag.put(MAP_ENTRY_TAG, nbtList);
        return mapTag;
    }

    @Override
    public Map<K, V> deserialize(CompoundTag tag, Level level) {

        String databaseName = tag.getString(DATABASE_NAME_TAG);
        ListTag nbtList = tag.getList(MAP_ENTRY_TAG, Tag.TAG_COMPOUND);
        Map<K, V> resultMap = new HashMap<>();

        for (Tag entryTag : nbtList) {
            try {
                CompoundTag compoundEntryTag = (CompoundTag) entryTag;
                K key = keySerializer.deserialize(compoundEntryTag.getCompound(KEY_TAG), level);
                V value = valueSerializer.deserialize(compoundEntryTag.getCompound(VALUE_TAG), level);

                if (key != null && value != null) {
                    resultMap.put(key, value);
                } else {
                    LOGGER.warn("[{}] Deserialized key or value is null. key={}, value={}",databaseName ,key, value);
                }
            } catch (Exception e) {
                LOGGER.error("[{}] Error deserializing map entry: {} due to {}",databaseName ,entryTag, e);
            }
        }


        return resultMap;
    }


}
