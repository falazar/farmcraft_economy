package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.util.CodecUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

public class WorldData {

    public static final Codec<WorldData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("upkeep_tick").forGetter(WorldData::getUpkeepTick)
            ).apply(instance, WorldData::new)
    );

    private int upkeepTick;


    public WorldData() {
        this.upkeepTick = 0;
    }


    public WorldData(int upkeepTick) {
        this.upkeepTick = upkeepTick;
    }

    public void setUpkeepTick(int upkeepTick) {
        this.upkeepTick = upkeepTick;
    }

    public int getUpkeepTick() {
        return upkeepTick;
    }

    public void increaseUpkeepTick() {
        upkeepTick++;
    }
}
