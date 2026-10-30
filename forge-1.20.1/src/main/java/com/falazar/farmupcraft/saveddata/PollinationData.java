package com.falazar.farmupcraft.saveddata;

import com.falazar.farmupcraft.util.PollinationCooldown;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class PollinationData extends SavedData {
    private final Map<String, List<Long>> lastSuccess = new HashMap<>();

    public static PollinationData load(CompoundTag tag) {
        var data = new PollinationData();
        for (String key : tag.getAllKeys()) {
            data.lastSuccess.put(key, new ArrayList<>(Arrays.stream(tag.getLongArray(key)).boxed().toList()));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        lastSuccess.forEach((key, times) -> tag.putLongArray(key, times));
        return tag;
    }

    public static boolean grow(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server)) return false;
        var data = server.getDataStorage().computeIfAbsent(
                PollinationData::load, PollinationData::new, "farmupcraft_pollination");
        String key = Long.toString(new ChunkPos(pos).toLong());
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
