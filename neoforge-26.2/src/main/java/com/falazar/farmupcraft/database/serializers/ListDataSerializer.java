package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ListDataSerializer<T> implements DataSerializer<List<T>> {
    private final DataSerializer<T> elementSerializer;

    public ListDataSerializer(DataSerializer<T> elementSerializer) {
        this.elementSerializer = Objects.requireNonNull(elementSerializer);
    }

    @Override
    public CompoundTag serialize(List<T> value, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (T element : value) {
            CompoundTag encoded = elementSerializer.serialize(element, registries);
            if (encoded == null) {
                throw new IllegalArgumentException("List element serializer returned null");
            }
            list.add(encoded);
        }
        tag.put("list", list);
        return tag;
    }

    @Override
    public List<T> deserialize(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = tag.getList("list")
                .orElseThrow(() -> new IllegalArgumentException("Expected list field 'list'"));
        List<T> result = new ArrayList<>();
        for (Tag element : list) {
            if (!(element instanceof CompoundTag compound)) {
                throw new IllegalArgumentException("Expected compound list element");
            }
            T decoded = elementSerializer.deserialize(compound, registries);
            if (decoded == null) {
                throw new IllegalArgumentException("List element deserializer returned null");
            }
            result.add(decoded);
        }
        return result;
    }
}
