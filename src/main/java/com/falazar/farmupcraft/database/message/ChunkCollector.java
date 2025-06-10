package com.falazar.farmupcraft.database.message;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class ChunkCollector {
    private static final Map<ResourceLocation, ChunkCollector> collectors = new HashMap<>();

    private final CompoundTag[] chunks;
    private final int totalChunks;

    public ChunkCollector(int totalChunks) {
        this.totalChunks = totalChunks;
        this.chunks = new CompoundTag[totalChunks];
    }

    public void addChunk(int index, CompoundTag chunk) {
        this.chunks[index] = chunk;
    }

    public boolean isComplete() {
        for (CompoundTag chunk : chunks) {
            if (chunk == null) return false;
        }
        return true;
    }

    /**
     * Merge all chunks into a single CompoundTag.
     * @param databaseName the name of the database (passed explicitly, not extracted from chunks)
     */
    public CompoundTag mergeAll(String databaseName) {
        CompoundTag full = new CompoundTag();
        ListTag mergedList = new ListTag();

        for (CompoundTag chunk : chunks) {
            ListTag list = chunk.getList("map_entry", Tag.TAG_COMPOUND);
            mergedList.addAll(list);
        }

        full.put("map_entry", mergedList);
        full.putString("database_name", databaseName);
        return full;
    }

    public static ChunkCollector getOrCreate(ResourceLocation id, int totalChunks) {
        return collectors.computeIfAbsent(id, k -> new ChunkCollector(totalChunks));
    }

    public static void clear(ResourceLocation id) {
        collectors.remove(id);
    }
}

