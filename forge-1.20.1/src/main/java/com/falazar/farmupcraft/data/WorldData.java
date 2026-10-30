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
            Codec.BOOL.optionalFieldOf("use_farms", true).forGetter(WorldData::isUseVillageFarms),
            Codec.BOOL.optionalFieldOf("use_biome_crop_rules", true).forGetter(WorldData::isUseBiomeCropRules),
            Codec.BOOL.optionalFieldOf("lycanite_light_block", true).forGetter(WorldData::isLycaniteLightBlock),
            Codec.BOOL.optionalFieldOf("lan_notice_enabled", true).forGetter(WorldData::isLanNoticeEnabled),
            Codec.BOOL.optionalFieldOf("mob_torch_breaking", true).forGetter(WorldData::isMobTorchBreaking))
            .apply(instance, WorldData::new));

    private int upkeepTick;
    private String lastRanMarketDaily;
    private boolean useFarms;
    private boolean useBiomeCropRules;
    private boolean lycaniteLightBlock;
    private boolean lanNoticeEnabled;
    private boolean mobTorchBreaking = true;

    public boolean isMobTorchBreaking() { return mobTorchBreaking; }
    public void setMobTorchBreaking(boolean enabled) { mobTorchBreaking = enabled; }

    public WorldData() {
        this.upkeepTick = 0;
        this.lastRanMarketDaily = "2000-01-01";
        this.useFarms = true;
        this.useBiomeCropRules = true;
        this.lycaniteLightBlock = true;
        this.lanNoticeEnabled = true;
    }

    public WorldData(int upkeepTick, String lastRanMarketDaily,
            boolean useFarms, boolean useBiomeCropRules, boolean lycaniteLightBlock, boolean lanNoticeEnabled) {
        this(upkeepTick, lastRanMarketDaily, useFarms, useBiomeCropRules, lycaniteLightBlock, lanNoticeEnabled, true);
    }

    public WorldData(int upkeepTick, String lastRanMarketDaily,
            boolean useFarms, boolean useBiomeCropRules, boolean lycaniteLightBlock, boolean lanNoticeEnabled,
            boolean mobTorchBreaking) {
        this.upkeepTick = upkeepTick;
        this.lastRanMarketDaily = lastRanMarketDaily != null ? lastRanMarketDaily : "2000-01-01";
        this.useFarms = useFarms;
        this.useBiomeCropRules = useBiomeCropRules;
        this.lycaniteLightBlock = lycaniteLightBlock;
        this.lanNoticeEnabled = lanNoticeEnabled;
        this.mobTorchBreaking = mobTorchBreaking;
    }

    public boolean isUseVillageFarms() {
        return useFarms;
    }

    public void setUseVillageFarms(boolean useFarms) {
        this.useFarms = useFarms;
    }

    public boolean isUseBiomeCropRules() {
        return useBiomeCropRules;
    }

    public void setUseBiomeCropRules(boolean useBiomeCropRules) {
        this.useBiomeCropRules = useBiomeCropRules;
    }

    /** When true, Lycanites mobs are blocked from spawning at block-light >= 8. */
    public boolean isLycaniteLightBlock() {
        return lycaniteLightBlock;
    }

    public void setLycaniteLightBlock(boolean lycaniteLightBlock) {
        this.lycaniteLightBlock = lycaniteLightBlock;
    }

    public boolean isLanNoticeEnabled() {
        return lanNoticeEnabled;
    }

    public void setLanNoticeEnabled(boolean lanNoticeEnabled) {
        this.lanNoticeEnabled = lanNoticeEnabled;
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
}
