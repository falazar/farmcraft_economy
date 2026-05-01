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
            Codec.STRING.optionalFieldOf("animal_grains", "").forGetter(VillageData::getAnimalGrains))
            .apply(instance, VillageData::new));

    private final UUID uuid;
    private String name;
    private final ChunkPos position;
    private int level;
    private List<ChunkPos> claimedChunks;
    private Set<Long> claimedChunkSet = new HashSet<>();
    private final boolean bought; // TODO what is this one? remove?
    private int coins;
    private String lastRanDaily;
    private String animalType; // e.g. "cow", "sheep", "pig", "chicken"
    private String lastAnimalTypeChange;
    private String founder; // Player name who founded the village
    private List<UUID> memberUUIDs; // UUIDs of all players who are members of this village
    private String animalGrains; // Serialized map: "cow=item1,item2|sheep=item3,item4|..."

    /**
     * Constructs a new VillageData object.
     * 
     * @param uuid     unique id of village
     * @param name     the mame of village
     * @param level    the level of village
     * @param position the 3d position of village
     */
    // Full constructor used by Codec.
    public VillageData(UUID uuid, String name, ChunkPos position, int level, List<ChunkPos> claimedChunks,
            boolean bought, int coins, String lastRanDaily, String animalType, String lastAnimalTypeChange,
            String founder,
            List<UUID> memberUUIDs, String animalGrains) {
        this.uuid = uuid;
        this.name = name;
        this.position = position;
        this.level = level;
        this.claimedChunks = new ArrayList<>(claimedChunks);
        this.bought = bought;
        for (ChunkPos pos : claimedChunks) {
            claimedChunkSet.add(ChunkPos.asLong(pos.x, pos.z));
        }
        this.coins = coins;
        this.lastRanDaily = lastRanDaily;
        this.animalType = animalType != null ? animalType : "";
        this.lastAnimalTypeChange = lastAnimalTypeChange != null ? lastAnimalTypeChange : "2000-01-01";
        this.founder = founder != null ? founder : "";
        this.memberUUIDs = memberUUIDs != null ? new ArrayList<>(memberUUIDs) : new ArrayList<>();
        this.animalGrains = animalGrains != null ? animalGrains : "";
    }

    // Convenience constructor for new village creation (no grain assignment yet).
    public VillageData(UUID uuid, String name, ChunkPos position, int level, List<ChunkPos> claimedChunks,
            boolean bought, int coins, String lastRanDaily, String animalType, String lastAnimalTypeChange,
            String founder,
            List<UUID> memberUUIDs) {
        this(uuid, name, position, level, claimedChunks, bought, coins, lastRanDaily, animalType,
                lastAnimalTypeChange, founder, memberUUIDs, "");
    }

    /**
     * Gets the village's unique id.
     * 
     * @return the village's unique id
     */
    public UUID getUUID() {
        return uuid;
    }

    /**
     * Gets the village's name.
     * 
     * @return the village's name
     */
    public String getName() {
        return name;
    }

    // Set village name
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the village's position.
     * 
     * @return the village's position
     */
    public ChunkPos getPosition() {
        return position;
    }

    /**
     * Gets the village's level.
     * 
     * @return the village's level
     */
    public int getLevel() {
        return level;
    }

    // Set level
    public void setLevel(int level) {
        this.level = level;
    }

    public List<ChunkPos> getClaimedChunks() {
        return claimedChunks;
    }

    public Set<Long> getClaimedChunkSet() {
        return claimedChunkSet;
    }

    public void addClaimedChunk(ChunkPos chunkPos) {
        claimedChunks.add(chunkPos);
        claimedChunkSet.add(ChunkPos.asLong(chunkPos.x, chunkPos.z));
    }

    public void removeClaimedChunk(ChunkPos chunkPos) {
        claimedChunks.remove(chunkPos);
        claimedChunkSet.remove(ChunkPos.asLong(chunkPos.x, chunkPos.z));
    }

    public boolean isBought() {
        return bought;
    }

    @Override
    public String toString() {
        return "Village ID: " + uuid + ", Owner: " + name + ", Chunks: " + claimedChunks;
    }

    public int getCoins() {
        return coins;
    }

    public void setCoins(int coins) {
        this.coins = coins;
    }

    public void addCoins(int coins) {
        if (coins < 0) {
            throw new IllegalArgumentException("Cannot add negative coins");
        }
        this.coins += coins;
    }

    public void subtractCoins(int coins) {
        if (coins < 0) {
            throw new IllegalArgumentException("Cannot subtract negative coins");
        }
        this.coins -= coins;
    }

    public boolean hasEnoughCoins(int coins) {
        return this.coins >= coins;
    }

    public String getLastRanDaily() {
        return lastRanDaily != null ? lastRanDaily : "2000-01-01";
    }

    public void setLastRanDaily(String date) {
        this.lastRanDaily = date;
    }

    public boolean hasRanTodayAlready() {
        String today = java.time.LocalDate.now().toString(); // yyyy-MM-dd
        return today.equals(getLastRanDaily());
    }

    public void markDailyRanToday() {
        this.lastRanDaily = java.time.LocalDate.now().toString();
    }

    public String getAnimalType() {
        return animalType != null ? animalType : "";
    }

    public void setAnimalType(String animalType) {
        this.animalType = animalType;
    }

    public String getLastAnimalTypeChange() {
        return lastAnimalTypeChange != null ? lastAnimalTypeChange : "2000-01-01";
    }

    public void setLastAnimalTypeChange(String date) {
        this.lastAnimalTypeChange = date;
    }

    // Returns true if it has been at least 7 days since the animal type was last
    // changed.
    public boolean canChangeAnimalType() {
        java.time.LocalDate last = java.time.LocalDate.parse(getLastAnimalTypeChange());
        return java.time.LocalDate.now().isAfter(last.plusDays(6));
    }

    public String getFounder() {
        return founder != null ? founder : "";
    }

    public void setFounder(String founder) {
        this.founder = founder;
    }

    public List<UUID> getMemberUUIDs() {
        if (memberUUIDs == null)
            memberUUIDs = new ArrayList<>();
        return memberUUIDs;
    }

    public void addMember(UUID playerUUID) {
        if (memberUUIDs == null)
            memberUUIDs = new ArrayList<>();
        if (!memberUUIDs.contains(playerUUID))
            memberUUIDs.add(playerUUID);
    }

    public void removeMember(UUID playerUUID) {
        if (memberUUIDs != null)
            memberUUIDs.remove(playerUUID);
    }

    public boolean isMember(UUID playerUUID) {
        return memberUUIDs != null && memberUUIDs.contains(playerUUID);
    }

    // --- Animal grain requirements ---

    public String getAnimalGrains() {
        return animalGrains != null ? animalGrains : "";
    }

    public void setAnimalGrains(String animalGrains) {
        this.animalGrains = animalGrains != null ? animalGrains : "";
    }

    public boolean hasAnimalGrainsAssigned() {
        return animalGrains != null && !animalGrains.isEmpty();
    }

    /** Returns the grain requirements as a map: animal type -> [grain1, grain2]. */
    public Map<String, List<String>> getAnimalGrainsMap() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (animalGrains == null || animalGrains.isEmpty())
            return map;
        for (String entry : animalGrains.split("\\|")) {
            int eq = entry.indexOf('=');
            if (eq < 0)
                continue;
            String animal = entry.substring(0, eq);
            String[] grains = entry.substring(eq + 1).split(",");
            List<String> grainList = new ArrayList<>();
            for (String g : grains)
                if (!g.isEmpty())
                    grainList.add(g);
            map.put(animal, grainList);
        }
        return map;
    }

    /** Serializes a grain map back into the stored string. */
    public void setAnimalGrainsFromMap(Map<String, List<String>> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : map.entrySet()) {
            if (sb.length() > 0)
                sb.append('|');
            sb.append(e.getKey()).append('=').append(String.join(",", e.getValue()));
        }
        this.animalGrains = sb.toString();
    }

    /**
     * Returns the two required grain item IDs for the given animal type, or empty
     * list.
     */
    public List<String> getGrainsForAnimal(String animalType) {
        Map<String, List<String>> map = getAnimalGrainsMap();
        return map.getOrDefault(animalType, new ArrayList<>());
    }
}