package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.util.CodecUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

public class WorldData {

    public static final Codec<WorldData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("upkeep_tick").forGetter(WorldData::getUpkeepTick),
            Codec.STRING.optionalFieldOf("last_ran_market_daily", "2000-01-01")
                    .forGetter(WorldData::getLastRanMarketDaily),
            Codec.STRING.optionalFieldOf("last_ran_village_daily", "2000-01-01")
                    .forGetter(WorldData::getLastRanVillageDaily))
            .apply(instance, WorldData::new));

    private int upkeepTick;
    private String lastRanMarketDaily;
    private String lastRanVillageDaily;

    public WorldData() {
        this.upkeepTick = 0;
        this.lastRanMarketDaily = "2000-01-01";
        this.lastRanVillageDaily = "2000-01-01";
    }

    public WorldData(int upkeepTick, String lastRanMarketDaily, String lastRanVillageDaily) {
        this.upkeepTick = upkeepTick;
        this.lastRanMarketDaily = lastRanMarketDaily != null ? lastRanMarketDaily : "2000-01-01";
        this.lastRanVillageDaily = lastRanVillageDaily != null ? lastRanVillageDaily : "2000-01-01";
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

    public String getLastRanMarketDaily() {
        return lastRanMarketDaily != null ? lastRanMarketDaily : "2000-01-01";
    }

    public void setLastRanMarketDaily(String date) {
        this.lastRanMarketDaily = date;
    }

    public boolean hasMarketRanTodayAlready() {
        String today = java.time.LocalDate.now().toString(); // yyyy-MM-dd
        return today.equals(getLastRanMarketDaily());
    }

    public void markMarketDailyRanToday() {
        this.lastRanMarketDaily = java.time.LocalDate.now().toString();
    }

    public String getLastRanVillageDaily() {
        return lastRanVillageDaily != null ? lastRanVillageDaily : "2000-01-01";
    }

    public void setLastRanVillageDaily(String date) {
        this.lastRanVillageDaily = date;
    }

    public boolean hasVillageRanTodayAlready() {
        String today = java.time.LocalDate.now().toString();
        return today.equals(getLastRanVillageDaily());
    }

    public void markVillageDailyRanToday() {
        this.lastRanVillageDaily = java.time.LocalDate.now().toString();
    }
}
