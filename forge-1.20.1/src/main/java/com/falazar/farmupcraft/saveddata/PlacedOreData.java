package com.falazar.farmupcraft.saveddata;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

public final class PlacedOreData extends SavedData {
    private final Set<Long> positions = new HashSet<>();

    public static PlacedOreData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PlacedOreData::load, PlacedOreData::new,
                "farmupcraft_placed_ores");
    }

    public static PlacedOreData load(CompoundTag tag) {
        var data = new PlacedOreData();
        for (long pos : tag.getLongArray("positions")) data.positions.add(pos);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray("positions", positions.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    public boolean contains(BlockPos pos) { return positions.contains(pos.asLong()); }

    public void mark(BlockPos pos) {
        if (positions.add(pos.asLong())) setDirty();
    }

    public void remove(BlockPos pos) {
        if (positions.remove(pos.asLong())) setDirty();
    }
}
