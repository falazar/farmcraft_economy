package com.falazar.farmupcraft.database;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * Serializes a database value using the registries belonging to its world.
 */
public interface DataSerializer<T> {
    CompoundTag serialize(T value, HolderLookup.Provider registries);

    T deserialize(CompoundTag tag, HolderLookup.Provider registries);
}
