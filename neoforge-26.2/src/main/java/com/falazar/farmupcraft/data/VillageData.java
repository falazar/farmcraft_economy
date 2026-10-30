package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.util.CodecUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

public class VillageData {
    public static final Codec<VillageData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("uuid").forGetter(VillageData::getUUID),
            Codec.STRING.fieldOf("name").forGetter(VillageData::getName),
            CodecUtils.CHUNK_POS_CODEC.fieldOf("position").forGetter(VillageData::getPosition),
            Codec.INT.fieldOf("level").forGetter(VillageData::getLevel),
            CodecUtils.CHUNK_POS_CODEC.listOf().fieldOf("claimed_chunks").forGetter(VillageData::getClaimedChunks),
            Codec.BOOL.fieldOf("bought").forGetter(VillageData::isBought),
            Codec.INT.optionalFieldOf("coins", 0).forGetter(VillageData::getCoins),
            Codec.STRING.optionalFieldOf("last_ran_daily", "2000-01-01").forGetter(VillageData::getLastRanDaily),
            Codec.STRING.optionalFieldOf("animal_type", "").forGetter(VillageData::getAnimalType),
            Codec.STRING.optionalFieldOf("last_animal_type_change", "2000-01-01")
                    .forGetter(VillageData::getLastAnimalTypeChange),
            Codec.STRING.optionalFieldOf("founder", "").forGetter(VillageData::getFounder),
            UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("member_uuids", new ArrayList<>())
                    .forGetter(VillageData::getMemberUUIDs),
            Codec.STRING.optionalFieldOf("animal_grains", "").forGetter(VillageData::getAnimalGrains),
            VillagerRecord.CODEC.listOf().optionalFieldOf("reincarnation_pool", List.of())
                    .forGetter(VillageData::getReincarnationPool))
            .apply(instance, VillageData::new));

    private final UUID uuid;
    private String name;
    private final ChunkPos position;
    private int level;
    private List<ChunkPos> claimedChunks;
    private Set<Long> claimedChunkSet = new HashSet<>();
    private final boolean bought;
    private int coins;
    private String lastRanDaily;
    private String animalType;
    private String lastAnimalTypeChange;
    private String founder;
    private List<UUID> memberUUIDs;
    private String animalGrains;
    private List<VillagerRecord> reincarnationPool;

    public VillageData(UUID uuid, String name, ChunkPos position, int level, List<ChunkPos> claimedChunks,
                       boolean bought, int coins, String lastRanDaily, String animalType, String lastAnimalTypeChange,
                       String founder, List<UUID> memberUUIDs, String animalGrains,
                       List<VillagerRecord> reincarnationPool) {
        this.uuid = uuid;
        this.name = name;
        this.position = position;
        this.level = level;
        this.claimedChunks = new ArrayList<>(claimedChunks);
        this.bought = bought;
        for (ChunkPos pos : claimedChunks) claimedChunkSet.add(pos.pack());
        this.coins = coins;
        this.lastRanDaily = lastRanDaily;
        this.animalType = animalType != null ? animalType : "";
        this.lastAnimalTypeChange = lastAnimalTypeChange != null ? lastAnimalTypeChange : "2000-01-01";
        this.founder = founder != null ? founder : "";
        this.memberUUIDs = memberUUIDs != null ? new ArrayList<>(memberUUIDs) : new ArrayList<>();
        this.animalGrains = animalGrains != null ? animalGrains : "";
        this.reincarnationPool = reincarnationPool != null ? new ArrayList<>(reincarnationPool) : new ArrayList<>();
    }

    public VillageData(UUID uuid, String name, ChunkPos position, int level, List<ChunkPos> claimedChunks,
                       boolean bought, int coins, String lastRanDaily, String animalType, String lastAnimalTypeChange,
                       String founder, List<UUID> memberUUIDs) {
        this(uuid, name, position, level, claimedChunks, bought, coins, lastRanDaily, animalType,
                lastAnimalTypeChange, founder, memberUUIDs, "", List.of());
    }

    public UUID getUUID() { return uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ChunkPos getPosition() { return position; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public List<ChunkPos> getClaimedChunks() { return claimedChunks; }
    public Set<Long> getClaimedChunkSet() { return claimedChunkSet; }
    public void addClaimedChunk(ChunkPos chunkPos) {
        claimedChunks.add(chunkPos);
        claimedChunkSet.add(chunkPos.pack());
    }
    public void removeClaimedChunk(ChunkPos chunkPos) {
        claimedChunks.remove(chunkPos);
        claimedChunkSet.remove(chunkPos.pack());
    }
    public boolean isBought() { return bought; }

    @Override
    public String toString() {
        return "Village ID: " + uuid + ", Owner: " + name + ", Chunks: " + claimedChunks;
    }

    public int getCoins() { return coins; }
    public void setCoins(int coins) { this.coins = coins; }
    public void addCoins(int coins) {
        if (coins < 0) throw new IllegalArgumentException("Cannot add negative coins");
        this.coins += coins;
    }
    public void subtractCoins(int coins) {
        if (coins < 0) throw new IllegalArgumentException("Cannot subtract negative coins");
        this.coins -= coins;
    }
    public boolean hasEnoughCoins(int coins) { return this.coins >= coins; }
    public String getLastRanDaily() { return lastRanDaily != null ? lastRanDaily : "2000-01-01"; }
    public void setLastRanDaily(String date) { lastRanDaily = date; }
    public boolean hasRanTodayAlready() { return java.time.LocalDate.now().toString().equals(getLastRanDaily()); }
    public void markDailyRanToday() { lastRanDaily = java.time.LocalDate.now().toString(); }
    public String getAnimalType() { return animalType != null ? animalType : ""; }
    public void setAnimalType(String animalType) { this.animalType = animalType; }
    public String getLastAnimalTypeChange() {
        return lastAnimalTypeChange != null ? lastAnimalTypeChange : "2000-01-01";
    }
    public void setLastAnimalTypeChange(String date) { lastAnimalTypeChange = date; }
    public boolean canChangeAnimalType() {
        java.time.LocalDate last = java.time.LocalDate.parse(getLastAnimalTypeChange());
        return java.time.LocalDate.now().isAfter(last.plusDays(13));
    }
    public String getFounder() { return founder != null ? founder : ""; }
    public void setFounder(String founder) { this.founder = founder; }
    public List<UUID> getMemberUUIDs() {
        if (memberUUIDs == null) memberUUIDs = new ArrayList<>();
        return memberUUIDs;
    }
    public void addMember(UUID playerUUID) {
        if (memberUUIDs == null) memberUUIDs = new ArrayList<>();
        if (!memberUUIDs.contains(playerUUID)) memberUUIDs.add(playerUUID);
    }
    public void removeMember(UUID playerUUID) {
        if (memberUUIDs != null) memberUUIDs.remove(playerUUID);
    }
    public boolean isMember(UUID playerUUID) { return memberUUIDs != null && memberUUIDs.contains(playerUUID); }
    public String getAnimalGrains() { return animalGrains != null ? animalGrains : ""; }
    public void setAnimalGrains(String animalGrains) { this.animalGrains = animalGrains != null ? animalGrains : ""; }
    public boolean hasAnimalGrainsAssigned() { return animalGrains != null && !animalGrains.isEmpty(); }
    public Map<String, List<String>> getAnimalGrainsMap() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (animalGrains == null || animalGrains.isEmpty()) return map;
        for (String entry : animalGrains.split("\\|")) {
            int eq = entry.indexOf('=');
            if (eq < 0) continue;
            String animal = entry.substring(0, eq);
            String[] grains = entry.substring(eq + 1).split(",");
            List<String> grainList = new ArrayList<>();
            for (String grain : grains) if (!grain.isEmpty()) grainList.add(grain);
            map.put(animal, grainList);
        }
        return map;
    }
    public void setAnimalGrainsFromMap(Map<String, List<String>> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            if (sb.length() > 0) sb.append('|');
            sb.append(entry.getKey()).append('=').append(String.join(",", entry.getValue()));
        }
        animalGrains = sb.toString();
    }
    public List<String> getGrainsForAnimal(String animalType) {
        return getAnimalGrainsMap().getOrDefault(animalType, new ArrayList<>());
    }
    public List<VillagerRecord> getReincarnationPool() {
        if (reincarnationPool == null) reincarnationPool = new ArrayList<>();
        return reincarnationPool;
    }
    public void addToReincarnationPool(VillagerRecord record) {
        if (reincarnationPool == null) reincarnationPool = new ArrayList<>();
        reincarnationPool.add(record);
    }
    public VillagerRecord pollReincarnation() {
        if (reincarnationPool == null || reincarnationPool.isEmpty()) return null;
        return reincarnationPool.remove(0);
    }
}
