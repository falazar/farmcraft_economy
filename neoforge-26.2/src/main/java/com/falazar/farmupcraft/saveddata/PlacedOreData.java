package com.falazar.farmupcraft.saveddata;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PlacedOreData extends SavedData {
    public static final Codec<PlacedOreData> CODEC = Codec.LONG.listOf()
            .xmap(PlacedOreData::new, data -> List.copyOf(data.positions));
    private static final SavedDataType<PlacedOreData> TYPE = new SavedDataType<>(
            Identifier.parse("farmupcraft:placed_ores"), () -> new PlacedOreData(List.of()), CODEC);
    private final Set<Long> positions;

    private PlacedOreData(List<Long> positions) {
        this.positions = new HashSet<>(positions);
    }

    public static PlacedOreData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean contains(BlockPos pos) { return positions.contains(pos.asLong()); }

    public void mark(BlockPos pos) {
        if (positions.add(pos.asLong())) setDirty();
    }

    public void remove(BlockPos pos) {
        if (positions.remove(pos.asLong())) setDirty();
    }
}
