package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MapDataSerializer<K, V> implements DataSerializer<Map<K, V>> {
    private final DataSerializer<K> keySerializer;
    private final DataSerializer<V> valueSerializer;

    public MapDataSerializer(DataSerializer<K> keySerializer, DataSerializer<V> valueSerializer) {
        this.keySerializer = Objects.requireNonNull(keySerializer);
        this.valueSerializer = Objects.requireNonNull(valueSerializer);
    }

    @Override
    public CompoundTag serialize(Map<K, V> value, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<K, V> entry : value.entrySet()) {
            CompoundTag key = keySerializer.serialize(entry.getKey(), registries);
            CompoundTag encodedValue = valueSerializer.serialize(entry.getValue(), registries);
            if (key == null || encodedValue == null) {
                throw new IllegalArgumentException("Map entry serializer returned null");
            }
            CompoundTag encodedEntry = new CompoundTag();
            encodedEntry.put("key", key);
            encodedEntry.put("value", encodedValue);
            list.add(encodedEntry);
        }
        tag.put("map_entry", list);
        return tag;
    }

    @Override
    public Map<K, V> deserialize(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = tag.getList("map_entry")
                .orElseThrow(() -> new IllegalArgumentException("Expected list field 'map_entry'"));
        Map<K, V> result = new HashMap<>();
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag compound)) {
                throw new IllegalArgumentException("Expected compound map entry");
            }
            CompoundTag keyTag = compound.getCompound("key")
                    .orElseThrow(() -> new IllegalArgumentException("Expected compound field 'key'"));
            CompoundTag valueTag = compound.getCompound("value")
                    .orElseThrow(() -> new IllegalArgumentException("Expected compound field 'value'"));
            K key = keySerializer.deserialize(keyTag, registries);
            V value = valueSerializer.deserialize(valueTag, registries);
            if (key == null || value == null) {
                throw new IllegalArgumentException("Map entry deserializer returned null");
            }
            if (result.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate deserialized map key: " + key);
            }
            result.put(key, value);
        }
        return result;
    }
}
