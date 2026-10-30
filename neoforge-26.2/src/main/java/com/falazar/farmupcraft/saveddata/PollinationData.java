package com.falazar.farmupcraft.saveddata;

import com.falazar.farmupcraft.util.PollinationCooldown;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PollinationData extends SavedData {
    public static final Codec<PollinationData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf())
            .xmap(PollinationData::new, data -> Map.copyOf(data.lastSuccess));
    static final SavedDataType<PollinationData> TYPE = new SavedDataType<>(
            Identifier.parse("farmupcraft:pollination"), () -> new PollinationData(Map.of()), CODEC);
    private final Map<String, List<Long>> lastSuccess;

    private PollinationData(Map<String, List<Long>> values) {
        lastSuccess = new HashMap<>();
        values.forEach((key, times) -> lastSuccess.put(key, new ArrayList<>(times)));
    }

    public static boolean grow(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server)) return false;
        var data = server.getDataStorage().computeIfAbsent(TYPE);
        String key = Long.toString(ChunkPos.pack(pos));
        long now = server.getGameTime();
        if (!PollinationCooldown.ready(data.lastSuccess.getOrDefault(key, List.of()), now)) return false;
        if (!server.setBlockAndUpdate(pos, state)) return false;
        PollinationCooldown.recordSuccess(data.lastSuccess.computeIfAbsent(key, unused -> new ArrayList<>()), now);
        data.setDirty();
        return true;
    }

    public static void butterfly(CropBlock crop, Level level, BlockPos pos, BlockState state) {
        int age = crop.getAge(state);
        if (age < crop.getMaxAge()) grow(level, pos, crop.getStateForAge(age + 1));
    }
}
